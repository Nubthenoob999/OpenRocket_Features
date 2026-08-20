package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.BarrowmanCalculator;
import info.openrocket.core.aerodynamics.AerodynamicForces;
import info.openrocket.core.aerodynamics.FlightConditions;
import info.openrocket.core.aerodynamics.PhysicsAeroAerodynamicCalculator;
import info.openrocket.core.aerodynamics.physicsaero.flow.AtmosphereState;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroMode;
import info.openrocket.core.aerodynamics.physicsaero.force.AerodynamicCoefficients;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.GeometryExtractor;
import info.openrocket.core.aerodynamics.physicsaero.interpolation.QueryResult;
import info.openrocket.core.aerodynamics.physicsaero.interpolation.TableQueryEngine;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.models.atmosphere.AtmosphericConditions;
import info.openrocket.core.models.atmosphere.ExtendedISAModel;
import info.openrocket.core.startup.OpenRocketCore;

/**
 * Subsonic zero-incidence comparison of the offline table against the
 * established Barrowman axial coefficient, decomposed by drag owner.
 * Reporting only: it changes no correlation and applies no calibration.
 */
@Tag("subsonic-agreement-audit")
class SubsonicBarrowmanAgreementAuditTest {
	private static final Path OUT = Path.of("build/reports/subsonic-agreement-audit");
	private static final double[] SWEEP_MACH = {
			0.05, 0.10, 0.15, 0.20, 0.25, 0.30, 0.40, 0.50,
			0.60, 0.70, 0.75, 0.80, 0.85, 0.90, 0.92, 0.95, 0.98, 0.99
	};

	@BeforeAll
	static void initializeCore() {
		OpenRocketCore.initialize();
	}

	@Test
	void compareSubsonicAxialAgainstBarrowman() throws Exception {
		Path referenceRoot = RocketFlightDatabaseComparisonTest.resolveDirectory(
				"openRocketReferenceDir", "tmp/ref-supersonic");
		RocketFlightDatabaseComparisonTest.preloadRasaeroMotors(List.of(
				referenceRoot.resolve("simvreal/rasp.eng"),
				referenceRoot.resolve("simvreal/Docs/Mesos/O4374_Sea_Level.eng"),
				referenceRoot.resolve("simvreal/Docs/Mesos/M787_Expanded_Nozzle_Sea_Level.eng")));
		Files.createDirectories(OUT);

		StringBuilder summary = new StringBuilder();
		try (BufferedWriter csv = Files.newBufferedWriter(
				OUT.resolve("SUBSONIC_AGREEMENT.csv"), StandardCharsets.UTF_8)) {
			csv.write("flight,mach,barrowman_ca,table_ca,delta,pct,"
					+ "barrowman_friction,table_friction,barrowman_base,table_base,"
					+ "barrowman_pressure,table_pressure\n");
			for (String token : System.getProperty("auditFlightIds", "1,13,15,16,24")
					.split(",")) {
				summary.append(audit(Integer.parseInt(token.trim()), referenceRoot, csv));
			}
		}
		Files.writeString(OUT.resolve("SUBSONIC_AGREEMENT.md"), summary, StandardCharsets.UTF_8);
		System.out.print(summary);
	}

	private String audit(int flightId, Path referenceRoot, BufferedWriter csv) throws Exception {
		Path model = referenceRoot.resolve(
				RocketFlightDatabaseComparisonTest.MODEL_FILES.get(flightId));
		assertTrue(Files.isRegularFile(model), "model missing: " + model);
		OpenRocketDocument document = new GeneralRocketLoader(model.toFile()).load();
		Simulation simulation = RocketFlightDatabaseComparisonTest
				.selectOrSynthesizeSimulation(document, flightId);
		RocketFlightDatabaseComparisonTest.configureDeterministicApogeeRun(simulation, flightId);

		AtmosphericConditions launch = new ExtendedISAModel(
				simulation.getOptions().getLaunchAltitude(),
				simulation.getOptions().getLaunchTemperature(),
				simulation.getOptions().getLaunchPressure(),
				simulation.getOptions().getLaunchRelativeHumidity())
				.getConditions(simulation.getOptions().getLaunchAltitude());
		AtmosphereState launchAtmosphere = new AtmosphereState(launch.getPressure(),
				launch.getTemperature(), launch.getDensity(), launch.getDynamicViscosity());
		boolean turbulent = simulation.getOptions().isForceTurbulentBoundaryLayer();
		double nozzle = simulation.getOptions().getNozzleExitDiameter();
		String settingsHash = RocketFlightDatabaseComparisonTest.referenceSettingsHash(
				launchAtmosphere, turbulent, nozzle);
		AeroGeometry geometry = new GeometryExtractor().extractWithComponentRoughness(
				simulation.getActiveConfiguration(), "ADIABATIC", settingsHash, turbulent);
		var artifact = RocketFlightDatabaseComparisonTest.loadOrBuildTable(
				geometry, settingsHash, launchAtmosphere, nozzle);
		TableQueryEngine engine = new TableQueryEngine();
		var aeroTable = artifact.table();
		BarrowmanCalculator barrowman = new BarrowmanCalculator();
		// Exercise the real runtime path so the measurement includes the
		// subsonic established-axial anchor, not just the raw table cell.
		PhysicsAeroAerodynamicCalculator runtime = new PhysicsAeroAerodynamicCalculator(
				aeroTable, geometry.geometryHash(), settingsHash, artifact.contentHash(),
				PhysicsAeroMode.STRICT, null);

		StringBuilder out = new StringBuilder();
		out.append("\n## Flight ").append(flightId).append('\n');
		out.append("| Mach | Barrowman CA | table CA | delta | % |")
				.append(" fric B/T | base B/T | press B/T |\n");
		out.append("|---:|---:|---:|---:|---:|---:|---:|---:|\n");
		double worst = 0;
		for (double mach : SWEEP_MACH) {
			FlightConditions conditions = new FlightConditions(
					simulation.getActiveConfiguration());
			conditions.setAtmosphericConditions(launch);
			conditions.setMach(mach);
			conditions.setAOA(0);
			AerodynamicForces established = barrowman.getAerodynamicForces(
					simulation.getActiveConfiguration(), conditions, new WarningSet());
			QueryResult query = engine.query(aeroTable, mach, 0, 0);
			Map<String, Double> owners = groupOwners(query);
			AerodynamicForces runtimeForces = runtime.getAerodynamicForces(
					simulation.getActiveConfiguration(), conditions, new WarningSet());

			double bca = established.getCDaxial();
			double tca = runtimeForces.getCDaxial();
			double pct = 100 * (tca - bca) / bca;
			worst = Math.max(worst, Math.abs(pct));
			out.append(String.format(Locale.ROOT,
					"| %.2f | %.4f | %.4f | %+.4f | %+.2f%% | %.4f/%.4f | %.4f/%.4f | %.4f/%.4f |%n",
					mach, bca, tca, tca - bca, pct,
					established.getFrictionCD(), owners.get("friction"),
					established.getBaseCD(), owners.get("base"),
					established.getPressureCD(), owners.get("pressure")));
			csv.write(String.format(Locale.ROOT,
					"%d,%.4f,%.6f,%.6f,%.6f,%.4f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f%n",
					flightId, mach, bca, tca, tca - bca, pct,
					established.getFrictionCD(), owners.get("friction"),
					established.getBaseCD(), owners.get("base"),
					established.getPressureCD(), owners.get("pressure")));
		}
		out.append(String.format(Locale.ROOT,
				"%nworst subsonic deviation: %.2f%% (requirement 0.10%%)%n", worst));

		QueryResult detail = engine.query(aeroTable, 0.5, 0, 0);
		out.append("\nowner ledger at Mach 0.50:\n\n");
		new TreeMap<>(detail.ownerTotals()).forEach((key, value) -> {
			if (Math.abs(value.ca()) > 1e-9) {
				out.append(String.format(Locale.ROOT, "- `%s` = %.4f%n", key, value.ca()));
			}
		});
		return out.toString();
	}

	private static Map<String, Double> groupOwners(QueryResult query) {
		Map<String, Double> grouped = new TreeMap<>();
		grouped.put("friction", 0.0);
		grouped.put("base", 0.0);
		grouped.put("pressure", 0.0);
		for (Map.Entry<String, AerodynamicCoefficients> entry : query.ownerTotals().entrySet()) {
			String key = entry.getKey();
			double ca = entry.getValue().ca();
			String bucket = key.contains("SKIN_FRICTION") ? "friction"
					: key.contains("BASE") && key.contains("PRESSURE_DRAG") ? "base"
					: "pressure";
			grouped.merge(bucket, ca, Double::sum);
		}
		return grouped;
	}
}

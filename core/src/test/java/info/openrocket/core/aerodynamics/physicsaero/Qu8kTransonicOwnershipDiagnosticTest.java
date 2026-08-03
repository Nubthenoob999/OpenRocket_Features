package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.physicsaero.flow.AtmosphereState;
import info.openrocket.core.aerodynamics.physicsaero.coupling.FinnedBasePressureClosureModel;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.PerfectGasAir;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.GeometryExtractor;
import info.openrocket.core.aerodynamics.physicsaero.integration.FullRegimeTableBuilder;
import info.openrocket.core.aerodynamics.physicsaero.integration.PhysicsAeroValidationGate;
import info.openrocket.core.aerodynamics.physicsaero.table.TableMetadata;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.models.atmosphere.ExtendedISAModel;
import info.openrocket.core.startup.OpenRocketCore;

/**
 * Diagnostic guard for the Qu8k terminal-boattail handoff.  RASAero II 1.0.2.0
 * Run Test coast-CD anchors are 0.696, 0.677, and 0.641 at Mach 1.15, 1.20,
 * and 1.30 respectively (the independently exported CDDataFile, rows 804,
 * 839, and 909).  These are comparison anchors, not calibration targets.
 */
@Tag("benchmark")
@Tag("flight-database")
class Qu8kTransonicOwnershipDiagnosticTest {
	private static final String SETTINGS = "qu8k-transonic-ownership-diagnostic-v1";
	private static final double[] MACH = {1.15, 1.20, 1.30};
	private static final double[] RASAERO_COAST_CD = {0.696, 0.677, 0.641};
	private static final double[] ACCEPTED_V83_COAST_CD = {0.671492200, 0.649602771, 0.661637959};

	@BeforeAll
	static void initializeCore() {
		OpenRocketCore.initialize();
	}

	@Test
	void importedQu8kExposesTransonicBaseAndBoattailOwnersWithoutFlightTuning() throws Exception {
		Path model = resolveQu8kModel();
		Assumptions.assumeTrue(Files.isRegularFile(model),
				"Qu8k CDX1 external diagnostic fixture is unavailable: " + model);

		OpenRocketDocument document = new GeneralRocketLoader(model.toFile()).load();
		Simulation simulation = document.getSimulations().get(0);
		PerfectGasAir air = new PerfectGasAir();
		var launch = new ExtendedISAModel(simulation.getOptions().getLaunchAltitude(),
				simulation.getOptions().getLaunchTemperature(),
				simulation.getOptions().getLaunchPressure(),
				simulation.getOptions().getLaunchRelativeHumidity())
				.getConditions(simulation.getOptions().getLaunchAltitude());
		AtmosphereState atmosphere = new AtmosphereState(launch.getPressure(),
				launch.getTemperature(), launch.getDensity(), launch.getDynamicViscosity());
		AeroGeometry geometry = new GeometryExtractor().extractWithComponentRoughness(
				simulation.getActiveConfiguration(), "ADIABATIC", SETTINGS,
				simulation.getOptions().isForceTurbulentBoundaryLayer());
		TableMetadata metadata = new TableMetadata(TableMetadata.CURRENT_SCHEMA,
				geometry.geometryHash(), SETTINGS, PhysicsAeroValidationGate.CODE_VERSION,
				PhysicsAeroValidationGate.REGISTRY_VERSION, TableMetadata.REQUIRED_UNITS,
				TableMetadata.REQUIRED_AXIS_CONVENTION, Instant.parse("2026-08-01T00:00:00Z"),
				Map.of("referenceAreaM2", geometry.references().referenceAreaM2()), Map.of(), "TEST");
		var table = new FullRegimeTableBuilder().build(geometry, MACH,
				new double[] {0}, new double[] {0}, atmosphere, air, metadata);

		for (int index = 0; index < MACH.length; index++) {
			var cell = table.cell(index, 0, 0);
			double totalCd = cell.coefficients().ca();
			System.out.printf("Qu8k M%.2f: strict CD=%.9f, RASAero CD=%.3f, delta=%+.9f%n",
					MACH[index], totalCd, RASAERO_COAST_CD[index],
					totalCd - RASAERO_COAST_CD[index]);
			assertEquals(ACCEPTED_V83_COAST_CD[index], totalCd, 1.0e-8,
					"strict-table diagnostic drift at Mach " + MACH[index]
							+ "; owners=" + cell.ownerTotals());
			assertEquals(totalCd, cell.ownerTotals().values().stream()
					.mapToDouble(value -> value.ca()).sum(), 1.0e-12,
					"owner recovery at Mach " + MACH[index]);
		}

		var m120 = table.cell(1, 0, 0);
		Map<String, info.openrocket.core.aerodynamics.physicsaero.force.AerodynamicCoefficients> owners =
				m120.ownerTotals();
		assertEquals(0.091800000, owners.get("BOATTAIL_PRESSURE_DRAG").ca(), 1.0e-8);
		assertEquals(0.130560000, owners.get("BODY_BASE_PRESSURE_DRAG").ca(), 1.0e-8);
		assertEquals(0, owners.get("FINNED_BODY_BASE_PRESSURE_CLOSURE").ca(), 0);
		assertFalse(m120.methodIds().contains(FinnedBasePressureClosureModel.METHOD_ID));
		assertTrue(owners.get("BOATTAIL_PRESSURE_DRAG").ca() > 0
				&& owners.get("BODY_BASE_PRESSURE_DRAG").ca() > 0,
				"primary base and boattail owners must remain active: " + owners);
	}

	private static Path resolveQu8kModel() {
		String configured = System.getProperty("openrocket.qu8kDiagnosticModel", "").trim();
		if (!configured.isEmpty()) return Path.of(configured).toAbsolutePath().normalize();
		Path relative = Path.of("build", "research", "openrocketsupersonic-index", "simvreal",
				"RasAero Sims", "Qu8k.CDX1");
		for (Path current = Path.of("").toAbsolutePath().normalize(); current != null;
				current = current.getParent()) {
			Path candidate = current.resolve(relative).normalize();
			if (Files.isRegularFile(candidate)) return candidate;
		}
		return relative.toAbsolutePath().normalize();
	}
}

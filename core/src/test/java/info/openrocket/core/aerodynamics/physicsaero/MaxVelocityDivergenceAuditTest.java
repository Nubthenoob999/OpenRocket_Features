package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.physicsaero.flow.AtmosphereState;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.GeometryExtractor;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroMode;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.models.atmosphere.ExtendedISAModel;
import info.openrocket.core.simulation.FlightData;
import info.openrocket.core.simulation.FlightDataBranch;
import info.openrocket.core.simulation.FlightDataType;
import info.openrocket.core.simulation.listeners.system.BoundedApogeeEndListener;
import info.openrocket.core.startup.OpenRocketCore;

/**
 * Controlled table-OFF / table-ON A/B for one flight, writing both synchronized
 * trajectories plus a first-divergence report. Reporting only: it changes no
 * correlation and applies no calibration.
 */
@Tag("velocity-divergence-audit")
class MaxVelocityDivergenceAuditTest {
	private static final Path OUT = Path.of("build/reports/max-velocity-audit");

	@BeforeAll
	static void initializeCore() {
		OpenRocketCore.initialize();
	}

	@Test
	void traceFirstDivergence() throws Exception {
		Path referenceRoot = RocketFlightDatabaseComparisonTest.resolveDirectory(
				"openRocketReferenceDir", "tmp/ref-supersonic");
		RocketFlightDatabaseComparisonTest.preloadRasaeroMotors(List.of(
				referenceRoot.resolve("simvreal/rasp.eng"),
				referenceRoot.resolve("simvreal/Docs/Mesos/O4374_Sea_Level.eng"),
				referenceRoot.resolve("simvreal/Docs/Mesos/M787_Expanded_Nozzle_Sea_Level.eng")));
		Files.createDirectories(OUT);

		String idProperty = System.getProperty("auditFlightIds", "1,13,16,24").trim();
		StringBuilder summary = new StringBuilder();
		for (String token : idProperty.split(",")) {
			int flightId = Integer.parseInt(token.trim());
			summary.append(audit(flightId, referenceRoot));
		}
		Files.writeString(OUT.resolve("MAX_VELOCITY_AUDIT.md"), summary, StandardCharsets.UTF_8);
		System.out.print(summary);
	}

	private String audit(int flightId, Path referenceRoot) throws Exception {
		Path model = referenceRoot.resolve(
				RocketFlightDatabaseComparisonTest.MODEL_FILES.get(flightId));
		assertTrue(Files.isRegularFile(model), "model missing: " + model);

		// --- table OFF (established Barrowman) ---
		OpenRocketDocument baseDoc = new GeneralRocketLoader(model.toFile()).load();
		Simulation baseSim = RocketFlightDatabaseComparisonTest
				.selectOrSynthesizeSimulation(baseDoc, flightId);
		RocketFlightDatabaseComparisonTest.configureDeterministicApogeeRun(baseSim, flightId);
		baseSim.getOptions().setPhysicsAeroMode(PhysicsAeroMode.OFF);
		baseSim.simulate(new BoundedApogeeEndListener());
		FlightDataBranch baseBranch = baseSim.getSimulatedData().getBranch(0);

		// --- table ON (strict physics-aero) ---
		OpenRocketDocument tableDoc = new GeneralRocketLoader(model.toFile()).load();
		Simulation tableSim = RocketFlightDatabaseComparisonTest
				.selectOrSynthesizeSimulation(tableDoc, flightId);
		RocketFlightDatabaseComparisonTest.configureDeterministicApogeeRun(tableSim, flightId);
		var launchConditions = new ExtendedISAModel(
				tableSim.getOptions().getLaunchAltitude(),
				tableSim.getOptions().getLaunchTemperature(),
				tableSim.getOptions().getLaunchPressure(),
				tableSim.getOptions().getLaunchRelativeHumidity())
				.getConditions(tableSim.getOptions().getLaunchAltitude());
		AtmosphereState launchAtmosphere = new AtmosphereState(
				launchConditions.getPressure(), launchConditions.getTemperature(),
				launchConditions.getDensity(), launchConditions.getDynamicViscosity());
		boolean turbulent = tableSim.getOptions().isForceTurbulentBoundaryLayer();
		double nozzleExitDiameterM = tableSim.getOptions().getNozzleExitDiameter();
		String settingsHash = RocketFlightDatabaseComparisonTest.referenceSettingsHash(
				launchAtmosphere, turbulent, nozzleExitDiameterM);
		AeroGeometry geometry = new GeometryExtractor().extractWithComponentRoughness(
				tableSim.getActiveConfiguration(), "ADIABATIC", settingsHash, turbulent);
		var artifact = RocketFlightDatabaseComparisonTest.loadOrBuildTable(
				geometry, settingsHash, launchAtmosphere, nozzleExitDiameterM);
		tableSim.getOptions().setPhysicsAeroTableIdentity(artifact.table(), artifact.contentHash());
		tableSim.getOptions().setPhysicsAeroMode(PhysicsAeroMode.STRICT);
		tableSim.simulate(new BoundedApogeeEndListener());
		FlightDataBranch tableBranch = tableSim.getSimulatedData().getBranch(0);

		writeTrace(flightId, baseBranch, tableBranch);
		return report(flightId, baseSim.getSimulatedData(), tableSim.getSimulatedData(),
				baseBranch, tableBranch);
	}

	private static double[] col(FlightDataBranch branch, FlightDataType type) {
		List<Double> values = branch.get(type);
		if (values == null) return new double[0];
		double[] out = new double[values.size()];
		for (int i = 0; i < out.length; i++) out[i] = values.get(i);
		return out;
	}

	private static double at(double[] values, int index) {
		return index >= 0 && index < values.length ? values[index] : Double.NaN;
	}

	private void writeTrace(int flightId, FlightDataBranch base, FlightDataBranch table)
			throws Exception {
		double[] bt = col(base, FlightDataType.TYPE_TIME);
		double[] tt = col(table, FlightDataType.TYPE_TIME);
		double[][] b = series(base);
		double[][] t = series(table);
		Path output = OUT.resolve(String.format(Locale.ROOT, "AERO_FORCE_TRACE-%02d.csv", flightId));
		try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
			writer.write("time_s,b_alt,t_alt,b_vel,t_vel,b_mach,t_mach,b_thrust,t_thrust,"
					+ "b_drag_N,t_drag_N,b_cd,t_cd,b_cdfric,t_cdfric,b_cdpress,t_cdpress,"
					+ "b_cdbase,t_cdbase,b_mass,t_mass,b_aoa_deg,t_aoa_deg,b_rho,t_rho\n");
			int n = Math.min(bt.length, tt.length);
			for (int i = 0; i < n; i++) {
				StringBuilder line = new StringBuilder();
				line.append(String.format(Locale.ROOT, "%.6f", bt[i]));
				for (int s = 0; s < b.length; s++) {
					line.append(String.format(Locale.ROOT, ",%.6f,%.6f", at(b[s], i), at(t[s], i)));
				}
				writer.write(line.append('\n').toString());
			}
		}
	}

	private static double[][] series(FlightDataBranch branch) {
		return new double[][] {
				col(branch, FlightDataType.TYPE_ALTITUDE),
				col(branch, FlightDataType.TYPE_VELOCITY_TOTAL),
				col(branch, FlightDataType.TYPE_MACH_NUMBER),
				col(branch, FlightDataType.TYPE_THRUST_FORCE),
				col(branch, FlightDataType.TYPE_DRAG_FORCE),
				col(branch, FlightDataType.TYPE_DRAG_COEFF),
				col(branch, FlightDataType.TYPE_FRICTION_DRAG_COEFF),
				col(branch, FlightDataType.TYPE_PRESSURE_DRAG_COEFF),
				col(branch, FlightDataType.TYPE_BASE_DRAG_COEFF),
				col(branch, FlightDataType.TYPE_MASS),
				col(branch, FlightDataType.TYPE_AOA),
				col(branch, FlightDataType.TYPE_AIR_DENSITY),
		};
	}

	private String report(int flightId, FlightData baseData, FlightData tableData,
			FlightDataBranch base, FlightDataBranch table) {
		double[] bt = col(base, FlightDataType.TYPE_TIME);
		double[] tt = col(table, FlightDataType.TYPE_TIME);
		double[] bv = col(base, FlightDataType.TYPE_VELOCITY_TOTAL);
		double[] tv = col(table, FlightDataType.TYPE_VELOCITY_TOTAL);
		double[] bcd = col(base, FlightDataType.TYPE_DRAG_COEFF);
		double[] tcd = col(table, FlightDataType.TYPE_DRAG_COEFF);
		double[] bd = col(base, FlightDataType.TYPE_DRAG_FORCE);
		double[] td = col(table, FlightDataType.TYPE_DRAG_FORCE);
		double[] bm = col(base, FlightDataType.TYPE_MACH_NUMBER);
		double[] bfr = col(base, FlightDataType.TYPE_FRICTION_DRAG_COEFF);
		double[] tfr = col(table, FlightDataType.TYPE_FRICTION_DRAG_COEFF);
		double[] bpr = col(base, FlightDataType.TYPE_PRESSURE_DRAG_COEFF);
		double[] tpr = col(table, FlightDataType.TYPE_PRESSURE_DRAG_COEFF);
		double[] bba = col(base, FlightDataType.TYPE_BASE_DRAG_COEFF);
		double[] tba = col(table, FlightDataType.TYPE_BASE_DRAG_COEFF);

		int n = Math.min(bt.length, tt.length);
		int firstCd = -1;
		int firstVel = -1;
		for (int i = 0; i < n; i++) {
			if (firstCd < 0 && differs(at(bcd, i), at(tcd, i), 1e-9, 1e-6)) firstCd = i;
			if (firstVel < 0 && differs(at(bv, i), at(tv, i), 1e-6, 1e-6)) firstVel = i;
		}

		StringBuilder out = new StringBuilder();
		out.append("\n## Flight ").append(flightId).append('\n');
		out.append(String.format(Locale.ROOT,
				"baseline Vmax %.3f m/s | table Vmax %.3f m/s | dV %.3f m/s%n",
				maximum(bv), maximum(tv), maximum(tv) - maximum(bv)));
		out.append(String.format(Locale.ROOT,
				"baseline apogee %.2f m | table apogee %.2f m | %+.2f%%%n",
				baseData.getMaxAltitude(), tableData.getMaxAltitude(),
				100 * (tableData.getMaxAltitude() - baseData.getMaxAltitude())
						/ baseData.getMaxAltitude()));
		out.append(String.format(Locale.ROOT,
				"first CD divergence idx %d (t=%.3f s) | first velocity divergence idx %d (t=%.3f s)%n",
				firstCd, at(bt, firstCd), firstVel, at(bt, firstVel)));

		out.append("\n| t | Mach | b_CD | t_CD | dCD | b_fric | t_fric | b_press | t_press |")
				.append(" b_base | t_base | b_drag N | t_drag N |\n");
		out.append("|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
		int step = Math.max(1, n / 40);
		for (int i = 0; i < n; i += step) {
			out.append(String.format(Locale.ROOT,
					"| %.2f | %.3f | %.4f | %.4f | %+.4f | %.4f | %.4f | %.4f | %.4f | %.4f | %.4f | %.1f | %.1f |%n",
					at(bt, i), at(bm, i), at(bcd, i), at(tcd, i), at(tcd, i) - at(bcd, i),
					at(bfr, i), at(tfr, i), at(bpr, i), at(tpr, i), at(bba, i), at(tba, i),
					at(bd, i), at(td, i)));
		}
		return out.toString();
	}

	private static boolean differs(double a, double b, double absTol, double relTol) {
		if (Double.isNaN(a) || Double.isNaN(b)) return false;
		double scale = Math.max(Math.abs(a), Math.abs(b));
		return Math.abs(a - b) > Math.max(absTol, relTol * scale);
	}

	private static double maximum(double[] values) {
		double result = Double.NEGATIVE_INFINITY;
		for (double value : values) if (Double.isFinite(value)) result = Math.max(result, value);
		return result;
	}
}

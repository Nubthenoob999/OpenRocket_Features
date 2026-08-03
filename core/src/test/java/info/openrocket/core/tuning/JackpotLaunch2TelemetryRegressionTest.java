package info.openrocket.core.tuning;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroRuntimeReport;
import info.openrocket.core.simulation.FlightData;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class JackpotLaunch2TelemetryRegressionTest {

	@Test
	public void jackpotTableRunProducesAValidDiagnosticTrajectory() throws Exception {
		double actualFeet = representativeTelemetryApogeeFeet();
		Simulation source = loadSource();
		SimulationRun baseline = simulate(baselineVariant(source));
		SimulationRun table = simulate(tableVariant(source));

		System.out.printf(Locale.US,
				"Jackpot actualRepresentative=%.2f ft baseline=%.2f ft table=%.2f ft tableQueries=%d fallbacks=%d%n",
				actualFeet, baseline.apogeeFeet(), table.apogeeFeet(),
				table.runtime().successfulTableQueries(), table.runtime().fallbackCount());

		double tableRelativeError = Math.abs(table.apogeeFeet() - actualFeet) / actualFeet;
		assertTrue(table.runtime().tableValid(), "Expected a verified table artifact");
		assertTrue(table.runtime().successfulTableQueries() > 0,
				"Expected the flight to exercise the physics-aero table");
		assertTrue(tableRelativeError < 0.50,
				"Expected the diagnostic table trajectory to remain within a broad tuning band. actual="
						+ actualFeet + " table=" + table.apogeeFeet());
	}

	private static double representativeTelemetryApogeeFeet() throws Exception {
		List<Double> apogeesFeet = new ArrayList<>();
		apogeesFeet.add(maxAltitudeFeet(TelemetryParsers.parse(resolve("ab_jackpot_launch_2.csv"))));
		apogeesFeet.add(maxAltitudeFeet(TelemetryParsers.parse(resolve("easymini_launch_2.csv"))));
		apogeesFeet.add(maxAltitudeFeet(TelemetryParsers.parse(resolve("fluctus_launch_2.csv"))));
		apogeesFeet.removeIf(value -> value == null || !Double.isFinite(value));
		Collections.sort(apogeesFeet);
		return apogeesFeet.get(apogeesFeet.size() / 2);
	}

	private static double maxAltitudeFeet(TelemetrySeries series) {
		double maxMeters = DerivedTelemetryQuantities.summarize(series).getApogeeAltitudeMeters();
		return maxMeters * 3.280839895;
	}

	private static Simulation loadSource() throws Exception {
		TuningTestInfrastructure.ensureApplicationInjector();
		GeneralRocketLoader loader = new GeneralRocketLoader(resolve("NASA_26_PDF_Config_Something.ork").toFile());
		OpenRocketDocument document = loader.load();
		return document.getSimulations().get(0);
	}

	private static Simulation baselineVariant(Simulation source) {
		Simulation simulation = source.clone(false);
		simulation.getOptions().setPhysicsAeroEnabled(false);
		return simulation;
	}

	private static Simulation tableVariant(Simulation source) {
		Simulation simulation = source.clone(false);
		PhaseThreeNativeAirbrakesConfigurer.forceTableRuntime(simulation.getOptions(), null);
		return simulation;
	}

	private static SimulationRun simulate(Simulation simulation) throws Exception {
		PhysicsAeroTuningSupport.prepareTableIfEnabled(simulation);
		simulation.simulate();
		FlightData data = simulation.getSimulatedData();
		return new SimulationRun(data.getMaxAltitude() * 3.280839895,
				simulation.getPhysicsAeroRuntimeReport());
	}

	private record SimulationRun(double apogeeFeet, PhysicsAeroRuntimeReport runtime) {
	}

	private static Path resolve(String file) {
		return Path.of("src", "test", "java", "info", "openrocket", "core", "tuning",
				"Jackpot_Launch_2", file);
	}
}

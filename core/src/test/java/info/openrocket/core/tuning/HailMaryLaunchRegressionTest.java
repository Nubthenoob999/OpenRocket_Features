package info.openrocket.core.tuning;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.simulation.FlightData;
import info.openrocket.core.startup.Application;
import info.openrocket.core.startup.OpenRocketCore;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class HailMaryLaunchRegressionTest {

	private static final Path ORK_PATH = Path.of(
			"C:\\Users\\Opteron92\\SchoolStuff\\7th - 8th Senior Design\\Hail Mary Launch\\NASA_26_PDF_Config.ork");
	private static final Path TELEMETRY_PATH = Path.of(
			"H:\\Shared drives\\HPRC\\1_NASA SL 2025-2026\\Senior Design\\Subteams\\Aerodynamics\\PDF\\PDF_2\\fluctus_launch_3.csv");

	@Test
	public void hailMaryRomRunProducesMeaningfulAscentNearTelemetry() throws Exception {
		Assumptions.assumeTrue(Files.exists(ORK_PATH), "Hail Mary ORK not available");
		Assumptions.assumeTrue(Files.exists(TELEMETRY_PATH), "Hail Mary telemetry not available");

		double actualFeet = telemetryBaroApogeeFeet();
		Simulation loaded = loadSource();
		double savedFeet = savedApogeeFeet(loaded);
		double rerunFeet = simulateFeet(loaded.clone(false));

		System.out.printf(Locale.US,
				"HailMary actual=%.2f ft saved=%.2f ft rerun=%.2f ft%n",
				actualFeet, savedFeet, rerunFeet);

		assertTrue(Math.abs(rerunFeet - actualFeet) < 500.0,
				"Expected rerun to stay close to telemetry. actual=" + actualFeet + " rerun=" + rerunFeet);
		assertTrue(Math.abs(rerunFeet - savedFeet) < 500.0,
				"Expected rerun to stay close to previously good saved run. saved=" + savedFeet + " rerun=" + rerunFeet);
	}

	private static Simulation loadSource() throws Exception {
		ensureApplicationInjector();
		GeneralRocketLoader loader = new GeneralRocketLoader(ORK_PATH.toFile());
		OpenRocketDocument document = loader.load();
		for (Simulation simulation : document.getSimulations()) {
			if (!"PDF Flight W/AB 4/4/2026".equals(simulation.getName())) {
				continue;
			}
			if (!simulation.getOptions().isAirbrakesEnabled()) {
				continue;
			}
			if (Math.abs(simulation.getOptions().getTargetApogee() - 1219.2) > 1e-6) {
				continue;
			}
			return simulation;
		}
		throw new IllegalStateException("Could not find the expected Hail Mary 4D airbrake simulation");
	}

	private static double simulateFeet(Simulation simulation) throws Exception {
		simulation.simulate();
		FlightData data = simulation.getSimulatedData();
		return data.getMaxAltitude() * 3.280839895;
	}

	private static double savedApogeeFeet(Simulation simulation) {
		FlightData data = simulation.getSimulatedData();
		if (data == null) {
			return Double.NaN;
		}
		return data.getMaxAltitude() * 3.280839895;
	}

	private static double telemetryBaroApogeeFeet() throws Exception {
		double maxMeters = Double.NEGATIVE_INFINITY;
		try (BufferedReader reader = Files.newBufferedReader(TELEMETRY_PATH)) {
			String header = reader.readLine();
			if (header == null) {
				return Double.NaN;
			}
			String line;
			while ((line = reader.readLine()) != null) {
				String[] tokens = line.split(",", 5);
				if (tokens.length < 4) {
					continue;
				}
				try {
					double altitudeMeters = Double.parseDouble(tokens[3].trim());
					maxMeters = Math.max(maxMeters, altitudeMeters);
				} catch (NumberFormatException ignored) {
				}
			}
		}
		return maxMeters * 3.280839895;
	}

	private static synchronized void ensureApplicationInjector() {
		if (Application.getInjector() != null && OpenRocketCore.isInitialized()) {
			return;
		}
		OpenRocketCore.initialize();
	}
}

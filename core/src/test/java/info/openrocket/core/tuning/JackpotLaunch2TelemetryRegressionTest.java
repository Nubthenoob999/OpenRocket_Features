package info.openrocket.core.tuning;

import info.openrocket.core.aerodynamics.rom.RomSurfaceMode;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.simulation.FlightData;
import info.openrocket.core.startup.Application;
import info.openrocket.core.startup.OpenRocketCore;
import org.junit.jupiter.api.Assumptions;
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
	public void jackpotRomRunIsCloserToTelemetryThanBaseline() throws Exception {
		double actualFeet = representativeTelemetryApogeeFeet();
		Simulation source = loadSource();
		Assumptions.assumeTrue(hasEmbeddedRomData(source),
				"Jackpot launch 2 ORK does not contain embedded ROM surfaces for a 3D/4D comparison");
		double baselineFeet = simulateFeet(baselineVariant(source));
		double romFeet = simulateFeet(source.clone(false));

		System.out.printf(Locale.US,
				"Jackpot actualRepresentative=%.2f ft baseline=%.2f ft rom4d=%.2f ft%n",
				actualFeet, baselineFeet, romFeet);

		double romError = Math.abs(romFeet - actualFeet);
		double baselineError = Math.abs(baselineFeet - actualFeet);
		assertTrue(romError < baselineError,
				"Expected Jackpot ROM run to be closer to telemetry than baseline. actual="
						+ actualFeet + " baseline=" + baselineFeet + " rom=" + romFeet);
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
		ensureApplicationInjector();
		GeneralRocketLoader loader = new GeneralRocketLoader(resolve("NASA_26_PDF_Config_Something.ork").toFile());
		OpenRocketDocument document = loader.load();
		return document.getSimulations().get(0);
	}

	private static Simulation baselineVariant(Simulation source) {
		Simulation simulation = source.clone(false);
		simulation.getOptions().setRomSurfaceMode(RomSurfaceMode.THREE_D);
		simulation.getOptions().setRomDragSurface(null);
		simulation.getOptions().setRomAeroSurface4D(null);
		return simulation;
	}

	private static double simulateFeet(Simulation simulation) throws Exception {
		simulation.simulate();
		FlightData data = simulation.getSimulatedData();
		return data.getMaxAltitude() * 3.280839895;
	}

	private static Path resolve(String file) {
		return Path.of("src", "test", "java", "info", "openrocket", "core", "tuning",
				"Jackpot_Launch_2", file);
	}

	private static boolean hasEmbeddedRomData(Simulation simulation) {
		return simulation.getOptions().hasRomAeroSurface4D() || simulation.getOptions().hasRomDragSurface();
	}

	private static synchronized void ensureApplicationInjector() {
		if (Application.getInjector() != null && OpenRocketCore.isInitialized()) {
			return;
		}
		OpenRocketCore.initialize();
	}
}

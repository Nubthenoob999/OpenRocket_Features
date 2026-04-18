package info.openrocket.core.tuning;

import info.openrocket.core.aerodynamics.rom.RomFallbackMode;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.simulation.FlightData;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RomHighMachDiagnosticsTest {

	private static final double APOGEE_TOLERANCE_FEET = 0.1;
	private static final double BASELINE_DELTA_FRACTION_MAX = 0.02;

	@Test
	public void pelencatorPathlineRunRemainsConservativeAgainstBaseline() throws Exception {
		Simulation source = loadSource("Pelencator_Launch_1", "VDF_Launch_1.ork");
		VariantResults results = simulateVariants(source);
		assertNotNull(results.pathline);
		assertNotNull(results.baseline);
		assertTrue(withinBaselineBand(results.pathline.apogeeFeet, results.baseline.apogeeFeet),
				"Pathline run should stay within 2% of the pure baseline on Pelencator. baseline="
						+ results.baseline.apogeeFeet + " pathline=" + results.pathline.apogeeFeet);
		assertTrue(results.pathline.maxMach > 0.30,
				"Pelencator pathline regression should remain in the high-Mach envelope. maxMach="
						+ results.pathline.maxMach);
	}

	@Test
	public void jackpotPathlineRunRemainsConservativeAgainstBaseline() throws Exception {
		Simulation source = loadSource("Jackpot_Launch_2", "NASA_26_PDF_Config_Something.ork");
		VariantResults results = simulateVariants(source);
		assertNotNull(results.pathline);
		assertNotNull(results.baseline);
		assertTrue(withinBaselineBand(results.pathline.apogeeFeet, results.baseline.apogeeFeet),
				"Pathline run should stay within 2% of the pure baseline on Jackpot. baseline="
						+ results.baseline.apogeeFeet + " pathline=" + results.pathline.apogeeFeet);
		assertTrue(results.pathline.maxMach > 0.40,
				"Jackpot pathline regression should remain in the high-Mach envelope. maxMach="
						+ results.pathline.maxMach);
	}

	@Test
	public void governmentWorkLaunch2StillLoadsAndSimulatesPathline() throws Exception {
		VariantResults results = simulateVariants(loadSource("Government_Work_Launch_2", "NASA_26_Subscale_2.ork"));
		assertNotNull(results.pathline);
		assertTrue(results.pathline.apogeeFeet > 500.0,
				"Government Work launch 2 pathline run should produce a meaningful ascent. apogeeFeet="
						+ results.pathline.apogeeFeet);
		assertTrue(results.pathline.maxMach > 0.20,
				"Government Work launch 2 pathline run should remain in a non-trivial flight regime. maxMach="
						+ results.pathline.maxMach);
	}

	private static Simulation loadSource(String folder, String file) throws Exception {
		TuningTestInfrastructure.ensureApplicationInjector();
		File orkFile = Path.of("src", "test", "java", "info", "openrocket", "core", "tuning", folder, file).toFile();
		GeneralRocketLoader loader = new GeneralRocketLoader(orkFile);
		OpenRocketDocument document = loader.load();
		return document.getSimulations().get(0);
	}

	private static VariantResults simulateVariants(Simulation source) throws Exception {
		return new VariantResults(
				simulate(source.clone(false)),
				simulate(baselineVariant(source)),
				simulate(pathlineVariant(source)));
	}

	private static Simulation baselineVariant(Simulation source) {
		Simulation simulation = source.clone(false);
		simulation.getOptions().setRomEnabled(false);
		simulation.getOptions().setRomFallbackMode(RomFallbackMode.BARROWMAN_ONLY);
		return simulation;
	}

	private static Simulation pathlineVariant(Simulation source) {
		Simulation simulation = source.clone(false);
		PhaseThreeNativeAirbrakesConfigurer.forcePathlineRuntime(simulation.getOptions(), null);
		return simulation;
	}

	private static SimulationResult simulate(Simulation simulation) throws Exception {
		simulation.simulate();
		FlightData data = simulation.getSimulatedData();
		double apogeeMeters = data.getMaxAltitude();
		double apogeeFeet = apogeeMeters * 3.280839895;
		double maxMach = data.getMaxMachNumber();
		return new SimulationResult(apogeeFeet, apogeeMeters, maxMach);
	}

	private record SimulationResult(double apogeeFeet, double apogeeMeters, double maxMach) {
	}

	private record VariantResults(
			SimulationResult saved,
			SimulationResult baseline,
			SimulationResult pathline) {
	}

	private static boolean withinBaselineBand(double candidateFeet, double baselineFeet) {
		double tolerance = Math.max(APOGEE_TOLERANCE_FEET, Math.abs(baselineFeet) * BASELINE_DELTA_FRACTION_MAX);
		return Math.abs(candidateFeet - baselineFeet) <= tolerance;
	}
}

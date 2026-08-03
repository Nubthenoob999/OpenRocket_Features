package info.openrocket.core.tuning;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroRuntimeReport;
import info.openrocket.core.simulation.FlightData;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PhysicsAeroHighMachDiagnosticsTest {

	@Test
	public void pelencatorTableRunProducesAValidHighMachTrajectory() throws Exception {
		Simulation source = loadSource("Pelencator_Launch_1", "VDF_Launch_1.ork");
		VariantResults results = simulateVariants(source);
		assertNotNull(results.table);
		assertNotNull(results.baseline);
		assertTableRuntimeUsed(results.table, "Pelencator");
		assertTrue(results.table.apogeeFeet > 500.0);
		assertTrue(results.table.maxMach > 0.30,
				"Pelencator table regression should remain in the high-Mach envelope. maxMach="
						+ results.table.maxMach);
	}

	@Test
	public void jackpotTableRunProducesAValidHighMachTrajectory() throws Exception {
		Simulation source = loadSource("Jackpot_Launch_2", "NASA_26_PDF_Config_Something.ork");
		VariantResults results = simulateVariants(source);
		assertNotNull(results.table);
		assertNotNull(results.baseline);
		assertTableRuntimeUsed(results.table, "Jackpot");
		assertTrue(results.table.apogeeFeet > 500.0);
		assertTrue(results.table.maxMach > 0.40,
				"Jackpot table regression should remain in the high-Mach envelope. maxMach="
						+ results.table.maxMach);
	}

	@Test
	public void governmentWorkLaunch2StillLoadsAndSimulatesWithTable() throws Exception {
		VariantResults results = simulateVariants(loadSource("Government_Work_Launch_2", "NASA_26_Subscale_2.ork"));
		assertNotNull(results.table);
		assertTableRuntimeUsed(results.table, "Government Work launch 2");
		assertTrue(results.table.apogeeFeet > 500.0,
				"Government Work launch 2 table run should produce a meaningful ascent. apogeeFeet="
						+ results.table.apogeeFeet);
		assertTrue(results.table.maxMach > 0.20,
				"Government Work launch 2 table run should remain in a non-trivial flight regime. maxMach="
						+ results.table.maxMach);
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
				simulate(tableVariant(source)));
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

	private static SimulationResult simulate(Simulation simulation) throws Exception {
		PhysicsAeroTuningSupport.prepareTableIfEnabled(simulation);
		simulation.simulate();
		FlightData data = simulation.getSimulatedData();
		double apogeeMeters = data.getMaxAltitude();
		double apogeeFeet = apogeeMeters * 3.280839895;
		double maxMach = data.getMaxMachNumber();
		return new SimulationResult(apogeeFeet, apogeeMeters, maxMach,
				simulation.getPhysicsAeroRuntimeReport());
	}

	private record SimulationResult(double apogeeFeet, double apogeeMeters, double maxMach,
			PhysicsAeroRuntimeReport runtime) {
	}

	private record VariantResults(
			SimulationResult saved,
			SimulationResult baseline,
			SimulationResult table) {
	}

	private static void assertTableRuntimeUsed(SimulationResult result, String dataset) {
		assertTrue(result.runtime.tableValid(), dataset + " should resolve a valid table artifact");
		assertTrue(result.runtime.successfulTableQueries() > 0,
				dataset + " should exercise the physics-aero table");
	}
}

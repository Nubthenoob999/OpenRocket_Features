package info.openrocket.core.tuning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroRuntimeReport;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.simulation.FlightData;
import info.openrocket.core.simulation.listeners.system.BoundedApogeeEndListener;

/**
 * Opt-in diagnostic for replaying every saved simulation in an external ORK.
 * The normal test suite never depends on a user-specific file path.
 */
class ExternalOrkSimulationDiagnosticTest {
	private static final double METERS_TO_FEET = 3.280839895013123;
	private static final int RANDOM_SEED = 0x5E1F13;

	@Test
	void replayEverySavedSimulation() throws Exception {
		String configuredPath = System.getProperty("physicsAero.externalOrk",
				System.getenv("PHYSICS_AERO_EXTERNAL_ORK"));
		assumeTrue(configuredPath != null && !configuredPath.isBlank(),
				"Set physicsAero.externalOrk or PHYSICS_AERO_EXTERNAL_ORK");
		Path ork = Path.of(configuredPath)
				.toAbsolutePath().normalize();
		assertTrue(Files.isRegularFile(ork), "Missing external ORK: " + ork);
		TuningTestInfrastructure.ensureApplicationInjector();
		OpenRocketDocument document = new GeneralRocketLoader(ork.toFile()).load();
		assertTrue(document.getSimulations().size() > 0, "ORK has no saved simulations");

		for (int index = 0; index < document.getSimulations().size(); index++) {
			Simulation simulation = document.getSimulations().get(index);
			simulation.getOptions().setRandomSeed(RANDOM_SEED);
			PhysicsAeroTuningSupport.prepareTableIfEnabled(simulation);
			simulation.simulate(new BoundedApogeeEndListener());
			FlightData data = simulation.getSimulatedData();
			PhysicsAeroRuntimeReport runtime = simulation.getPhysicsAeroRuntimeReport();

			System.out.printf(Locale.ROOT,
					"EXTERNAL_ORK_RESULT index=%d name=%s mode=%s turbulent=%s "
							+ "apogee_ft=%.6f max_velocity_mps=%.6f max_mach=%.6f "
							+ "time_to_apogee_s=%.6f table_queries=%d successful_queries=%d "
							+ "fallbacks=%d failures=%s flags=%s%n",
					index, simulation.getName(), simulation.getOptions().getPhysicsAeroMode(),
					simulation.getOptions().isForceTurbulentBoundaryLayer(),
					data.getMaxAltitude() * METERS_TO_FEET, data.getMaxVelocity(),
					data.getMaxMachNumber(), data.getTimeToApogee(), runtime.totalQueries(),
					runtime.successfulTableQueries(), runtime.fallbackCount(),
					runtime.failureCounts(), runtime.runtimeFlags());

			assertTrue(Double.isFinite(data.getMaxAltitude()));
			if (simulation.getOptions().getPhysicsAeroSettings().isEnabled()) {
				assertTrue(runtime.tableValid());
				assertEquals(runtime.totalQueries(), runtime.successfulTableQueries());
				assertEquals(0, runtime.fallbackCount());
			}
		}
	}
}

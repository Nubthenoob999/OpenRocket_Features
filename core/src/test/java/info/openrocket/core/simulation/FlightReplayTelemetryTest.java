package info.openrocket.core.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import info.openrocket.core.document.Simulation;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.simulation.exception.SimulationException;
import info.openrocket.core.util.BaseTestCase;
import info.openrocket.core.util.TestRockets;

public class FlightReplayTelemetryTest extends BaseTestCase {

	@Test
	public void simulatedFlightStoresReplayPositionAndOrientationTelemetry() throws SimulationException {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		Simulation simulation = new Simulation(rocket);
		simulation.setFlightConfigurationId(TestRockets.TEST_FCID_0);
		simulation.getOptions().setISAAtmosphere(true);
		simulation.getOptions().setTimeStep(0.05);
		simulation.getOptions().setRandomSeed(0xC0FFEE);

		simulation.simulate();

		FlightDataBranch branch = simulation.getSimulatedData().getBranch(0);
		List<Double> time = requireData(branch, FlightDataType.TYPE_TIME);

		assertSameSampleCount(time, requireData(branch, FlightDataType.TYPE_POSITION_X));
		assertSameSampleCount(time, requireData(branch, FlightDataType.TYPE_POSITION_Y));
		assertSameSampleCount(time, requireData(branch, FlightDataType.TYPE_ALTITUDE));
		assertSameSampleCount(time, requireData(branch, FlightDataType.TYPE_ORIENTATION_THETA));
		assertSameSampleCount(time, requireData(branch, FlightDataType.TYPE_ORIENTATION_PHI));
		assertSameSampleCount(time, requireData(branch, FlightDataType.TYPE_ORIENTATION_QW));
		assertSameSampleCount(time, requireData(branch, FlightDataType.TYPE_ORIENTATION_QX));
		assertSameSampleCount(time, requireData(branch, FlightDataType.TYPE_ORIENTATION_QY));
		assertSameSampleCount(time, requireData(branch, FlightDataType.TYPE_ORIENTATION_QZ));
	}

	private static List<Double> requireData(FlightDataBranch branch, FlightDataType type) {
		List<Double> data = branch.get(type);
		assertNotNull(data, () -> type.getName() + " data should exist");
		assertFalse(data.isEmpty(), () -> type.getName() + " data should not be empty");
		assertTrue(data.stream().anyMatch(v -> v != null && Double.isFinite(v)),
				() -> type.getName() + " should include finite data");
		return data;
	}

	private static void assertSameSampleCount(List<Double> time, List<Double> data) {
		assertEquals(time.size(), data.size());
	}
}

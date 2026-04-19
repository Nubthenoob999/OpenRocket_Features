package info.openrocket.core.tuning;

import info.openrocket.core.util.BaseTestCase;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HeadlessOrkSimulationRunnerTest extends BaseTestCase {
	@Test
	public void rejectsDerivedAccelerationSpikesWhenFallbackAvailable() {
		List<Double> time = List.of(0.0, 0.1, 0.2);
		List<Double> velocity = List.of(0.0, 90.0, 0.0);
		List<Double> fallback = List.of(12.0, 12.0, 12.0);

		List<Double> accel = HeadlessOrkSimulationRunner.deriveVerticalAcceleration(time, velocity, fallback);

		assertEquals(12.0, accel.get(0), 1.0e-9);
		assertEquals(0.0, accel.get(1), 1.0e-9);
		assertEquals(12.0, accel.get(2), 1.0e-9);
	}

	@Test
	public void usesDerivedAccelerationWhenFallbackMissing() {
		List<Double> time = List.of(0.0, 0.5, 1.0);
		List<Double> velocity = List.of(0.0, 5.0, 15.0);
		List<Double> fallback = java.util.Arrays.asList(null, null, null);

		List<Double> accel = HeadlessOrkSimulationRunner.deriveVerticalAcceleration(time, velocity, fallback);

		assertEquals(10.0, accel.get(0), 1.0e-9);
		assertEquals(15.0, accel.get(1), 1.0e-9);
		assertEquals(20.0, accel.get(2), 1.0e-9);
	}
}

package info.openrocket.core.aerodynamics.rom.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class RK4IntegratorTest {

	@Test
	public void scalarStepTracksExponentialGrowth() {
		double value = 1.0;
		double station = 0.0;
		double stepSize = 0.1;

		for (int i = 0; i < 10; i++) {
			value = RK4Integrator.step((x, y) -> y, station, value, stepSize);
			station += stepSize;
		}

		assertEquals(Math.E, value, 3.0e-6);
	}

	@Test
	public void vectorStepTracksSimpleHarmonicOscillator() {
		double[] state = { 1.0, 0.0 };
		double station = 0.0;
		double stepSize = 0.01;

		for (int i = 0; i < 628; i++) {
			state = RK4Integrator.step((x, current, derivative) -> {
				derivative[0] = current[1];
				derivative[1] = -current[0];
			}, station, state, stepSize);
			station += stepSize;
		}

		assertEquals(1.0, state[0], 5.0e-4);
		assertEquals(0.0, state[1], 5.0e-3);
	}
}

package info.openrocket.core.aerodynamics.rom.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class MonotoneCubicHermiteTest {

	@Test
	public void interpolatePreservesMonotonicity() {
		double[] x = { 0.0, 0.3, 0.7, 1.0 };
		double[] y = { 0.0, 0.45, 0.8, 1.0 };

		double previous = MonotoneCubicHermite.interpolate(x, y, 0.0);
		for (int i = 1; i <= 200; i++) {
			double sample = i / 200.0;
			double current = MonotoneCubicHermite.interpolate(x, y, sample);
			assertTrue(current >= previous - 1.0e-12);
			previous = current;
		}
	}

	@Test
	public void interpolateReproducesLinearData() {
		double[] x = { 0.0, 1.0, 2.0, 3.0 };
		double[] y = { 2.0, 2.5, 3.0, 3.5 };

		for (double sample = 0.0; sample <= 3.0; sample += 0.1) {
			assertEquals(2.0 + 0.5 * sample,
					MonotoneCubicHermite.interpolate(x, y, sample), 1.0e-12);
		}
	}
}

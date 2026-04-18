package info.openrocket.core.aerodynamics.rom.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class SavitzkyGolayFilterTest {

	@Test
	public void smoothPreservesConstantSignal() {
		double[] values = { 3.0, 3.0, 3.0, 3.0, 3.0, 3.0, 3.0 };
		double[] smoothed = SavitzkyGolayFilter.smooth(values, 5, 2);

		for (double value : smoothed) {
			assertEquals(3.0, value, 1.0e-12);
		}
	}

	@Test
	public void coefficientsReproduceQuadraticAtTheWindowCenter() {
		double[] coefficients = SavitzkyGolayFilter.coefficients(5, 2);
		double[] samples = { 4.0, 1.0, 0.0, 1.0, 4.0 };

		double reconstructed = 0.0;
		for (int i = 0; i < coefficients.length; i++) {
			reconstructed += coefficients[i] * samples[i];
		}

		assertEquals(0.0, reconstructed, 1.0e-12);
	}
}

package info.openrocket.core.aerodynamics.rom.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class GaussLegendre3Test {

	@Test
	public void integratesFifthOrderPolynomialExactly() {
		double lowerBound = -1.3;
		double upperBound = 2.1;
		double integral = GaussLegendre3.integrate(x -> x * x * x * x * x - 2.0 * x * x * x + x - 1.0,
				lowerBound, upperBound);
		double expected = antiderivative(upperBound) - antiderivative(lowerBound);

		assertEquals(expected, integral, 1.0e-12);
	}

	private static double antiderivative(double x) {
		return Math.pow(x, 6.0) / 6.0 - 0.5 * Math.pow(x, 4.0) + 0.5 * x * x - x;
	}
}

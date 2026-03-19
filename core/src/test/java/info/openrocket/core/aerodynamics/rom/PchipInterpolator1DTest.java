package info.openrocket.core.aerodynamics.rom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class PchipInterpolator1DTest {

	private static final double EPSILON = 1e-9;

	@Test
	public void testExactAtKnots() {
		double[] x = new double[] { 0.0, 0.5, 1.0, 2.0 };
		double[] y = new double[] { 0.0, 0.4, 0.8, 1.3 };
		PchipInterpolator1D pchip = new PchipInterpolator1D(x, y);

		for (int i = 0; i < x.length; i++) {
			assertEquals(y[i], pchip.evaluate(x[i]), EPSILON);
		}
	}

	@Test
	public void testMonotonePreservation() {
		double[] x = new double[] { 0.0, 0.3, 0.7, 1.0 };
		double[] y = new double[] { 0.0, 0.5, 0.8, 1.0 };
		PchipInterpolator1D pchip = new PchipInterpolator1D(x, y);

		double previous = pchip.evaluate(0.0);
		for (int i = 1; i <= 100; i++) {
			double xi = i / 100.0;
			double yi = pchip.evaluate(xi);
			assertTrue(yi >= previous - 1e-12);
			previous = yi;
		}
	}

	@Test
	public void testNoOvershootStepLikeInput() {
		double[] x = new double[] { 0.0, 0.8, 1.0, 1.2, 2.0 };
		double[] y = new double[] { 0.2, 0.25, 0.6, 0.55, 0.4 };
		PchipInterpolator1D pchip = new PchipInterpolator1D(x, y);

		for (int i = 0; i <= 200; i++) {
			double xi = 2.0 * i / 200.0;
			double yi = pchip.evaluate(xi);
			assertTrue(yi >= 0.2 - 1e-9);
			assertTrue(yi <= 0.6 + 1e-9);
		}
	}

	@Test
	public void testClampOutsideRange() {
		double[] x = new double[] { 0.0, 1.0, 2.0 };
		double[] y = new double[] { 2.0, 4.0, 8.0 };
		PchipInterpolator1D pchip = new PchipInterpolator1D(x, y);

		assertEquals(2.0, pchip.evaluate(-1.0), EPSILON);
		assertEquals(8.0, pchip.evaluate(3.0), EPSILON);
	}
}

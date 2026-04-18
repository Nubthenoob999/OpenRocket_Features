package info.openrocket.core.aerodynamics.rom.util;

import java.util.function.DoubleUnaryOperator;

public final class GaussLegendre3 {

	private static final double ROOT = Math.sqrt(3.0 / 5.0);
	private static final double[] NODES = { -ROOT, 0.0, ROOT };
	private static final double[] WEIGHTS = { 5.0 / 9.0, 8.0 / 9.0, 5.0 / 9.0 };

	private GaussLegendre3() {
	}

	public static double integrate(DoubleUnaryOperator function, double a, double b) {
		if (function == null || !Double.isFinite(a) || !Double.isFinite(b)) {
			throw new IllegalArgumentException("Integration inputs must be finite.");
		}
		if (a == b) {
			return 0.0;
		}

		double midpoint = 0.5 * (a + b);
		double halfSpan = 0.5 * (b - a);
		double sum = 0.0;

		for (int i = 0; i < NODES.length; i++) {
			double sample = midpoint + halfSpan * NODES[i];
			double value = function.applyAsDouble(sample);
			if (!Double.isFinite(value)) {
				throw new IllegalArgumentException("Integrand returned a non-finite value.");
			}
			sum += WEIGHTS[i] * value;
		}

		return halfSpan * sum;
	}
}

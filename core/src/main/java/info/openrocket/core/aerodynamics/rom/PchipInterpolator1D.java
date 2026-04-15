package info.openrocket.core.aerodynamics.rom;

/**
 * Fritsch-Carlson monotone piecewise cubic Hermite interpolant.
 * Guarantees monotonicity preservation on each interval.
 * C1 continuous (not C2, unlike standard cubic splines).
 */
public class PchipInterpolator1D {

	private final double[] x;   // knot positions, strictly increasing
	private final double[] y;   // function values at knots
	private final double[] d;   // derivative estimates at knots (Fritsch-Carlson)

	public PchipInterpolator1D(double[] x, double[] y) {
		validateInput(x, y);
		this.x = x.clone();
		this.y = y.clone();
		this.d = computeDerivatives(this.x, this.y);
	}

	/** Evaluate the interpolant at point xi (clamps to boundary outside range). */
	public double evaluate(double xi) {
		int n = x.length;
		if (xi <= x[0]) {
			return y[0];
		}
		if (xi >= x[n - 1]) {
			return y[n - 1];
		}

		int lo = 0;
		int hi = n - 2;
		while (lo < hi) {
			int mid = (lo + hi + 1) >>> 1;
			if (x[mid] <= xi) {
				lo = mid;
			} else {
				hi = mid - 1;
			}
		}

		double h = x[lo + 1] - x[lo];
		double t = (xi - x[lo]) / h;
		double t2 = t * t;
		double t3 = t2 * t;

		double h00 = 2 * t3 - 3 * t2 + 1;
		double h10 = t3 - 2 * t2 + t;
		double h01 = -2 * t3 + 3 * t2;
		double h11 = t3 - t2;

		return h00 * y[lo] + h10 * h * d[lo]
				+ h01 * y[lo + 1] + h11 * h * d[lo + 1];
	}

	private static double[] computeDerivatives(double[] x, double[] y) {
		int n = x.length;
		double[] d = new double[n];
		double[] delta = new double[n - 1];
		double[] h = new double[n - 1];

		for (int i = 0; i < n - 1; i++) {
			h[i] = x[i + 1] - x[i];
			if (!(h[i] > 0.0)) {
				throw new IllegalArgumentException("PCHIP x-axis must be strictly increasing");
			}
			delta[i] = (y[i + 1] - y[i]) / h[i];
		}

		if (n == 2) {
			d[0] = delta[0];
			d[1] = delta[0];
			return d;
		}

		d[0] = endpointDerivative(delta[0], delta[1]);
		d[n - 1] = endpointDerivative(delta[n - 2], delta[n - 3]);

		for (int i = 1; i < n - 1; i++) {
			if (delta[i - 1] * delta[i] <= 0) {
				d[i] = 0.0;
			} else {
				double w1 = 2 * h[i] + h[i - 1];
				double w2 = h[i] + 2 * h[i - 1];
				d[i] = (w1 + w2) / (w1 / delta[i - 1] + w2 / delta[i]);
			}
		}

		for (int i = 0; i < n - 1; i++) {
			if (Math.abs(delta[i]) < 1e-14) {
				d[i] = 0.0;
				d[i + 1] = 0.0;
				continue;
			}
			double alpha = d[i] / delta[i];
			double beta = d[i + 1] / delta[i];
			double r = Math.sqrt(alpha * alpha + beta * beta);
			if (r > 3.0) {
				double tau = 3.0 / r;
				d[i] = tau * alpha * delta[i];
				d[i + 1] = tau * beta * delta[i];
			}
		}
		return d;
	}

	private static double endpointDerivative(double d1, double d2) {
		double d = 1.5 * d1 - 0.5 * d2;
		if (d1 * d <= 0) {
			return 0.0;
		}
		if (d1 * d2 <= 0 && Math.abs(d) > 3 * Math.abs(d1)) {
			return 3 * d1;
		}
		return d;
	}

	private static void validateInput(double[] x, double[] y) {
		if (x == null || y == null) {
			throw new IllegalArgumentException("PCHIP axes must not be null");
		}
		if (x.length != y.length || x.length < 2) {
			throw new IllegalArgumentException("Need at least 2 matching points");
		}
		for (int i = 0; i < x.length; i++) {
			if (!Double.isFinite(x[i]) || !Double.isFinite(y[i])) {
				throw new IllegalArgumentException("PCHIP axes must contain only finite values");
			}
			if (i > 0 && !(x[i] > x[i - 1])) {
				throw new IllegalArgumentException("PCHIP x-axis must be strictly increasing");
			}
		}
	}
}

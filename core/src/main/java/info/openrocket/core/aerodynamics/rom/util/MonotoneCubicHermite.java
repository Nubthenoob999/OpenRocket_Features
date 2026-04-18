package info.openrocket.core.aerodynamics.rom.util;

public final class MonotoneCubicHermite {
	private MonotoneCubicHermite() {
	}

	public static double interpolate(double[] x, double[] y, double query) {
		if (x == null || y == null || x.length == 0 || x.length != y.length) {
			return 0.0;
		}
		if (x.length == 1 || query <= x[0]) {
			return y[0];
		}
		int last = x.length - 1;
		if (query >= x[last]) {
			return y[last];
		}

		double[] slopes = monotoneSlopes(x, y);
		for (int i = 1; i < x.length; i++) {
			if (query > x[i]) {
				continue;
			}
			double h = Math.max(1e-9, x[i] - x[i - 1]);
			double t = (query - x[i - 1]) / h;
			double h00 = (2.0 * t * t * t) - (3.0 * t * t) + 1.0;
			double h10 = (t * t * t) - (2.0 * t * t) + t;
			double h01 = (-2.0 * t * t * t) + (3.0 * t * t);
			double h11 = (t * t * t) - (t * t);
			return h00 * y[i - 1] + h10 * h * slopes[i - 1] + h01 * y[i] + h11 * h * slopes[i];
		}
		return y[last];
	}

	private static double[] monotoneSlopes(double[] x, double[] y) {
		int n = x.length;
		double[] secant = new double[n - 1];
		double[] slope = new double[n];
		for (int i = 0; i < n - 1; i++) {
			secant[i] = (y[i + 1] - y[i]) / Math.max(1e-9, x[i + 1] - x[i]);
		}
		slope[0] = secant[0];
		slope[n - 1] = secant[n - 2];
		for (int i = 1; i < n - 1; i++) {
			if (secant[i - 1] * secant[i] <= 0.0) {
				slope[i] = 0.0;
			} else {
				double w1 = 2.0 * (x[i + 1] - x[i]) + (x[i] - x[i - 1]);
				double w2 = (x[i + 1] - x[i]) + 2.0 * (x[i] - x[i - 1]);
				slope[i] = (w1 + w2) / ((w1 / secant[i - 1]) + (w2 / secant[i]));
			}
		}
		return slope;
	}
}

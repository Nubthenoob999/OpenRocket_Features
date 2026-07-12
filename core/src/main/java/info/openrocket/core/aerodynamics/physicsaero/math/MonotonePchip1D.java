package info.openrocket.core.aerodynamics.physicsaero.math;

public final class MonotonePchip1D {
	private MonotonePchip1D() {}
	public static double interpolate(double[] x, double[] y, double query) {
		if (x.length != y.length || x.length < 2) throw new IllegalArgumentException("matching arrays with at least two points required");
		if (query < x[0] || query > x[x.length - 1]) throw new IllegalArgumentException("extrapolation is disabled");
		int n = x.length; double[] h = new double[n - 1], delta = new double[n - 1], d = new double[n];
		for (int i = 0; i < n - 1; i++) { h[i] = x[i + 1] - x[i]; if (h[i] <= 0) throw new IllegalArgumentException("x must increase"); delta[i] = (y[i + 1] - y[i]) / h[i]; }
		if (n == 2) { d[0] = d[1] = delta[0]; }
		else {
			d[0] = endpoint(h[0], h[1], delta[0], delta[1]); d[n - 1] = endpoint(h[n - 2], h[n - 3], delta[n - 2], delta[n - 3]);
			for (int i = 1; i < n - 1; i++) {
				if (delta[i - 1] * delta[i] <= 0) d[i] = 0;
				else { double w1 = 2 * h[i] + h[i - 1], w2 = h[i] + 2 * h[i - 1]; d[i] = (w1 + w2) / (w1 / delta[i - 1] + w2 / delta[i]); }
			}
		}
		int k = n - 2; for (int i = 0; i < n - 1; i++) if (query <= x[i + 1]) { k = i; break; }
		double t = (query - x[k]) / h[k], t2 = t * t, t3 = t2 * t;
		return (2 * t3 - 3 * t2 + 1) * y[k] + (t3 - 2 * t2 + t) * h[k] * d[k]
				+ (-2 * t3 + 3 * t2) * y[k + 1] + (t3 - t2) * h[k] * d[k + 1];
	}
	private static double endpoint(double h0, double h1, double d0, double d1) {
		double value = ((2 * h0 + h1) * d0 - h0 * d1) / (h0 + h1);
		if (Math.signum(value) != Math.signum(d0)) return 0;
		if (Math.signum(d0) != Math.signum(d1) && Math.abs(value) > 3 * Math.abs(d0)) return 3 * d0;
		return value;
	}
}

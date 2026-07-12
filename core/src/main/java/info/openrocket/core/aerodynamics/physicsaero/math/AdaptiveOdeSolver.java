package info.openrocket.core.aerodynamics.physicsaero.math;

import java.util.Arrays;

/** Deterministic Dormand-Prince 5(4) adaptive ODE integrator. */
public final class AdaptiveOdeSolver {
	@FunctionalInterface public interface Derivative { void compute(double x, double[] y, double[] dydx); }
	public record Result(double x, double[] y, int acceptedSteps, int rejectedSteps, double errorNorm) {
		public Result { y = y.clone(); }
		@Override public double[] y() { return y.clone(); }
	}
	public Result integrate(Derivative derivative, double start, double end, double[] initial,
			double absoluteTolerance, double relativeTolerance, int maximumSteps) {
		if (derivative == null || initial.length == 0 || absoluteTolerance <= 0 || relativeTolerance <= 0 || maximumSteps <= 0)
			throw new IllegalArgumentException("invalid ODE input");
		double direction = Math.copySign(1, end - start), x = start;
		if (start == end) return new Result(start, initial, 0, 0, 0);
		double h = direction * Math.min(Math.abs(end - start) / 20, 0.01);
		double[] y = initial.clone(); int accepted = 0, rejected = 0; double lastError = 0;
		for (int step = 0; step < maximumSteps; step++) {
			if (direction * (x + h - end) > 0) h = end - x;
			Step result = step(derivative, x, y, h, absoluteTolerance, relativeTolerance);
			lastError = result.error;
			if (result.error <= 1) {
				x += h; y = result.value; accepted++;
				if (x == end || direction * (end - x) <= Math.ulp(Math.abs(end))) return new Result(end, y, accepted, rejected, lastError);
			} else rejected++;
			double factor = result.error == 0 ? 5 : Math.max(0.1, Math.min(5, 0.9 * Math.pow(result.error, -0.2)));
			h *= factor;
			if (Math.abs(h) < 1e-14) throw new IllegalStateException("ODE step underflow");
		}
		throw new IllegalStateException("ODE step limit exceeded");
	}
	private static Step step(Derivative f, double x, double[] y, double h, double atol, double rtol) {
		int n = y.length; double[][] k = new double[7][n]; double[] temp = new double[n];
		f.compute(x, y, k[0]);
		stage(f, x + h / 5, y, h, temp, k, 1, new double[] {1.0 / 5});
		stage(f, x + 3 * h / 10, y, h, temp, k, 2, new double[] {3.0 / 40, 9.0 / 40});
		stage(f, x + 4 * h / 5, y, h, temp, k, 3, new double[] {44.0 / 45, -56.0 / 15, 32.0 / 9});
		stage(f, x + 8 * h / 9, y, h, temp, k, 4, new double[] {19372.0 / 6561, -25360.0 / 2187, 64448.0 / 6561, -212.0 / 729});
		stage(f, x + h, y, h, temp, k, 5, new double[] {9017.0 / 3168, -355.0 / 33, 46732.0 / 5247, 49.0 / 176, -5103.0 / 18656});
		stage(f, x + h, y, h, temp, k, 6, new double[] {35.0 / 384, 0, 500.0 / 1113, 125.0 / 192, -2187.0 / 6784, 11.0 / 84});
		double[] fifth = new double[n]; double error = 0;
		for (int i = 0; i < n; i++) {
			fifth[i] = y[i] + h * (35.0 / 384 * k[0][i] + 500.0 / 1113 * k[2][i] + 125.0 / 192 * k[3][i]
					- 2187.0 / 6784 * k[4][i] + 11.0 / 84 * k[5][i]);
			double fourth = y[i] + h * (5179.0 / 57600 * k[0][i] + 7571.0 / 16695 * k[2][i]
					+ 393.0 / 640 * k[3][i] - 92097.0 / 339200 * k[4][i] + 187.0 / 2100 * k[5][i] + 1.0 / 40 * k[6][i]);
			double scale = atol + rtol * Math.max(Math.abs(y[i]), Math.abs(fifth[i]));
			error = Math.max(error, Math.abs(fifth[i] - fourth) / scale);
		}
		if (Arrays.stream(fifth).anyMatch(v -> !Double.isFinite(v))) throw new IllegalStateException("non-finite ODE trajectory");
		return new Step(fifth, error);
	}
	private static void stage(Derivative f, double x, double[] y, double h, double[] temp,
			double[][] k, int index, double[] coefficients) {
		for (int i = 0; i < y.length; i++) { temp[i] = y[i]; for (int j = 0; j < coefficients.length; j++) temp[i] += h * coefficients[j] * k[j][i]; }
		f.compute(x, temp, k[index]);
	}
	private record Step(double[] value, double error) {}
}

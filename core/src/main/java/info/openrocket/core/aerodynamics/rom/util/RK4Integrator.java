package info.openrocket.core.aerodynamics.rom.util;

import java.util.function.DoubleBinaryOperator;

public final class RK4Integrator {

	private RK4Integrator() {
	}

	public static double step(DoubleBinaryOperator derivative, double x, double y, double stepSize) {
		if (derivative == null || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(stepSize)) {
			throw new IllegalArgumentException("RK4 scalar inputs must be finite.");
		}
		if (stepSize == 0.0) {
			return y;
		}

		double k1 = derivative.applyAsDouble(x, y);
		double k2 = derivative.applyAsDouble(x + 0.5 * stepSize, y + 0.5 * stepSize * k1);
		double k3 = derivative.applyAsDouble(x + 0.5 * stepSize, y + 0.5 * stepSize * k2);
		double k4 = derivative.applyAsDouble(x + stepSize, y + stepSize * k3);
		requireFinite(k1);
		requireFinite(k2);
		requireFinite(k3);
		requireFinite(k4);

		double next = y + (stepSize / 6.0) * (k1 + 2.0 * (k2 + k3) + k4);
		if (!Double.isFinite(next)) {
			throw new IllegalArgumentException("RK4 scalar step produced a non-finite result.");
		}
		return next;
	}

	public static double[] step(VectorField derivative, double x, double[] state, double stepSize) {
		if (derivative == null || state == null || state.length == 0
				|| !Double.isFinite(x) || !Double.isFinite(stepSize)) {
			throw new IllegalArgumentException("RK4 vector inputs must be finite.");
		}
		if (stepSize == 0.0) {
			return state.clone();
		}

		double[] current = state.clone();
		double[] k1 = new double[state.length];
		double[] k2 = new double[state.length];
		double[] k3 = new double[state.length];
		double[] k4 = new double[state.length];
		double[] temp = new double[state.length];

		validateState(current);
		derivative.compute(x, current, k1);
		requireFinite(k1);

		for (int i = 0; i < state.length; i++) {
			temp[i] = current[i] + 0.5 * stepSize * k1[i];
		}
		derivative.compute(x + 0.5 * stepSize, temp, k2);
		requireFinite(k2);

		for (int i = 0; i < state.length; i++) {
			temp[i] = current[i] + 0.5 * stepSize * k2[i];
		}
		derivative.compute(x + 0.5 * stepSize, temp, k3);
		requireFinite(k3);

		for (int i = 0; i < state.length; i++) {
			temp[i] = current[i] + stepSize * k3[i];
		}
		derivative.compute(x + stepSize, temp, k4);
		requireFinite(k4);

		double[] next = new double[state.length];
		for (int i = 0; i < state.length; i++) {
			next[i] = current[i] + (stepSize / 6.0) * (k1[i] + 2.0 * (k2[i] + k3[i]) + k4[i]);
		}
		validateState(next);
		return next;
	}

	public interface VectorField {
		void compute(double x, double[] state, double[] derivative);
	}

	private static void validateState(double[] state) {
		for (double value : state) {
			requireFinite(value);
		}
	}

	private static void requireFinite(double[] values) {
		for (double value : values) {
			requireFinite(value);
		}
	}

	private static void requireFinite(double value) {
		if (!Double.isFinite(value)) {
			throw new IllegalArgumentException("RK4 encountered a non-finite value.");
		}
	}
}

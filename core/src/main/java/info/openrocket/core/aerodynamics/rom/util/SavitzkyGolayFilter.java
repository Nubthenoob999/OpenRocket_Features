package info.openrocket.core.aerodynamics.rom.util;

public final class SavitzkyGolayFilter {
	private static final double[] FIVE_POINT_COEFFICIENTS = {
			-3.0 / 35.0, 12.0 / 35.0, 17.0 / 35.0, 12.0 / 35.0, -3.0 / 35.0
	};

	private SavitzkyGolayFilter() {
	}

	public static double[] smooth(double[] samples) {
		return smooth(samples, 5, 2);
	}

	public static double[] smooth(double[] samples, int windowLength, int polynomialOrder) {
		if (samples == null) {
			return new double[0];
		}
		double[] coefficients = coefficients(windowLength, polynomialOrder);
		double[] output = samples.clone();
		if (samples.length < coefficients.length) {
			return output;
		}
		int radius = coefficients.length / 2;
		for (int i = radius; i < samples.length - radius; i++) {
			double sum = 0.0;
			for (int j = -radius; j <= radius; j++) {
				sum += coefficients[j + radius] * samples[i + j];
			}
			output[i] = sum;
		}
		return output;
	}

	public static double[] coefficients(int windowLength, int polynomialOrder) {
		if (windowLength == 5 && polynomialOrder == 2) {
			return FIVE_POINT_COEFFICIENTS.clone();
		}
		throw new IllegalArgumentException("Phase I only supports a 5-point quadratic Savitzky-Golay stencil.");
	}
}

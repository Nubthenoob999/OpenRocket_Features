package info.openrocket.core.aerodynamics.physicsaero.interaction;

import java.util.OptionalDouble;

import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;

/**
	 * Static drag-rise and center-of-pressure closure for the matched NASA
	 * TN D-4013/D-4014 slender cruciform development family. The measured total
	 * axial coefficient must not be added as a generic increment to unrelated
	 * slender rockets whose component drag is already owned elsewhere.
 */
public final class SlenderCruciformCorrelation {
	public static final String METHOD_ID = "D4013_D4014_SLENDER_CRUCIFORM_STATIC_V1";

	private static final double[] TRANSONIC_MACH = {0.6, 0.7, 0.8, 0.9, 0.95, 1.0, 1.1, 1.2};
	private static final double[] SHORT_CP = {0.75, 0.73, 0.71, 0.71, 0.77, 0.84, 0.87, 0.85};
	private static final double[] SHORT_CANT2_CP = {0.77, 0.76, 0.76, 0.74, 0.78, 0.83, 0.84, 0.82};
	private static final double[] LONG_CP = {0.77, 0.76, 0.76, 0.77, 0.74, 0.71, 0.74, 0.82};
	private static final double[] SHORT_FIN_CA = {0.30, 0.31, 0.33, 0.40, 0.58, 0.69, 0.67, 0.60};
	private static final double[] SHORT_BODY_CA = {0.23, 0.23, 0.24, 0.27, 0.31, 0.43, 0.44, 0.44};
	private static final double[] LONG_FIN_CA = {0.34, 0.35, 0.37, 0.44, 0.59, 0.73, 0.69, 0.63};
	private static final double[] LONG_BODY_CA = {0.25, 0.25, 0.26, 0.28, 0.31, 0.37, 0.40, 0.42};
	private static final double[] SUPERSONIC_MACH = {1.5, 1.8, 2.3, 2.96, 3.96, 4.63};
	private static final double[] SUPERSONIC_CP = {0.7825, 0.765, 0.7225, 0.6675, 0.5975, 0.56};
	private static final double[] SHORT_SUPERSONIC_CA = {0.535, 0.47, 0.375, 0.295, 0.225, 0.195};
	private static final double[] LONG_SUPERSONIC_CA = {0.505, 0.505, 0.405, 0.33, 0.265, 0.225};

	public boolean eligible(AeroGeometry geometry) {
		int finCount = geometry.components().stream().filter(c -> c.finGeometry() != null)
				.mapToInt(c -> c.finGeometry().count()).sum();
		return sourceSimilar(geometry) && finCount == 4;
	}

	public double centerOfPressureFraction(AeroGeometry geometry, double mach) {
		if (!eligible(geometry)) throw new IllegalArgumentException("OUTSIDE_SLENDER_CRUCIFORM_DOMAIN");
		if (mach <= 1.2) {
			double finenessWeight = clamp((fineness(geometry) - 18.2) / (23.77 - 18.2));
			double cantWeight = clamp(maxCant(geometry) / Math.toRadians(2));
			double shortCp = interpolate(TRANSONIC_MACH, SHORT_CP, mach) * (1 - cantWeight)
					+ interpolate(TRANSONIC_MACH, SHORT_CANT2_CP, mach) * cantWeight;
			return shortCp * (1 - finenessWeight)
					+ interpolate(TRANSONIC_MACH, LONG_CP, mach) * finenessWeight;
		}
		if (mach < 1.5) {
			double at12 = centerOfPressureFraction(geometry, 1.2);
			return at12 + (mach - 1.2) / 0.3 * (SUPERSONIC_CP[0] - at12);
		}
		return interpolate(SUPERSONIC_MACH, SUPERSONIC_CP, mach);
	}

	public OptionalDouble axialCoefficient(AeroGeometry geometry, double mach) {
		double fineness = fineness(geometry);
		if (!sourceSimilar(geometry) || mach < 0.6 || mach > 4.63) return OptionalDouble.empty();
		double finenessWeight = clamp((fineness - 18.2) / (23.77 - 18.2));
		boolean finned = geometry.components().stream().anyMatch(c -> c.finGeometry() != null);
		if (mach <= 1.2) {
			double shortValue = interpolate(TRANSONIC_MACH, finned ? SHORT_FIN_CA : SHORT_BODY_CA, mach);
			double longValue = interpolate(TRANSONIC_MACH, finned ? LONG_FIN_CA : LONG_BODY_CA, mach);
			double cantDelta = finned ? 0.70 * maxCant(geometry) : 0;
			return OptionalDouble.of(shortValue * (1 - finenessWeight) + longValue * finenessWeight + cantDelta);
		}
		if (!finned) return OptionalDouble.empty();
		double shortValue = interpolate(SUPERSONIC_MACH, SHORT_SUPERSONIC_CA, Math.max(1.5, mach));
		double longValue = interpolate(SUPERSONIC_MACH, LONG_SUPERSONIC_CA, Math.max(1.5, mach));
		if (mach < 1.5) {
			double at12 = axialCoefficient(geometry, 1.2).orElseThrow();
			double at15 = shortValue * (1 - finenessWeight) + longValue * finenessWeight;
			return OptionalDouble.of(at12 + (mach - 1.2) / 0.3 * (at15 - at12));
		}
		return OptionalDouble.of(shortValue * (1 - finenessWeight) + longValue * finenessWeight);
	}

	public double axialDragIncrement(AeroGeometry geometry, double mach) {
		/*
		 * Exact source-family cells use axialCoefficient(...) as a replacement.
		 * No independent D-4013/D-4014 increment was measured for arbitrary
		 * fineness, fin planform, base ratio, or forebody geometry.
		 */
		return 0;
	}

	private static double fineness(AeroGeometry geometry) {
		return geometry.references().vehicleLengthM() / geometry.references().maximumBodyDiameterM();
	}
	private static boolean sourceSimilar(AeroGeometry geometry) {
		double fineness = fineness(geometry);
		boolean sourceFineness = Math.abs(fineness - 18.20) <= 0.25
				|| Math.abs(fineness - 23.77) <= 0.25;
		double baseDiameter = 2 * Math.sqrt(
				geometry.references().exposedBaseAreaM2() / Math.PI);
		double baseToBodyDiameter = baseDiameter
				/ geometry.references().maximumBodyDiameterM();
		int finCount = geometry.components().stream().filter(c -> c.finGeometry() != null)
				.mapToInt(c -> c.finGeometry().count()).sum();
		return sourceFineness && baseToBodyDiameter >= 0.60
				&& baseToBodyDiameter <= 0.70 && (finCount == 0 || finCount == 4);
	}
	private static double maxCant(AeroGeometry geometry) {
		return geometry.components().stream().filter(c -> c.finGeometry() != null)
				.mapToDouble(c -> Math.abs(c.finGeometry().cantRad())).max().orElse(0);
	}

	private static double interpolate(double[] x, double[] y, double value) {
		if (value <= x[0]) return y[0];
		if (value >= x[x.length - 1]) return y[y.length - 1];
		for (int i = 1; i < x.length; i++) if (value <= x[i]) {
			double fraction = (value - x[i - 1]) / (x[i] - x[i - 1]);
			return y[i - 1] + fraction * (y[i] - y[i - 1]);
		}
		throw new IllegalStateException();
	}

	private static double clamp(double value) {
		return Math.max(0, Math.min(1, value));
	}
}

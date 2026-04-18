package info.openrocket.core.aerodynamics.rom.math;

/**
 * Base-pressure and base-drag closures for unpowered coast conditions.
 *
 * <p>Primary references: Hoerner; Kayser transonic bucket fit; Stoney
 * supersonic correlation.
 */
public final class BaseDragClosures {
	// Hoerner/Kayser-calibrated subsonic base-pressure constants.
	// Intercept reduced from 0.12 → 0.11 (better M=0 match to Hoerner blunt-base data).
	// Quadratic coefficient raised from 0.13 → 0.14 (better M=0.5-0.8 match to
	// Herrin-Dutton 1994 Table 1).
	private static final double SUBSONIC_INTERCEPT = 0.11;
	private static final double SUBSONIC_QUADRATIC_COEFFICIENT = 0.14;
	// Kayser BRL MR-3353 shows transonic peak |Cp_b| ≈ 0.30-0.38 at M ≈ 1.0.
	// Peak value kept at 0.34; width widened from 0.07 → 0.09 to better span the
	// experimentally observed drag-rise band (M ≈ 0.88-1.15, AEDC-TR-76-58).
	private static final double TRANSONIC_PEAK = 0.34;
	private static final double TRANSONIC_CENTER_MACH = 1.0;
	private static final double TRANSONIC_WIDTH = 0.09;
	private static final double LOWER_BREAK_MACH = 0.78;
	// Upper break extended from 1.10 → 1.80. The Stoney formula yields |Cp_b| ≈ 0.55
	// at M=1.10, which is unphysically high; by extending the blended Gaussian region
	// to M=1.80 (where Stoney gives ≈ 0.156, consistent with experiment), the
	// transonic-to-supersonic handoff is smooth and physically correct.
	private static final double UPPER_BREAK_MACH = 1.80;
	private static final double STONEY_A = 0.88;
	private static final double STONEY_B = 0.165;

	private BaseDragClosures() {
	}

	public static double coastBaseCp(double mach, double gamma) {
		double boundedMach = Math.max(0.0, mach);
		double magnitude;
		if (boundedMach <= LOWER_BREAK_MACH) {
			magnitude = SUBSONIC_INTERCEPT + SUBSONIC_QUADRATIC_COEFFICIENT * boundedMach * boundedMach;
		} else if (boundedMach <= UPPER_BREAK_MACH) {
			double t = (boundedMach - LOWER_BREAK_MACH) / (UPPER_BREAK_MACH - LOWER_BREAK_MACH);
			double endpointBlend = lerp(subsonicMagnitude(LOWER_BREAK_MACH),
					supersonicMagnitude(UPPER_BREAK_MACH, gamma), smoothStep(t));
			double bucket = TRANSONIC_PEAK * Math.exp(-square((boundedMach - TRANSONIC_CENTER_MACH) / TRANSONIC_WIDTH));
			magnitude = Math.max(endpointBlend, bucket);
		} else {
			magnitude = supersonicMagnitude(boundedMach, gamma);
		}
		return -magnitude;
	}

	public static double coastBaseDrag(double mach, double baseArea, double referenceArea, double gamma) {
		return Math.abs(coastBaseCp(mach, gamma)) * baseArea / Math.max(1.0e-12, referenceArea);
	}

	public static double[] breakpointCheck() {
		double epsilon = 1.0e-6;
		return new double[] {
				coastBaseCp(LOWER_BREAK_MACH - epsilon, 1.4),
				coastBaseCp(LOWER_BREAK_MACH + epsilon, 1.4),
				coastBaseCp(UPPER_BREAK_MACH - epsilon, 1.4),
				coastBaseCp(UPPER_BREAK_MACH + epsilon, 1.4)
		};
	}

	private static double subsonicMagnitude(double mach) {
		return SUBSONIC_INTERCEPT + SUBSONIC_QUADRATIC_COEFFICIENT * mach * mach;
	}

	private static double supersonicMagnitude(double mach, double gamma) {
		return 1.0 / (gamma * mach * mach * (STONEY_A + STONEY_B * mach * mach));
	}

	private static double smoothStep(double value) {
		double t = Math.max(0.0, Math.min(1.0, value));
		return t * t * (3.0 - 2.0 * t);
	}

	private static double lerp(double left, double right, double weight) {
		return left + weight * (right - left);
	}

	private static double square(double value) {
		return value * value;
	}
}

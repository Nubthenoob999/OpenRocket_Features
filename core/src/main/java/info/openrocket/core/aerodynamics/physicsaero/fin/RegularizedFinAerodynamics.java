package info.openrocket.core.aerodynamics.physicsaero.fin;

/**
 * Fin corrections shared with the established OpenRocket aerodynamic model.
 *
 * <p>The supersonic center-of-pressure equation from NACA Report 1307 is only
 * valid for {@code AR * beta > 1}.  The bounded bridge below that boundary is
 * the same regularization used by the base simulation pipeline, so Physics
 * aero tables cannot reintroduce the low-aspect-ratio pole.</p>
 */
public final class RegularizedFinAerodynamics {
	public static final String CP_METHOD_ID = "NACA_1307_REGULARIZED_FIN_CP_V1";
	public static final String INTERFERENCE_METHOD_ID =
			"NACA_1307_BODY_FIN_NORMAL_FORCE_INTERFERENCE_V1";

	private static final double SUBSONIC_CP = 0.25;
	private static final double TRANSONIC_START_MACH = 0.5;
	private static final double TRANSONIC_END_MACH = 2.0;
	private static final double SUPERSONIC_CP_OFFSET = 0.67;
	private static final double SOURCE_BOUNDARY = 1.0;
	private static final double BRIDGE_START =
			(SUPERSONIC_CP_OFFSET - SUBSONIC_CP) / (1 - 2 * SUBSONIC_CP);
	private static final double MONOTONIC_SLOPE_RATIO = 5.0 / 3.0;
	private static final double CNA_SUBSONIC_MACH = 0.9;
	private static final double CNA_SUPERSONIC_MACH = 1.5;

	private RegularizedFinAerodynamics() {
	}

	/**
	 * Return the aerodynamic-center position as a fraction of mean aerodynamic chord.
	 * Aspect ratio uses OpenRocket's full-wing convention: {@code 2 * span^2 / area}.
	 */
	public static double centerOfPressureChordFraction(double mach, double aspectRatio) {
		if (!Double.isFinite(mach) || !Double.isFinite(aspectRatio)
				|| mach < 0 || aspectRatio < 0) {
			throw new IllegalArgumentException("invalid Mach or fin aspect ratio");
		}
		if (mach <= TRANSONIC_START_MACH) return SUBSONIC_CP;
		if (mach >= TRANSONIC_END_MACH) {
			return supersonicPosition(aspectRatio * Math.sqrt(mach * mach - 1));
		}
		return transonicPosition(mach, aspectRatio);
	}

	public static double centerOfPressureM(double leadingEdgeM,
			double meanAerodynamicChordM, double mach, double aspectRatio) {
		if (!Double.isFinite(leadingEdgeM) || !(meanAerodynamicChordM > 0)) {
			throw new IllegalArgumentException("invalid mean aerodynamic chord geometry");
		}
		return leadingEdgeM + meanAerodynamicChordM
				* centerOfPressureChordFraction(mach, aspectRatio);
	}

	/** Combined fin-in-body and body-in-fin normal-force multiplier. */
	public static double bodyFinInterferenceFactor(double tau, double mach) {
		if (!Double.isFinite(tau) || !Double.isFinite(mach) || tau < 0 || mach < 0) {
			throw new IllegalArgumentException("invalid body-fin interference state");
		}
		double finInBody = 1 + tau;
		if (mach <= CNA_SUBSONIC_MACH) return finInBody * finInBody;
		if (mach >= CNA_SUPERSONIC_MACH) return finInBody;
		double bodyInFin = tau * finInBody;
		double weight = (CNA_SUPERSONIC_MACH - mach)
				/ (CNA_SUPERSONIC_MACH - CNA_SUBSONIC_MACH);
		return finInBody + weight * bodyInFin;
	}

	private static double supersonicPosition(double arBeta) {
		if (arBeta <= BRIDGE_START) return SUBSONIC_CP;
		if (arBeta >= SOURCE_BOUNDARY) return sourcePosition(arBeta);

		double width = SOURCE_BOUNDARY - BRIDGE_START;
		double t = (arBeta - BRIDGE_START) / width;
		double t2 = t * t;
		double t3 = t2 * t;
		double endPosition = sourcePosition(SOURCE_BOUNDARY);
		double endGradient = sourceGradient(SOURCE_BOUNDARY);
		return (2 * t3 - 3 * t2 + 1) * SUBSONIC_CP
				+ (-2 * t3 + 3 * t2) * endPosition
				+ (t3 - t2) * width * endGradient;
	}

	private static double transonicPosition(double mach, double aspectRatio) {
		double range = TRANSONIC_END_MACH - TRANSONIC_START_MACH;
		double t = (mach - TRANSONIC_START_MACH) / range;
		double arBetaAtMach2 = aspectRatio * Math.sqrt(3);
		double endpoint = supersonicPosition(arBetaAtMach2);
		double delta = endpoint - SUBSONIC_CP;
		if (delta <= 0) return SUBSONIC_CP;

		double endpointSlope = arBetaAtMach2 * supersonicGradient(arBetaAtMach2);
		double slopeRatio = endpointSlope / delta;
		double t2 = t * t;
		double t3 = t2 * t;
		double t4 = t3 * t;
		double t5 = t4 * t;
		if (slopeRatio <= MONOTONIC_SLOPE_RATIO) {
			return SUBSONIC_CP
					+ (10 * delta - 6 * endpointSlope) * t2
					+ (-20 * delta + 14 * endpointSlope) * t3
					+ (15 * delta - 11 * endpointSlope) * t4
					+ (3 * endpointSlope - 4 * delta) * t5;
		}

		double limitingQuintic = t3 * (10.0 / 3.0 - (10.0 / 3.0) * t + t2);
		return SUBSONIC_CP + delta * Math.pow(limitingQuintic,
				slopeRatio / MONOTONIC_SLOPE_RATIO);
	}

	private static double sourcePosition(double arBeta) {
		return (arBeta - SUPERSONIC_CP_OFFSET) / (2 * arBeta - 1);
	}

	private static double sourceGradient(double arBeta) {
		double denominator = 2 * arBeta - 1;
		return (2 * SUPERSONIC_CP_OFFSET - 1) / (denominator * denominator);
	}

	private static double supersonicGradient(double arBeta) {
		if (arBeta <= BRIDGE_START) return 0;
		if (arBeta >= SOURCE_BOUNDARY) return sourceGradient(arBeta);

		double width = SOURCE_BOUNDARY - BRIDGE_START;
		double t = (arBeta - BRIDGE_START) / width;
		double t2 = t * t;
		double startGradient = 6 * t2 - 6 * t;
		double endPosition = sourcePosition(SOURCE_BOUNDARY);
		double endGradient = sourceGradient(SOURCE_BOUNDARY);
		return (startGradient * SUBSONIC_CP - startGradient * endPosition) / width
				+ (3 * t2 - 2 * t) * endGradient;
	}
}

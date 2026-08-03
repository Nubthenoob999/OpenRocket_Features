package info.openrocket.core.aerodynamics.physicsaero.body;

/**
 * RASAero II 1.0.2.0 empirical zero-incidence wave-drag correlations for a
 * conical diameter expansion and a terminal conical reducer (boattail).
 *
 * <p>The correlations are nondimensional geometry/Mach fits.  The expansion
 * form uses the shoulder annular area and {@code 2 M tan(theta)}.  The reducer
 * form uses the conical half-angle, diameter ratio, and the RASAero 17.5 degree
 * separated-flow angle limit.  They are used as engineering pressure-drag
 * envelopes, not as flight-specific multipliers.</p>
 */
public final class RasaeroSupersonicTransitionDragCorrelation {
	public static final String EXPANSION_METHOD_ID =
			"RASAERO_102_SUPERSONIC_EXPANSION_WAVE_DRAG_V1";
	public static final String REDUCER_METHOD_ID =
			"RASAERO_102_SUPERSONIC_REDUCER_WAVE_DRAG_V1";

	private static final double GAMMA = 1.4;
	private static final double MAX_REDUCER_ANGLE_DEG = 17.5;

	public double expansionDragCoefficient(double smallerRadiusM,
			double largerRadiusM, double lengthM, double mach,
			double referenceAreaM2) {
		if (!validInputs(smallerRadiusM, largerRadiusM, lengthM, mach,
				referenceAreaM2) || !(largerRadiusM > smallerRadiusM)) {
			return Double.NaN;
		}
		double similarityParameter = 2 * mach
				* (largerRadiusM - smallerRadiusM) / lengthM;
		double numerator;
		if (similarityParameter <= 1.4792) {
			numerator = 0.016351 + 0.11179 * similarityParameter
					+ 0.38354 * similarityParameter * similarityParameter;
		} else {
			numerator = 0.080555 + 0.12364 * similarityParameter
					+ 0.34619 * similarityParameter * similarityParameter;
		}
		double pressureCoefficient = numerator / (GAMMA * 0.5 * mach * mach);
		double annularArea = Math.PI * (largerRadiusM * largerRadiusM
				- smallerRadiusM * smallerRadiusM);
		return pressureCoefficient * annularArea / referenceAreaM2;
	}

	public double terminalReducerDragCoefficient(double foreRadiusM,
			double aftRadiusM, double lengthM, double mach,
			double referenceAreaM2) {
		if (!validInputs(aftRadiusM, foreRadiusM, lengthM, mach,
				referenceAreaM2) || !(foreRadiusM > aftRadiusM)) {
			return Double.NaN;
		}
		double exponent = mach <= 3 ? 4 : mach < 4 ? 7 - mach : 3;
		double actualAngleDeg = Math.toDegrees(Math.atan2(
				foreRadiusM - aftRadiusM, lengthM));
		double correlationAngleDeg = Math.min(actualAngleDeg,
				MAX_REDUCER_ANGLE_DEG);
		double effectiveAftRadius = aftRadiusM;
		if (actualAngleDeg > MAX_REDUCER_ANGLE_DEG) {
			effectiveAftRadius = foreRadiusM
					- lengthM * Math.tan(Math.toRadians(MAX_REDUCER_ANGLE_DEG));
			effectiveAftRadius = Math.max(0, effectiveAftRadius);
		}
		double diameterRatio = effectiveAftRadius / foreRadiusM;
		double pressureCoefficient = (0.001 * correlationAngleDeg
				+ 0.00071 * correlationAngleDeg * correlationAngleDeg) / mach;
		double foreArea = Math.PI * foreRadiusM * foreRadiusM;
		return pressureCoefficient
				* (1 - Math.pow(diameterRatio, exponent))
				* foreArea / referenceAreaM2;
	}

	private static boolean validInputs(double firstRadiusM,
			double secondRadiusM, double lengthM, double mach,
			double referenceAreaM2) {
		return Double.isFinite(firstRadiusM + secondRadiusM + lengthM + mach
				+ referenceAreaM2) && firstRadiusM >= 0 && secondRadiusM > 0
				&& lengthM > 0 && mach >= 1.05 && referenceAreaM2 > 0;
	}
}

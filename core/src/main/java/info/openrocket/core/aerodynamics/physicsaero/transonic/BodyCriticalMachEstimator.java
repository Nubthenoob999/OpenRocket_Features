package info.openrocket.core.aerodynamics.physicsaero.transonic;
public final class BodyCriticalMachEstimator implements CriticalMachEstimator {
	public static final String TIP_ANGLE_METHOD_ID =
			"NASA_TR_R100_NOSE_TIP_DRAG_DIVERGENCE_V1";

	@Override
	public Estimate estimate(double finenessInverse, double curvatureMetric,
			double incidence) {
		double mach = .94 - .55 * finenessInverse
				- .08 * Math.min(1, curvatureMetric) - .18 * Math.abs(incidence);
		return new Estimate(Math.max(.55, Math.min(.96, mach)), .65,
				"BODY_GEOMETRY_CRITICAL_MACH_V1");
	}

	/**
	 * NASA TR-R-100-anchored transonic onset used by the companion pressure
	 * model.  The local nose-tip angle, rather than whole-vehicle fineness,
	 * controls the first locally sonic surface flow.
	 */
	public Estimate estimateFromTipHalfAngle(double tipHalfAngleRad) {
		if (!Double.isFinite(tipHalfAngleRad) || tipHalfAngleRad < 0
				|| tipHalfAngleRad > Math.PI / 2) {
			throw new IllegalArgumentException("invalid nose tip half-angle");
		}
		double sinTip = Math.sin(tipHalfAngleRad);
		double mach = 0.95 - 0.15 * Math.pow(Math.max(sinTip, 0.01), 0.4);
		return new Estimate(Math.max(0.65, Math.min(0.96, mach)), 0.75,
				TIP_ANGLE_METHOD_ID);
	}
}

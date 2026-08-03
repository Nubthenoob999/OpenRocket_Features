package info.openrocket.core.aerodynamics.physicsaero.table;

import java.util.Arrays;

/** Reynolds correction information generated with a table cell. */
public record RuntimeCorrectionData(double referenceReynolds, double minimumRatio,
		double maximumRatio, double[] dCoefficientDLogRe,
		double[] dCoefficientDLogReSquared, double[] dCoefficientDLogReCubed,
		double[] dCoefficientDLogReFourth,
		double[] dCoefficientDLogReFifth, double[] dCoefficientDLogReSixth,
		double[] dCoefficientDLogReSeventh,
		boolean topologySensitive,
		String methodId) {
	public static final String REBUILD_REQUIRED_METHOD = "REYNOLDS_REBUILD_REQUIRED";
	private static final double REFERENCE_RATIO_LOG_TOLERANCE = 1.0e-3;
	public static final String PIECEWISE_LOG_RE_METHOD = "DIRECT_ANCHOR_PIECEWISE_LOG_RE_V3";
	private static final double PIECEWISE_RATIO_ONE = 1.0;
	private static final double PIECEWISE_RATIO_TWO = 0.25;
	private static final double PIECEWISE_RATIO_THREE = 0.10;
	private static final double PIECEWISE_RATIO_FOUR = 0.05623413251903491;
	private static final double PIECEWISE_RATIO_FIVE = 0.03162277660168379;
	private static final double PIECEWISE_RATIO_SIX = 0.01778279410038923;
	private static final double PIECEWISE_RATIO_SEVEN = 0.01;
	private static final double PIECEWISE_RATIO_EIGHT = 0.005;

	public RuntimeCorrectionData {
		dCoefficientDLogRe = dCoefficientDLogRe.clone();
		dCoefficientDLogReSquared = dCoefficientDLogReSquared.clone();
		dCoefficientDLogReCubed = dCoefficientDLogReCubed.clone();
		dCoefficientDLogReFourth = dCoefficientDLogReFourth.clone();
		dCoefficientDLogReFifth = dCoefficientDLogReFifth.clone();
		dCoefficientDLogReSixth = dCoefficientDLogReSixth.clone();
		dCoefficientDLogReSeventh = dCoefficientDLogReSeventh.clone();
		if (dCoefficientDLogRe.length != 6 || dCoefficientDLogReSquared.length != 6
				|| dCoefficientDLogReCubed.length != 6 || dCoefficientDLogReFourth.length != 6
				|| dCoefficientDLogReFifth.length != 6 || dCoefficientDLogReSixth.length != 6
				|| dCoefficientDLogReSeventh.length != 6
				|| Arrays.stream(dCoefficientDLogRe).anyMatch(v -> !Double.isFinite(v))
				|| Arrays.stream(dCoefficientDLogReSquared).anyMatch(v -> !Double.isFinite(v))
				|| Arrays.stream(dCoefficientDLogReCubed).anyMatch(v -> !Double.isFinite(v))
				|| Arrays.stream(dCoefficientDLogReFourth).anyMatch(v -> !Double.isFinite(v))
				|| Arrays.stream(dCoefficientDLogReFifth).anyMatch(v -> !Double.isFinite(v))
				|| Arrays.stream(dCoefficientDLogReSixth).anyMatch(v -> !Double.isFinite(v))
				|| Arrays.stream(dCoefficientDLogReSeventh).anyMatch(v -> !Double.isFinite(v))) {
			throw new IllegalArgumentException("seven finite Reynolds correction arrays required");
		}
		if (!Double.isFinite(referenceReynolds) || referenceReynolds < 0
				|| !Double.isFinite(minimumRatio) || !Double.isFinite(maximumRatio)
				|| minimumRatio <= 0 || maximumRatio < minimumRatio) {
			throw new IllegalArgumentException("invalid Reynolds correction domain");
		}
		if (methodId == null || methodId.isBlank()) {
			throw new IllegalArgumentException("Reynolds correction method ID required");
		}
	}

	@Override
	public double[] dCoefficientDLogRe() {
		return dCoefficientDLogRe.clone();
	}

	@Override
	public double[] dCoefficientDLogReSquared() {
		return dCoefficientDLogReSquared.clone();
	}

	@Override
	public double[] dCoefficientDLogReCubed() {
		return dCoefficientDLogReCubed.clone();
	}

	@Override
	public double[] dCoefficientDLogReFourth() {
		return dCoefficientDLogReFourth.clone();
	}

	@Override
	public double[] dCoefficientDLogReFifth() {
		return dCoefficientDLogReFifth.clone();
	}

	@Override
	public double[] dCoefficientDLogReSixth() {
		return dCoefficientDLogReSixth.clone();
	}

	@Override
	public double[] dCoefficientDLogReSeventh() {
		return dCoefficientDLogReSeventh.clone();
	}

	/** Source-compatible constructor for the prior six-array table schema. */
	public RuntimeCorrectionData(double referenceReynolds, double minimumRatio,
			double maximumRatio, double[] dCoefficientDLogRe,
			double[] dCoefficientDLogReSquared, double[] dCoefficientDLogReCubed,
			double[] dCoefficientDLogReFourth, double[] dCoefficientDLogReFifth,
			double[] dCoefficientDLogReSixth, boolean topologySensitive,
			String methodId) {
		this(referenceReynolds, minimumRatio, maximumRatio, dCoefficientDLogRe,
				dCoefficientDLogReSquared, dCoefficientDLogReCubed,
				dCoefficientDLogReFourth, dCoefficientDLogReFifth,
				dCoefficientDLogReSixth, new double[6], topologySensitive, methodId);
	}

	/** Source-compatible constructor for the prior four-array table schema. */
	public RuntimeCorrectionData(double referenceReynolds, double minimumRatio,
			double maximumRatio, double[] dCoefficientDLogRe,
			double[] dCoefficientDLogReSquared, double[] dCoefficientDLogReCubed,
			double[] dCoefficientDLogReFourth, boolean topologySensitive,
			String methodId) {
		this(referenceReynolds, minimumRatio, maximumRatio, dCoefficientDLogRe,
				dCoefficientDLogReSquared, dCoefficientDLogReCubed,
				dCoefficientDLogReFourth, new double[6], new double[6],
				new double[6], topologySensitive, methodId);
	}

	/** Source-compatible constructor for cubic correction callers. */
	public RuntimeCorrectionData(double referenceReynolds, double minimumRatio,
			double maximumRatio, double[] dCoefficientDLogRe,
			double[] dCoefficientDLogReSquared, double[] dCoefficientDLogReCubed,
			boolean topologySensitive, String methodId) {
		this(referenceReynolds, minimumRatio, maximumRatio, dCoefficientDLogRe,
				dCoefficientDLogReSquared, dCoefficientDLogReCubed, new double[6],
				new double[6], new double[6], new double[6],
				topologySensitive, methodId);
	}

	/** Source-compatible constructor for second-order correction callers. */
	public RuntimeCorrectionData(double referenceReynolds, double minimumRatio,
			double maximumRatio, double[] dCoefficientDLogRe,
			double[] dCoefficientDLogReSquared, boolean topologySensitive,
			String methodId) {
		this(referenceReynolds, minimumRatio, maximumRatio, dCoefficientDLogRe,
				dCoefficientDLogReSquared, new double[6], new double[6],
				new double[6], new double[6], new double[6], topologySensitive, methodId);
	}

	/** Source-compatible constructor for first-order correction callers. */
	public RuntimeCorrectionData(double referenceReynolds, double minimumRatio,
			double maximumRatio, double[] dCoefficientDLogRe, boolean topologySensitive,
			String methodId) {
		this(referenceReynolds, minimumRatio, maximumRatio, dCoefficientDLogRe,
				new double[6], new double[6], new double[6], new double[6],
				new double[6], new double[6], topologySensitive, methodId);
	}

	public boolean requiresRebuild() {
		return topologySensitive || REBUILD_REQUIRED_METHOD.equals(methodId);
	}

	public boolean supportsRatio(double ratio) {
		if (!Double.isFinite(ratio) || !(ratio > 0)) return false;
		if (requiresRebuild()) {
			/*
			 * A topology-sensitive stencil cannot be used to move away from its
			 * reference Reynolds state, but the directly generated reference cell
			 * remains valid.  Launch-atmosphere roundoff commonly produces ratios a
			 * few 1e-8 away from unity.
			 */
			return Math.abs(Math.log(ratio)) <= REFERENCE_RATIO_LOG_TOLERANCE;
		}
		return ratio >= minimumRatio && ratio <= maximumRatio;
	}

	/** Coefficient delta represented by this offline Reynolds surface. */
	public double coefficientDelta(double ratio, int coefficientIndex) {
		if (coefficientIndex < 0 || coefficientIndex >= 6 || !(ratio > 0)
				|| !Double.isFinite(ratio)) {
			throw new IllegalArgumentException("invalid Reynolds correction query");
		}
		if (PIECEWISE_LOG_RE_METHOD.equals(methodId)) {
			return piecewiseLogDelta(ratio, dCoefficientDLogRe[coefficientIndex],
					dCoefficientDLogReSquared[coefficientIndex],
					dCoefficientDLogReCubed[coefficientIndex],
					dCoefficientDLogReFourth[coefficientIndex],
					dCoefficientDLogReFifth[coefficientIndex],
					dCoefficientDLogReSixth[coefficientIndex],
					dCoefficientDLogReSeventh[coefficientIndex]);
		}
		double logRatio = Math.log(ratio);
		return dCoefficientDLogRe[coefficientIndex] * logRatio
				+ dCoefficientDLogReSquared[coefficientIndex] * logRatio * logRatio
				+ dCoefficientDLogReCubed[coefficientIndex] * logRatio * logRatio * logRatio;
	}

	private static double piecewiseLogDelta(double ratio, double atPointTwo,
			double atPointThree, double atPointFour, double atPointFive,
			double atPointSix, double atPointSeven, double atPointEight) {
		double log = Math.log(ratio);
		if (ratio >= PIECEWISE_RATIO_TWO) {
			return interpolateLog(log, Math.log(PIECEWISE_RATIO_ONE), 0,
					Math.log(PIECEWISE_RATIO_TWO), atPointTwo);
		}
		if (ratio >= PIECEWISE_RATIO_THREE) {
			return interpolateLog(log, Math.log(PIECEWISE_RATIO_TWO), atPointTwo,
					Math.log(PIECEWISE_RATIO_THREE), atPointThree);
		}
		if (ratio >= PIECEWISE_RATIO_FOUR) {
			return interpolateLog(log, Math.log(PIECEWISE_RATIO_THREE), atPointThree,
					Math.log(PIECEWISE_RATIO_FOUR), atPointFour);
		}
		if (ratio >= PIECEWISE_RATIO_FIVE) {
			return interpolateLog(log, Math.log(PIECEWISE_RATIO_FOUR), atPointFour,
					Math.log(PIECEWISE_RATIO_FIVE), atPointFive);
		}
		if (ratio >= PIECEWISE_RATIO_SIX) {
			return interpolateLog(log, Math.log(PIECEWISE_RATIO_FIVE), atPointFive,
					Math.log(PIECEWISE_RATIO_SIX), atPointSix);
		}
		if (ratio >= PIECEWISE_RATIO_SEVEN) {
			return interpolateLog(log, Math.log(PIECEWISE_RATIO_SIX), atPointSix,
					Math.log(PIECEWISE_RATIO_SEVEN), atPointSeven);
		}
		return interpolateLog(log, Math.log(PIECEWISE_RATIO_SEVEN), atPointSeven,
				Math.log(PIECEWISE_RATIO_EIGHT), atPointEight);
	}

	private static double interpolateLog(double query, double x0, double y0,
			double x1, double y1) {
		return y0 + (query - x0) / (x1 - x0) * (y1 - y0);
	}

	public static RuntimeCorrectionData rebuildRequired(double referenceReynolds) {
		return new RuntimeCorrectionData(Math.max(0, referenceReynolds), 1, 1,
				new double[6], new double[6], new double[6], new double[6], true,
				REBUILD_REQUIRED_METHOD);
	}
}

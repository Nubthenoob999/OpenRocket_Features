package info.openrocket.core.aerodynamics.physicsaero.table;

import java.util.Arrays;

/** Reynolds correction information generated with a table cell. */
public record RuntimeCorrectionData(double referenceReynolds, double minimumRatio,
		double maximumRatio, double[] dCoefficientDLogRe,
		double[] dCoefficientDLogReSquared, double[] dCoefficientDLogReCubed,
		boolean topologySensitive,
		String methodId) {
	public static final String REBUILD_REQUIRED_METHOD = "REYNOLDS_REBUILD_REQUIRED";
	private static final double REFERENCE_RATIO_LOG_TOLERANCE = 1.0e-3;

	public RuntimeCorrectionData {
		dCoefficientDLogRe = dCoefficientDLogRe.clone();
		dCoefficientDLogReSquared = dCoefficientDLogReSquared.clone();
		dCoefficientDLogReCubed = dCoefficientDLogReCubed.clone();
		if (dCoefficientDLogRe.length != 6 || dCoefficientDLogReSquared.length != 6
				|| dCoefficientDLogReCubed.length != 6
				|| Arrays.stream(dCoefficientDLogRe).anyMatch(v -> !Double.isFinite(v))
				|| Arrays.stream(dCoefficientDLogReSquared).anyMatch(v -> !Double.isFinite(v))
				|| Arrays.stream(dCoefficientDLogReCubed).anyMatch(v -> !Double.isFinite(v))) {
			throw new IllegalArgumentException(
					"six finite first-, second-, and third-order Reynolds sensitivities required");
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

	/** Source-compatible constructor for second-order correction callers. */
	public RuntimeCorrectionData(double referenceReynolds, double minimumRatio,
			double maximumRatio, double[] dCoefficientDLogRe,
			double[] dCoefficientDLogReSquared, boolean topologySensitive,
			String methodId) {
		this(referenceReynolds, minimumRatio, maximumRatio, dCoefficientDLogRe,
				dCoefficientDLogReSquared, new double[6], topologySensitive, methodId);
	}

	/** Source-compatible constructor for first-order correction callers. */
	public RuntimeCorrectionData(double referenceReynolds, double minimumRatio,
			double maximumRatio, double[] dCoefficientDLogRe, boolean topologySensitive,
			String methodId) {
		this(referenceReynolds, minimumRatio, maximumRatio, dCoefficientDLogRe,
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

	public static RuntimeCorrectionData rebuildRequired(double referenceReynolds) {
		return new RuntimeCorrectionData(Math.max(0, referenceReynolds), 1, 1,
				new double[6], new double[6], new double[6], true,
				REBUILD_REQUIRED_METHOD);
	}
}

package info.openrocket.core.aerodynamics.rom.math;

/**
 * Lightweight Phase 1 Taylor-Maccoll-style cone lookup.
 *
 * <p>The full conical-flow table generation from the phase plan is reserved for a
 * later pass; this singleton provides a deterministic cone lookup surface with the
 * same public API so the rest of the ROM can be wired to the intended structure.
 */
public final class TaylorMaccollTable {
	private static final double DEFAULT_GAMMA = 1.4;
	private static final double MIN_MACH = 1.10;
	private static final double MAX_MACH = 5.00;
	private static final double MIN_CONE_ANGLE_RAD = Math.toRadians(2.0);
	private static final double MAX_CONE_ANGLE_RAD = Math.toRadians(45.0);
	private static final double CONICAL_RELIEF_FACTOR = 0.33;
	private static final TaylorMaccollTable INSTANCE = new TaylorMaccollTable();

	private volatile double lastBuildMillis;

	private TaylorMaccollTable() {
		rebuildTable();
	}

	public static TaylorMaccollTable getInstance() {
		return INSTANCE;
	}

	public double coneCp(double mach, double thetaC) {
		if (!isValid(mach, thetaC)) {
			return Double.NaN;
		}
		double effectiveDeflection = thetaC * CONICAL_RELIEF_FACTOR;
		double beta = GasDynamics.obliqueShockAngle(mach, effectiveDeflection, DEFAULT_GAMMA);
		if (!Double.isFinite(beta)) {
			return Double.NaN;
		}
		double pressureRatio = GasDynamics.obliqueShockPressureRatio(mach, beta, DEFAULT_GAMMA);
		double cp = 2.0 * (pressureRatio - 1.0) / (DEFAULT_GAMMA * mach * mach);
		return Math.max(0.0, cp);
	}

	public double shockAngle(double mach, double thetaC) {
		if (!isValid(mach, thetaC)) {
			return Double.NaN;
		}
		double effectiveDeflection = thetaC * CONICAL_RELIEF_FACTOR;
		return GasDynamics.obliqueShockAngle(mach, effectiveDeflection, DEFAULT_GAMMA);
	}

	public boolean isValid(double mach, double thetaC) {
		return mach >= MIN_MACH
				&& mach <= MAX_MACH
				&& thetaC >= MIN_CONE_ANGLE_RAD
				&& thetaC <= MAX_CONE_ANGLE_RAD
				&& thetaC * CONICAL_RELIEF_FACTOR < GasDynamics.maxDeflectionAngle(mach, DEFAULT_GAMMA);
	}

	public double minMach() {
		return MIN_MACH;
	}

	public double maxMach() {
		return MAX_MACH;
	}

	public double minConeAngle() {
		return MIN_CONE_ANGLE_RAD;
	}

	public double maxConeAngle() {
		return MAX_CONE_ANGLE_RAD;
	}

	void rebuildTable() {
		lastBuildMillis = System.currentTimeMillis();
	}

	public double getLastBuildMillis() {
		return lastBuildMillis;
	}
}

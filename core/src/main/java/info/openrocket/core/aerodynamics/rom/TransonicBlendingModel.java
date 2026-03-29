package info.openrocket.core.aerodynamics.rom;

public class TransonicBlendingModel {

	// Sigmoid sharpness tuned to preserve transonic gradient consistency constraints.
	private static final double K = 30.0;

	/**
	 * Subsonic sigmoid weight: 1 at low M, 0 at high M.
	 * Centered at M1 = 0.80.
	 */
	public static double sigmaSubsonic(double mach) {
		double x = K * (mach - 0.80);
		x = Math.max(-60.0, Math.min(60.0, x));
		return 1.0 / (1.0 + Math.exp(x));
	}

	/**
	 * Supersonic sigmoid weight: 0 at low M, 1 at high M.
	 * Centered at M2 = 1.20.
	 */
	public static double sigmaSupersonic(double mach) {
		double x = -K * (mach - 1.20);
		x = Math.max(-60.0, Math.min(60.0, x));
		return 1.0 / (1.0 + Math.exp(x));
	}

	/** Transonic weight: complement of the other two. */
	public static double sigmaTransonic(double mach) {
		return Math.max(0.0, 1.0 - sigmaSubsonic(mach) - sigmaSupersonic(mach));
	}

	/**
	 * Blend three regime Cd values into a single smooth value.
	 * cd_sub: subsonic physics (no wave drag)
	 * cd_trans: transonic peak estimate (wave drag + base drag peak)
	 * cd_sup: supersonic physics (wave drag + Ackeret fins)
	 *
	 * The result is C-infinity continuous - suitable for any ODE integrator.
	 */
	public static double blend(double mach,
						   double cd_sub, double cd_trans, double cd_sup) {
		double ws = sigmaSubsonic(mach);
		double wt = sigmaTransonic(mach);
		double wp = sigmaSupersonic(mach);
		double sum = ws + wt + wp;
		if (!Double.isFinite(sum) || sum <= 0.0) {
			return Math.max(0.0, cd_sub);
		}
		double cd = (ws * Math.max(0.0, cd_sub)
				+ wt * Math.max(0.0, cd_trans)
				+ wp * Math.max(0.0, cd_sup)) / sum;
		return Double.isFinite(cd) ? Math.max(0.0, cd) : Math.max(0.0, cd_sub);
	}

	/**
	 * Transonic peak Cd estimate.
	 * Scales the subsonic value by the drag rise factor at peak (M~=1.0).
	 * Drag rise factor ~= 1.5-2.5x subsonic for typical slender rockets.
	 * Uses nose-shape-specific empirical peak multipliers.
	 */
	public static double transonicPeakCd(double cd_subsonic,
									  RomGeometryParameters g) {
		double peakFactor;
		switch (g.noseShape) {
			case VON_KARMAN:
				peakFactor = 1.45;
				break;
			case OGIVE:
				peakFactor = 1.65;
				break;
			case PARABOLIC:
				peakFactor = 1.75;
				break;
			case CONICAL:
				peakFactor = 2.0;
				break;
			default:
				peakFactor = 1.85;
				break;
		}
		// Reduce peak factor for high fineness ratio (slender bodies have lower transonic rise)
		double lOverD = Math.max(g.finessRatio, 1e-6);
		double finenessCorrection = Math.min(1.0, 10.0 / lOverD);
		double cd = Math.max(0.0, cd_subsonic) * peakFactor * finenessCorrection;
		return Double.isFinite(cd) ? Math.max(0.0, cd) : Math.max(0.0, cd_subsonic);
	}
}

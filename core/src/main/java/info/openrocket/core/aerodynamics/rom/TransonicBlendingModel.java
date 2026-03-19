package info.openrocket.core.aerodynamics.rom;

public class TransonicBlendingModel {

	// Sigmoid sharpness - higher k = sharper boundary transition
	private static final double K = 30.0;

	/**
	 * Subsonic sigmoid weight: 1 at low M, 0 at high M.
	 * Centered at M1 = 0.80.
	 */
	public static double sigmaSubsonic(double mach) {
		return 1.0 / (1.0 + Math.exp(K * (mach - 0.80)));
	}

	/**
	 * Supersonic sigmoid weight: 0 at low M, 1 at high M.
	 * Centered at M2 = 1.20.
	 */
	public static double sigmaSupersonic(double mach) {
		return 1.0 / (1.0 + Math.exp(-K * (mach - 1.20)));
	}

	/** Transonic weight: complement of the other two. */
	public static double sigmaTransonic(double mach) {
		return 1.0 - sigmaSubsonic(mach) - sigmaSupersonic(mach);
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
		return sigmaSubsonic(mach) * cd_sub
				+ sigmaTransonic(mach) * cd_trans
				+ sigmaSupersonic(mach) * cd_sup;
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
				peakFactor = 1.6;
				break;
			case OGIVE:
				peakFactor = 1.8;
				break;
			case PARABOLIC:
				peakFactor = 1.9;
				break;
			case CONICAL:
				peakFactor = 2.2;
				break;
			default:
				peakFactor = 2.0;
				break;
		}
		// Reduce peak factor for high fineness ratio (slender bodies have lower transonic rise)
		double lOverD = g.finessRatio;
		double finenessCorrection = Math.min(1.0, 10.0 / lOverD);
		return cd_subsonic * peakFactor * finenessCorrection;
	}
}

package info.openrocket.core.aerodynamics.rom.core.physics;

import info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput;

public class TransonicBlendingModel {

	private static final double K = 30.0;

	private TransonicBlendingModel() {
	}

	public static double sigmaSubsonic(double mach) {
		double x = K * (mach - 0.80);
		x = Math.max(-60.0, Math.min(60.0, x));
		return 1.0 / (1.0 + Math.exp(x));
	}

	public static double sigmaSupersonic(double mach) {
		double x = -K * (mach - 1.20);
		x = Math.max(-60.0, Math.min(60.0, x));
		return 1.0 / (1.0 + Math.exp(x));
	}

	public static double sigmaTransonic(double mach) {
		return Math.max(0.0, 1.0 - sigmaSubsonic(mach) - sigmaSupersonic(mach));
	}

	public static double blend(double mach, double cd_sub, double cd_trans, double cd_sup) {
		double ws = sigmaSubsonic(mach);
		double wt = sigmaTransonic(mach);
		double wp = sigmaSupersonic(mach);
		double sum = ws + wt + wp;
		if (!Double.isFinite(sum) || sum <= 0.0) {
			return sanitizeCd(cd_sub);
		}
		double cd = (ws * sanitizeCd(cd_sub) + wt * sanitizeCd(cd_trans) + wp * sanitizeCd(cd_sup)) / sum;
		return sanitizeCd(cd);
	}

	public static double transonicPeakCd(double cd_subsonic, RomGeometryInput g) {
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
		double lOverD = Math.max(g.finenessRatio, 1e-6);
		double finenessCorrection = Math.min(1.0, 10.0 / lOverD);
		double cd = sanitizeCd(cd_subsonic) * peakFactor * finenessCorrection;
		return sanitizeCd(cd);
	}

	private static double sanitizeCd(double cd) {
		if (!Double.isFinite(cd)) {
			return 0.0;
		}
		return Math.max(0.0, cd);
	}
}

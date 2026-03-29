package info.openrocket.core.aerodynamics.rom.core.physics;

import info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput;

public class SkinFrictionModel {
	private static final double EPS = 1e-9;

	private static final double GAMMA = 1.4;
	private static final double RECOVERY_FACTOR = 0.88;

	private SkinFrictionModel() {
	}

	public static double cfIncompressible(double re_L, double re_tr) {
		re_L = sanitizeRe(re_L);
		re_tr = sanitizeRe(re_tr);
		if (re_L < 1e3) {
			return 1.328 / Math.sqrt(re_L);
		}
		double cf_turb = 0.455 / Math.pow(Math.log10(re_L), 2.58);
		double A = re_tr * (0.455 / Math.pow(Math.log10(re_tr), 2.58)
				- 1.328 / Math.sqrt(re_tr));
		return Math.max(cf_turb - A / re_L, 1.328 / Math.sqrt(re_L));
	}

	public static double vanDriestII(double cf_incomp, double mach, double t_ratio) {
		if (!Double.isFinite(cf_incomp) || cf_incomp <= 0.0) {
			return 0.0;
		}
		mach = Double.isFinite(mach) ? Math.max(0.0, mach) : 0.0;
		t_ratio = Double.isFinite(t_ratio) ? Math.max(t_ratio, EPS) : 1.0;

		double m = (GAMMA - 1.0) / 2.0 * mach * mach;
		double r = RECOVERY_FACTOR;
		double F = t_ratio;
		double rm = r * m;

		double A = Math.sqrt(rm / F);
		double B = (1.0 + rm - F) / F;
		double denom = Math.sqrt(4.0 * A * A + B * B);
		double alpha = (2.0 * A * A - B) / denom;
		double beta = B / denom;

		alpha = Math.max(-1.0, Math.min(1.0, alpha));
		beta = Math.max(-1.0, Math.min(1.0, beta));

		double Fc = rm / Math.pow(Math.asin(alpha) + Math.asin(beta), 2.0);
		if (Fc <= 0 || Double.isNaN(Fc)) {
			return cf_incomp;
		}
		double cf = cf_incomp / Fc;
		return Double.isFinite(cf) ? Math.max(0.0, cf) : cf_incomp;
	}

	public static double cfWithRoughness(double cf_smooth, double bodyLength, double k_s) {
		if (!Double.isFinite(cf_smooth) || cf_smooth <= 0.0) {
			cf_smooth = 0.0;
		}
		bodyLength = Double.isFinite(bodyLength) ? Math.max(bodyLength, EPS) : EPS;
		k_s = Double.isFinite(k_s) ? Math.max(k_s, 0.0) : 0.0;

		if (k_s <= 0) {
			return cf_smooth;
		}
		double ratio = Math.max(bodyLength / Math.max(k_s, EPS), 1.0 + EPS);
		double cf_rough = Math.pow(1.89 + 1.62 * Math.log10(ratio), -2.5);
		double cf = Math.max(cf_smooth, cf_rough);
		return Double.isFinite(cf) ? Math.max(0.0, cf) : cf_smooth;
	}

	public static double bodyFormFactor(double diameter, double length) {
		diameter = Double.isFinite(diameter) ? Math.max(diameter, 0.0) : 0.0;
		length = Double.isFinite(length) ? Math.max(length, EPS) : EPS;
		double ratio = diameter / length;
		return 1.0 + 1.5 * Math.pow(ratio, 1.5) + 50.0 * Math.pow(ratio, 3.0);
	}

	public static double cdFriction(double mach, double re_L, RomGeometryInput g) {
		double re_tr = transitionReynolds(re_L, g.surfaceRoughness, g.bodyLength);
		double cf_inc = cfIncompressible(re_L, re_tr);
		double cf_smooth = vanDriestII(cf_inc, mach, adiabaticWallRatio(mach));
		double cf = cfWithRoughness(cf_smooth, g.bodyLength, g.surfaceRoughness);
		double ff = bodyFormFactor(g.maxDiameter, g.bodyLength);
		double areaRatio = Math.max(g.wetArea, 0.0) / Math.max(g.referenceArea, EPS);
		double cd = 0.4 * cf * ff * areaRatio;
		return Double.isFinite(cd) ? Math.max(0.0, cd) : 0.0;
	}

	private static double transitionReynolds(double re_L, double k_s, double L) {
		re_L = sanitizeRe(re_L);
		k_s = Double.isFinite(k_s) ? Math.max(k_s, 0.0) : 0.0;
		L = Double.isFinite(L) ? Math.max(L, EPS) : EPS;

		double re_tr_clean = 5e5;
		if (k_s <= 0) {
			return re_tr_clean;
		}
		double k_plus_factor = k_s / L * re_L;
		if (k_plus_factor > 120) {
			return 1e4;
		}
		return Math.max(1e4, re_tr_clean * (1.0 - k_plus_factor / 120.0));
	}

	private static double adiabaticWallRatio(double mach) {
		mach = Double.isFinite(mach) ? Math.max(0.0, mach) : 0.0;
		return 1.0 + RECOVERY_FACTOR * (GAMMA - 1.0) / 2.0 * mach * mach;
	}

	private static double sanitizeRe(double re) {
		if (!Double.isFinite(re)) {
			return 1e4;
		}
		return Math.max(re, 1.0);
	}
}

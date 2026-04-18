package info.openrocket.core.aerodynamics.rom;

public class SkinFrictionModel {

	private static final double GAMMA = 1.4;
	private static final double PR_TURB = 0.9;     // turbulent Prandtl number
	private static final double RECOVERY_FACTOR = 0.88; // turbulent

	/**
	 * Returns the incompressible average skin friction coefficient over
	 * a flat plate of length L at Reynolds number Re_L.
	 * Uses Prandtl-Schlichting with transition correction.
	 *
	 * Cf = 0.455 / (log10(Re_L))^2.58  - A/Re_L
	 * where A = 1700 for Re_tr = 5e5 (default paint finish).
	 */
	public static double cfIncompressible(double re_L, double re_tr) {
		if (re_L < 1e3) {
			return 1.328 / Math.sqrt(re_L); // Blasius, fully laminar
		}
		double cf_turb = 0.455 / Math.pow(Math.log10(re_L), 2.58);
		double A = re_tr * (0.455 / Math.pow(Math.log10(re_tr), 2.58)
				- 1.328 / Math.sqrt(re_tr));
		return Math.max(cf_turb - A / re_L, 1.328 / Math.sqrt(re_L));
	}

	/**
	 * Van Driest II compressibility transformation for turbulent flat-plate Cf.
	 * Returns compressible Cf given incompressible Cf, freestream Mach M,
	 * and wall temperature ratio T_w/T_e (use 1.0 for adiabatic wall).
	 *
	 * Reference: Hopkins & Inouye (1971), AIAA J.
	 */
	public static double vanDriestII(double cf_incomp, double mach, double t_ratio) {
		double m = (GAMMA - 1.0) / 2.0 * mach * mach;
		double r = RECOVERY_FACTOR;
		double F = t_ratio;
		double rm = r * m;

		double A = Math.sqrt(rm / F);
		double B = (1.0 + rm - F) / F;
		double denom = Math.sqrt(4.0 * A * A + B * B);
		double alpha = (2.0 * A * A - B) / denom;
		double beta = B / denom;

		// Clamp to valid arcsin range
		alpha = Math.max(-1.0, Math.min(1.0, alpha));
		beta = Math.max(-1.0, Math.min(1.0, beta));

		double Fc = rm / Math.pow(Math.asin(alpha) + Math.asin(beta), 2.0);
		if (Fc <= 0 || Double.isNaN(Fc)) {
			return cf_incomp;
		}
		return cf_incomp / Fc;
	}

	/**
	 * Roughness-limited skin friction (fully rough regime).
	 * Cf_rough = [1.89 + 1.62 * log10(L/k_s)]^(-2.5)
	 * Returns the larger of smooth-wall and roughness-limited values.
	 */
	public static double cfWithRoughness(double cf_smooth, double bodyLength, double k_s) {
		if (k_s <= 0) {
			return cf_smooth;
		}
		double cf_rough = Math.pow(1.89 + 1.62 * Math.log10(bodyLength / k_s), -2.5);
		return Math.max(cf_smooth, cf_rough);
	}

	/**
	 * Body form factor accounting for pressure gradient on body of revolution.
	 * FF = 1 + 1.5*(d/L)^1.5 + 50*(d/L)^3
	 */
	public static double bodyFormFactor(double diameter, double length) {
		double ratio = diameter / length;
		return 1.0 + 1.5 * Math.pow(ratio, 1.5) + 50.0 * Math.pow(ratio, 3.0);
	}

	/**
	 * Complete friction Cd for the body, referenced to frontal area.
	 * Applies Van Driest II, roughness correction, form factor, and
	 * wetted-area-to-reference-area scaling.
	 */
	public static double cdFriction(double mach, double re_L, RomGeometryParameters g) {
		double re_tr = transitionReynolds(re_L, g.surfaceRoughness, g.bodyLength);
		double cf_inc = cfIncompressible(re_L, re_tr);
		double cf_smooth = vanDriestII(cf_inc, mach, adiabaticWallRatio(mach));
		double cf = cfWithRoughness(cf_smooth, g.bodyLength, g.surfaceRoughness);
		double ff = bodyFormFactor(g.maxDiameter, g.bodyLength);
		// Flat-plate Cf correlations represent shear referenced to a two-sided plate.
		// Body wetted area is single-sided, so apply normalization.
		return 0.5 * cf * ff * (g.wetArea / g.referenceArea);
	}

	/**
	 * Effective transition Reynolds number as a function of Re and roughness.
	 * Uses a blended model between clean (Re_tr = 5e5) and fully rough limits.
	 */
	private static double transitionReynolds(double re_L, double k_s, double L) {
		double re_tr_clean = 5e5;
		if (k_s <= 0) {
			return re_tr_clean;
		}
		// Roughness Reynolds number at x = L
		// Transition trips at k+ ~ 5-10; approximate Re_tr reduction
		double k_plus_factor = k_s / L * re_L;
		if (k_plus_factor > 120) {
			return 1e4; // fully rough, effectively immediate transition
		}
		return Math.max(1e4, re_tr_clean * (1.0 - k_plus_factor / 120.0));
	}

	/** Adiabatic wall temperature ratio for turbulent boundary layer in air. */
	private static double adiabaticWallRatio(double mach) {
		return 1.0 + RECOVERY_FACTOR * (GAMMA - 1.0) / 2.0 * mach * mach;
	}
}

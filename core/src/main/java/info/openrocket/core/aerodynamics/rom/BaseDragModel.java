package info.openrocket.core.aerodynamics.rom;

/** Base drag model for rocket aerodynamics.
 * WWEEEE
 */

public class BaseDragModel {

	/**
	 * Subsonic base drag (M < 0.6), referenced to BASE area.
	 * Braeunig modification of Hoerner: Cd_base = Kb * (db/d)^n / sqrt(Cf)
	 * where Kb and n depend on afterbody geometry (Lo = length aft of max diameter).
	 *
	 * For a simple cylinder (Lo = 0): Kb ~= 0.0274*atan(0)+0.0116+1 = 1.0116
	 */
	public static double cdBaseSubsonic(double cf_body, double loOverD) {
		double Kb = 0.0274 * Math.atan(loOverD) + 0.0116 + 1.0;
		double n = 3.6542 * Math.pow(loOverD + 1e-9, -0.2733);
		n = Math.max(0.5, Math.min(n, 3.0)); // clamp to physical range
		// Hoerner/Braeunig-style base drag uses a small empirical prefactor.
		// Without this scaling, low-Re values become unrealistically large.
		return 0.026 * Kb / Math.sqrt(Math.max(cf_body, 1e-4));
	}

	/**
	 * Transonic base drag multiplier (Braeunig empirical fit).
	 *   0.6 <= M <= 1.0:  fb = 1.0 + 215.8*(M-0.6)^6
	 *   1.0 < M <= 2.0:  cubic polynomial
	 *   M > 2.0:         linear decay ~ 1/M
	 */
	public static double transonicMultiplier(double mach) {
		if (mach < 0.6) {
			return 1.0;
		}
		if (mach <= 1.0) {
			double dm = mach - 0.6;
			return 1.0 + 175.0 * Math.pow(dm, 6.0);
		}
		if (mach <= 2.0) {
			double dm = mach - 1.0;
			return 1.75 * dm * dm * dm - 3.20 * dm * dm + 1.35 * dm + 1.72;
		}
		// Decay for M > 2: match at M=2 then 1/M scaling
		double fb2 = transonicMultiplier(2.0);
		return fb2 * (2.0 / mach);
	}

	/**
	 * Complete base Cd for coast phase (plume-off), referenced to FRONTAL area.
	 * Handles boattail area reduction.
	 */
	public static double cdBasePlumeOff(double mach, double cf_body,
									 RomGeometryParameters g) {
		double loOverD = (g.boattailLength > 0)
				? g.boattailLength / g.maxDiameter : 0.0;
		double cd_base_area = cdBaseSubsonic(cf_body, loOverD);
		double fb = transonicMultiplier(mach);
		cd_base_area *= fb;

		double baseArea = Math.max(g.baseArea, 0.0);
		double cd = cd_base_area * (baseArea / Math.max(g.referenceArea, 1.0e-12));
		return Double.isFinite(cd) ? Math.max(0.0, cd) : 0.0;
	}

	/**
	 * Powered flight (plume-on) base drag.
	 * Motor exit subtracts from effective base area: A_eff = A_base - A_exit
	 * Residual drag from annular base region only.
	 */
	public static double cdBasePlumeOn(double mach, double cf_body,
									RomGeometryParameters g) {
		if (g.motorExitArea <= 0) {
			return cdBasePlumeOff(mach, cf_body, g);
		}
		double effectiveBaseArea = Math.max(0.0, g.baseArea - g.motorExitArea);
		if (!(effectiveBaseArea > 0.0)) {
			return 0.0;
		}
		double loOverD = (g.boattailLength > 0)
				? g.boattailLength / Math.max(g.maxDiameter, 1.0e-12) : 0.0;
		double cdBaseArea = cdBaseSubsonic(cf_body, loOverD) * transonicMultiplier(mach);
		double cd = cdBaseArea * (effectiveBaseArea / Math.max(g.referenceArea, 1.0e-12));
		return Double.isFinite(cd) ? Math.max(0.0, cd) : 0.0;
	}
}

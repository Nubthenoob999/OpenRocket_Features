package info.openrocket.core.aerodynamics.rom.core.physics;

import info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput;

public class BaseDragModel {
	private static final double EPS = 1e-9;

	private BaseDragModel() {
	}

	public static double cdBaseSubsonic(double cf_body, double loOverD) {
		double safeLoOverD = Double.isFinite(loOverD) ? Math.max(0.0, loOverD) : 0.0;
		double Kb = 0.0274 * Math.atan(safeLoOverD) + 0.0116 + 1.0;
		double cd = 0.029 * Kb / Math.sqrt(Math.max(cf_body, 1e-4));
		return Double.isFinite(cd) ? Math.max(0.0, cd) : 0.0;
	}

	public static double transonicMultiplier(double mach) {
		if (!Double.isFinite(mach)) {
			return 1.0;
		}
		mach = Math.max(0.0, mach);
		if (mach < 0.6) {
			return 1.0;
		}
		if (mach <= 1.0) {
			double dm = mach - 0.6;
			return 1.0 + 215.8 * Math.pow(dm, 6.0);
		}
		if (mach <= 2.0) {
			double dm = mach - 1.0;
			return 1.75 * dm * dm * dm - 3.20 * dm * dm + 1.35 * dm + 1.884;
		}
		double fb2 = transonicMultiplier(2.0);
		return fb2 * (2.0 / mach);
	}

	public static double cdBasePlumeOff(double mach, double cf_body, RomGeometryInput g) {
		double maxDiameter = Math.max(g.maxDiameter, EPS);
		double loOverD = (g.boattailLength > 0) ? g.boattailLength / maxDiameter : 0.0;
		double cd_base_area = cdBaseSubsonic(cf_body, loOverD);
		double fb = transonicMultiplier(mach);
		cd_base_area *= fb;

		double baseArea = Math.max(g.baseArea, 0.0);
		double referenceArea = Math.max(g.referenceArea, EPS);
		double cd = cd_base_area * (baseArea / referenceArea);
		return Double.isFinite(cd) ? Math.max(0.0, cd) : 0.0;
	}

	public static double cdBasePlumeOn(double mach, double cf_body, RomGeometryInput g) {
		if (g.motorExitArea <= 0) {
			return cdBasePlumeOff(mach, cf_body, g);
		}
		double effectiveBaseArea = Math.max(0.0, g.baseArea - g.motorExitArea);
		if (effectiveBaseArea <= EPS) {
			return 0.0;
		}
		double maxDiameter = Math.max(g.maxDiameter, EPS);
		double loOverD = (g.boattailLength > 0) ? g.boattailLength / maxDiameter : 0.0;
		double cdBaseArea = cdBaseSubsonic(cf_body, loOverD) * transonicMultiplier(mach);
		double cd = cdBaseArea * (effectiveBaseArea / Math.max(g.referenceArea, EPS));
		return Double.isFinite(cd) ? Math.max(0.0, cd) : 0.0;
	}
}

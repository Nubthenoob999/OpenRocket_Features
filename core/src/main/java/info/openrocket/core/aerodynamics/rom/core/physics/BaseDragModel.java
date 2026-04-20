package info.openrocket.core.aerodynamics.rom.core.physics;

import info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput;

public class BaseDragModel {
	private static final double EPS = 1e-9;

	private BaseDragModel() {
	}

	public static double cdBaseSubsonic(double cf_body, double loOverD) {
		double safeLoOverD = Double.isFinite(loOverD) ? Math.max(0.0, loOverD) : 0.0;
		double Kb = 0.0274 * Math.atan(safeLoOverD) + 0.0116 + 1.0;
		double n = 3.6542 * Math.pow(loOverD + 1e-9, -0.2733);
		n = Math.max(0.5, Math.min(n, 3.0));
		// Hoerner/Braeunig base drag with Reynolds exponent n
		double cd = 0.026 * Kb / Math.pow(Math.max(cf_body, 1e-4), n / 2.0);
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
			return 1.0 + 175.0 * Math.pow(dm, 6.0);
		}
		if (mach <= 2.0) {
			double dm = mach - 1.0;
			return 1.75 * dm * dm * dm - 3.20 * dm * dm + 1.35 * dm + 1.72;
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

		double baseD = (g.boattailBaseDiameter > 0) ? g.boattailBaseDiameter : maxDiameter;
		double baseArea = Math.PI * (baseD / 2.0) * (baseD / 2.0);
		double referenceArea = Math.max(g.referenceArea, EPS);
		double cd = cd_base_area * (baseArea / referenceArea);
		return Double.isFinite(cd) ? Math.max(0.0, cd) : 0.0;
	}

	public static double cdBasePlumeOn(double mach, double cf_body, RomGeometryInput g) {
		if (g.motorExitArea <= 0) {
			return cdBasePlumeOff(mach, cf_body, g);
		}
		double baseD = (g.boattailBaseDiameter > 0) ? g.boattailBaseDiameter : Math.max(g.maxDiameter, EPS);
		double baseArea = Math.PI * (baseD / 2.0) * (baseD / 2.0);
		if (baseArea <= EPS) {
			return 0.0;
		}
		double effectiveRatio = Math.max(0.0, (baseArea - g.motorExitArea) / baseArea);
		double cd = cdBasePlumeOff(mach, cf_body, g) * effectiveRatio;
		return Double.isFinite(cd) ? Math.max(0.0, cd) : 0.0;
	}
}

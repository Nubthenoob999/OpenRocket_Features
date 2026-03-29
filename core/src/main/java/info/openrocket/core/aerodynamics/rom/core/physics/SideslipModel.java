package info.openrocket.core.aerodynamics.rom.core.physics;

import info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput;

public final class SideslipModel {

	private SideslipModel() {
	}

	static double crossflowCdCf(double mach) {
		if (mach <= 0.5) {
			return 1.2;
		}
		if (mach < 1.0) {
			double t = (mach - 0.5) / 0.5;
			return 1.2 - 0.4 * t;
		}
		return 0.8;
	}

	public static double cdBodyCrossflow(double betaRad, double mach, RomGeometryInput g) {
		if (Math.abs(betaRad) < 1e-6) {
			return 0.0;
		}
		double cdCf = crossflowCdCf(mach);
		double sinBeta = Math.sin(betaRad);
		double diam = Math.max(g.maxDiameter, 1e-4);
		double areaRatio = 4.0 * g.bodyLength / (Math.PI * diam);
		return cdCf * areaRatio * sinBeta * sinBeta;
	}

	public static double cdSideslipIncrement(double alphaRad, double betaRad, double mach, RomGeometryInput g) {
		if (Math.abs(betaRad) < 1e-6) {
			return 0.0;
		}
		double dCdBody = cdBodyCrossflow(betaRad, mach, g);
		double dCdFinBeta = FinDragModel.cdFinInducedDrag(alphaRad, betaRad, mach, g);
		double dCdFinAlpha = FinDragModel.cdFinInducedDrag(alphaRad, 0.0, mach, g);
		return dCdBody + Math.max(0.0, dCdFinBeta - dCdFinAlpha);
	}
}

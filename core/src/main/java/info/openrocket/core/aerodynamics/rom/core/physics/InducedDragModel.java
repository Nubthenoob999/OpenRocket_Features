package info.openrocket.core.aerodynamics.rom.core.physics;

import info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput;

public class InducedDragModel {

	private static volatile double protuberanceFactor = 1.02;

	private InducedDragModel() {
	}

	public static double cdInduced(double alphaRad, double mach, RomGeometryInput g) {
		if (alphaRad < 1e-6) {
			return 0.0;
		}
		double chord_mean = (g.finRootChord + g.finTipChord) / 2.0;
		double ar = 2.0 * g.finSpan / chord_mean;
		double e = 0.9;

		double cNa_fin = 2.0 * Math.PI / (1.0 + 2.0 / ar);
		double cl = cNa_fin * alphaRad * g.finCount
				* (g.finSpan * chord_mean) / g.referenceArea;

		double compressibilityFactor = 1.0;
		if (mach <= 0.8) {
			double beta2 = Math.max(1.0 - mach * mach, 0.35);
			compressibilityFactor = 1.0 / Math.sqrt(beta2);
		} else if (mach < 1.2) {
			double beta2At08 = Math.max(1.0 - 0.8 * 0.8, 0.35);
			double subsonicAt08 = 1.0 / Math.sqrt(beta2At08);
			double t = (mach - 0.8) / 0.4;
			double smooth = t * t * (3.0 - 2.0 * t);
			compressibilityFactor = subsonicAt08 * (1.0 - smooth) + smooth;
		}
		cl *= compressibilityFactor;

		double cd_induced = cl * cl / (Math.PI * ar * e);
		double cd_body_aoa = 0.075 * Math.sin(alphaRad) * Math.sin(alphaRad);
		return cd_induced + cd_body_aoa;
	}

	public static double protuberanceFactor() {
		return protuberanceFactor;
	}

	public static void setProtuberanceFactor(double factor) {
		protuberanceFactor = Math.max(1.0, Math.min(1.20, factor));
	}
}

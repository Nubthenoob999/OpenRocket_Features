package info.openrocket.core.aerodynamics.rom.core.physics;

import info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput;

public class InducedDragModel {

	private static volatile double protuberanceFactor = 1.02;

	private InducedDragModel() {
	}

	public static double cdInduced(double alphaRad, double betaRad, double mach, RomGeometryInput g) {
		// M15: Total effective angle of attack including sideslip
		double totalAlpha = Math.sqrt(alphaRad * alphaRad + betaRad * betaRad);
		if (totalAlpha < 1e-6) {
			return 0.0;
		}
		double chord_mean = (g.finRootChord + g.finTipChord) / 2.0;
		double ar = 2.0 * g.finSpan / chord_mean;
		double e = 0.9;

		double cNa_fin = 2.0 * Math.PI / (1.0 + 2.0 / ar);
		double cl = cNa_fin * totalAlpha * g.finCount
				* (g.finSpan * chord_mean) / g.referenceArea;

		double compressibilityFactor = 1.0;
		if (mach <= 0.8) {
			double beta2 = Math.max(1.0 - mach * mach, 0.35);
			compressibilityFactor = 1.0 / Math.sqrt(beta2);
		} else if (mach < 1.2) {
			double beta2At08 = Math.max(1.0 - 0.8 * 0.8, 0.35);
			double subsonicAt08 = 1.0 / Math.sqrt(beta2At08);
			double supAt12 = 1.0 / Math.sqrt(Math.max(1.2 * 1.2 - 1.0, 0.01));
			double t = (mach - 0.8) / 0.4;
			double smooth = t * t * (3.0 - 2.0 * t);
			compressibilityFactor = subsonicAt08 * (1.0 - smooth) + supAt12 * smooth;
		} else {
			// Supersonic: Ackeret-based compressibility factor
			compressibilityFactor = 1.0 / Math.sqrt(Math.max(mach * mach - 1.0, 0.01));
		}
		cl *= compressibilityFactor;

		double cd_induced = cl * cl / (Math.PI * ar * e);
		double cd_body_aoa = 0.075 * Math.sin(totalAlpha) * Math.sin(totalAlpha);
		return cd_induced + cd_body_aoa;
	}

	public static double protuberanceFactor() {
		return protuberanceFactor;
	}

	public static void setProtuberanceFactor(double factor) {
		protuberanceFactor = Math.max(1.0, Math.min(1.20, factor));
	}
}

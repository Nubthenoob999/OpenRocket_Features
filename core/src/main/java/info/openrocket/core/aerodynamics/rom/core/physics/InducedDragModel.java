package info.openrocket.core.aerodynamics.rom.core.physics;

import info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput;

public class InducedDragModel {

	private static final double PROTUBERANCE_FACTOR = 1.02;

	private InducedDragModel() {
	}

	public static double cdInduced(double alphaRad, double mach, RomGeometryInput g) {
		if (alphaRad < 1e-6 || g.finSets.isEmpty()) {
			return 0.0;
		}
		double e = 0.9;

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

		double cd_induced = 0.0;
		for (RomGeometryInput.FinGeom finSet : g.finSets) {
			double chordMean = finSet.meanChord();
			double ar = 2.0 * finSet.span() / Math.max(chordMean, 1e-9);
			if (!(ar > 0.0)) {
				continue;
			}
			double cNaFin = 2.0 * Math.PI / (1.0 + 2.0 / ar);
			double cl = cNaFin * alphaRad * (finSet.totalPlanformArea() / g.referenceArea);
			cl *= compressibilityFactor;
			cd_induced += cl * cl / (Math.PI * ar * e);
		}
		double cd_body_aoa = 0.075 * Math.sin(alphaRad) * Math.sin(alphaRad);
		return cd_induced + cd_body_aoa;
	}

	public static double protuberanceFactor() {
		return PROTUBERANCE_FACTOR;
	}
}

package info.openrocket.core.aerodynamics.rom.core.physics;

import info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput;

/**
 * Semi-empirical normal force coefficient used for ROM diagnostics.
 */
public final class NormalForceModel {

	private static final double TWO_PI = 2.0 * Math.PI;

	private NormalForceModel() {
	}

	public static double CN(double alphaRad, double betaRad, double mach, RomGeometryInput g) {
		if (Math.abs(alphaRad) < 1e-8 && Math.abs(betaRad) < 1e-8) {
			return 0.0;
		}

		double cnBody = 2.0 * alphaRad;
		double cnFin = 0.0;
		if (g.finCount > 0) {
			double cMean = (g.finRootChord + g.finTipChord) / 2.0;
			double ar = (cMean > 1e-6) ? 2.0 * g.finSpan / cMean : 1.0;
			double cnAlpha = TWO_PI / (1.0 + 2.0 / Math.max(ar, 0.5));

			final double pgFactor;
			if (mach <= 0.8) {
				double beta2 = Math.max(1.0 - mach * mach, 0.01);
				pgFactor = 1.0 / Math.sqrt(beta2);
			} else if (mach < 1.2) {
				double subAt08 = 1.0 / Math.sqrt(Math.max(1.0 - 0.8 * 0.8, 0.01));
				double supAt12 = 1.0 / Math.sqrt(Math.max(1.2 * 1.2 - 1.0, 0.01));
				double t = (mach - 0.8) / 0.4;
				double smooth = t * t * (3.0 - 2.0 * t);
				pgFactor = subAt08 * (1.0 - smooth) + supAt12 * smooth;
			} else {
				double beta2 = Math.max(mach * mach - 1.0, 0.01);
				pgFactor = 1.0 / Math.sqrt(beta2);
			}

			double planformTotal = cMean * g.finSpan * g.finCount;
			cnFin = cnAlpha * alphaRad * pgFactor * planformTotal / g.referenceArea;
		}

		return cnBody + cnFin;
	}
}

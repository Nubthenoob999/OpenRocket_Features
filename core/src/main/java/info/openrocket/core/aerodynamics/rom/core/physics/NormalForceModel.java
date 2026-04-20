package info.openrocket.core.aerodynamics.rom.core.physics;

import info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput;

/**
 * Semi-empirical normal force coefficient used for ROM diagnostics.
 */
public final class NormalForceModel {

	private static final double TWO_PI = 2.0 * Math.PI;

	// Crossflow drag coefficient for a circular cylinder (Jorgensen TR R-474)
	private static final double CD_CROSSFLOW = 1.2;

	// Threshold (rad) above which crossflow drag becomes significant (~15°)
	private static final double CROSSFLOW_ALPHA_THRESHOLD = Math.toRadians(15.0);

	private NormalForceModel() {
	}

	public static double CN(double alphaRad, double betaRad, double mach, RomGeometryInput g) {
		if (Math.abs(alphaRad) < 1e-8 && Math.abs(betaRad) < 1e-8) {
			return 0.0;
		}

		// Body normal force with Mach correction (Prandtl-Glauert / Ackeret)
		double cnBody;
		if (mach <= 0.8) {
			double beta2 = Math.max(1.0 - mach * mach, 0.01);
			cnBody = 2.0 * alphaRad / Math.sqrt(beta2);
		} else if (mach < 1.2) {
			double subAt08 = 2.0 * alphaRad / Math.sqrt(Math.max(1.0 - 0.8 * 0.8, 0.01));
			double supAt12 = 2.0 * alphaRad / Math.sqrt(Math.max(1.2 * 1.2 - 1.0, 0.01));
			double t = (mach - 0.8) / 0.4;
			double smooth = t * t * (3.0 - 2.0 * t);
			cnBody = subAt08 * (1.0 - smooth) + supAt12 * smooth;
		} else {
			double beta2 = Math.max(mach * mach - 1.0, 0.01);
			cnBody = 2.0 * alphaRad / Math.sqrt(beta2);
		}

		// M2: Crossflow drag term for high angle of attack (Jorgensen decomposition)
		// CN_crossflow = Cd_c * (S_planform / S_ref) * sin²(α) * cos(α)
		double absAlpha = Math.abs(alphaRad);
		if (absAlpha > CROSSFLOW_ALPHA_THRESHOLD && g.bodyLength > 1e-6 && g.referenceArea > 1e-12) {
			double sinA = Math.sin(absAlpha);
			double cosA = Math.cos(absAlpha);
			double bodyPlanformArea = g.bodyLength * g.maxDiameter;
			double cnCrossflow = CD_CROSSFLOW * (bodyPlanformArea / g.referenceArea) * sinA * sinA * cosA;
			cnBody += Math.signum(alphaRad) * cnCrossflow;
		}

		double cnFin = 0.0;
		if (g.finCount > 0) {
			double cMean = (g.finRootChord + g.finTipChord) / 2.0;
			double ar = (cMean > 1e-6) ? 2.0 * g.finSpan / cMean : 1.0;

			// M3: Use regime-appropriate CNα directly instead of multiplying
			// incompressible CNα by a compressibility factor, which double-counts.
			final double cnAlphaEff;
			if (mach <= 0.8) {
				// Subsonic: Prandtl-Glauert corrected lifting-line
				double beta2 = Math.max(1.0 - mach * mach, 0.01);
				cnAlphaEff = TWO_PI / (1.0 + 2.0 / Math.max(ar, 0.5)) / Math.sqrt(beta2);
			} else if (mach < 1.2) {
				// Transonic blend
				double subAt08 = TWO_PI / (1.0 + 2.0 / Math.max(ar, 0.5))
						/ Math.sqrt(Math.max(1.0 - 0.8 * 0.8, 0.01));
				// Supersonic: Ackeret linear theory CNα = 4 / √(M²-1)
				double supAt12 = 4.0 / Math.sqrt(Math.max(1.2 * 1.2 - 1.0, 0.01));
				double t = (mach - 0.8) / 0.4;
				double smooth = t * t * (3.0 - 2.0 * t);
				cnAlphaEff = subAt08 * (1.0 - smooth) + supAt12 * smooth;
			} else {
				// Supersonic: Ackeret linear theory
				cnAlphaEff = 4.0 / Math.sqrt(Math.max(mach * mach - 1.0, 0.01));
			}

			double planformTotal = cMean * g.finSpan * g.finCount;
			cnFin = cnAlphaEff * alphaRad * planformTotal / g.referenceArea;
		}

		return cnBody + cnFin;
	}
}

package info.openrocket.core.aerodynamics.rom.core.physics;

import info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput;

public final class FinDragModel {

	private FinDragModel() {
	}

	public static double cdFinFriction(double mach, double reLBody, RomGeometryInput g) {
		if (g.finCount == 0 || g.finSets.isEmpty()) {
			return 0.0;
		}
		double cd = 0.0;
		for (RomGeometryInput.FinGeom finSet : g.finSets) {
			double cMean = finSet.meanChord();
			if (cMean < 1e-6 || g.bodyLength <= 0.0) {
				continue;
			}
			double reFin = reLBody * (cMean / g.bodyLength);
			double cfInc = SkinFrictionModel.cfIncompressible(reFin, 5e5);
			double cfComp = SkinFrictionModel.vanDriestII(cfInc, mach, 1.0);
			double tc = finSet.thickness() / cMean;
			double ffFin = 1.0 + 2.0 * tc;
			cd += cfComp * ffFin * finSet.wettedArea() / g.referenceArea;
		}
		return cd;
	}

	public static double cdFinWaveSupersonic(double mach, RomGeometryInput g) {
		if (g.finCount == 0 || g.finSets.isEmpty() || mach <= 1.2) {
			return 0.0;
		}
		double betaM = Math.sqrt(mach * mach - 1.0);
		double cd = 0.0;
		for (RomGeometryInput.FinGeom finSet : g.finSets) {
			double cMean = finSet.meanChord();
			if (cMean < 1e-6) {
				continue;
			}
			double tc = finSet.thickness() / cMean;
			double planformTotal = finSet.totalPlanformArea();
			cd += 4.0 * tc * tc / betaM * planformTotal / g.referenceArea;
		}
		return cd;
	}

	public static double cdFinInterference(double cdFinFrictionPlusWave, RomGeometryInput g) {
		if (g.finCount == 0) {
			return 0.0;
		}
		return cdFinFrictionPlusWave * 0.04;
	}

	public static double cdFinInducedDrag(double alphaRad, double betaRad, double mach, RomGeometryInput g) {
		if (g.finCount == 0 || g.finSets.isEmpty()) {
			return 0.0;
		}
		double alphaEff = Math.sqrt(alphaRad * alphaRad + betaRad * betaRad);
		if (alphaEff < 1e-8) {
			return 0.0;
		}

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

		double cd = 0.0;
		for (RomGeometryInput.FinGeom finSet : g.finSets) {
			double cMean = finSet.meanChord();
			if (cMean < 1e-6) {
				continue;
			}
			double ar = 2.0 * finSet.span() / cMean;
			double cnAlpha = 2.0 * Math.PI / (1.0 + 2.0 / Math.max(ar, 0.5));
			double cl = cnAlpha * alphaEff * pgFactor * (finSet.totalPlanformArea() / g.referenceArea);
			cd += cl * Math.sin(alphaEff);
		}

		return cd;
	}
}

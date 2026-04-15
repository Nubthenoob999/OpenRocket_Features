package info.openrocket.core.aerodynamics.rom.core.physics;

import info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput;

public class WaveDragModel {
	private static final double EPS = 1e-9;

	private static final double GAMMA = 1.4;

	private WaveDragModel() {
	}

	public static double dragDivergenceMach(RomGeometryInput g) {
		double ratio = Math.max(g.noseLength, 0.0) / Math.max(g.maxDiameter, EPS);
		if (ratio > 6.0) {
			ratio = 6.0;
		}
		double mdd = -0.0156 * ratio * ratio + 0.136 * ratio + 0.6817;
		return Double.isFinite(mdd) ? mdd : 0.8;
	}

	public static double criticalMach(RomGeometryInput g) {
		return dragDivergenceMach(g) - 0.10;
	}

	public static double cdNoseWaveSupersonic(double mach, RomGeometryInput g) {
		mach = Double.isFinite(mach) ? Math.max(mach, 0.0) : 0.0;
		double lnOverD = Math.max(g.noseLength, EPS) / Math.max(g.maxDiameter, EPS);
		double cd;
		switch (g.noseShape) {
			case VON_KARMAN:
				cd = 0.083 / (lnOverD * lnOverD) * searsHaackDecay(mach);
				break;
			case OGIVE:
				cd = 0.10 / (lnOverD * lnOverD) * searsHaackDecay(mach);
				break;
			case PARABOLIC:
				cd = 0.11 / (lnOverD * lnOverD) * searsHaackDecay(mach);
				break;
			case CONICAL: {
				double theta = Math.atan(0.5 / lnOverD);
				cd = (4.0 / (GAMMA * Math.max(mach * mach, 0.05)))
						* Math.pow(theta, 2.0)
						* (1.0 + 0.5 * (GAMMA + 1) * Math.pow(theta, 2.0));
				break;
			}
			case ELLIPSOID:
				cd = 0.13 / (lnOverD * lnOverD) * searsHaackDecay(mach);
				break;
			default:
				cd = 0.12 / (lnOverD * lnOverD) * searsHaackDecay(mach);
				break;
		}
		return Double.isFinite(cd) ? Math.max(0.0, cd) : 0.0;
	}

	private static double searsHaackDecay(double mach) {
		if (mach <= 1.0) {
			return 1.0;
		}
		double beta2 = mach * mach - 1.0;
		double regularized = Math.sqrt(beta2 + 0.25);
		double normFactor = Math.sqrt(1.5 * 1.5 - 1.0 + 0.25);
		return normFactor / regularized;
	}

	public static double cdFinWaveSupersonic(double mach, RomGeometryInput g) {
		if (mach <= 1.0 || g.finSets.isEmpty()) {
			return 0.0;
		}
		double beta = Math.sqrt(mach * mach - 1.0);
		double cd = 0.0;
		for (RomGeometryInput.FinGeom finSet : g.finSets) {
			double meanChord = Math.max(finSet.meanChord(), EPS);
			double tc = Math.max(finSet.thickness(), 0.0) / meanChord;
			double cdPerFin = 4.0 * tc * tc / beta;
			cd += cdPerFin * finSet.totalPlanformArea() / Math.max(g.referenceArea, EPS);
		}
		return Double.isFinite(cd) ? Math.max(0.0, cd) : 0.0;
	}
}

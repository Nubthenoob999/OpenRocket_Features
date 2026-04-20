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
		if (mach <= 1.0) {
			return 0.0;
		}
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
		return 1.0 / Math.sqrt(mach * mach - 1.0 + 0.01);
	}

	public static double cdFinWaveSupersonic(double mach, RomGeometryInput g) {
		if (mach <= 1.0) {
			return 0.0;
		}
		double meanChord = Math.max((g.finRootChord + g.finTipChord) / 2.0, EPS);
		double tc = Math.max(g.finThickness, 0.0) / meanChord;
		double beta = Math.sqrt(Math.max(mach * mach - 1.0, 1e-6));
		double cdPerFin = 4.0 * tc * tc / beta;
		double finPlanform = 0.5 * Math.max(g.finRootChord + g.finTipChord, 0.0) * Math.max(g.finSpan, 0.0);
		double cd = cdPerFin * Math.max(g.finCount, 0) * finPlanform / Math.max(g.referenceArea, EPS);
		return Double.isFinite(cd) ? Math.max(0.0, cd) : 0.0;
	}
}

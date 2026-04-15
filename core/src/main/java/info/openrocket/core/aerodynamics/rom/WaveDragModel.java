package info.openrocket.core.aerodynamics.rom;

import java.util.List;

public class WaveDragModel {
	private static final double EPS = 1e-9;

	private static final double GAMMA = 1.4;

	/**
	 * Drag divergence Mach number from Braeunig correlation.
	 * M_DD = -0.0156*(L_N/d)^2 + 0.136*(L_N/d) + 0.6817
	 * Valid for L_N/d < 0.6 (nose length to effective body diameter ratio).
	 */
	public static double dragDivergenceMach(RomGeometryParameters g) {
		double ratio = Math.max(g.noseLength, 0.0) / Math.max(g.maxDiameter, EPS);
		if (ratio > 6.0) {
			ratio = 6.0; // clamp to correlation range
		}
		double mdd = -0.0156 * ratio * ratio + 0.136 * ratio + 0.6817;
		return Double.isFinite(mdd) ? mdd : 0.8;
	}

	/**
	 * Critical Mach number: onset of local supersonic flow.
	 * Approximate: M_cr ~= M_DD - 0.10 for slender rockets.
	 */
	public static double criticalMach(RomGeometryParameters g) {
		return dragDivergenceMach(g) - 0.10;
	}

	/**
	 * Nose cone wave drag coefficient at supersonic speeds.
	 * Uses nose-shape-specific correlations referenced to frontal area.
	 *
	 * Von Karman ogive: lowest wave drag (Karman-Moore integral result)
	 * Conical: Cd_wave ~= (theta_half)^2 * 4/gamma for M >> 1 (theta in radians)
	 * Others: interpolated empirical fits
	 */
	public static double cdNoseWaveSupersonic(double mach, RomGeometryParameters g) {
		mach = Double.isFinite(mach) ? Math.max(mach, 0.0) : 0.0;
		double lnOverD = Math.max(g.noseLength, EPS) / Math.max(g.maxDiameter, EPS);
		double cd;
		switch (g.noseShape) {
			case VON_KARMAN:
				// Minimum wave drag: approaches Sears-Haack scaling
				// Cd_wave ~= 0.083 / (lnOverD^2) at M=1.5, decays with M
				cd = 0.083 / (lnOverD * lnOverD) * searsHaackDecay(mach);
				break;
			case OGIVE:
				cd = 0.10 / (lnOverD * lnOverD) * searsHaackDecay(mach);
				break;
			case PARABOLIC:
				cd = 0.11 / (lnOverD * lnOverD) * searsHaackDecay(mach);
				break;
			case CONICAL: {
				double theta = Math.atan(0.5 / lnOverD); // half-angle
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

	/** Mach decay factor for wave drag: 1/sqrt(M^2-1) dependence. */
	private static double searsHaackDecay(double mach) {
		if (mach <= 1.0) {
			return 1.0;
		}
		return 1.0 / Math.sqrt(mach * mach - 1.0 + 0.01); // +0.01 prevents singularity at M=1
	}

	/**
	 * Fin wave drag at supersonic speeds using Ackeret thin-airfoil theory.
	 * Cd_fin_wave = 4*(t/c)^2 / sqrt(M^2-1) per fin, summed and scaled.
	 */
	public static double cdFinWaveSupersonic(double mach, RomGeometryParameters g) {
		List<RomGeometryParameters.FinGeom> finSets = g.resolvedFinSets();
		if (mach <= 1.0 || finSets.isEmpty()) {
			return 0.0;
		}
		double beta = Math.sqrt(mach * mach - 1.0);
		double cd = 0.0;
		for (RomGeometryParameters.FinGeom finSet : finSets) {
			double cMean = Math.max(finSet.meanChord(), EPS);
			double tc = Math.max(finSet.thickness(), 0.0) / cMean;
			double cdPerFin = 4.0 * tc * tc / beta;
			cd += cdPerFin * finSet.totalPlanformArea() / Math.max(g.referenceArea, EPS);
		}
		return Double.isFinite(cd) ? Math.max(0.0, cd) : 0.0;
	}
}

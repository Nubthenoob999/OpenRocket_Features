package info.openrocket.core.aerodynamics.rom;

public class WaveDragModel {

	private static final double GAMMA = 1.4;

	/**
	 * Drag divergence Mach number from Braeunig correlation.
	 * M_DD = -0.0156*(L_N/d)^2 + 0.136*(L_N/d) + 0.6817
	 * Valid for L_N/d < 0.6 (nose length to effective body diameter ratio).
	 */
	public static double dragDivergenceMach(RomGeometryParameters g) {
		double ratio = g.noseLength / g.maxDiameter;
		if (ratio > 6.0) {
			ratio = 6.0; // clamp to correlation range
		}
		return -0.0156 * ratio * ratio + 0.136 * ratio + 0.6817;
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
		double lnOverD = g.noseLength / g.maxDiameter;
		switch (g.noseShape) {
			case VON_KARMAN:
				// Minimum wave drag: approaches Sears-Haack scaling
				// Cd_wave ~= 0.083 / (lnOverD^2) at M=1.5, decays with M
				return 0.083 / (lnOverD * lnOverD) * searsHaackDecay(mach);
			case OGIVE:
				return 0.10 / (lnOverD * lnOverD) * searsHaackDecay(mach);
			case PARABOLIC:
				return 0.11 / (lnOverD * lnOverD) * searsHaackDecay(mach);
			case CONICAL: {
				double theta = Math.atan(0.5 / lnOverD); // half-angle
				return (4.0 / (GAMMA * mach * mach))
						* Math.pow(theta, 2.0)
						* (1.0 + 0.5 * (GAMMA + 1) * Math.pow(theta, 2.0));
			}
			case ELLIPSOID:
				return 0.13 / (lnOverD * lnOverD) * searsHaackDecay(mach);
			default:
				return 0.12 / (lnOverD * lnOverD) * searsHaackDecay(mach);
		}
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
		if (mach <= 1.0) {
			return 0.0;
		}
		double tc = g.finThickness / ((g.finRootChord + g.finTipChord) / 2.0);
		double beta = Math.sqrt(mach * mach - 1.0);
		double cdPerFin = 4.0 * tc * tc / beta;
		// Scale fin wave drag to frontal reference area
		double finPlanform = 0.5 * (g.finRootChord + g.finTipChord) * g.finSpan;
		return cdPerFin * g.finCount * finPlanform / g.referenceArea;
	}
}

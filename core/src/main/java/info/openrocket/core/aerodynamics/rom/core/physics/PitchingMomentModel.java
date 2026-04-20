package info.openrocket.core.aerodynamics.rom.core.physics;

import info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput;

/**
 * Semi-empirical pitching moment coefficient used for ROM diagnostics.
 */
public final class PitchingMomentModel {

	private PitchingMomentModel() {
	}

	/**
	 * Overload without explicit CG — assumes CG at 55% body length (legacy default).
	 */
	public static double Cm(double CN, double alphaRad, RomGeometryInput g) {
		return Cm(CN, alphaRad, 0.0, g, 0.55 * g.bodyLength);
	}

	/**
	 * Overload without mach — defaults to incompressible.
	 */
	public static double Cm(double CN, double alphaRad, RomGeometryInput g, double xCG) {
		return Cm(CN, alphaRad, 0.0, g, xCG);
	}

	/**
	 * Pitching moment about the given CG location.
	 *
	 * @param CN       total normal force coefficient
	 * @param alphaRad angle of attack (rad)
	 * @param mach     freestream Mach number
	 * @param g        geometry
	 * @param xCG      axial CG position measured from nose tip (m)
	 * @return Cm about xCG; positive = nose-up when CG is ahead of CP
	 */
	public static double Cm(double CN, double alphaRad, double mach, RomGeometryInput g, double xCG) {
		if (Math.abs(alphaRad) < 1e-8 || g.bodyLength < 1e-6 || Math.abs(CN) < 1e-10) {
			return 0.0;
		}

		final double noseCpFraction;
		switch (g.noseShape) {
			case CONICAL:
				noseCpFraction = 1.0 / 3.0;
				break;
			case OGIVE:
				noseCpFraction = 0.466;
				break;
			case VON_KARMAN:
				noseCpFraction = 0.500;
				break;
			case PARABOLIC:
				noseCpFraction = 0.417;
				break;
			case ELLIPSOID:
				noseCpFraction = 0.500;
				break;
			case HAACK:
				noseCpFraction = 0.500;
				break;
			default:
				noseCpFraction = 0.466;
		}
		double xCpNose = noseCpFraction * g.noseLength;

		double xCpFins = xCpNose;
		if (g.finCount > 0 && g.finRootChord > 1e-6) {
			double lambda = g.finTipChord / g.finRootChord;
			double macLeOffset = (g.finRootChord / 3.0) * (1.0 + 2.0 * lambda) / (1.0 + lambda);
			double finRootLeX = (g.finAxialPosition > 0.0)
					? g.finAxialPosition
					: Math.max(0.0, g.bodyLength - g.finRootChord - Math.max(g.boattailLength, 0.0));
			double cMean = (g.finRootChord + g.finTipChord) / 2.0;
			xCpFins = finRootLeX + macLeOffset + 0.25 * cMean;
			xCpFins = Math.max(xCpNose, Math.min(xCpFins, g.bodyLength));
		}

		// M16: Apply Prandtl-Glauert / Ackeret compressibility correction to body CN
		double compFactor;
		if (mach <= 0.8) {
			double beta2 = Math.max(1.0 - mach * mach, 0.01);
			compFactor = 1.0 / Math.sqrt(beta2);
		} else if (mach < 1.2) {
			double subAt08 = 1.0 / Math.sqrt(Math.max(1.0 - 0.8 * 0.8, 0.01));
			double supAt12 = 1.0 / Math.sqrt(Math.max(1.2 * 1.2 - 1.0, 0.01));
			double t = (mach - 0.8) / 0.4;
			double smooth = t * t * (3.0 - 2.0 * t);
			compFactor = subAt08 * (1.0 - smooth) + supAt12 * smooth;
		} else {
			double beta2 = Math.max(mach * mach - 1.0, 0.01);
			compFactor = 1.0 / Math.sqrt(beta2);
		}
		double cnBody = 2.0 * Math.abs(alphaRad) * compFactor;
		double cnFin = Math.max(0.0, Math.abs(CN) - cnBody);
		double cnTotal = cnBody + cnFin;
		double xCp = (cnTotal > 1e-10)
				? (cnBody * xCpNose + cnFin * xCpFins) / cnTotal
				: xCpNose;

		// Moment about CG: Cm = CN * (xCG - xCP) / Lref
		// Positive Cm = nose-up when CG is ahead of CP (statically stable)
		return CN * (xCG - xCp) / g.bodyLength;
	}
}

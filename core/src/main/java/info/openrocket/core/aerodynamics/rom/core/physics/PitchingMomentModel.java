package info.openrocket.core.aerodynamics.rom.core.physics;

import info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput;

/**
 * Semi-empirical pitching moment coefficient used for ROM diagnostics.
 */
public final class PitchingMomentModel {

	private PitchingMomentModel() {
	}

	public static double Cm(double CN, double alphaRad, RomGeometryInput g) {
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
				noseCpFraction = 0.333;
				break;
			case HAACK:
				noseCpFraction = 0.500;
				break;
			default:
				noseCpFraction = 0.466;
		}
		double xCpNose = noseCpFraction * g.noseLength;

		double xCpFins = xCpNose;
		if (g.finCount > 0 && !g.finSets.isEmpty()) {
			double weightedXcp = 0.0;
			double totalPlanformArea = 0.0;
			for (RomGeometryInput.FinGeom finSet : g.finSets) {
				if (!(finSet.rootChord() > 1e-6)) {
					continue;
				}
				double lambda = finSet.tipChord() / finSet.rootChord();
				double macLeOffset = (finSet.rootChord() / 3.0) * (1.0 + 2.0 * lambda) / (1.0 + lambda);
				double finRootLeX = (finSet.axialPosition() > 0.0)
						? finSet.axialPosition()
						: Math.max(0.0, g.bodyLength - finSet.rootChord() - Math.max(g.boattailLength, 0.0));
				double cMean = finSet.meanChord();
				double setXcp = finRootLeX + macLeOffset + 0.25 * cMean;
				setXcp = Math.max(xCpNose, Math.min(setXcp, g.bodyLength));
				double weight = Math.max(finSet.totalPlanformArea(), 1e-12);
				totalPlanformArea += weight;
				weightedXcp += weight * setXcp;
			}
			if (totalPlanformArea > 0.0) {
				xCpFins = weightedXcp / totalPlanformArea;
			}
		}

		double cnBody = 2.0 * Math.abs(alphaRad);
		double cnFin = Math.max(0.0, Math.abs(CN) - cnBody);
		double cnTotal = cnBody + cnFin;
		double xCp = (cnTotal > 1e-10)
				? (cnBody * xCpNose + cnFin * xCpFins) / cnTotal
				: xCpNose;

		return -CN * (xCp / g.bodyLength);
	}
}

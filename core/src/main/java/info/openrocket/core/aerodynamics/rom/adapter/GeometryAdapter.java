package info.openrocket.core.aerodynamics.rom.adapter;

import java.util.ArrayList;
import java.util.List;

import info.openrocket.core.aerodynamics.rom.RomGeometryParameters;
import info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput;

/**
 * Converts OpenRocket ROM geometry into rom.core input geometry.
 */
public final class GeometryAdapter {

	private GeometryAdapter() {
	}

	public static RomGeometryInput toInput(RomGeometryParameters g) {
		List<RomGeometryInput.FinGeom> finSets = new ArrayList<>(g.resolvedFinSets().size());
		for (RomGeometryParameters.FinGeom finSet : g.resolvedFinSets()) {
			finSets.add(new RomGeometryInput.FinGeom(
					finSet.count(),
					finSet.rootChord(),
					finSet.tipChord(),
					finSet.span(),
					finSet.thickness(),
					finSet.sweepLength(),
					finSet.wettedArea(),
					finSet.axialPosition(),
					finSet.finType(),
					finSet.thicknessFallbackUsed()));
		}
		double finAxialPosition = weightedFinAxialPosition(g);
		return new RomGeometryInput(
				g.bodyLength,
				g.maxDiameter,
				g.baseArea,
				g.bodyWetArea,
				g.wetArea,
				g.noseLength,
				mapNoseShape(g.noseShape),
				g.finessRatio,
				g.referenceArea,
				g.boattailLength,
				g.boattailBaseDiameter,
				finSets,
				g.finCount,
				g.finRootChord,
				g.finTipChord,
				g.finSpan,
				g.finThickness,
				g.finSweepAngle,
				g.finWettedArea,
				finAxialPosition,
				g.motorExitArea,
				g.surfaceRoughness);
	}

	private static double weightedFinAxialPosition(RomGeometryParameters g) {
		double weightedAxial = 0.0;
		double totalPlanformArea = 0.0;
		for (RomGeometryParameters.FinGeom finSet : g.resolvedFinSets()) {
			double weight = Math.max(finSet.totalPlanformArea(), 1e-12);
			weightedAxial += weight * finSet.axialPosition();
			totalPlanformArea += weight;
		}
		if (totalPlanformArea > 0.0) {
			return weightedAxial / totalPlanformArea;
		}
		return Math.max(0.0, g.bodyLength - g.finRootChord - Math.max(g.boattailLength, 0.0));
	}

	private static RomGeometryInput.NoseShape mapNoseShape(RomGeometryParameters.NoseShape s) {
		if (s == null) {
			return RomGeometryInput.NoseShape.OGIVE;
		}
		switch (s) {
			case CONICAL:
				return RomGeometryInput.NoseShape.CONICAL;
			case OGIVE:
				return RomGeometryInput.NoseShape.OGIVE;
			case VON_KARMAN:
				return RomGeometryInput.NoseShape.VON_KARMAN;
			case PARABOLIC:
				return RomGeometryInput.NoseShape.PARABOLIC;
			case ELLIPSOID:
				return RomGeometryInput.NoseShape.ELLIPSOID;
			case HAACK:
			default:
				return RomGeometryInput.NoseShape.HAACK;
		}
	}
}

package info.openrocket.core.aerodynamics.rom.adapter;

import info.openrocket.core.aerodynamics.rom.RomGeometryParameters;
import info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput;

/**
 * Converts OpenRocket ROM geometry into rom.core input geometry.
 */
public final class GeometryAdapter {

	private GeometryAdapter() {
	}

	public static RomGeometryInput toInput(RomGeometryParameters g) {
		return new RomGeometryInput(
				g.bodyLength,
				g.maxDiameter,
				g.baseArea,
				g.wetArea,
				g.noseLength,
				mapNoseShape(g.noseShape),
				g.finessRatio,
				g.referenceArea,
				g.boattailLength,
				g.boattailBaseDiameter,
				g.finCount,
				g.finRootChord,
				g.finTipChord,
				g.finSpan,
				g.finThickness,
				g.finSweepAngle,
				g.finWettedArea,
				g.motorExitArea,
				g.surfaceRoughness);
	}

	private static RomGeometryInput.NoseShape mapNoseShape(RomGeometryParameters.NoseShape s) {
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

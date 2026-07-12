package info.openrocket.core.aerodynamics.physicsaero.fin;

import java.util.Objects;

import info.openrocket.core.util.Coordinate;

/** Immutable SI/radian geometry and section state for one spanwise fin strip. */
public record FinStrip(double spanwiseStationM, double spanwiseWidthM,
		double leadingEdgeXM, double trailingEdgeXM, double chordM,
		double leadingEdgeSweepRad, double halfChordSweepRad, double thicknessToChord,
		FinSectionFamily sectionFamily, Coordinate centroidBodyM, double areaM2,
		FinLocalFrame localFrame, double bodyRootRadiusM) {
	private static final double GEOMETRY_TOLERANCE = 1.0e-10;

	public FinStrip {
		Objects.requireNonNull(sectionFamily, "sectionFamily");
		Objects.requireNonNull(centroidBodyM, "centroidBodyM");
		Objects.requireNonNull(localFrame, "localFrame");
		if (!finite(spanwiseStationM, spanwiseWidthM, leadingEdgeXM, trailingEdgeXM, chordM,
				leadingEdgeSweepRad, halfChordSweepRad, thicknessToChord, areaM2, bodyRootRadiusM)
				|| !finite(centroidBodyM.x, centroidBodyM.y, centroidBodyM.z)) {
			throw new IllegalArgumentException("fin strip values must be finite");
		}
		if (spanwiseStationM < 0.0 || spanwiseWidthM <= 0.0 || chordM <= 0.0
				|| thicknessToChord < 0.0 || areaM2 <= 0.0 || bodyRootRadiusM < 0.0) {
			throw new IllegalArgumentException("invalid fin strip dimensions");
		}
		double edgeChord = trailingEdgeXM - leadingEdgeXM;
		if (Math.abs(edgeChord - chordM) > GEOMETRY_TOLERANCE * Math.max(1.0, chordM)) {
			throw new IllegalArgumentException("chordM must equal trailingEdgeXM - leadingEdgeXM");
		}
		centroidBodyM = new Coordinate(centroidBodyM.x, centroidBodyM.y, centroidBodyM.z);
	}

	private static boolean finite(double... values) {
		for (double value : values) {
			if (!Double.isFinite(value)) {
				return false;
			}
		}
		return true;
	}
}

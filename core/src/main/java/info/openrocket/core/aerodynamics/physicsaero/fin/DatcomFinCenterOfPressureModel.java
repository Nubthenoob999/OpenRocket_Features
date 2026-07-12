package info.openrocket.core.aerodynamics.physicsaero.fin;

/** Named low-confidence fallback when no sourced DATCOM CP correlation is in range. */
public final class DatcomFinCenterOfPressureModel {
	public Result halfMeanAerodynamicChordFallback(double leadingEdgeXM, double meanAerodynamicChordM) {
		if (meanAerodynamicChordM <= 0) throw new IllegalArgumentException("invalid mean aerodynamic chord");
		return new Result(leadingEdgeXM + 0.5 * meanAerodynamicChordM, 0.35,
				"HALF_MEAN_AERODYNAMIC_CHORD_LOW_CONFIDENCE_FALLBACK");
	}
	public record Result(double xM, double confidence, String method) {}
}

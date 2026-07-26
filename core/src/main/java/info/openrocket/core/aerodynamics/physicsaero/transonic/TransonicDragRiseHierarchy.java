package info.openrocket.core.aerodynamics.physicsaero.transonic;

/** Geometry-scaled hierarchy for the zero-lift transonic wave-drag peak. */
public final class TransonicDragRiseHierarchy {
	public static final String METHOD_ID = "MISSILE_GEOMETRY_SCALED_DRAG_RISE_V3";
	public static final String BODY_METHOD_ID = METHOD_ID + ":BODY";
	public static final String FIN_METHOD_ID = METHOD_ID + ":FIN";
	public static final String INTERFERENCE_METHOD_ID = METHOD_ID + ":INTERFERENCE";

	/** Component closures whose sum is exactly the legacy aggregate closure. */
	public record Decomposition(TransonicDragRiseModel.Parameters body,
			TransonicDragRiseModel.Parameters fin,
			TransonicDragRiseModel.Parameters interference,
			TransonicDragRiseModel.Parameters total) { }

	/**
	 * Inputs are already nondimensionalized on the vehicle reference geometry.
	 * Fin planform area includes every physical fin in the set.
	 */
	public record GeometryInputs(double finenessRatio, double bodyFrontalAreaRatio,
			double bodyCurvatureMetric, double finThicknessRatio,
			double finPlanformAreaRatio, double halfChordSweepRad) {
		public GeometryInputs {
			if (!Double.isFinite(finenessRatio + bodyFrontalAreaRatio + bodyCurvatureMetric
					+ finThicknessRatio + finPlanformAreaRatio + halfChordSweepRad)
					|| finenessRatio <= 0 || bodyFrontalAreaRatio < 0 || bodyCurvatureMetric < 0
					|| finThicknessRatio < 0 || finPlanformAreaRatio < 0
					|| Math.abs(halfChordSweepRad) >= Math.PI / 2) {
				throw new IllegalArgumentException("invalid transonic geometry inputs");
			}
		}
	}

	public TransonicDragRiseModel.Parameters select(GeometryInputs geometry,
			double criticalMach, double dragDivergenceMach) {
		return decompose(geometry, criticalMach, dragDivergenceMach).total();
	}

	/**
	 * Separates body, fin, and body-fin interference ownership without changing
	 * the published aggregate drag-rise value used by existing tables.
	 */
	public Decomposition decompose(GeometryInputs geometry,
			double criticalMach, double dragDivergenceMach) {
		double peakMach = Math.max(1.02, dragDivergenceMach + 0.04);

		// Missile-family body peak, scaled by fineness and by the actual frontal
		// area/reference-area ratio.  The square-root scaling avoids an unphysical
		// constant peak for geometrically dissimilar bodies.
		double finenessScale = Math.sqrt(10 / geometry.finenessRatio());
		finenessScale = Math.max(0.5, Math.min(2, finenessScale));
		double bodyAmplitude = 0.12 * finenessScale * geometry.bodyFrontalAreaRatio();
		double curvatureAmplitude = 0.02 * Math.min(1, geometry.bodyCurvatureMetric())
				* geometry.bodyFrontalAreaRatio();

		// Linearized one-sided wedge wave drag is proportional to (t/c)^2/beta.
		// A beta floor regularizes the sonic limit; half-chord sweep enters through
		// the normal-flow component and the area is referenced to the vehicle.
		double beta = Math.sqrt(Math.max(0.04, peakMach * peakMach - 1));
		double normalFlow = Math.pow(Math.cos(geometry.halfChordSweepRad()), 2);
		double finAmplitude = geometry.finThicknessRatio() * geometry.finThicknessRatio()
				* geometry.finPlanformAreaRatio() * normalFlow / beta;

		double rawBodyAmplitude = bodyAmplitude + curvatureAmplitude;
		double rawAmplitude = rawBodyAmplitude + finAmplitude;
		double amplitude = Math.min(0.45, rawAmplitude);
		double scale = rawAmplitude > 0 ? amplitude / rawAmplitude : 0;

		// The interaction share is carved out of the body and fin terms, rather
		// than added to them, so decomposition is coefficient preserving.
		double rawInterference = 0.08 * Math.min(rawBodyAmplitude, finAmplitude);
		double interferenceAmplitude = rawInterference * scale;
		double bodyOwnedAmplitude = Math.max(0,
				(rawBodyAmplitude - 0.5 * rawInterference) * scale);
		double finOwnedAmplitude = Math.max(0,
				(finAmplitude - 0.5 * rawInterference) * scale);
		double recoveryMach = Math.max(1.35, peakMach + 0.10);
		return new Decomposition(
				parameters(criticalMach, dragDivergenceMach, peakMach, bodyOwnedAmplitude,
						recoveryMach, BODY_METHOD_ID),
				parameters(criticalMach, dragDivergenceMach, peakMach, finOwnedAmplitude,
						recoveryMach, FIN_METHOD_ID),
				parameters(criticalMach, dragDivergenceMach, peakMach, interferenceAmplitude,
						recoveryMach, INTERFERENCE_METHOD_ID),
				parameters(criticalMach, dragDivergenceMach, peakMach, amplitude,
						recoveryMach, METHOD_ID));
	}

	private static TransonicDragRiseModel.Parameters parameters(double criticalMach,
			double dragDivergenceMach, double peakMach, double amplitude,
			double recoveryMach, String methodId) {
		return new TransonicDragRiseModel.Parameters(criticalMach, dragDivergenceMach,
				peakMach, amplitude, recoveryMach, 2, 4, methodId);
	}

	/** Compatibility overload for callers without explicit area data. */
	public TransonicDragRiseModel.Parameters select(double finenessRatio,
			double finThicknessRatio, double criticalMach, double dragDivergenceMach) {
		if (!Double.isFinite(finenessRatio + finThicknessRatio)
				|| finenessRatio <= 0 || finThicknessRatio < 0) {
			throw new IllegalArgumentException("invalid legacy transonic geometry inputs");
		}
		double peakMach = Math.max(1.02, dragDivergenceMach + 0.08);
		double amplitude = Math.min(0.45, 0.12 + 0.3 * finThicknessRatio
				+ 0.08 / Math.max(2, finenessRatio));
		return new TransonicDragRiseModel.Parameters(criticalMach, dragDivergenceMach,
				peakMach, amplitude, Math.max(1.35, peakMach + 0.10), 2, 4,
				"MISSILE_GEOMETRY_FAMILY_V1");
	}
}

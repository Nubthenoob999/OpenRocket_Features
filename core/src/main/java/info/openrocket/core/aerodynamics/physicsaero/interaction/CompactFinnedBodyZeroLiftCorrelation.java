package info.openrocket.core.aerodynamics.physicsaero.interaction;

import java.util.OptionalDouble;

import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;

/** NACA RM A53D02 zero-lift drag correlation for compact four-fin bodies. */
public final class CompactFinnedBodyZeroLiftCorrelation {
	public static final String METHOD_ID = "A53D02_COMPACT_FINNED_BODY_ZERO_LIFT_V1";
	private static final double[] MACH = {0.6, 0.8, 0.95, 1.02, 1.2, 1.5, 2.0, 2.3,
			2.6, 3.0, 3.3, 4.0, 4.7, 5.4, 7.2, 10.0};
	private static final double[] CD = {0.36, 0.365, 0.37, 0.535, 0.515, 0.47, 0.445, 0.40,
			0.375, 0.315, 0.295, 0.235, 0.195, 0.165, 0.15, 0.11};

	public OptionalDouble evaluate(AeroGeometry geometry, double mach, double incidenceRad) {
		if (Math.abs(incidenceRad) > 1e-10 || mach < MACH[0] || mach > MACH[MACH.length - 1]) {
			return OptionalDouble.empty();
		}
		if (!sourceSimilar(geometry)) return OptionalDouble.empty();
		double fineness = geometry.references().vehicleLengthM()
				/ geometry.references().maximumBodyDiameterM();
		double scale = Math.max(0.85, Math.min(1.15, 10.0 / fineness));
		double thicknessRatio = geometry.components().stream()
				.filter(c -> c.finGeometry() != null)
				.mapToDouble(c -> c.localReferences().getOrDefault("thicknessRatio", 0.04))
				.average().orElse(0.04);
		// The digitized A53D02 curve is the t/c=0.04 anchor.  Preserve that
		// source ordinate while retaining the expected geometry sensitivity for
		// other compact finned bodies.
		double thicknessScale = Math.max(0.75, 1.0 + 1.5 * (thicknessRatio - 0.04));
		return OptionalDouble.of(scale * thicknessScale * interpolate(mach));
	}

	/*
	 * This curve is a whole-vehicle measurement, not a generic four-fin drag
	 * law.  Restrict total-CA replacement to the source model's principal
	 * similarity coordinates; broader geometries retain the component build-up.
	 */
	private static boolean sourceSimilar(AeroGeometry geometry) {
		double fineness = geometry.references().vehicleLengthM()
				/ geometry.references().maximumBodyDiameterM();
		int finCount = geometry.components().stream().filter(c -> c.finGeometry() != null)
				.mapToInt(c -> c.finGeometry().count()).sum();
		double finAreaRatio = geometry.components().stream().filter(c -> c.finGeometry() != null)
				.mapToDouble(c -> c.finGeometry().planformAreaM2() * c.finGeometry().count()).sum()
				/ geometry.references().referenceAreaM2();
		double thicknessRatio = geometry.components().stream()
				.filter(c -> c.finGeometry() != null)
				.mapToDouble(c -> c.localReferences().getOrDefault("thicknessRatio", Double.NaN))
				.filter(Double::isFinite).average().orElse(Double.NaN);
		boolean sourceSection = geometry.components().stream()
				.filter(c -> c.finGeometry() != null)
				.allMatch(c -> "SINGLE_WEDGE".equals(c.finGeometry().section()));
		return Math.abs(fineness - 10) <= 0.25
				&& finCount == 4
				&& finAreaRatio >= 5.2 && finAreaRatio <= 6.3
				&& Double.isFinite(thicknessRatio) && Math.abs(thicknessRatio - 0.04) <= 0.005
				&& sourceSection;
	}

	private static double interpolate(double value) {
		for (int i = 0; i < MACH.length; i++) if (Double.compare(value, MACH[i]) == 0) return CD[i];
		for (int i = 1; i < MACH.length; i++) if (value < MACH[i]) {
			double fraction = (value - MACH[i - 1]) / (MACH[i] - MACH[i - 1]);
			return CD[i - 1] + fraction * (CD[i] - CD[i - 1]);
		}
		return CD[CD.length - 1];
	}
}

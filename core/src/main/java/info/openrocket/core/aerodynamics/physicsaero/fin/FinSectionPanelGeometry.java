package info.openrocket.core.aerodynamics.physicsaero.fin;

import java.util.Arrays;

import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroComponent;

/**
 * Piecewise-linear symmetric fin-section geometry for shock-expansion loading.
 *
 * <p>OpenRocket's generic AIRFOIL section historically maps to a symmetric
 * double-wedge with maximum thickness at half chord.  When an imported
 * hexagonal section supplies its half-span leading and trailing bevel lengths,
 * those physical dimensions instead define a leading ramp, flat center panel,
 * and trailing ramp.  The full fin thickness is split equally about the
 * section centerline.</p>
 */
public final class FinSectionPanelGeometry {
	public static final String DEFAULT_DIAMOND_METHOD_ID =
			"OPENROCKET_SYMMETRIC_DOUBLE_WEDGE_SECTION_V1";
	public static final String EXPLICIT_HEXAGONAL_METHOD_ID =
			"RASAERO_HEXAGONAL_FX1_FX3_SECTION_V1";

	private static final double FRACTION_TOLERANCE = 1.0e-10;

	public PanelLayout layout(AeroComponent component, FinStrip strip) {
		if (component == null || component.finGeometry() == null || strip == null) {
			throw new IllegalArgumentException("missing fin section geometry");
		}
		Double leadingLength = component.localReferences().get(
				"sectionLeadingRampLengthM");
		Double trailingLength = component.localReferences().get(
				"sectionTrailingRampLengthM");
		if (leadingLength == null || trailingLength == null
				|| leadingLength == 0 || trailingLength == 0) {
			double angle = Math.atan(strip.thicknessToChord());
			return new PanelLayout(new double[] {angle, -angle},
					new double[] {.5, .5}, DEFAULT_DIAMOND_METHOD_ID);
		}
		if (!Double.isFinite(leadingLength) || !Double.isFinite(trailingLength)
				|| leadingLength < 0 || trailingLength < 0) {
			throw new IllegalArgumentException(
					"hexagonal fin ramp lengths must be finite and nonnegative");
		}

		double representativeChord = component.finGeometry().planformAreaM2()
				/ component.finGeometry().spanM();
		double rampSum = leadingLength + trailingLength;
		double tolerance = FRACTION_TOLERANCE
				* Math.max(1, representativeChord);
		if (rampSum > representativeChord + tolerance) {
			throw new IllegalArgumentException(
					"hexagonal fin ramp lengths exceed half-span chord");
		}
		double fullThickness = strip.thicknessToChord() * strip.chordM();
		double leadingAngle = Math.atan(0.5 * fullThickness / leadingLength);
		double trailingAngle = Math.atan(0.5 * fullThickness / trailingLength);
		double leadingFraction = leadingLength / representativeChord;
		double trailingFraction = trailingLength / representativeChord;
		double centerFraction = Math.max(0,
				1 - leadingFraction - trailingFraction);
		if (centerFraction <= FRACTION_TOLERANCE) {
			double sum = leadingFraction + trailingFraction;
			return new PanelLayout(
					new double[] {leadingAngle, -trailingAngle},
					new double[] {leadingFraction / sum,
							trailingFraction / sum},
					EXPLICIT_HEXAGONAL_METHOD_ID);
		}
		return new PanelLayout(
				new double[] {leadingAngle, 0, -trailingAngle},
				new double[] {leadingFraction, centerFraction,
						trailingFraction},
				EXPLICIT_HEXAGONAL_METHOD_ID);
	}

	public record PanelLayout(double[] surfaceAnglesRad,
			double[] chordFractions, String methodId) {
		public PanelLayout {
			surfaceAnglesRad = Arrays.copyOf(surfaceAnglesRad,
					surfaceAnglesRad.length);
			chordFractions = Arrays.copyOf(chordFractions,
					chordFractions.length);
			if (surfaceAnglesRad.length == 0
					|| surfaceAnglesRad.length != chordFractions.length
					|| methodId == null || methodId.isBlank()) {
				throw new IllegalArgumentException("invalid fin panel layout");
			}
			double sum = 0;
			for (int i = 0; i < surfaceAnglesRad.length; i++) {
				if (!Double.isFinite(surfaceAnglesRad[i])
						|| !Double.isFinite(chordFractions[i])
						|| chordFractions[i] <= 0) {
					throw new IllegalArgumentException(
							"invalid fin section panel");
				}
				sum += chordFractions[i];
			}
			if (Math.abs(sum - 1) > 1.0e-9) {
				throw new IllegalArgumentException(
						"fin section panel fractions must sum to one");
			}
		}

		@Override
		public double[] surfaceAnglesRad() {
			return Arrays.copyOf(surfaceAnglesRad,
					surfaceAnglesRad.length);
		}

		@Override
		public double[] chordFractions() {
			return Arrays.copyOf(chordFractions,
					chordFractions.length);
		}
	}
}

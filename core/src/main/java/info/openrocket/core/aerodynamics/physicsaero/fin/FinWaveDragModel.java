package info.openrocket.core.aerodynamics.physicsaero.fin;

public final class FinWaveDragModel {
	/** Linearized zero-lift coefficient for a symmetric double-wedge section. */
	public double linearizedDiamondCoefficient(double normalMach, double thicknessToChord) {
		validate(normalMach, thicknessToChord);
		return 4 * thicknessToChord * thicknessToChord / Math.sqrt(normalMach * normalMach - 1);
	}

	/**
	 * Linearized zero-lift coefficient for an asymmetric wedge whose inclined face
	 * extends over the complete chord.  One face is inclined by atan(t/c), while
	 * the other is parallel to the freestream, so this is one half (not one
	 * quarter) of the symmetric double-wedge coefficient.
	 *
	 * <p>The trailing-edge base-pressure term is intentionally not included.</p>
	 */
	public double linearizedSingleWedgeCoefficient(double mach, double thicknessToChord) {
		validate(mach, thicknessToChord);
		return 2 * thicknessToChord * thicknessToChord / Math.sqrt(mach * mach - 1);
	}

	/**
	 * Engineering Ackeret closure for an asymmetric leading-edge bevel followed
	 * by parallel faces.  Pressure acts only on the beveled face, whose projected
	 * frontal area is (t/c) times the fin planform area.  The cosine factor is a
	 * finite-span pressure-relief approximation used only when the swept leading
	 * edge is locally subsonic and a two-dimensional shock-expansion march is not
	 * available.
	 */
	public double linearizedAsymmetricBevelCoefficient(double mach, double thicknessToChord,
			double wedgeAngleRad, double leadingEdgeSweepRad) {
		validate(mach, thicknessToChord);
		if (!Double.isFinite(wedgeAngleRad) || !Double.isFinite(leadingEdgeSweepRad)
				|| wedgeAngleRad <= 0 || wedgeAngleRad >= Math.PI / 2
				|| thicknessToChord / Math.tan(wedgeAngleRad) > 1 + 1e-12) {
			throw new IllegalArgumentException("invalid asymmetric bevel geometry");
		}
		double pressureCoefficient = 2 * wedgeAngleRad / Math.sqrt(mach * mach - 1);
		double finiteSpanRelief = Math.max(0, Math.cos(leadingEdgeSweepRad));
		return pressureCoefficient * thicknessToChord * finiteSpanRelief;
	}

	private static void validate(double mach, double thicknessToChord) {
		if (!Double.isFinite(mach) || !Double.isFinite(thicknessToChord)
				|| mach <= 1 || thicknessToChord < 0) {
			throw new IllegalArgumentException("invalid wave-drag input");
		}
	}
}

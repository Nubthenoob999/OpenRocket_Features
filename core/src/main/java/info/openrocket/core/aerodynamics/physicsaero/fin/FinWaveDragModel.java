package info.openrocket.core.aerodynamics.physicsaero.fin;

public final class FinWaveDragModel {
	public double linearizedDiamondCoefficient(double normalMach, double thicknessToChord) {
		if (normalMach <= 1 || thicknessToChord < 0) throw new IllegalArgumentException("invalid wave-drag input");
		return 4 * thicknessToChord * thicknessToChord / Math.sqrt(normalMach * normalMach - 1);
	}
}

package info.openrocket.core.aerodynamics.physicsaero.table;

public record DeterministicTaskIndex(int flatIndex, int machIndex, int alphaIndex, int betaIndex) {
	public static DeterministicTaskIndex fromFlat(TableAxes axes, int flat) {
		if (flat < 0 || flat >= axes.cellCount()) throw new IndexOutOfBoundsException();
		int nb = axes.betaRad().length, na = axes.alphaRad().length;
		int im = flat / (na * nb), remainder = flat % (na * nb);
		return new DeterministicTaskIndex(flat, im, remainder / nb, remainder % nb);
	}
}

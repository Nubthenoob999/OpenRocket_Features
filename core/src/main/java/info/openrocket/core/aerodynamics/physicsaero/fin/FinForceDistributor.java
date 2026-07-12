package info.openrocket.core.aerodynamics.physicsaero.fin;

/** Conservative distribution of a correlation-derived total load using explicit chord weights. */
public final class FinForceDistributor {
	public double[] distributeByChord(double totalForceN, double[] chordM, double[] stripWidthsM) {
		if (chordM.length == 0 || chordM.length != stripWidthsM.length) throw new IllegalArgumentException("invalid strip arrays");
		double denominator = 0; for (int i = 0; i < chordM.length; i++) { if (chordM[i] <= 0 || stripWidthsM[i] <= 0) throw new IllegalArgumentException("invalid strip geometry"); denominator += chordM[i] * stripWidthsM[i]; }
		double[] result = new double[chordM.length]; double accumulated = 0;
		for (int i = 0; i < result.length - 1; i++) { result[i] = totalForceN * chordM[i] * stripWidthsM[i] / denominator; accumulated += result[i]; }
		result[result.length - 1] = totalForceN - accumulated;
		return result;
	}
}

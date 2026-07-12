package info.openrocket.core.aerodynamics.physicsaero.transition;
public final class IntermittencyModel {
	public double value(double sM, double onsetM, double lengthM, double exponent) {
		if (sM <= onsetM) return 0; if (lengthM <= 0 || exponent <= 0) throw new IllegalArgumentException("invalid transition blend");
		return Math.min(1.0, Math.max(0.0, 1.0 - Math.exp(-Math.pow((sM - onsetM) / lengthM, exponent))));
	}
}

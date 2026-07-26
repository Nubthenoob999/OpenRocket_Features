package info.openrocket.core.aerodynamics.physicsaero.force;

/** Non-dimensional rotary derivatives used by the six-degree-of-freedom runtime. */
public record AerodynamicDerivatives(double clp, double cmq, double cnr) {
	public AerodynamicDerivatives {
		if (!Double.isFinite(clp + cmq + cnr)) {
			throw new IllegalArgumentException("non-finite aerodynamic derivative");
		}
	}

	public static AerodynamicDerivatives zero() {
		return new AerodynamicDerivatives(0, 0, 0);
	}

	public double[] toArray() {
		return new double[] {clp, cmq, cnr};
	}

	public static AerodynamicDerivatives fromArray(double[] values) {
		if (values.length != 3) throw new IllegalArgumentException("three derivatives required");
		return new AerodynamicDerivatives(values[0], values[1], values[2]);
	}
}

package info.openrocket.core.aerodynamics.physicsaero.boundarylayer;

/** Thwaites (1949) engineering closure, with all correlation constants owned here. */
public final class LaminarClosure {
	public static final String METHOD_ID = "thwaites-1949-closure-v1";
	public double shapeFactor(double lambda) {
		if (lambda <= -0.107) throw new BoundaryLayerException(BoundaryLayerException.Reason.NONPHYSICAL_STATE, "Thwaites closure beyond laminar separation");
		return lambda <= 0 ? 2.088 + 0.0731 / (lambda + 0.14) : 2.61 - 3.75 * lambda + 5.24 * lambda * lambda;
	}
	public double shearFunction(double lambda) {
		if (lambda <= -0.107) throw new BoundaryLayerException(BoundaryLayerException.Reason.NONPHYSICAL_STATE, "Thwaites shear closure outside range");
		return lambda <= 0 ? 0.22 + 1.402 * lambda + 0.018 * lambda / (0.107 + lambda)
				: 0.22 + 1.57 * lambda - 1.8 * lambda * lambda;
	}
	public double skinFriction(double lambda, double reynoldsTheta) {
		double value = 2 * shearFunction(lambda) / reynoldsTheta;
		if (value < 0 || !Double.isFinite(value)) throw new BoundaryLayerException(BoundaryLayerException.Reason.NONPHYSICAL_STATE, "negative laminar wall shear");
		return value;
	}
}

package info.openrocket.core.aerodynamics.physicsaero.boundarylayer;
/** Ludwieg-Tillmann smooth-wall closure. */
public final class TurbulentSkinFrictionClosure {
	public static final String METHOD_ID = "ludwieg-tillmann-1949-v1";
	public double skinFriction(double h, double reynoldsTheta) {
		if (h <= 1 || reynoldsTheta <= 0) throw new IllegalArgumentException("invalid turbulent closure state");
		return 0.246 * Math.pow(10, -0.678 * h) * Math.pow(reynoldsTheta, -0.268);
	}
}

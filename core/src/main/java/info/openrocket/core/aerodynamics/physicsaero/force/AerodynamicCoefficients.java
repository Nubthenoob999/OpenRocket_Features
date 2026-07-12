package info.openrocket.core.aerodynamics.physicsaero.force;

/** OpenRocket body-axis coefficients: positive CA is drag, then CN, CY, Cl, Cm and Cn. */
public record AerodynamicCoefficients(double ca, double cn, double cy, double cl, double cm, double cYaw) {
	public AerodynamicCoefficients {
		if (!Double.isFinite(ca + cn + cy + cl + cm + cYaw)) throw new IllegalArgumentException("non-finite coefficient");
	}
	public double[] toArray() { return new double[] { ca, cn, cy, cl, cm, cYaw }; }
	public static AerodynamicCoefficients fromArray(double[] v) {
		if (v.length != 6) throw new IllegalArgumentException("six coefficients required");
		return new AerodynamicCoefficients(v[0], v[1], v[2], v[3], v[4], v[5]);
	}
}

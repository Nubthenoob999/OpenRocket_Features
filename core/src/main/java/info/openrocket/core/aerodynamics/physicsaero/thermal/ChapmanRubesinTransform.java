package info.openrocket.core.aerodynamics.physicsaero.thermal;
/** Uses C=(rho*mu)/(rho_e*mu_e) at the Eckert reference state. */
public final class ChapmanRubesinTransform {
	public double parameter(double referenceDensity, double referenceViscosity, double edgeDensity, double edgeViscosity) {
		if (referenceDensity <= 0 || referenceViscosity <= 0 || edgeDensity <= 0 || edgeViscosity <= 0) throw new IllegalArgumentException("invalid transformed properties");
		return referenceDensity * referenceViscosity / (edgeDensity * edgeViscosity);
	}
	public double effectiveReynolds(double edgeReynolds, double parameter) { return edgeReynolds / parameter; }
}

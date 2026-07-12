package info.openrocket.core.aerodynamics.physicsaero.thermal;
/** Explicit Eckert-reference-property fallback for Van-Driest-II behavior; identified as lower fidelity. */
public final class VanDriestIICorrection {
	public static final String METHOD_ID = "eckert-reference-temperature-fallback-v1";
	public double apply(double incompressibleCf, double edgeDensity, double edgeViscosity, double referenceDensity, double referenceViscosity) {
		double cr = new ChapmanRubesinTransform().parameter(referenceDensity, referenceViscosity, edgeDensity, edgeViscosity);
		return incompressibleCf / Math.sqrt(cr);
	}
}

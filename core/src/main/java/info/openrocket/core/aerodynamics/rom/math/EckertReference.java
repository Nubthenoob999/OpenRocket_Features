package info.openrocket.core.aerodynamics.rom.math;

/**
 * Eckert reference-temperature utilities for compressible boundary layers.
 *
 * <p>Primary reference: White, Viscous Fluid Flow, Ch. 7.
 */
public final class EckertReference {
	private static final double AIR_PRANDTL_NUMBER = 0.71;
	private static final double ECKERT_REFERENCE_BLEND = 0.22;

	private EckertReference() {
	}

	public static double referenceTemperature(double edgeTemperature, double wallTemperature, double adiabaticWallTemperature) {
		return 0.5 * (edgeTemperature + wallTemperature)
				+ ECKERT_REFERENCE_BLEND * (adiabaticWallTemperature - edgeTemperature);
	}

	public static double adiabaticWallTemp(double edgeTemperature, double edgeMach, double gamma, boolean turbulent) {
		double recoveryFactor = turbulent ? Math.cbrt(AIR_PRANDTL_NUMBER) : Math.sqrt(AIR_PRANDTL_NUMBER);
		return edgeTemperature * (1.0 + recoveryFactor * 0.5 * (gamma - 1.0) * edgeMach * edgeMach);
	}

	public static double referenceDensity(double edgeDensity, double edgeTemperature, double referenceTemperature) {
		return edgeDensity * edgeTemperature / Math.max(1.0e-9, referenceTemperature);
	}

	public static double referenceViscosity(double referenceTemperature) {
		return GasDynamics.sutherlandViscosity(referenceTemperature);
	}

	public static double referenceKinematicViscosity(double referenceTemperature, double edgeDensity, double edgeTemperature) {
		double density = referenceDensity(edgeDensity, edgeTemperature, referenceTemperature);
		return referenceViscosity(referenceTemperature) / Math.max(1.0e-12, density);
	}

	public static double referenceReynolds(double edgeDensity, double edgeTemperature, double referenceTemperature,
			double edgeVelocity, double x) {
		double density = referenceDensity(edgeDensity, edgeTemperature, referenceTemperature);
		double viscosity = referenceViscosity(referenceTemperature);
		return density * edgeVelocity * x / Math.max(1.0e-12, viscosity);
	}
}

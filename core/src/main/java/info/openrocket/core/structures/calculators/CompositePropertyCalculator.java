package info.openrocket.core.structures.calculators;

import info.openrocket.core.structures.materials.CompositeMaterialEstimate;

public final class CompositePropertyCalculator {
	public CompositeMaterialEstimate calculate(double fiberMass, double laminateMass, double fiberDensity,
			double resinDensity, double fiberStrength, double resinStrength, double fiberModulus, double resinModulus,
			double knockdownFactor, double zeroDegreePlyFraction, double compressionFactor, double flexuralFactor) {
		if (fiberMass <= 0 || laminateMass <= 0 || fiberMass > laminateMass ||
				fiberDensity <= 0 || resinDensity <= 0) {
			return new CompositeMaterialEstimate(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
		}
		double resinMass = laminateMass - fiberMass;
		double fiberVolume = fiberMass / fiberDensity;
		double resinVolume = resinMass / resinDensity;
		double fiberVolumeFraction = fiberVolume / (fiberVolume + resinVolume);
		double fiberMassFraction = fiberMass / laminateMass;
		double matrixFraction = 1.0 - fiberVolumeFraction;
		double tensileStrength = knockdownFactor * zeroDegreePlyFraction *
				(fiberVolumeFraction * fiberStrength + matrixFraction * resinStrength);
		double tensileModulus = knockdownFactor *
				(fiberVolumeFraction * fiberModulus + matrixFraction * resinModulus);
		double compressionStrength = tensileStrength * compressionFactor;
		double flexuralStrength = tensileStrength * flexuralFactor;
		return new CompositeMaterialEstimate(fiberVolumeFraction, fiberMassFraction, tensileStrength, tensileModulus,
				compressionStrength, flexuralStrength);
	}
}

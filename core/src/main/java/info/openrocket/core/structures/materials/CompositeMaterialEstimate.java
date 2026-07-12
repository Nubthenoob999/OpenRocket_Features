package info.openrocket.core.structures.materials;

public final class CompositeMaterialEstimate {
	private final double fiberVolumeFraction;
	private final double fiberMassFraction;
	private final double estimatedTensileStrength;
	private final double estimatedTensileModulus;
	private final double estimatedCompressionStrength;
	private final double estimatedFlexuralStrength;

	public CompositeMaterialEstimate(double fiberVolumeFraction, double fiberMassFraction,
			double estimatedTensileStrength, double estimatedTensileModulus,
			double estimatedCompressionStrength, double estimatedFlexuralStrength) {
		this.fiberVolumeFraction = fiberVolumeFraction;
		this.fiberMassFraction = fiberMassFraction;
		this.estimatedTensileStrength = estimatedTensileStrength;
		this.estimatedTensileModulus = estimatedTensileModulus;
		this.estimatedCompressionStrength = estimatedCompressionStrength;
		this.estimatedFlexuralStrength = estimatedFlexuralStrength;
	}

	public double getFiberVolumeFraction() {
		return fiberVolumeFraction;
	}

	public double getFiberMassFraction() {
		return fiberMassFraction;
	}

	public double getEstimatedTensileStrength() {
		return estimatedTensileStrength;
	}

	public double getEstimatedTensileModulus() {
		return estimatedTensileModulus;
	}

	public double getEstimatedCompressionStrength() {
		return estimatedCompressionStrength;
	}

	public double getEstimatedFlexuralStrength() {
		return estimatedFlexuralStrength;
	}
}

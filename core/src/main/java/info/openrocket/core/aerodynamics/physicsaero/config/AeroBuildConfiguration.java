package info.openrocket.core.aerodynamics.physicsaero.config;

public record AeroBuildConfiguration(SamplingConfiguration sampling, PhysicsConfiguration physics,
		NumericalTolerances tolerances, OutputConfiguration output, int workerCount,
		String codeVersion, String correlationRegistryVersion) {
	public AeroBuildConfiguration {
		if (sampling == null || physics == null || tolerances == null || output == null || workerCount <= 0)
			throw new IllegalArgumentException("invalid build configuration");
	}
}

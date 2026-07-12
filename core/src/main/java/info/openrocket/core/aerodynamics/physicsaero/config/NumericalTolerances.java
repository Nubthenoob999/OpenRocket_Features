package info.openrocket.core.aerodynamics.physicsaero.config;

public record NumericalTolerances(double angleConsistencyRad, double rootResidual, double geometryM,
		double interpolation, int maximumRootIterations, double cornerAngleThresholdRad,
		double cornerPressureSignificance) {
	public NumericalTolerances {
		if (angleConsistencyRad <= 0 || rootResidual <= 0 || geometryM <= 0 || interpolation <= 0 || maximumRootIterations <= 0
				|| cornerAngleThresholdRad <= 0 || cornerPressureSignificance <= 0)
			throw new IllegalArgumentException("tolerances must be positive");
	}
	public static NumericalTolerances defaults() {
		return new NumericalTolerances(1e-8, 1e-10, 1e-9, 1e-10, 100,
				Math.toRadians(0.05), 1e-4);
	}
}

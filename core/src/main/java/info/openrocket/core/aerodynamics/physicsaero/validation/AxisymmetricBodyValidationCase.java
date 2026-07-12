package info.openrocket.core.aerodynamics.physicsaero.validation;

public record AxisymmetricBodyValidationCase(String source, String geometryId, double mach,
		double expectedAxialCoefficient, double relativeTolerance, String notes) {}

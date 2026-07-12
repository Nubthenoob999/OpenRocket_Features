package info.openrocket.core.aerodynamics.physicsaero.validation;

public record IsolatedFinValidationCase(String id, double mach, double incidenceRad,
		double expectedNormalCoefficient, double tolerance) {
	public IsolatedFinValidationCase { if (id == null || id.isBlank() || mach <= 1 || tolerance < 0) throw new IllegalArgumentException("invalid isolated-fin validation case"); }
}

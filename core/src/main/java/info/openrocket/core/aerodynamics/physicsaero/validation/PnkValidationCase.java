package info.openrocket.core.aerodynamics.physicsaero.validation;

public record PnkValidationCase(String id, double radiusOverSemispan, double expectedWingBodyFactor,
		double expectedBodyWingFactor, double tolerance) {
	public PnkValidationCase { if (id == null || id.isBlank() || radiusOverSemispan < 0 || tolerance < 0) throw new IllegalArgumentException("invalid PNK validation case"); }
}

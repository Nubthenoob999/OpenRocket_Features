package info.openrocket.core.aerodynamics.physicsaero.validation;

public record OrientationInvarianceCase(String id, double rotationRad, double forceTolerance, double momentTolerance) {
	public OrientationInvarianceCase { if (id == null || id.isBlank() || forceTolerance < 0 || momentTolerance < 0) throw new IllegalArgumentException("invalid orientation case"); }
}

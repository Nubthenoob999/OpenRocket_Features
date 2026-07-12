package info.openrocket.core.aerodynamics.physicsaero.validation;

/** Published or independently cross-integrated sharp-cone validation datum. */
public record ConeTableValidationCase(String source, double mach, double coneHalfAngleRad,
		double gamma, double shockAngleRad, double surfaceMach, double surfacePressureRatio,
		double shockAngleToleranceRad, double pressureRelativeTolerance) {}

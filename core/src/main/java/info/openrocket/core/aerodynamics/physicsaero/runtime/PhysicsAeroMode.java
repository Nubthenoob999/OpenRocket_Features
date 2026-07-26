package info.openrocket.core.aerodynamics.physicsaero.runtime;

public enum PhysicsAeroMode {
	OFF,
	STRICT,
	DIAGNOSTIC_HYBRID,
	/**
	 * Uses the physics table for axial/wind-axis drag while retaining the
	 * established calculator for lateral forces, moments, damping, and CP.
	 * Intended for drag/apogee validation before six-axis flight certification.
	 */
	DIAGNOSTIC_AXIAL_HYBRID
}

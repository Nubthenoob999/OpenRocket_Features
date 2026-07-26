package info.openrocket.core.aerodynamics.physicsaero.table;

/**
 * Certification carried by a published table artifact.  Correlation-table
 * validation is deliberately distinct from validation against measured flight
 * data.
 */
public enum CertificationState {
	NOT_READY,
	EXPERIMENTAL_FLIGHT_PENDING,
	FLIGHT_VALIDATED
}

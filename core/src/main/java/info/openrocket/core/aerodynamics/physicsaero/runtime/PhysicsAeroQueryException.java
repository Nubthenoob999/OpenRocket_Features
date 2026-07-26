package info.openrocket.core.aerodynamics.physicsaero.runtime;

import info.openrocket.core.aerodynamics.physicsaero.diagnostics.FailureReason;

/** A stable, inspectable failure from a table lookup or runtime correction. */
public final class PhysicsAeroQueryException extends IllegalStateException {
	private final FailureReason reason;
	private final QueryCoordinates coordinates;

	public PhysicsAeroQueryException(FailureReason reason, QueryCoordinates coordinates, String detail) {
		super(reason.name() + (detail == null || detail.isBlank() ? "" : ": " + detail));
		this.reason = java.util.Objects.requireNonNull(reason, "reason");
		this.coordinates = java.util.Objects.requireNonNull(coordinates, "coordinates");
	}

	public PhysicsAeroQueryException(FailureReason reason, QueryCoordinates coordinates,
			String detail, Throwable cause) {
		super(reason.name() + (detail == null || detail.isBlank() ? "" : ": " + detail), cause);
		this.reason = java.util.Objects.requireNonNull(reason, "reason");
		this.coordinates = java.util.Objects.requireNonNull(coordinates, "coordinates");
	}

	public FailureReason reason() { return reason; }
	public QueryCoordinates coordinates() { return coordinates; }

	public record QueryCoordinates(double mach, double alphaRad, double betaRad,
			double poweredFraction, double reynoldsNumber) { }
}

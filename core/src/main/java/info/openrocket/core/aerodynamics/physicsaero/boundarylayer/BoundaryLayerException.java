package info.openrocket.core.aerodynamics.physicsaero.boundarylayer;

/** A rejected boundary-layer input or a named, non-recoverable march failure. */
public final class BoundaryLayerException extends RuntimeException {
	public enum Reason {
		INVALID_EDGE_HISTORY, THERMODYNAMIC_INCONSISTENCY, NON_MONOTONE_TRACK,
		UNREGISTERED_DISCONTINUITY, MISSING_WALL_STATE, MISSING_ROUGHNESS_STATE,
		NONPHYSICAL_STATE, UNSUPPORTED_COMPRESSIBILITY
	}
	private final Reason reason;
	public BoundaryLayerException(Reason reason, String message) { super(message); this.reason = reason; }
	public Reason reason() { return reason; }
}

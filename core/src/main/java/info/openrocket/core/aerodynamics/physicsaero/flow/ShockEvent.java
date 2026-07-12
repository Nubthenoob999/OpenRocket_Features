package info.openrocket.core.aerodynamics.physicsaero.flow;

import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.ShockSolution;

public record ShockEvent(double xM, double turnAngleRad, double shockAngleRad,
		ShockSolution.Attachment attachment, double totalPressureRatio, SurfaceState upstream,
		SurfaceState downstream, String methodId) implements FlowEvent {
	public ShockEvent {
		if (attachment == null || upstream == null || downstream == null || methodId == null
				|| totalPressureRatio <= 0 || totalPressureRatio > 1.0 + 1e-10) throw new IllegalArgumentException("invalid shock event");
	}
}

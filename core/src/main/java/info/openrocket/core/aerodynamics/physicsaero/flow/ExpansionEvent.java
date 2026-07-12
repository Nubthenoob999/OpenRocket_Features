package info.openrocket.core.aerodynamics.physicsaero.flow;

public record ExpansionEvent(double xM, double turnAngleRad, double downstreamMach,
		double totalPressureRatio, SurfaceState upstream, SurfaceState downstream,
		String methodId) implements FlowEvent {
	public ExpansionEvent {
		if (upstream == null || downstream == null || methodId == null || turnAngleRad < 0
				|| downstreamMach <= 1 || Math.abs(totalPressureRatio - 1) > 1e-8) throw new IllegalArgumentException("invalid expansion event");
	}
}

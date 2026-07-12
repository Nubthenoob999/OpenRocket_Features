package info.openrocket.core.aerodynamics.physicsaero.transition;
public record TransitionDecision(boolean triggered, double margin, String methodId, String reason) {
	public TransitionDecision { if (methodId == null || methodId.isBlank() || reason == null || !Double.isFinite(margin)) throw new IllegalArgumentException("invalid transition decision"); }
}

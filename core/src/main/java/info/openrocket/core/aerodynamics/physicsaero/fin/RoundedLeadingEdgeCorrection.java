package info.openrocket.core.aerodynamics.physicsaero.fin;

public final class RoundedLeadingEdgeCorrection {
	public Result unavailable() { return new Result(false, 0, 0, "ROUNDED_LEADING_EDGE_MODEL_UNOWNED_EXPLICIT_FALLBACK"); }
	public record Result(boolean valid, double normalForceCorrectionN, double dragCorrectionN, String reason) {}
}

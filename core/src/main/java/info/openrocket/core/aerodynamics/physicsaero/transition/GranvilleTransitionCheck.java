package info.openrocket.core.aerodynamics.physicsaero.transition;
/** Granville pressure-gradient check, retained as a diagnostic and never averaged into the primary decision. */
public final class GranvilleTransitionCheck implements TransitionModel {
	public static final String METHOD_ID = "granville-1953-v1";
	@Override public TransitionDecision evaluate(double reS, double reTheta, double h, double lambda, double tu) {
		if (reS <= 0 || lambda < -0.1 || lambda > 0.1) return new TransitionDecision(false, -Double.MAX_VALUE, METHOD_ID, "OUTSIDE_VALIDITY_DOMAIN");
		// Granville's amplification-distance construction represented by its commonly used momentum-thickness envelope.
		double critical = 1.174 * Math.pow(reS, 0.46) * (1.0 + 2.0 * lambda);
		double margin = reTheta / critical - 1.0;
		return new TransitionDecision(margin >= 0, margin, METHOD_ID, "INDEPENDENT_PRESSURE_GRADIENT_CHECK");
	}
	@Override public String methodId() { return METHOD_ID; }
}

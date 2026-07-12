package info.openrocket.core.aerodynamics.physicsaero.transition;
/** Michel (1951) zero-turbulence natural-transition check. */
public final class MichelTransitionCheck implements TransitionModel {
	public static final String METHOD_ID = "michel-1951-v1";
	@Override public TransitionDecision evaluate(double reS, double reTheta, double h, double lambda, double tu) {
		if (reS <= 0) return new TransitionDecision(false, -Double.MAX_VALUE, METHOD_ID, "OUTSIDE_VALIDITY_DOMAIN");
		double critical = 1.174 * (1.0 + 22400.0 / reS) * Math.pow(reS, 0.46);
		double margin = reTheta / critical - 1.0;
		return new TransitionDecision(margin >= 0, margin, METHOD_ID, "INDEPENDENT_CHECK");
	}
	@Override public String methodId() { return METHOD_ID; }
}

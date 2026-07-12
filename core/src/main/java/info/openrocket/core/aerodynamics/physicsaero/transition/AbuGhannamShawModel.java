package info.openrocket.core.aerodynamics.physicsaero.transition;

/** Abu-Ghannam and Shaw (1980), IMechE 194(26), natural-transition onset correlation. Tu is percent. */
public final class AbuGhannamShawModel implements TransitionModel {
	public static final String METHOD_ID = "abu-ghannam-shaw-1980-v1";
	@Override public TransitionDecision evaluate(double reS, double reTheta, double h, double lambda, double tuPercent) {
		if (!(tuPercent >= 0.1 && tuPercent <= 6.0) || lambda < -0.1 || lambda > 0.1)
			return new TransitionDecision(false, -Double.MAX_VALUE, METHOD_ID, "OUTSIDE_VALIDITY_DOMAIN");
		double f = lambda <= 0
				? 6.91 + 12.75 * lambda + 63.64 * lambda * lambda
				: 6.91 + 2.48 * lambda - 12.27 * lambda * lambda;
		double critical = 163.0 + Math.exp(f / (1.0 - tuPercent / 6.91));
		double margin = reTheta / critical - 1.0;
		return new TransitionDecision(margin >= 0, margin, METHOD_ID, margin >= 0 ? "NATURAL_ONSET" : "BELOW_ONSET");
	}
	@Override public String methodId() { return METHOD_ID; }
}

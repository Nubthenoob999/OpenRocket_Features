package info.openrocket.core.aerodynamics.physicsaero.transition;
/** Configured bypass criterion; no universal roughness threshold is hidden in this class. */
public final class RoughnessTransitionModel {
	private final double criticalReK; private final String sourceId;
	public RoughnessTransitionModel(double criticalReK, String sourceId) {
		if (criticalReK <= 0 || sourceId == null || sourceId.isBlank()) throw new IllegalArgumentException("roughness criterion needs threshold and provenance");
		this.criticalReK=criticalReK; this.sourceId=sourceId;
	}
	public TransitionDecision evaluate(double density, double velocity, double roughnessM, double viscosity) {
		double reK=density*velocity*roughnessM/viscosity, margin=reK/criticalReK-1;
		return new TransitionDecision(margin>=0,margin,"roughness-bypass-"+sourceId,margin>=0?"ROUGHNESS_BYPASS":"BELOW_ROUGHNESS_THRESHOLD");
	}
}

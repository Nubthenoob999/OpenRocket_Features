package info.openrocket.core.aerodynamics.physicsaero.boundarylayer;
import info.openrocket.core.aerodynamics.physicsaero.transition.TransitionState;
public final class AttachedFlowHealthEvaluator {
	public AttachedFlowHealth evaluate(double lambda, double h, TransitionState state) {
		if (state == TransitionState.LAMINAR) {
			if (lambda <= -0.09) return AttachedFlowHealth.LAMINAR_SEPARATION_TRIGGER;
			if (lambda <= -0.07) return AttachedFlowHealth.INCIPIENT_LAMINAR_SEPARATION;
			if (lambda < -0.02) return AttachedFlowHealth.ADVERSE_GRADIENT_WARNING;
		} else if (h >= 2.4) return AttachedFlowHealth.INCIPIENT_TURBULENT_SEPARATION;
		return AttachedFlowHealth.ATTACHED;
	}
}

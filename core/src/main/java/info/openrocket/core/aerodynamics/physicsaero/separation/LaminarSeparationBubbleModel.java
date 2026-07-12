package info.openrocket.core.aerodynamics.physicsaero.separation;
/** Conservative bubble state only; no quantitative bubble length or reattachment heat flux is claimed. */
public final class LaminarSeparationBubbleModel {
	public enum BubbleState { LAMINAR_SEPARATED_SHEAR_LAYER, TRANSITION_RISK_IN_SHEAR_LAYER, POSSIBLE_TURBULENT_REATTACHMENT }
	public record Result(BubbleState state,double severity,boolean forceTransition,String reattachmentStatus,double confidence){ }
	public Result evaluate(SeparationSignals signals,double transitionMargin){
		double severity=Math.min(1,Math.max(0,(-signals.thwaitesLambda()-.07)/.05+signals.shockRisk()*.3));
		boolean transition=transitionMargin+severity>=1;
		return new Result(transition?BubbleState.TRANSITION_RISK_IN_SHEAR_LAYER:BubbleState.LAMINAR_SEPARATED_SHEAR_LAYER,severity,transition,"UNRESOLVED",.55);
	}
}

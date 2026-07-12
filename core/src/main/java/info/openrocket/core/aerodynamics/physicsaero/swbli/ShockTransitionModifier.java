package info.openrocket.core.aerodynamics.physicsaero.swbli;
import info.openrocket.core.aerodynamics.physicsaero.transition.TransitionState;
public final class ShockTransitionModifier {
	public record Result(double preShockMargin,double increment,double postShockMargin,TransitionState state,boolean forced,String reason){ }
	public Result evaluate(TransitionState incoming,double preShockMargin,double risk,ShockInteractionClassification classification){
		if(incoming==TransitionState.TURBULENT)return new Result(preShockMargin,0,preShockMargin,incoming,false,"ALREADY_TURBULENT");
		double increment=.65*risk;double post=preShockMargin+increment;
		boolean force=classification==ShockInteractionClassification.SEPARATED&&risk>=.85||post>=1;
		TransitionState state=force?TransitionState.TURBULENT:post>0?TransitionState.TRANSITIONAL:incoming;
		return new Result(preShockMargin,increment,post,state,force,force?"SHOCK_OR_SEPARATED_SHEAR_TRANSITION":"WEAK_SHOCK_RISK_ONLY");
	}
}

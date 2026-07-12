package info.openrocket.core.aerodynamics.physicsaero.separation;
import java.util.ArrayList;
import java.util.List;
public final class LaminarSeparationModel {
	public SeparationClassification classify(SeparationSignals s,int persistence){
		List<String> r=new ArrayList<>(); int warnings=0;
		if(s.velocityGradientPerS()<0&&s.pressureGradientPaM()>0){warnings++;r.add("SUSTAINED_ADVERSE_GRADIENT");}
		if(s.thwaitesLambda()<=-.07){warnings++;r.add("THWAITES_LAMBDA_WARNING");}
		if(s.shapeFactor()>=3.0){warnings++;r.add("HIGH_LAMINAR_SHAPE_FACTOR");}
		if(s.skinFrictionFallRate()>0){warnings++;r.add("FALLING_SKIN_FRICTION");}
		if(s.displacementGrowthRate()>0.02){warnings++;r.add("RAPID_DISPLACEMENT_GROWTH");}
		SeparationState state=SeparationState.ATTACHED; double severity=0;
		if(s.reversedFlow()||s.closureBreakdown()){state=SeparationState.SEPARATED;severity=1;r.add("LAMINAR_CLOSURE_FAILURE");}
		else if(s.skinFrictionCoefficient()<=0||s.thwaitesLambda()<=-.09){state=SeparationState.PROBABLE_SEPARATION;severity=Math.min(1,.7+Math.max(0,-s.thwaitesLambda()-.09)*5);}
		else if(warnings>=2){state=SeparationState.INCIPIENT_SEPARATION;severity=Math.min(.69,.25+.12*warnings);}
		else if(warnings==1){state=SeparationState.ADVERSE_GRADIENT_WARNING;severity=.2;}
		return new SeparationClassification(state,severity,persistence,s,r,state==SeparationState.ATTACHED?.95:.8);
	}
}

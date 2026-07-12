package info.openrocket.core.aerodynamics.physicsaero.separation;
import java.util.ArrayList;
import java.util.List;
public final class TurbulentSeparationModel {
	public SeparationClassification classify(SeparationSignals s,int persistence){
		List<String> r=new ArrayList<>();int warnings=0;
		if(s.velocityGradientPerS()<0&&s.pressureGradientPaM()>0){warnings++;r.add("ADVERSE_GRADIENT");}
		if(s.shapeFactor()>=2.2){warnings++;r.add("HIGH_TURBULENT_SHAPE_FACTOR");}
		if(s.clauserBeta()>=8){warnings++;r.add("HIGH_CLAUSER_BETA");}
		if(s.skinFrictionCoefficient()<5e-4){warnings++;r.add("LOW_TURBULENT_SKIN_FRICTION");}
		SeparationState state=SeparationState.ATTACHED;double severity=0;
		if(s.reversedFlow()||s.closureBreakdown()){state=SeparationState.SEPARATED;severity=1;r.add("ENTRAINMENT_CLOSURE_FAILURE");}
		else if(s.skinFrictionCoefficient()<=0||warnings>=3){state=SeparationState.PROBABLE_SEPARATION;severity=.75;}
		else if(warnings>=2){state=SeparationState.INCIPIENT_SEPARATION;severity=.5;}
		else if(warnings==1){state=SeparationState.ADVERSE_GRADIENT_WARNING;severity=.2;}
		return new SeparationClassification(state,severity,persistence,s,r,state==SeparationState.ATTACHED?.95:.82);
	}
}

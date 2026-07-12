package info.openrocket.core.aerodynamics.physicsaero.coupling;
import info.openrocket.core.aerodynamics.physicsaero.transition.TransitionState;
public record WakeAffectedRegion(String originComponent,double startM,double endM,double angularExtentRad,double velocityDeficitFraction,
		double totalPressureDeficitFraction,TransitionState transitionState,String validity,double confidence){
	public WakeAffectedRegion{if(originComponent==null||originComponent.isBlank()||endM<startM||angularExtentRad<0||velocityDeficitFraction<0||velocityDeficitFraction>1
				||totalPressureDeficitFraction<0||totalPressureDeficitFraction>1||transitionState==null||validity==null||confidence<0||confidence>1)throw new IllegalArgumentException("invalid wake region");}
}

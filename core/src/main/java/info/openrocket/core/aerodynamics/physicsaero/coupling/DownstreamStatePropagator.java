package info.openrocket.core.aerodynamics.physicsaero.coupling;
import info.openrocket.core.aerodynamics.physicsaero.swbli.ShockInteractionInput;
import info.openrocket.core.aerodynamics.physicsaero.swbli.ShockInteractionResult;
public final class DownstreamStatePropagator {
	public WakeAffectedRegion propagate(ShockInteractionInput input,ShockInteractionResult result,double componentEndM){
		if(!result.wakeAffected())return null;
		double deficit=Math.min(.65,.15+.5*result.thermalRisk());double pt=Math.min(.8,input.totalPressureLoss()+.35*result.thermalRisk());
		return new WakeAffectedRegion(input.componentId(),input.locationM(),componentEndM,2*Math.PI,deficit,pt,result.downstreamState().transition(),
				result.classification().name()+"_BOUNDED_WAKE_NO_ATTACHED_PATHLINE",result.confidence()*.75);
	}
}

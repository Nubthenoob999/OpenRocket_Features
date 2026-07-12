package info.openrocket.core.aerodynamics.physicsaero.coupling;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import info.openrocket.core.aerodynamics.physicsaero.boundarylayer.*;
import info.openrocket.core.aerodynamics.physicsaero.flow.ShockEvent;
import info.openrocket.core.aerodynamics.physicsaero.force.*;
import info.openrocket.core.aerodynamics.physicsaero.swbli.*;
import info.openrocket.core.aerodynamics.physicsaero.thermal.RecoveryTemperatureModel;
import info.openrocket.core.util.Coordinate;

/** Applies ordered typed shock events once, then rebuilds skin friction from the corrected history. */
public final class OneWayViscousCoupling {
	public record Result(BoundaryLayerHistory correctedHistory,List<ShockInteractionResult> interactions,List<WakeAffectedRegion> wakes,
			List<ForceContribution> contributions,boolean attachedMarchStopped,List<String> diagnostics){
		public Result{interactions=List.copyOf(interactions);wakes=List.copyOf(wakes);contributions=List.copyOf(contributions);diagnostics=List.copyOf(diagnostics);}
	}
	public Result apply(BoundaryLayerHistory raw,List<ShockEvent> shocks,double localScaleM,double componentEndM,ReferenceState reference,Coordinate momentReference,
			double gamma,double prandtl){
		List<BoundaryLayerState> states=new ArrayList<>(raw.states());List<ShockInteractionResult> interactions=new ArrayList<>();List<WakeAffectedRegion>wakes=new ArrayList<>();
		List<ForceContribution> residuals=new ArrayList<>();List<String> diagnostics=new ArrayList<>();boolean stopped=false;
		for(ShockEvent shock:shocks.stream().sorted(Comparator.comparingDouble(ShockEvent::xM)).toList()){
			int index=upstreamIndex(raw,shock.xM());BoundaryLayerStation station=raw.track().stations().get(index);BoundaryLayerState upstream=states.get(index);
			double tr=new RecoveryTemperatureModel().recoveryTemperatureK(station.temperatureK(),station.mach(),gamma,prandtl,upstream.transition()!=info.openrocket.core.aerodynamics.physicsaero.transition.TransitionState.LAMINAR);
			BoundaryLayerHistory current=new BoundaryLayerHistory(raw.track(),states);ShockInteractionInput input=ShockInteractionInput.from(shock,current,index,tr,localScaleM);
			ShockInteractionResult result=new ShockBoundaryLayerCoupler().couple(input,reference);interactions.add(result);states.set(Math.min(index+1,states.size()-1),result.downstreamState());
			if(result.drag().residualDeltaCd()>0)residuals.add(result.residualForce());
			WakeAffectedRegion wake=new DownstreamStatePropagator().propagate(input,result,componentEndM);if(wake!=null)wakes.add(wake);
			if(result.stopAttachedMarch()){stopped=true;suppressDownstream(states,index+1,result.downstreamState());diagnostics.add("ATTACHED_MARCH_STOPPED@"+shock.xM());break;}
		}
		BoundaryLayerHistory corrected=new BoundaryLayerHistory(raw.track(),states);List<ForceContribution> contributions=new ArrayList<>();
		contributions.add(new SkinFrictionForceIntegrator().integrate(corrected,momentReference));contributions.addAll(residuals);
		return new Result(corrected,interactions,wakes,contributions,stopped,diagnostics);
	}
	private int upstreamIndex(BoundaryLayerHistory history,double x){int best=0;for(int i=0;i<history.track().stations().size();i++){if(history.track().stations().get(i).positionM().x<=x)best=i;else break;}return Math.min(best,history.states().size()-2);}
	private void suppressDownstream(List<BoundaryLayerState> states,int from,BoundaryLayerState separated){for(int i=Math.max(0,from);i<states.size();i++)states.set(i,new BoundaryLayerState(
			separated.thetaM(),separated.displacementThicknessM(),separated.delta99M(),separated.shapeFactor(),separated.entrainmentShapeFactor(),0,0,
			states.get(i).reynoldsS(),separated.reynoldsTheta(),separated.thwaitesLambda(),1,separated.transition(),AttachedFlowHealth.INCIPIENT_TURBULENT_SEPARATION,
			separated.roughnessRegime(),separated.wallTemperatureK(),"separated-wake-suppressed-wall-shear-v1"));}
}

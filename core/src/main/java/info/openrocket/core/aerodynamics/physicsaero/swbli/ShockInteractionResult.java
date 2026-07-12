package info.openrocket.core.aerodynamics.physicsaero.swbli;

import java.util.List;
import info.openrocket.core.aerodynamics.physicsaero.boundarylayer.BoundaryLayerState;
import info.openrocket.core.aerodynamics.physicsaero.force.ForceContribution;
import info.openrocket.core.aerodynamics.physicsaero.separation.SeparatedFlowCorrection;

public record ShockInteractionResult(String interactionId,ShockInteractionClassification classification,double rawRisk,double thermalRisk,
		BoundaryLayerState upstreamState,BoundaryLayerState downstreamState,ShockTransitionModifier.Result transitionModification,
		PressureRecoveryLimiter.Result pressureRecovery,SwbliDragModel.Result drag,SeparatedFlowCorrection.Result separatedCorrection,
		boolean stopAttachedMarch,boolean wakeAffected,double confidence,List<String> diagnostics){
	public ShockInteractionResult{diagnostics=List.copyOf(diagnostics);if(interactionId==null||classification==null||upstreamState==null||downstreamState==null||transitionModification==null||pressureRecovery==null||drag==null||confidence<0||confidence>1)throw new IllegalArgumentException("invalid interaction result");}
	public ForceContribution residualForce(){return drag.residualForce();}
}

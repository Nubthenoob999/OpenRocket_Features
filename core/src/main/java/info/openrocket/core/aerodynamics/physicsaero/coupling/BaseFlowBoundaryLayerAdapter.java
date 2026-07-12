package info.openrocket.core.aerodynamics.physicsaero.coupling;

import info.openrocket.core.aerodynamics.physicsaero.boundarylayer.BoundaryLayerState;
import info.openrocket.core.aerodynamics.physicsaero.separation.SeparationState;

/** Explicit terminal boundary-layer state passed to base pressure without transferring ownership. */
public final class BaseFlowBoundaryLayerAdapter {
	public record BaseFlowState(SeparationState boattailState,double thetaM,double displacementThicknessM,double delta99M,double shapeFactor,
			double skinFrictionCoefficient,double reynoldsTheta,double thermalRatio,double wakeAreaM2,double basePressureForceFactor,double confidence){
		public BaseFlowState{if(boattailState==null||thetaM<=0||displacementThicknessM<=0||delta99M<=0||shapeFactor<=1||skinFrictionCoefficient<0
				||reynoldsTheta<=0||thermalRatio<=0||wakeAreaM2<0||basePressureForceFactor<=0||basePressureForceFactor>1.35||confidence<0||confidence>1)throw new IllegalArgumentException("invalid base flow state");}
	}
	public BaseFlowState adapt(SeparationState state,BoundaryLayerState terminal,double thermalRatio,double exposedBaseAreaM2,double confidence){
		double severity=switch(state){case ATTACHED,ADVERSE_GRADIENT_WARNING->0;case INCIPIENT_SEPARATION,PROBABLE_SEPARATION->.5;case SEPARATED->1;default->.75;};
		double wake=Math.min(exposedBaseAreaM2,Math.PI*terminal.delta99M()*terminal.delta99M()*(1+severity));
		return new BaseFlowState(state,terminal.thetaM(),terminal.displacementThicknessM(),terminal.delta99M(),terminal.shapeFactor(),terminal.skinFrictionCoefficient(),
				terminal.reynoldsTheta(),thermalRatio,wake,1+.2*severity,confidence);
	}
}

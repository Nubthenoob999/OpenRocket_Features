package info.openrocket.core.aerodynamics.physicsaero.separation;

import info.openrocket.core.aerodynamics.physicsaero.boundarylayer.BoundaryLayerState;
import info.openrocket.core.aerodynamics.physicsaero.roughness.RoughnessRegime;
import info.openrocket.core.aerodynamics.physicsaero.transition.TransitionState;

/** Bounded state consequence after leaving the attached equations. */
public final class SeparatedFlowCorrection {
	public record Calibration(double displacementFactor,double shapeIncrement,double maximumShapeFactor,String sourceId,String version){
		public Calibration{if(displacementFactor<0||shapeIncrement<0||maximumShapeFactor<=1||sourceId==null||sourceId.isBlank()||version==null||version.isBlank())throw new IllegalArgumentException("correction calibration requires provenance");}
	}
	public record Result(BoundaryLayerState raw,BoundaryLayerState corrected,double severity,String methodId){ }
	private final Calibration calibration;
	public SeparatedFlowCorrection(Calibration calibration){this.calibration=calibration;}
	public Result apply(BoundaryLayerState raw,double severity){
		if(severity<0||severity>1)throw new IllegalArgumentException("invalid separation severity");
		double deltaStar=raw.displacementThicknessM()*(1+calibration.displacementFactor()*severity);
		double h=Math.min(calibration.maximumShapeFactor(),raw.shapeFactor()+calibration.shapeIncrement()*severity);
		double theta=deltaStar/h;double delta99=raw.delta99M()*(1+calibration.displacementFactor()*severity);
		BoundaryLayerState corrected=new BoundaryLayerState(theta,deltaStar,delta99,h,raw.entrainmentShapeFactor(),0,0,
				raw.reynoldsS(),raw.reynoldsTheta()*theta/raw.thetaM(),raw.thwaitesLambda(),1,
				raw.transition()==TransitionState.LAMINAR?TransitionState.TRANSITIONAL:raw.transition(),
				info.openrocket.core.aerodynamics.physicsaero.boundarylayer.AttachedFlowHealth.INCIPIENT_TURBULENT_SEPARATION,
				raw.roughnessRegime()==null?RoughnessRegime.HYDRAULICALLY_SMOOTH:raw.roughnessRegime(),raw.wallTemperatureK(),
				"separated-flow-bounded-"+calibration.sourceId()+"-"+calibration.version());
		return new Result(raw,corrected,severity,corrected.methodId());
	}
}

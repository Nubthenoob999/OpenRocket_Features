package info.openrocket.core.aerodynamics.physicsaero.swbli;

import java.util.ArrayList;
import java.util.List;
import info.openrocket.core.aerodynamics.physicsaero.boundarylayer.*;
import info.openrocket.core.aerodynamics.physicsaero.force.ReferenceState;
import info.openrocket.core.aerodynamics.physicsaero.separation.SeparatedFlowCorrection;
import info.openrocket.core.aerodynamics.physicsaero.transition.TransitionState;

/** One-way shock/BL coupling. It never recalculates or iterates the inviscid shock. */
public final class ShockBoundaryLayerCoupler {
	public record ResponseCalibration(String sourceId,String version,double thetaGain,double displacementGain,double shapeGain,double frictionReduction){
		public ResponseCalibration{if(sourceId==null||sourceId.isBlank()||version==null||thetaGain<0||displacementGain<0||shapeGain<0||frictionReduction<0||frictionReduction>1)throw new IllegalArgumentException("invalid response calibration");}
	}
	private final ResponseCalibration response;
	private final WallTemperatureRiskModifier thermal;
	public ShockBoundaryLayerCoupler(){this(new ResponseCalibration("Delery-1985-bounded-response","v1",.35,.55,.45,.65),
			new WallTemperatureRiskModifier(new WallTemperatureRiskModifier.Calibration("Mach-2.7-wall-temperature-DNS-trend","v1",.5,.35,.20,.8,1.35,.35,1.8)));}
	public ShockBoundaryLayerCoupler(ResponseCalibration response,WallTemperatureRiskModifier thermal){this.response=response;this.thermal=thermal;}
	public ShockInteractionResult couple(ShockInteractionInput input,ReferenceState reference){
		StrongInteractionGuard.Result guard=new StrongInteractionGuard().evaluate(input);List<String> diagnostics=new ArrayList<>(guard.reasons());
		WallTemperatureRiskModifier.Result thermalResult=thermal.apply(1,input.thermalRatio());double factor=thermalResult.factor();
		InteractionRiskMap.Evaluation risk=switch(input.upstreamBoundaryLayer().transition()){
			case LAMINAR->new LaminarSwbliModel().evaluate(input,factor);
			case TRANSITIONAL->new TransitionalSwbliModel().evaluate(input,factor);
			case TURBULENT->new TurbulentSwbliModel().evaluate(input,factor);
		};
		ShockInteractionClassification classification=!guard.valid()||!risk.valid()?ShockInteractionClassification.INVALID_LOW_ORDER_MODEL:risk.classification();
		if(input.upstreamBoundaryLayer().skinFrictionCoefficient()<=0&&classification!=ShockInteractionClassification.INVALID_LOW_ORDER_MODEL)
			classification=ShockInteractionClassification.SEPARATED;
		if(risk.unmodifiedRisk()>=.9&&classification.ordinal()<ShockInteractionClassification.SEPARATED.ordinal())classification=ShockInteractionClassification.SEPARATED;
		if(!thermalResult.inRange())diagnostics.add("WALL_TEMPERATURE_OUTSIDE_CALIBRATION_RANGE");
		if(!risk.valid())diagnostics.add(risk.reason());
		ShockTransitionModifier.Result transition=new ShockTransitionModifier().evaluate(input.upstreamBoundaryLayer().transition(),-.25,risk.thermalModifiedRisk(),classification);
		BoundaryLayerState downstream=input.upstreamBoundaryLayer();SeparatedFlowCorrection.Result separated=null;boolean stop=false,wake=false;
		if(classification==ShockInteractionClassification.ATTACHED_THICKENED||classification==ShockInteractionClassification.INCIPIENT_SEPARATION){
			downstream=thicken(input.upstreamBoundaryLayer(),risk.thermalModifiedRisk(),transition.state());
		}else if(classification==ShockInteractionClassification.SEPARATED){
			SeparatedFlowCorrection correction=new SeparatedFlowCorrection(new SeparatedFlowCorrection.Calibration(.8,.8,4.5,"Delery-1985-conservative-separated-envelope","v1"));
			separated=correction.apply(input.upstreamBoundaryLayer(),risk.thermalModifiedRisk());downstream=separated.corrected();stop=true;wake=true;
		}else if(classification==ShockInteractionClassification.INVALID_LOW_ORDER_MODEL){stop=true;wake=true;diagnostics.add("NO_PRECISE_LOW_ORDER_COEFFICIENT");}
		PressureRecoveryLimiter.Result pressure=new PressureRecoveryLimiter().apply(input.downstreamPressurePa(),null,risk.thermalModifiedRisk());
		double dragSeverity=(classification==ShockInteractionClassification.INCIPIENT_SEPARATION||classification==ShockInteractionClassification.SEPARATED)
				?risk.thermalModifiedRisk():0;
		SwbliDragModel.Result drag=new SwbliDragModel().reconcile(input,dragSeverity,0,reference);
		double confidence=Math.min(guard.confidence(),Math.min(thermalResult.confidence(),classification==ShockInteractionClassification.INVALID_LOW_ORDER_MODEL?.15:.75));
		return new ShockInteractionResult(input.interactionId(),classification,risk.unmodifiedRisk(),risk.thermalModifiedRisk(),input.upstreamBoundaryLayer(),downstream,
				transition,pressure,drag,separated,stop,wake,confidence,diagnostics);
	}
	private BoundaryLayerState thicken(BoundaryLayerState raw,double severity,TransitionState transition){
		double theta=raw.thetaM()*(1+response.thetaGain()*severity),delta=raw.displacementThicknessM()*(1+response.displacementGain()*severity);
		double h=delta/theta+response.shapeGain()*severity,cf=raw.skinFrictionCoefficient()*(1-response.frictionReduction()*severity);
		if(cf<0)throw new BoundaryLayerException(BoundaryLayerException.Reason.NONPHYSICAL_STATE,"SWBLI response produced negative wall shear");
		double tau=raw.wallShearPa()*(cf/raw.skinFrictionCoefficient());
		return new BoundaryLayerState(theta,h*theta,raw.delta99M()*(1+response.displacementGain()*severity),h,raw.entrainmentShapeFactor(),cf,tau,
				raw.reynoldsS(),raw.reynoldsTheta()*theta/raw.thetaM(),raw.thwaitesLambda(),transition==TransitionState.LAMINAR?0:1,transition,
				AttachedFlowHealth.ADVERSE_GRADIENT_WARNING,raw.roughnessRegime(),raw.wallTemperatureK(),"swbli-thickening-"+response.sourceId()+"-"+response.version());
	}
}

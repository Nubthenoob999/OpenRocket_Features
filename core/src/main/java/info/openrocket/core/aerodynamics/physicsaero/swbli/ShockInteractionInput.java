package info.openrocket.core.aerodynamics.physicsaero.swbli;

import info.openrocket.core.aerodynamics.physicsaero.boundarylayer.*;
import info.openrocket.core.aerodynamics.physicsaero.flow.ShockEvent;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.ShockSolution;
import info.openrocket.core.util.Coordinate;

/** Complete typed shock and incoming boundary-layer snapshot required by every SWBLI model. */
public record ShockInteractionInput(String interactionId,String shockId,String surfaceTrackId,String componentId,
		double locationM,double upstreamMach,double upstreamNormalMach,double upstreamPressurePa,double downstreamPressurePa,
		double upstreamTemperatureK,double downstreamTemperatureK,double upstreamDensity,double downstreamDensity,
		double upstreamTotalPressurePa,double downstreamTotalPressurePa,double shockAngleRad,double turnAngleRad,
		String shockType,String root,ShockSolution.Attachment attachment,String shockOrigin,Coordinate shockDirection,
		double intersectionConfidence,Coordinate upstreamVelocity,Coordinate downstreamVelocity,double solverResidual,
		BoundaryLayerState upstreamBoundaryLayer,double wallTemperatureK,double recoveryTemperatureK,double localScaleM,
		boolean shockShockInteraction,boolean strongCrossflow,boolean geometryDiscontinuity){
	public ShockInteractionInput{
		if(interactionId==null||interactionId.isBlank()||shockId==null||shockId.isBlank()||surfaceTrackId==null||surfaceTrackId.isBlank()
				||componentId==null||componentId.isBlank()||shockType==null||root==null||attachment==null||shockOrigin==null
				||shockDirection==null||upstreamVelocity==null||downstreamVelocity==null||upstreamBoundaryLayer==null
				||!finite(locationM,upstreamMach,upstreamNormalMach,upstreamPressurePa,downstreamPressurePa,upstreamTemperatureK,
				downstreamTemperatureK,upstreamDensity,downstreamDensity,upstreamTotalPressurePa,downstreamTotalPressurePa,
				shockAngleRad,turnAngleRad,intersectionConfidence,solverResidual,wallTemperatureK,recoveryTemperatureK,localScaleM)
				||upstreamPressurePa<=0||downstreamPressurePa<=0||upstreamTotalPressurePa<=0||downstreamTotalPressurePa<=0
				||upstreamDensity<=0||downstreamDensity<=0||localScaleM<=0||intersectionConfidence<0||intersectionConfidence>1)
			throw new IllegalArgumentException("invalid shock interaction input");
	}
	public static ShockInteractionInput from(ShockEvent shock,BoundaryLayerHistory history,int upstreamIndex,double recoveryTemperatureK,double localScaleM){
		BoundaryLayerState bl=history.states().get(upstreamIndex);var u=shock.upstream();var d=shock.downstream();
		Coordinate uv=direction(u.flowAngleRad(),u.staticState().velocityMS()),dv=direction(d.flowAngleRad(),d.staticState().velocityMS());
		return new ShockInteractionInput("swbli@"+shock.xM(),shock.methodId()+"@"+shock.xM(),history.track().regionId(),history.track().componentId(),shock.xM(),
				u.staticState().mach(),u.staticState().mach()*Math.sin(shock.shockAngleRad()),u.staticState().pressurePa(),d.staticState().pressurePa(),
				u.staticState().temperatureK(),d.staticState().temperatureK(),u.staticState().densityKgM3(),d.staticState().densityKgM3(),
				u.totalState().pressurePa(),d.totalState().pressurePa(),shock.shockAngleRad(),shock.turnAngleRad(),"SURFACE_SHOCK","WEAK_OR_SOURCE_ROOT",
				shock.attachment(),shock.methodId(),direction(shock.shockAngleRad(),1),1,uv,dv,0,bl,bl.wallTemperatureK(),recoveryTemperatureK,localScaleM,false,
				history.track().reducedConfidence(),false);
	}
	public double pressureRiseCoefficient(){double q=.5*upstreamDensity*upstreamVelocity.length()*upstreamVelocity.length();return (downstreamPressurePa-upstreamPressurePa)/q;}
	public double pressureRatio(){return downstreamPressurePa/upstreamPressurePa;}
	public double totalPressureLoss(){return 1-downstreamTotalPressurePa/upstreamTotalPressurePa;}
	public double thermalRatio(){return wallTemperatureK/recoveryTemperatureK;}
	public double displacementRatio(){return upstreamBoundaryLayer.displacementThicknessM()/localScaleM;}
	public double delta99Ratio(){return upstreamBoundaryLayer.delta99M()/localScaleM;}
	public ShockInteractionInput withWallTemperatureRatio(double ratio){return copy(downstreamPressurePa,ratio*recoveryTemperatureK);}
	public ShockInteractionInput withPressureRiseScale(double scale){
		if(scale<0)throw new IllegalArgumentException("negative pressure-rise scale");
		return copy(upstreamPressurePa+scale*(downstreamPressurePa-upstreamPressurePa),wallTemperatureK);
	}
	private ShockInteractionInput copy(double p2,double wallT){return new ShockInteractionInput(interactionId,shockId,surfaceTrackId,componentId,locationM,upstreamMach,
			upstreamNormalMach,upstreamPressurePa,p2,upstreamTemperatureK,downstreamTemperatureK,upstreamDensity,downstreamDensity,upstreamTotalPressurePa,
			downstreamTotalPressurePa,shockAngleRad,turnAngleRad,shockType,root,attachment,shockOrigin,shockDirection,intersectionConfidence,upstreamVelocity,
			downstreamVelocity,solverResidual,upstreamBoundaryLayer,wallT,recoveryTemperatureK,localScaleM,shockShockInteraction,strongCrossflow,geometryDiscontinuity);}
	private static Coordinate direction(double angle,double magnitude){return new Coordinate(magnitude*Math.cos(angle),magnitude*Math.sin(angle),0);}
	private static boolean finite(double... values){for(double v:values)if(!Double.isFinite(v))return false;return true;}
}

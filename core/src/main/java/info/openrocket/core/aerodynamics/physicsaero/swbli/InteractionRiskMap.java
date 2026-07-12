package info.openrocket.core.aerodynamics.physicsaero.swbli;

/** Source-versioned bounded feature map. Calibration is injected and cannot be implicit. */
public final class InteractionRiskMap {
	public record Calibration(String sourceId,String version,double pressureScale,double normalMachScale,double shapeScale,
			double frictionDeficitScale,double inverseReThetaScale,double pressureWeight,double machWeight,double shapeWeight,
			double frictionWeight,double reynoldsWeight,double thickeningThreshold,double incipientThreshold,double separatedThreshold,
			double minimumReTheta,double maximumReTheta,double maximumPressureRise){
		public Calibration{if(sourceId==null||sourceId.isBlank()||version==null||version.isBlank()||pressureScale<=0||normalMachScale<=0||shapeScale<=0
				||frictionDeficitScale<=0||inverseReThetaScale<=0||thickeningThreshold<0||incipientThreshold<=thickeningThreshold
				||separatedThreshold<=incipientThreshold||separatedThreshold>1||minimumReTheta<=0||maximumReTheta<=minimumReTheta||maximumPressureRise<=0)
			throw new IllegalArgumentException("invalid risk-map calibration");}
	}
	public record Evaluation(double unmodifiedRisk,double thermalModifiedRisk,ShockInteractionClassification classification,boolean valid,String reason){ }
	private final Calibration calibration;
	public InteractionRiskMap(Calibration calibration){this.calibration=calibration;}
	public Evaluation evaluate(ShockInteractionInput in,double referenceCf,double thermalFactor){
		double re=in.upstreamBoundaryLayer().reynoldsTheta(),pi=in.pressureRiseCoefficient();
		if(re<calibration.minimumReTheta()||re>calibration.maximumReTheta()||pi>calibration.maximumPressureRise())return new Evaluation(0,0,ShockInteractionClassification.INVALID_LOW_ORDER_MODEL,false,"OUTSIDE_CALIBRATION_DOMAIN");
		double raw=calibration.pressureWeight()*unit(pi/calibration.pressureScale())+
				calibration.machWeight()*unit((in.upstreamNormalMach()-1)/calibration.normalMachScale())+
				calibration.shapeWeight()*unit((in.upstreamBoundaryLayer().shapeFactor()-1.4)/calibration.shapeScale())+
				calibration.frictionWeight()*unit((1-in.upstreamBoundaryLayer().skinFrictionCoefficient()/referenceCf)/calibration.frictionDeficitScale())+
				calibration.reynoldsWeight()*unit(calibration.inverseReThetaScale()/re);
		raw=unit(raw);double modified=unit(raw*thermalFactor);return new Evaluation(raw,modified,classify(modified),true,"VALID_"+calibration.sourceId()+"_"+calibration.version());
	}
	private ShockInteractionClassification classify(double risk){return risk<calibration.thickeningThreshold()?ShockInteractionClassification.ATTACHED_WEAK:
			risk<calibration.incipientThreshold()?ShockInteractionClassification.ATTACHED_THICKENED:
			risk<calibration.separatedThreshold()?ShockInteractionClassification.INCIPIENT_SEPARATION:ShockInteractionClassification.SEPARATED;}
	private static double unit(double x){return Math.max(0,Math.min(1,x));}
}

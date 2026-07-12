package info.openrocket.core.aerodynamics.physicsaero.swbli;
/** Bounded heating/cooling trend model with source-provenance required in calibration. */
public final class WallTemperatureRiskModifier {
	public record Calibration(String sourceId,String version,double ratioSpan,double heatingGain,double coolingGain,double minimumFactor,double maximumFactor,double minimumRatio,double maximumRatio){
		public Calibration{if(sourceId==null||sourceId.isBlank()||version==null||ratioSpan<=0||heatingGain<0||coolingGain<0||minimumFactor<=0||maximumFactor<minimumFactor||minimumRatio<=0||maximumRatio<=minimumRatio)throw new IllegalArgumentException("invalid thermal-risk calibration");}
	}
	public record Result(double factor,double modifiedRisk,boolean inRange,double confidence,String methodId){ }
	private final Calibration calibration;
	public WallTemperatureRiskModifier(Calibration calibration){this.calibration=calibration;}
	public Result apply(double adiabaticRisk,double wallToRecoveryRatio){
		double chi=clip((wallToRecoveryRatio-1)/calibration.ratioSpan(),-1,1);
		double factor=clip(1+calibration.heatingGain()*Math.max(chi,0)-calibration.coolingGain()*Math.max(-chi,0),calibration.minimumFactor(),calibration.maximumFactor());
		boolean range=wallToRecoveryRatio>=calibration.minimumRatio()&&wallToRecoveryRatio<=calibration.maximumRatio();
		return new Result(factor,clip(adiabaticRisk*factor,0,1),range,range?.9:.45,"wall-temperature-risk-"+calibration.sourceId()+"-"+calibration.version());
	}
	private static double clip(double x,double a,double b){return Math.max(a,Math.min(b,x));}
}

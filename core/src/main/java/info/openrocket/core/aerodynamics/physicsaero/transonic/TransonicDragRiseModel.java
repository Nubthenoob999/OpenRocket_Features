package info.openrocket.core.aerodynamics.physicsaero.transonic;
/** Dedicated asymmetric transonic excess; branch blending never creates this peak. */
public final class TransonicDragRiseModel {
	public record Parameters(double criticalMach,double dragDivergenceMach,double peakMach,double peakDeltaCd,double recoveryMach,double growthExponent,double decayRate,String sourceId){public Parameters{if(criticalMach<=0||dragDivergenceMach<=criticalMach||peakMach<=dragDivergenceMach||peakDeltaCd<0||recoveryMach<=peakMach||growthExponent<=1||decayRate<=0||sourceId==null)throw new IllegalArgumentException();}}
	public double deltaCd(double mach,double sonicFraction,Parameters p){
		if(mach<=p.criticalMach())return 0;if(mach<p.dragDivergenceMach()){double x=(mach-p.criticalMach())/(p.dragDivergenceMach()-p.criticalMach());return .15*p.peakDeltaCd()*Math.pow(Math.max(0,sonicFraction),1.2)*x*x;}
		if(mach<=p.peakMach()){double x=(mach-p.dragDivergenceMach())/(p.peakMach()-p.dragDivergenceMach());double smooth=x*x*(3-2*x);return p.peakDeltaCd()*smooth;}
		return p.peakDeltaCd()*Math.exp(-p.decayRate()*(mach-p.peakMach()));
	}
}

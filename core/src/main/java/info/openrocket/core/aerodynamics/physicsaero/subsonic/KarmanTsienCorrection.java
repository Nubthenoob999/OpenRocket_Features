package info.openrocket.core.aerodynamics.physicsaero.subsonic;
/** Karman-Tsien pressure correction, rejected before its sonic/singular boundary. */
public final class KarmanTsienCorrection {
	public record Result(double pressureCoefficient,boolean valid,String reason){ }
	public Result apply(double incompressibleCp,double mach){
		if(mach<0||mach>=1)return new Result(Double.NaN,false,"GLOBALLY_NONSUBSONIC");double beta=Math.sqrt(1-mach*mach);
		double denominator=beta+mach*mach*incompressibleCp/(2*(1+beta));
		if(denominator<=.05)return new Result(Double.NaN,false,"SONIC_MARGIN_CLOSED");
		double cp=incompressibleCp/denominator;if(!Double.isFinite(cp)||cp<-3||cp>2)return new Result(Double.NaN,false,"CP_OUTSIDE_VALIDITY");
		return new Result(cp,true,"KARMAN_TSIEN_VALID");
	}
}

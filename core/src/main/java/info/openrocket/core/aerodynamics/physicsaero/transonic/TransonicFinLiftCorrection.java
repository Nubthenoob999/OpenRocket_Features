package info.openrocket.core.aerodynamics.physicsaero.transonic;
public final class TransonicFinLiftCorrection {
	public double factor(double mach,double thicknessRatio,double aspectRatio){double hump=thicknessRatio<.08?.12*Math.exp(-Math.pow((mach-.92)/.08,2)):0;double soften=(.12+.4*thicknessRatio)/(1+Math.exp(-(mach-1.02)/.04));return Math.max(.65,Math.min(1.2,1+hump-soften));}
}

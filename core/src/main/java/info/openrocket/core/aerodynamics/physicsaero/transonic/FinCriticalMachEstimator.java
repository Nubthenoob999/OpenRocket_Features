package info.openrocket.core.aerodynamics.physicsaero.transonic;
public final class FinCriticalMachEstimator implements CriticalMachEstimator {
	@Override public Estimate estimate(double thicknessRatio,double sweep,double incidence){double normal=.92-.9*thicknessRatio-.15*Math.abs(incidence);return new Estimate(Math.max(.5,Math.min(.98,normal/Math.max(.4,Math.cos(sweep)))),.7,"FIN_NORMAL_MACH_ONSET_V1");}
}

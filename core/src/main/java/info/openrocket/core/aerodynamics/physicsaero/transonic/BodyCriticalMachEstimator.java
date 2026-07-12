package info.openrocket.core.aerodynamics.physicsaero.transonic;
public final class BodyCriticalMachEstimator implements CriticalMachEstimator {
	@Override public Estimate estimate(double finenessInverse,double curvatureMetric,double incidence){double m=.94-.55*finenessInverse-.08*Math.min(1,curvatureMetric)-.18*Math.abs(incidence);return new Estimate(Math.max(.55,Math.min(.96,m)),.65,"BODY_GEOMETRY_CRITICAL_MACH_V1");}
}

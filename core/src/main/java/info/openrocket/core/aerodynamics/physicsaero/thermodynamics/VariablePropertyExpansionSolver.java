package info.openrocket.core.aerodynamics.physicsaero.thermodynamics;
public final class VariablePropertyExpansionSolver {
	public record Solution(double pressurePa,double temperatureK,double velocityMS,double entropyResidual,double energyResidual,boolean converged,String methodId){ }
	public Solution expand(double p1,double t1,double u1,double p2,ThermallyPerfectAir air){if(p2<=0||p2>=p1||t1<200)throw new IllegalArgumentException();double s=air.entropy(t1,p1),energy=air.enthalpy(t1)+u1*u1/2,lo=200,hi=t1;for(int i=0;i<80;i++){double mid=.5*(lo+hi);if(air.entropy(mid,p2)<s)lo=mid;else hi=mid;}double t=.5*(lo+hi),u=Math.sqrt(Math.max(0,2*(energy-air.enthalpy(t))));double sr=Math.abs(air.entropy(t,p2)-s)/Math.max(1,Math.abs(s)),er=Math.abs(air.enthalpy(t)+u*u/2-energy)/Math.max(1,Math.abs(energy));return new Solution(p2,t,u,sr,er,Math.max(sr,er)<1e-7,"VARIABLE_PROPERTY_ISENTROPIC_EXPANSION_V1");}
}

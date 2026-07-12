package info.openrocket.core.aerodynamics.physicsaero.thermodynamics;
public final class VariablePropertyShockSolver {
	public record Solution(double downstreamPressurePa,double downstreamTemperatureK,double downstreamDensity,double downstreamVelocityMS,double massResidual,double momentumResidual,double energyResidual,boolean converged,String methodId){ }
	public Solution normalShock(double p1,double t1,double rho1,double u1,ThermallyPerfectAir air){
		if(p1<=0||t1<200||rho1<=0||u1<=air.speedOfSound(t1))throw new IllegalArgumentException("variable shock requires supersonic valid state");
		double mass=rho1*u1,momentum=p1+rho1*u1*u1,energy=air.enthalpy(t1)+u1*u1/2;double t=Math.min(5900,t1*(1+.18*u1*u1/(air.speedOfSound(t1)*air.speedOfSound(t1)))),rho=rho1*4;
		for(int iteration=0;iteration<60;iteration++){
			double[]r=residual(t,rho,mass,momentum,energy,air);if(Math.hypot(r[0]/momentum,r[1]/energy)<1e-10)break;double dt=Math.max(.01,t*1e-5),dr=Math.max(1e-8,rho*1e-5);double[]rt=residual(t+dt,rho,mass,momentum,energy,air),rr=residual(t,rho+dr,mass,momentum,energy,air);double a=(rt[0]-r[0])/dt,b=(rr[0]-r[0])/dr,c=(rt[1]-r[1])/dt,d=(rr[1]-r[1])/dr,det=a*d-b*c;if(Math.abs(det)<1e-20)throw new IllegalArgumentException("variable shock singular Jacobian");double dT=(-r[0]*d+b*r[1])/det,dR=(c*r[0]-a*r[1])/det;double scale=1;while(t+scale*dT<200||t+scale*dT>6000||rho+scale*dR<=rho1)scale*=.5;t+=scale*dT;rho+=scale*dR;
		}
		double u=mass/rho,p=rho*air.gasConstant()*t;double mr=Math.abs(rho*u-mass)/mass,mom=Math.abs(p+rho*u*u-momentum)/momentum,en=Math.abs(air.enthalpy(t)+u*u/2-energy)/Math.abs(energy);return new Solution(p,t,rho,u,mr,mom,en,Math.max(mom,en)<1e-7,"VARIABLE_PROPERTY_NORMAL_SHOCK_V1");
	}
	private double[] residual(double t,double rho,double mass,double momentum,double energy,ThermallyPerfectAir air){double u=mass/rho,p=rho*air.gasConstant()*t;return new double[]{p+rho*u*u-momentum,air.enthalpy(t)+u*u/2-energy};}
}

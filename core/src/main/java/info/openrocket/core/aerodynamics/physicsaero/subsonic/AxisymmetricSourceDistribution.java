package info.openrocket.core.aerodynamics.physicsaero.subsonic;

import java.util.List;
import info.openrocket.core.aerodynamics.physicsaero.geometry.GeometryStation;

/** Mid-panel line-source quadrature. The singular self panel is evaluated by its symmetric principal value, not clipped. */
public final class AxisymmetricSourceDistribution {
	public record VelocityPerturbation(double axialMS,double radialMS){ }
	public VelocityPerturbation velocityAt(List<GeometryStation> stations,int target,double freestreamMS){
		if(stations.size()<2||target<0||target>=stations.size())throw new IllegalArgumentException("invalid source distribution");
		double x=stations.get(target).xM(),r=stations.get(target).radiusM(),u=0,v=0;
		for(int i=0;i<stations.size()-1;i++){
			double x0=stations.get(i).xM(),x1=stations.get(i+1).xM(),dx=x1-x0;if(dx<=0)throw new IllegalArgumentException("nonmonotone profile");
			double xm=.5*(x0+x1),rm=.5*(stations.get(i).radiusM()+stations.get(i+1).radiusM());
			double dSdx=Math.PI*(stations.get(i+1).radiusM()*stations.get(i+1).radiusM()-stations.get(i).radiusM()*stations.get(i).radiusM())/dx;
			double source=freestreamMS*dSdx*dx,rx=x-xm,rr=r-rm,den2=rx*rx+rr*rr;
			if(den2<1e-24&&target==i||den2<1e-24&&target==i+1)continue;
			double den=Math.pow(den2,1.5);u+=-source*rx/(4*Math.PI*den);v+=-source*rr/(4*Math.PI*den);
		}
		return new VelocityPerturbation(u,v);
	}
}

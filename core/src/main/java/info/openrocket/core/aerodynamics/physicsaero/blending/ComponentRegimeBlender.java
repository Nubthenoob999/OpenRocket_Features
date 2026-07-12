package info.openrocket.core.aerodynamics.physicsaero.blending;
import info.openrocket.core.aerodynamics.physicsaero.force.AerodynamicCoefficients;
public final class ComponentRegimeBlender {
	public AerodynamicCoefficients blend(AerodynamicCoefficients a,double wa,AerodynamicCoefficients b,double wb){if(wa<0||wb<0||wa+wb<=0)throw new IllegalArgumentException("no valid branch");double[]x=a.toArray(),y=b.toArray(),z=new double[6];for(int i=0;i<6;i++)z[i]=(wa*x[i]+wb*y[i])/(wa+wb);return new AerodynamicCoefficients(z[0],z[1],z[2],z[3],z[4],z[5]);}
}

package info.openrocket.core.aerodynamics.physicsaero.transonic;
public final class TransonicMomentModel {
	public double aerodynamicCenterFraction(double mach,double subsonicFraction,double supersonicFraction){double x=Math.max(0,Math.min(1,(mach-.8)/.4));double w=x*x*(3-2*x);return (1-w)*subsonicFraction+w*supersonicFraction;}
}

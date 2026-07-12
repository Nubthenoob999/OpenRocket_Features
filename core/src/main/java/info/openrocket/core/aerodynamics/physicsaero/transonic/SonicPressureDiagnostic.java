package info.openrocket.core.aerodynamics.physicsaero.transonic;
public final class SonicPressureDiagnostic {
	public double sonicPressureCoefficient(double mach,double gamma){if(mach<=0||mach>=1||gamma<=1)throw new IllegalArgumentException();return 2/(gamma*mach*mach)*(Math.pow((1+(gamma-1)*.5*mach*mach)/(1+(gamma-1)*.5),gamma/(gamma-1))-1);}
	public double margin(double cp,double mach,double gamma){return cp-sonicPressureCoefficient(mach,gamma);}
}

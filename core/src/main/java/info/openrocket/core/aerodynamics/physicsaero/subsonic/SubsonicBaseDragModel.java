package info.openrocket.core.aerodynamics.physicsaero.subsonic;
public final class SubsonicBaseDragModel {
	public double basePressureCoefficient(double mach,double reynoldsDiameter){if(mach<0||mach>=1||reynoldsDiameter<=0)throw new IllegalArgumentException();return -Math.min(.3,.12+.10*mach*mach);}
}

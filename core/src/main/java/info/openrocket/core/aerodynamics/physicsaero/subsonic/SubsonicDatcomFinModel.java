package info.openrocket.core.aerodynamics.physicsaero.subsonic;
public final class SubsonicDatcomFinModel {
	public double liftSlopePerRad(double mach,double aspectRatio,double halfChordSweepRad,double kappa){
		if(mach<0||mach>=.95||aspectRatio<=0||kappa<=0)throw new IllegalArgumentException("outside subsonic DATCOM range");
		double beta=Math.sqrt(1-mach*mach),sweep=Math.tan(halfChordSweepRad);return 2*Math.PI*aspectRatio/(2+Math.sqrt(4+Math.pow(aspectRatio*beta/kappa,2)*(1+sweep*sweep/(beta*beta))));
	}
}

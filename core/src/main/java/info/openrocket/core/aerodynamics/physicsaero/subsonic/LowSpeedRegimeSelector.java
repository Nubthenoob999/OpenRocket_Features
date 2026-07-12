package info.openrocket.core.aerodynamics.physicsaero.subsonic;
public final class LowSpeedRegimeSelector {
	public enum Regime { BARROWMAN, BARROWMAN_CORRELATION_OVERLAP, CORRELATION, HIGH_SUBSONIC, TRANSONIC }
	public Regime select(double mach){if(mach<0||mach>=1.2)throw new IllegalArgumentException("outside low/transonic selector");return mach<.25?Regime.BARROWMAN:mach<.35?Regime.BARROWMAN_CORRELATION_OVERLAP:mach<.7?Regime.CORRELATION:mach<.9?Regime.HIGH_SUBSONIC:Regime.TRANSONIC;}
	public double correlationWeight(double mach){double x=Math.max(0,Math.min(1,(mach-.25)/.1));return x*x*(3-2*x);}
}

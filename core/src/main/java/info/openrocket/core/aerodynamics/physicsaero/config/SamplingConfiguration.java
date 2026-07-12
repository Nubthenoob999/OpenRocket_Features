package info.openrocket.core.aerodynamics.physicsaero.config;

public record SamplingConfiguration(double[] mach, double[] alphaRad, double[] betaRad) {
	public SamplingConfiguration {
		mach = mach.clone(); alphaRad = alphaRad.clone(); betaRad = betaRad.clone();
		validate(mach, "Mach", true); validate(alphaRad, "alpha", false); validate(betaRad, "beta", false);
	}
	@Override public double[] mach() { return mach.clone(); }
	@Override public double[] alphaRad() { return alphaRad.clone(); }
	@Override public double[] betaRad() { return betaRad.clone(); }
	private static void validate(double[] values, String name, boolean nonnegative) {
		if (values.length == 0) throw new IllegalArgumentException(name + " axis is empty");
		for (int i = 0; i < values.length; i++) {
			if (!Double.isFinite(values[i]) || (nonnegative && values[i] < 0) || (i > 0 && values[i] <= values[i - 1]))
				throw new IllegalArgumentException(name + " axis must be finite and strictly increasing");
		}
	}
}

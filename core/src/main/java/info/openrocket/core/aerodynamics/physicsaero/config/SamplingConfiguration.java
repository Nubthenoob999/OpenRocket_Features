package info.openrocket.core.aerodynamics.physicsaero.config;

public record SamplingConfiguration(double[] mach, double[] alphaRad, double[] betaRad) {
	private static final double[] DEFAULT_MACH = {0, 0.1, 0.2, 0.3, 0.5, 0.7, 0.8,
			0.85, 0.9, 0.95, 1.0, 1.05, 1.1, 1.2, 1.3, 1.5, 2, 3, 4, 5, 6, 7};
	private static final double[] DEFAULT_ALPHA_DEG = {-15, -10, -5, 0, 5, 10, 15};
	private static final double[] DEFAULT_BETA_DEG = {-5, 0, 5};
	public SamplingConfiguration {
		mach = mach.clone(); alphaRad = alphaRad.clone(); betaRad = betaRad.clone();
		validate(mach, "Mach", true); validate(alphaRad, "alpha", false); validate(betaRad, "beta", false);
	}
	@Override public double[] mach() { return mach.clone(); }
	@Override public double[] alphaRad() { return alphaRad.clone(); }
	@Override public double[] betaRad() { return betaRad.clone(); }
	/** Complete coast-table flight envelope with explicit regime handoff nodes. */
	public static SamplingConfiguration flightDomainDefaults() {
		return new SamplingConfiguration(DEFAULT_MACH,
				java.util.Arrays.stream(DEFAULT_ALPHA_DEG).map(Math::toRadians).toArray(),
				java.util.Arrays.stream(DEFAULT_BETA_DEG).map(Math::toRadians).toArray());
	}
	private static void validate(double[] values, String name, boolean nonnegative) {
		if (values.length == 0) throw new IllegalArgumentException(name + " axis is empty");
		for (int i = 0; i < values.length; i++) {
			if (!Double.isFinite(values[i]) || (nonnegative && values[i] < 0) || (i > 0 && values[i] <= values[i - 1]))
				throw new IllegalArgumentException(name + " axis must be finite and strictly increasing");
		}
	}
}

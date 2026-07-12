package info.openrocket.core.aerodynamics.physicsaero.interaction;

public record PnkValidityDomain(double minimumMach, double maximumMach, double minimumRadiusOverSemispan,
		double maximumRadiusOverSemispan) {
	public PnkValidityDomain {
		if (minimumMach < 0 || maximumMach < minimumMach || minimumRadiusOverSemispan < 0
				|| maximumRadiusOverSemispan <= minimumRadiusOverSemispan) throw new IllegalArgumentException("invalid PNK domain");
	}
	public boolean contains(double mach, double radiusOverSemispan) {
		return mach >= minimumMach && mach <= maximumMach && radiusOverSemispan >= minimumRadiusOverSemispan
				&& radiusOverSemispan <= maximumRadiusOverSemispan;
	}
}

package info.openrocket.core.util.ejection;

/**
 * Empirical de-rating preset applied to the Lamé-derived coupler friction
 * force before it is combined with the shear-pin force. Real coupler joints
 * include lubrication, surface polish, and clearance from repeated assembly
 * cycles, so the textbook prediction over-estimates breakaway friction.
 *
 * <p>The three presets bracket the range typically observed on bench-pull
 * data. {@link #LOW} is appropriate for well-lubricated / loose fits,
 * {@link #MEDIUM} is the default, and {@link #HIGH} is appropriate for
 * dry / new / tight fits where the textbook number is closest to reality.
 */
public enum FrictionDerating {
	LOW("Low", 0.4),
	MEDIUM("Medium", 0.6),
	HIGH("High", 0.8);

	private final String displayName;
	private final double factor;

	FrictionDerating(String displayName, double factor) {
		this.displayName = displayName;
		this.factor = factor;
	}

	public String getDisplayName() {
		return displayName;
	}

	public double getFactor() {
		return factor;
	}

	@Override
	public String toString() {
		return displayName + " (" + factor + ")";
	}
}

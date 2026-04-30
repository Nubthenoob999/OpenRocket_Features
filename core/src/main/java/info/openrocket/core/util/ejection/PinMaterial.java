package info.openrocket.core.util.ejection;

/**
 * Shear-pin material categories. Drives strength-table grouping and the
 * tested vs. theoretical strength availability.
 */
public enum PinMaterial {
	NYLON_6_6_IMPERIAL("Nylon 6/6 (imperial)"),
	NYLON_6_6_METRIC("Nylon 6/6 (metric)"),
	STYRENE_ROD("Styrene rod"),
	ABS_ROD("ABS rod");

	private final String displayName;

	PinMaterial(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}

	@Override
	public String toString() {
		return displayName;
	}
}

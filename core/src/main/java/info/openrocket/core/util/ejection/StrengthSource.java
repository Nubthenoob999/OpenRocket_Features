package info.openrocket.core.util.ejection;

/**
 * Selects which shear-strength column of {@link ShearPinSpec} to use for a
 * calculation. {@code TESTED_MIN} is the conservative default for design.
 */
public enum StrengthSource {
	THEORETICAL_MIN,
	THEORETICAL_MAX,
	TESTED_MIN,
	TESTED_MAX
}

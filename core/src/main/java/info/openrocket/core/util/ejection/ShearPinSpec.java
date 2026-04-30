package info.openrocket.core.util.ejection;

/**
 * Single shear-pin entry: geometry and per-pin single-shear strength values
 * (lbs). Strength values may be 0 when no data is available; callers should
 * fall back to the theoretical column in that case.
 */
public final class ShearPinSpec {

	private final String designation;
	private final PinMaterial material;
	private final double majorDiameter_in;
	private final double pitchDiameter_in;
	private final double minorDiameter_in;
	private final double shearStrengthTheoreticalMin_lbs;
	private final double shearStrengthTheoreticalMax_lbs;
	private final double shearStrengthTestedMin_lbs;
	private final double shearStrengthTestedMax_lbs;
	private final String typicalRocketDiameter;
	private final String usageNotes;

	public ShearPinSpec(String designation,
						PinMaterial material,
						double majorDiameter_in,
						double pitchDiameter_in,
						double minorDiameter_in,
						double theoMin_lbs,
						double theoMax_lbs,
						double testedMin_lbs,
						double testedMax_lbs,
						String typicalRocketDiameter,
						String usageNotes) {
		this.designation = designation;
		this.material = material;
		this.majorDiameter_in = majorDiameter_in;
		this.pitchDiameter_in = pitchDiameter_in;
		this.minorDiameter_in = minorDiameter_in;
		this.shearStrengthTheoreticalMin_lbs = theoMin_lbs;
		this.shearStrengthTheoreticalMax_lbs = theoMax_lbs;
		this.shearStrengthTestedMin_lbs = testedMin_lbs;
		this.shearStrengthTestedMax_lbs = testedMax_lbs;
		this.typicalRocketDiameter = typicalRocketDiameter;
		this.usageNotes = usageNotes;
	}

	public String getDesignation() {
		return designation;
	}

	public PinMaterial getMaterial() {
		return material;
	}

	public double getMajorDiameter_in() {
		return majorDiameter_in;
	}

	public double getPitchDiameter_in() {
		return pitchDiameter_in;
	}

	public double getMinorDiameter_in() {
		return minorDiameter_in;
	}

	public double getShearStrengthTheoreticalMin_lbs() {
		return shearStrengthTheoreticalMin_lbs;
	}

	public double getShearStrengthTheoreticalMax_lbs() {
		return shearStrengthTheoreticalMax_lbs;
	}

	public double getShearStrengthTestedMin_lbs() {
		return shearStrengthTestedMin_lbs;
	}

	public double getShearStrengthTestedMax_lbs() {
		return shearStrengthTestedMax_lbs;
	}

	public String getTypicalRocketDiameter() {
		return typicalRocketDiameter;
	}

	public String getUsageNotes() {
		return usageNotes;
	}

	/**
	 * Whether this pin has any tested-strength data. False for metric nylon and
	 * all rod types; callers should fall back to theoretical values and warn.
	 */
	public boolean hasTestedData() {
		return shearStrengthTestedMin_lbs > 0.0;
	}

	/**
	 * Returns the requested strength value (lbs/pin). If {@code TESTED_MIN} or
	 * {@code TESTED_MAX} is requested but no tested data exists, falls back to
	 * the corresponding theoretical column.
	 */
	public double getShearStrength(StrengthSource source) {
		switch (source) {
			case THEORETICAL_MIN:
				return shearStrengthTheoreticalMin_lbs;
			case THEORETICAL_MAX:
				return shearStrengthTheoreticalMax_lbs;
			case TESTED_MIN:
				return hasTestedData()
						? shearStrengthTestedMin_lbs
						: shearStrengthTheoreticalMin_lbs;
			case TESTED_MAX:
				return shearStrengthTestedMax_lbs > 0.0
						? shearStrengthTestedMax_lbs
						: shearStrengthTheoreticalMax_lbs;
			default:
				throw new IllegalArgumentException("Unknown strength source: " + source);
		}
	}

	@Override
	public String toString() {
		return designation;
	}
}

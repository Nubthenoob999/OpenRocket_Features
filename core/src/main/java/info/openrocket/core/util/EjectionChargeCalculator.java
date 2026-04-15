package info.openrocket.core.util;

/**
 * Utility class for calculating black powder (BP) ejection charges for
 * dual-deployment recovery systems.
 *
 * <p>The standard formula for black powder ejection charge mass is:
 * <pre>
 *   BP_grams = (pressurePa * volumeM3) / 30.0413
 * </pre>
 * which is equivalent to:
 * <pre>
 *   BP_grams = (pressurePSI * volumeCubicInches) / 265.9
 * </pre>
 *
 * <p>Typical deployment pressures range from 10-15 PSI (68,948 - 103,421 Pa).
 *
 * @author Alex Zoghlin
 */
public class EjectionChargeCalculator {

	/** Conversion constant: 1 PSI in Pascals */
	public static final double PSI_TO_PA = 6894.75729;

	/** Minimum recommended pressure in PSI */
	public static final double MIN_PRESSURE_PSI = 5.0;

	/** Default recommended pressure in PSI */
	public static final double DEFAULT_PRESSURE_PSI = 15.0;

	/** Maximum recommended pressure in PSI */
	public static final double MAX_PRESSURE_PSI = 20.0;

	/** Typical low pressure for recommended range in PSI */
	public static final double LOW_PRESSURE_PSI = 10.0;

	/** Typical high pressure for recommended range in PSI */
	public static final double HIGH_PRESSURE_PSI = 15.0;

	/** Cubic inches per cubic meter */
	public static final double CUBIC_INCHES_PER_CUBIC_METER = 61023.7;

	/**
	 * The BP constant used in the imperial formula.
	 * BP_grams = (pressurePSI * volumeCubicInches) / BP_CONSTANT_IMPERIAL
	 */
	public static final double BP_CONSTANT_IMPERIAL = 265.9;

	/**
	 * The BP constant used in the metric formula.
	 * BP_grams = (pressurePa * volumeM3) / BP_CONSTANT_METRIC
	 * Derived from imperial equivalence using the same constants to avoid drift.
	 */
	public static final double BP_CONSTANT_METRIC =
			BP_CONSTANT_IMPERIAL * PSI_TO_PA / CUBIC_INCHES_PER_CUBIC_METER;

	/** Grams per ounce */
	public static final double GRAMS_PER_OUNCE = 28.3495;

	/** Grams per teaspoon of FFFFg black powder (approximate) */
	public static final double GRAMS_PER_TEASPOON_BP = 2.5;

	private EjectionChargeCalculator() {
		// Utility class - do not instantiate
	}

	/**
	 * Calculate the pressurized volume of a cylindrical airframe section.
	 *
	 * @param innerDiameter inner diameter in meters
	 * @param length        compartment length in meters
	 * @return volume in cubic meters
	 */
	public static double calculateVolume(double innerDiameter, double length) {
		double radius = innerDiameter / 2.0;
		return Math.PI * radius * radius * length;
	}

	/**
	 * Calculate the black powder charge mass for a given pressure and volume.
	 *
	 * @param pressurePa pressure in Pascals
	 * @param volumeM3   volume in cubic meters
	 * @return mass of black powder in grams
	 */
	public static double calculateBPMassGrams(double pressurePa, double volumeM3) {
		return (pressurePa * volumeM3) / BP_CONSTANT_METRIC;
	}

	/**
	 * Calculate the black powder charge mass using imperial units.
	 *
	 * @param pressurePSI     pressure in PSI
	 * @param volumeCubicIn   volume in cubic inches
	 * @return mass of black powder in grams
	 */
	public static double calculateBPMassGramsImperial(double pressurePSI, double volumeCubicIn) {
		return (pressurePSI * volumeCubicIn) / BP_CONSTANT_IMPERIAL;
	}

	/**
	 * Convert grams to ounces.
	 *
	 * @param grams mass in grams
	 * @return mass in ounces
	 */
	public static double gramsToOunces(double grams) {
		return grams / GRAMS_PER_OUNCE;
	}

	/**
	 * Convert a mass in grams to approximate teaspoons of FFFFg black powder.
	 *
	 * @param grams mass in grams
	 * @return approximate teaspoons
	 */
	public static double gramsToTeaspoons(double grams) {
		return grams / GRAMS_PER_TEASPOON_BP;
	}

	/**
	 * Convert PSI to Pascals.
	 *
	 * @param psi pressure in PSI
	 * @return pressure in Pascals
	 */
	public static double psiToPascals(double psi) {
		return psi * PSI_TO_PA;
	}

	/**
	 * Convert Pascals to PSI.
	 *
	 * @param pa pressure in Pascals
	 * @return pressure in PSI
	 */
	public static double pascalsToPsi(double pa) {
		return pa / PSI_TO_PA;
	}

	/**
	 * Convert cubic meters to cubic inches.
	 *
	 * @param m3 volume in cubic meters
	 * @return volume in cubic inches
	 */
	public static double cubicMetersToInches(double m3) {
		return m3 * CUBIC_INCHES_PER_CUBIC_METER;
	}

	/**
	 * Convert cubic inches to cubic meters.
	 *
	 * @param cubicIn volume in cubic inches
	 * @return volume in cubic meters
	 */
	public static double cubicInchesToMeters(double cubicIn) {
		return cubicIn / CUBIC_INCHES_PER_CUBIC_METER;
	}
}

package info.openrocket.core.aerodynamics.physicsaero.measurement;

/**
 * Converts measured static pressure to U.S. Standard Atmosphere pressure
 * altitude, then subtracts the pad pressure altitude to report height above
 * launch point.
 *
 * <p>This matches the documented convention used by most commercial rocket
 * barometric altimeters: convert each absolute pressure sample to standard
 * pressure altitude and subtract the launch sample. It intentionally does not
 * temperature-correct the indicated altitude to geometric height.
 */
public final class StandardAtmosphereBarometricAltimeter {
	public static final String METHOD_ID =
			"US_STANDARD_ATMOSPHERE_1976_PRESSURE_ALTITUDE_PAD_DIFFERENCE_V1";

	private static final double SEA_LEVEL_PRESSURE_PA = 101_325;
	private static final double SEA_LEVEL_TEMPERATURE_K = 288.15;
	private static final double TROPOPAUSE_TEMPERATURE_K = 216.65;
	private static final double TROPOSPHERE_LAPSE_K_PER_M = 0.0065;
	private static final double TROPOPAUSE_ALTITUDE_M = 11_000;
	private static final double TROPOPAUSE_PRESSURE_PA = 22_632.06;
	private static final double TWENTY_KM_PRESSURE_PA = 5_474.89;
	private static final double SPECIFIC_GAS_CONSTANT_AIR = 287.05287;
	private static final double STANDARD_GRAVITY_M_S2 = 9.80665;
	private static final double TROPOSPHERE_EXPONENT =
			SPECIFIC_GAS_CONSTANT_AIR * TROPOSPHERE_LAPSE_K_PER_M
					/ STANDARD_GRAVITY_M_S2;

	/**
	 * Returns standard pressure altitude from sea level through 20 km.
	 */
	public double pressureAltitudeM(double pressurePa) {
		if (!Double.isFinite(pressurePa)
				|| pressurePa < TWENTY_KM_PRESSURE_PA
				|| pressurePa > SEA_LEVEL_PRESSURE_PA * 1.25) {
			throw new IllegalArgumentException(
					"pressure is outside the 0-20 km standard-atmosphere domain");
		}
		if (pressurePa >= TROPOPAUSE_PRESSURE_PA) {
			return SEA_LEVEL_TEMPERATURE_K / TROPOSPHERE_LAPSE_K_PER_M
					* (1 - Math.pow(pressurePa / SEA_LEVEL_PRESSURE_PA,
							TROPOSPHERE_EXPONENT));
		}
		return TROPOPAUSE_ALTITUDE_M
				- SPECIFIC_GAS_CONSTANT_AIR * TROPOPAUSE_TEMPERATURE_K
						/ STANDARD_GRAVITY_M_S2
						* Math.log(pressurePa / TROPOPAUSE_PRESSURE_PA);
	}

	/**
	 * Returns the pressure-indicated height above the launch point.
	 */
	public double heightAbovePadM(double launchPressurePa,
			double flightPressurePa) {
		if (!(flightPressurePa <= launchPressurePa)) {
			throw new IllegalArgumentException(
					"flight pressure must not exceed launch pressure");
		}
		return pressureAltitudeM(flightPressurePa)
				- pressureAltitudeM(launchPressurePa);
	}
}

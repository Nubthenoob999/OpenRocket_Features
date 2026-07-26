/*
 * Exact atmosphere relations adapted from AidanSYu/openrocketsupersonic,
 * supersonic-aero-dev.  Primary references: U.S. Standard Atmosphere (1976)
 * and Sutherland, Philosophical Magazine 36 (1893), 507-531.
 */
package info.openrocket.core.models.atmosphere;

import info.openrocket.core.util.BugException;
import info.openrocket.core.util.MathUtil;
import info.openrocket.core.util.Monitorable;
import info.openrocket.core.util.ModID;

/**
 * Represents atmospheric conditions at a specific point, containing fundamental
 * properties (temperature and pressure) and methods to calculate derived properties.
 * This class serves as the basic unit of atmospheric data in the simulation.
 */
public class AtmosphericConditions implements Cloneable, Monitorable {

	/** Specific gas constant of dry air (J/(kg*K)). */
	public static final double R = 287.05287;

	/** Specific heat ratio of air (dimensionless). */
	public static final double GAMMA = 1.4;

	/** Ratio of the molar mass of water vapor and dry air */
	public static final double EPSILON = 0.622;

	/** Sutherland's law reference dynamic viscosity (Pa*s) at {@link #T_REF}. */
	private static final double MU_REF = 1.716e-5;

	/** Sutherland's law reference temperature (K). */
	private static final double T_REF = 273.15;

	/** Sutherland's law constant for air (K). */
	private static final double SUTHERLAND_TEMPERATURE = 110.4;

	/** The standard air pressure (Pa). */
	public static final double STANDARD_PRESSURE = 101325.0;

	/** The standard air temperature (K). */
	public static final double STANDARD_TEMPERATURE = 293.15;

	/** The standard air humidity. */
	public static final double STANDARD_HUMIDITY = 0;

	/** Air pressure, in Pascals. */
	private double pressure;

	/** Air temperature, in Kelvins. */
	private double temperature;

	/** Relative air humidity. */
	private double relativeHumidity;

	private ModID modID;

	/**
	 * Construct standard atmospheric conditions.
	 */
	public AtmosphericConditions() {
		this(STANDARD_TEMPERATURE, STANDARD_PRESSURE, STANDARD_HUMIDITY);
	}

	public AtmosphericConditions(double temperature, double pressure) {
		this.setTemperature(temperature);
		this.setPressure(pressure);
		this.setRelativeHumidity(STANDARD_HUMIDITY);
		this.modID = new ModID();
	}

	/**
	 * Construct specified atmospheric conditions.
	 * 
	 * @param temperature the temperature in Kelvins.
	 * @param pressure    the pressure in Pascals.
	 */
	public AtmosphericConditions(double temperature, double pressure, double relativeHumidity) {
		this.setTemperature(temperature);
		this.setPressure(pressure);
		this.setRelativeHumidity(relativeHumidity);
		this.modID = new ModID();
	}

	public double getPressure() {
		return pressure;
	}

	public void setPressure(double pressure) {
		if (pressure <= 0) {
			throw new IllegalArgumentException("Pressure must be positive (Pascals)");
		}
		this.pressure = pressure;
		this.modID = new ModID();
	}

	public double getTemperature() {
		return temperature;
	}

	public void setTemperature(double temperature) {
		if (temperature <= 0) {
			throw new IllegalArgumentException("Temperature must be positive (Kelvin)");
		}
		this.temperature = temperature;
		this.modID = new ModID();
	}

	/**
	 * Get the relative humidity (0 to 1).
	 * @return the relative humidity
	 */
	public double getRelativeHumidity() {
		return relativeHumidity;
	}

	/**
	 * Set the relative humidity (0 to 1).
	 * @param relativeHumidity the relative humidity
	 */
	public void setRelativeHumidity(double relativeHumidity) {
		if (relativeHumidity < 0 || relativeHumidity > 1) {
			throw new IllegalArgumentException("Humidity must be between 0 and 1");
		}
		this.relativeHumidity = relativeHumidity;
		this.modID = new ModID();
	}

	/**
	 * Calculate the saturation water pressure using the Clausius-Clapeyron equation.
	 * @return The saturation vapor pressure in Pa
	 */
	public double vaporPressureSaturation() {
		// 611.3 * Math.exp(5423 * (1/273.15 - 1/getTemperature()));
		return 611.3 * Math.exp(19.854 - 5423/getTemperature());
	}

	/**
	 * Calculate the gas constant of humid air.
     * - EPSILON is the ratio of the molar mass of water vapor and dry air
     * - R is the gas constant of dry air
     * - e_s(T) is the temperature-dependent saturation vapor pressure
     * - RH is the relative humidity
     *
     * @return The gas constant of air in J/kg*K. R if humidity is 0
	 */
    public double getGasConstant() {
		if (getRelativeHumidity() > 0) {
			double numerator = EPSILON * getRelativeHumidity() * vaporPressureSaturation();
			double denominator = getPressure() - getRelativeHumidity() * vaporPressureSaturation() * (1 - EPSILON);
			double scalingFactor = (1/EPSILON - 1);

			return R * (1 + numerator*scalingFactor/denominator);
		} else {
			return R;
		}


    }
	/**
	 * Calculate the current density of air using the ideal gas law for dry air.
	 * The formula used is rho = P/(R*T) where:
	 * - rho is the density in kg/m3
	 * - P is the pressure in Pa
	 * - R is the gas constant for air
	 * - T is the temperature in Kelvin
	 *
	 * @return The current air density in kg/m3
	 */
	public double getDensity() {
		return getPressure() / (getGasConstant() * getTemperature());
	}

	/**
	 * Return the current speed of sound.
	 * <p>
	 * The speed of sound is calculated from the frozen-specific-heat ideal-gas
	 * relation {@code a = sqrt(gamma * R * T)}.  The humidity-aware gas constant
	 * is retained.
	 * 
	 * @return the current speed of sound in m/s.
	 */
	public double getMachSpeed() {
		return Math.sqrt(GAMMA * getGasConstant() * getTemperature());
	}

	/**
	 * Return the dynamic viscosity of air using Sutherland's law.
	 *
	 * @return the dynamic viscosity in Pa*s.
	 */
	public double getDynamicViscosity() {
		double temperatureK = getTemperature();
		return MU_REF * Math.pow(temperatureK / T_REF, 1.5)
				* (T_REF + SUTHERLAND_TEMPERATURE)
				/ (temperatureK + SUTHERLAND_TEMPERATURE);
	}

	/**
	 * Return the current kinematic viscosity of the air.
	 * <p>
	 * Computed as Sutherland-law dynamic viscosity divided by density.
	 * 
	 * @return the current kinematic viscosity in m^2/s.
	 */
	public double getKinematicViscosity() {
		return getDynamicViscosity() / getDensity();
	}

	/**
	 * Compute an effective specific-heat ratio that accounts for vibrational
	 * excitation in high-temperature air.
	 * <p>
	 * The Einstein harmonic-oscillator heat capacity is evaluated for a
	 * 79% nitrogen / 21% oxygen mixture.  This helper is intended for explicit
	 * high-Mach thermodynamic selection; the ordinary speed of sound remains a
	 * frozen-gamma calculation.
	 *
	 * @param stagnationTempK stagnation temperature in K.
	 * @return effective gamma, clamped to [1.3, 1.4].
	 */
	public static double effectiveGamma(double stagnationTempK) {
		if (stagnationTempK <= 800.0) {
			return GAMMA;
		}

		double nitrogenVibrationalCv = vibrationalCv(stagnationTempK, 3371.0);
		double oxygenVibrationalCv = vibrationalCv(stagnationTempK, 2256.0);
		double mixtureVibrationalCv = 0.79 * nitrogenVibrationalCv
				+ 0.21 * oxygenVibrationalCv;
		double totalCvOverR = 2.5 + mixtureVibrationalCv;
		double gamma = (totalCvOverR + 1.0) / totalCvOverR;
		return Math.max(1.3, Math.min(GAMMA, gamma));
	}

	private static double vibrationalCv(double temperatureK, double characteristicTemperatureK) {
		if (temperatureK < 100.0) {
			return 0;
		}
		double ratio = characteristicTemperatureK / temperatureK;
		if (ratio > 50.0) {
			return 0;
		}
		double exponential = Math.exp(ratio);
		double denominator = exponential - 1.0;
		return ratio * ratio * exponential / (denominator * denominator);
	}

	/**
	 * Return a copy of the atmospheric conditions.
	 */
	@Override
	public AtmosphericConditions clone() {
		try {
			return (AtmosphericConditions) super.clone();
		} catch (CloneNotSupportedException e) {
			throw new BugException("CloneNotSupportedException encountered!");
		}
	}

	@Override
	public boolean equals(Object other) {
		if (this == other)
			return true;
		if (!(other instanceof AtmosphericConditions))
			return false;
		AtmosphericConditions o = (AtmosphericConditions) other;
		return MathUtil.equals(this.pressure, o.pressure)
				&& MathUtil.equals(this.temperature, o.temperature)
				&& MathUtil.equals(this.relativeHumidity, o.relativeHumidity);
	}

	@Override
	public int hashCode() {
		return (int) (this.pressure + this.temperature * 1000
				+ this.relativeHumidity * 1000000);
	}

	@Override
	public ModID getModID() {
		return modID;
	}

	@Override
	public String toString() {
		return String.format("AtmosphericConditions[T=%.2f,P=%.2f]", getTemperature(), getPressure());
	}

}

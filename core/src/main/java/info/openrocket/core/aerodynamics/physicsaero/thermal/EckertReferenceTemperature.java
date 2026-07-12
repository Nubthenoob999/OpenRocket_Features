package info.openrocket.core.aerodynamics.physicsaero.thermal;
/** Eckert engineering reference-temperature method. */
public final class EckertReferenceTemperature {
	public double temperatureK(double edgeTemperatureK, double wallTemperatureK, double mach) {
		if (edgeTemperatureK <= 0 || wallTemperatureK <= 0) throw new IllegalArgumentException("invalid thermal state");
		return edgeTemperatureK * (0.5 + 0.039 * mach * mach + 0.5 * wallTemperatureK / edgeTemperatureK);
	}
}

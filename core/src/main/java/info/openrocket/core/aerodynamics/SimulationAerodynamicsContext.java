package info.openrocket.core.aerodynamics;

/** Simulation state unavailable through the historical FlightConditions API. */
public record SimulationAerodynamicsContext(double timeSeconds, boolean powered, double thrustN,
		double poweredFraction, double ambientPressurePa) {
	public SimulationAerodynamicsContext {
		if (!Double.isFinite(timeSeconds) || timeSeconds < 0 || !Double.isFinite(thrustN) || thrustN < 0
				|| !Double.isFinite(poweredFraction) || poweredFraction < 0 || poweredFraction > 1
				|| !Double.isFinite(ambientPressurePa) || ambientPressurePa <= 0) {
			throw new IllegalArgumentException("INVALID_SIMULATION_AERODYNAMICS_CONTEXT");
		}
	}
	public static SimulationAerodynamicsContext coast(double timeSeconds, double ambientPressurePa) {
		return new SimulationAerodynamicsContext(timeSeconds, false, 0, 0, ambientPressurePa);
	}
}

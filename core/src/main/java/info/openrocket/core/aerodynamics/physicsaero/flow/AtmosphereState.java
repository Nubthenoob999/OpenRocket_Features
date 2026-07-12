package info.openrocket.core.aerodynamics.physicsaero.flow;

public record AtmosphereState(double pressurePa, double temperatureK, double densityKgM3,
		double dynamicViscosityPaS) {
	public AtmosphereState {
		if (!(pressurePa > 0) || !(temperatureK > 0) || !(densityKgM3 > 0)
				|| (!Double.isNaN(dynamicViscosityPaS) && !(dynamicViscosityPaS > 0))) {
			throw new IllegalArgumentException("atmospheric state must be finite and positive");
		}
	}
}

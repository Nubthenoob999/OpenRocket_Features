package info.openrocket.core.aerodynamics.physicsaero.flow;

public record GasState(double mach, double pressurePa, double temperatureK, double densityKgM3,
		double velocityMS) {
	public GasState {
		if (mach < 0 || pressurePa <= 0 || temperatureK <= 0 || densityKgM3 <= 0 || velocityMS < 0
				|| !Double.isFinite(mach + pressurePa + temperatureK + densityKgM3 + velocityMS)) {
			throw new IllegalArgumentException("invalid gas state");
		}
	}
}

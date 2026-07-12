package info.openrocket.core.aerodynamics.physicsaero.flow;

public record TotalState(double pressurePa, double temperatureK, double densityKgM3) {
	public TotalState {
		if (pressurePa <= 0 || temperatureK <= 0 || densityKgM3 <= 0) throw new IllegalArgumentException("invalid total state");
	}
}

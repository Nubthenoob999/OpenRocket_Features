package info.openrocket.core.aerodynamics.physicsaero.config;

import java.util.Set;
import info.openrocket.core.aerodynamics.physicsaero.api.MethodId;

public record PhysicsConfiguration(Set<MethodId> enabledMethods, String wallTemperatureModel,
		double roughnessM, boolean powered) {
	public PhysicsConfiguration {
		enabledMethods = Set.copyOf(enabledMethods);
		if (roughnessM < 0 || !Double.isFinite(roughnessM)) throw new IllegalArgumentException("invalid roughness");
	}
}

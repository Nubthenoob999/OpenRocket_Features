package info.openrocket.core.aerodynamics.physicsaero.config;

import java.util.Set;
import info.openrocket.core.aerodynamics.physicsaero.api.MethodId;
import info.openrocket.core.aerodynamics.physicsaero.boundarylayer.EngineeringSkinFrictionCorrelation;
import info.openrocket.core.aerodynamics.physicsaero.thermal.VanDriestIITransformation;

public record PhysicsConfiguration(Set<MethodId> enabledMethods, String wallTemperatureModel,
		double roughnessM, boolean powered, boolean forceTurbulentBoundaryLayer) {
	public PhysicsConfiguration(Set<MethodId> enabledMethods, String wallTemperatureModel,
			double roughnessM, boolean powered) {
		this(enabledMethods, wallTemperatureModel, roughnessM, powered, false);
	}
	public static Set<MethodId> allRegisteredMethods() {
		return Set.of(
				new MethodId("ALL_REGISTERED_CORRELATIONS"),
				new MethodId(EngineeringSkinFrictionCorrelation.METHOD_ID),
				new MethodId(VanDriestIITransformation.METHOD_ID));
	}

	public PhysicsConfiguration {
		enabledMethods = Set.copyOf(enabledMethods);
		if (roughnessM < 0 || !Double.isFinite(roughnessM)) throw new IllegalArgumentException("invalid roughness");
	}
}

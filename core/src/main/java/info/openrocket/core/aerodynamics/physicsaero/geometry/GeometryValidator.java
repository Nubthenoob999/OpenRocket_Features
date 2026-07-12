package info.openrocket.core.aerodynamics.physicsaero.geometry;

import java.util.HashSet;
import java.util.Set;

public final class GeometryValidator {
	private GeometryValidator() {}
	public static void validate(AeroGeometry geometry) {
		Set<String> ids = new HashSet<>();
		for (AeroComponent c : geometry.components()) {
			if (!ids.add(c.id())) throw new IllegalArgumentException("duplicate component ID: " + c.id());
			if (!(c.axialEndM() > c.axialStartM()) || !Double.isFinite(c.axialStartM() + c.axialEndM()))
				throw new IllegalArgumentException("non-positive or non-finite component length: " + c.id());
			if (c.baseAreaM2() > Math.PI * c.rootRadiusM() * c.rootRadiusM() + 1e-12)
				throw new IllegalArgumentException("base area exceeds enclosing body area: " + c.id());
		}
	}
}

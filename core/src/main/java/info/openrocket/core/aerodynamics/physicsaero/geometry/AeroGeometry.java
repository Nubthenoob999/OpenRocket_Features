package info.openrocket.core.aerodynamics.physicsaero.geometry;

import java.util.List;

public record AeroGeometry(List<AeroComponent> components, ReferenceGeometry references, String geometryHash) {
	public AeroGeometry {
		components = List.copyOf(components);
		if (components.isEmpty() || references == null) throw new IllegalArgumentException("empty aerodynamic geometry");
	}
	public AeroGeometry withHash(String hash) { return new AeroGeometry(components, references, hash); }
}

package info.openrocket.core.aerodynamics.physicsaero.force;

import info.openrocket.core.util.Coordinate;

public record ReferenceState(double dynamicPressurePa, double referenceAreaM2, double referenceLengthM,
		Coordinate momentOriginM) {
	public ReferenceState {
		if (dynamicPressurePa <= 0 || referenceAreaM2 <= 0 || referenceLengthM <= 0 || momentOriginM == null) {
			throw new IllegalArgumentException("invalid aerodynamic reference state");
		}
	}
}

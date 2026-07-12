package info.openrocket.core.aerodynamics.physicsaero.geometry;

import java.util.Map;
import info.openrocket.core.util.Coordinate;

public record ReferenceGeometry(double referenceAreaM2, double exposedBaseAreaM2, double vehicleLengthM,
		double maximumBodyDiameterM, Map<String, Double> wettedAreaByComponentM2,
		Coordinate momentOriginM, double referenceLengthM) {
	public ReferenceGeometry {
		wettedAreaByComponentM2 = Map.copyOf(wettedAreaByComponentM2);
		if (referenceAreaM2 <= 0 || exposedBaseAreaM2 < 0 || vehicleLengthM <= 0 || maximumBodyDiameterM <= 0
				|| referenceLengthM <= 0 || momentOriginM == null) throw new IllegalArgumentException("invalid reference geometry");
	}
}

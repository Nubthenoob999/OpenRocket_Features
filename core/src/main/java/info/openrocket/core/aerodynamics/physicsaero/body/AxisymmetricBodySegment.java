package info.openrocket.core.aerodynamics.physicsaero.body;

import java.util.List;
import info.openrocket.core.aerodynamics.physicsaero.geometry.GeometryStation;

public record AxisymmetricBodySegment(String componentId, String regionId, BodySegmentType type,
		double startXM, double endXM, double startRadiusM, double endRadiusM,
		double startTangentRad, double endTangentRad, List<GeometryStation> quadratureStations,
		List<String> eligibleMethods) {
	public AxisymmetricBodySegment {
		quadratureStations = List.copyOf(quadratureStations); eligibleMethods = List.copyOf(eligibleMethods);
		if (componentId == null || regionId == null || type == null || endXM < startXM
				|| startRadiusM < 0 || endRadiusM < 0) throw new IllegalArgumentException("invalid body segment");
	}
	public double turnAngleRad() { return endTangentRad - startTangentRad; }
}

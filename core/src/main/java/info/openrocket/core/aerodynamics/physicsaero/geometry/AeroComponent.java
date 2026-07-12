package info.openrocket.core.aerodynamics.physicsaero.geometry;

import java.util.List;
import java.util.Map;
import info.openrocket.core.util.Coordinate;

public record AeroComponent(String id, String sourcePath, String type, String classification,
		String parentStageId, int axialOrder, Coordinate originM, double axialStartM, double axialEndM,
		double rootRadiusM, double wettedAreaM2, double projectedAreaM2, double baseAreaM2,
		double roughnessM, String wallTemperatureModelId, Map<String, Double> localReferences,
		List<String> eligibleCorrelationIds, AxisymmetricProfile axisymmetricProfile,
		FinGeometry finGeometry, ProtuberanceGeometry protuberanceGeometry) {
	public AeroComponent {
		localReferences = Map.copyOf(localReferences); eligibleCorrelationIds = List.copyOf(eligibleCorrelationIds);
		if (id == null || id.isBlank() || sourcePath == null || sourcePath.isBlank() || axialEndM < axialStartM
				|| rootRadiusM < 0 || wettedAreaM2 < 0 || projectedAreaM2 < 0 || baseAreaM2 < 0 || roughnessM < 0)
			throw new IllegalArgumentException("invalid aerodynamic component");
	}
}

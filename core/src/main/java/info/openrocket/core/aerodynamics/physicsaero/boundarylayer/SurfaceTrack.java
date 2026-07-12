package info.openrocket.core.aerodynamics.physicsaero.boundarylayer;

import java.util.List;

public record SurfaceTrack(String componentId, String regionId, BoundaryLayerMode mode,
		List<BoundaryLayerStation> stations, List<BoundaryLayerRegion> regions, boolean reducedConfidence) {
	public SurfaceTrack {
		if (componentId == null || componentId.isBlank() || regionId == null || regionId.isBlank() || mode == null)
			throw new IllegalArgumentException("invalid surface-track identity");
		stations = List.copyOf(stations); regions = List.copyOf(regions);
		if (stations.size() < 2) throw new IllegalArgumentException("surface track needs at least two stations");
	}
}

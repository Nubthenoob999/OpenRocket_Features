package info.openrocket.core.aerodynamics.physicsaero.boundarylayer;
public record BoundaryLayerRegion(String id, BoundaryLayerRegionType type, int firstStation, int lastStation) {
	public BoundaryLayerRegion {
		if (id == null || id.isBlank() || type == null || firstStation < 0 || lastStation < firstStation)
			throw new IllegalArgumentException("invalid boundary-layer region");
	}
}

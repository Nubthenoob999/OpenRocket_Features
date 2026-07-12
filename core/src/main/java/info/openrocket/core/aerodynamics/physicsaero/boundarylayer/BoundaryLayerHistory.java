package info.openrocket.core.aerodynamics.physicsaero.boundarylayer;
import java.util.List;
public record BoundaryLayerHistory(SurfaceTrack track, List<BoundaryLayerState> states) {
	public BoundaryLayerHistory { states = List.copyOf(states); if (track == null || states.size() != track.stations().size()) throw new IllegalArgumentException("history size mismatch"); }
}

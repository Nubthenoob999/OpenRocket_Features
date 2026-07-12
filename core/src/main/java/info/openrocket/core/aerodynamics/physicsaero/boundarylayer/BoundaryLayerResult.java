package info.openrocket.core.aerodynamics.physicsaero.boundarylayer;

import java.util.List;
import info.openrocket.core.aerodynamics.physicsaero.force.ForceContribution;

public record BoundaryLayerResult(BoundaryLayerHistory history, ForceContribution skinFriction,
		double transitionLocationM, double minimumDragN, double maximumDragN, List<String> diagnostics) {
	public BoundaryLayerResult { diagnostics = List.copyOf(diagnostics); if (history == null || skinFriction == null) throw new IllegalArgumentException("missing result data"); }
}

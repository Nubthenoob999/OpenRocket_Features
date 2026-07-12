package info.openrocket.core.aerodynamics.physicsaero.interaction;

import info.openrocket.core.aerodynamics.physicsaero.body.AxisymmetricEdgeStateHistory;
import info.openrocket.core.aerodynamics.physicsaero.fin.FinStrip;
import info.openrocket.core.aerodynamics.physicsaero.flow.FlowCondition;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;
import info.openrocket.core.util.Coordinate;

public interface BodyUpwashModel {
	UpwashResult evaluate(AeroGeometry body, FinStrip strip, FlowCondition flow, AxisymmetricEdgeStateHistory bodyState);
	record UpwashResult(Coordinate velocityIncrementBody, boolean valid, double confidence, String provenance) {
		public UpwashResult { if (velocityIncrementBody == null || provenance == null || provenance.isBlank() || confidence < 0 || confidence > 1) throw new IllegalArgumentException("invalid upwash result"); }
	}
}

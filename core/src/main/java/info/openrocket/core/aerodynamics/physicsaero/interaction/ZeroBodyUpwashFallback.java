package info.openrocket.core.aerodynamics.physicsaero.interaction;

import info.openrocket.core.aerodynamics.physicsaero.body.AxisymmetricEdgeStateHistory;
import info.openrocket.core.aerodynamics.physicsaero.fin.FinStrip;
import info.openrocket.core.aerodynamics.physicsaero.flow.FlowCondition;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;
import info.openrocket.core.util.Coordinate;

/** Explicit, low-confidence fallback used outside a sourced body-upwash correlation. */
public final class ZeroBodyUpwashFallback implements BodyUpwashModel {
	@Override public UpwashResult evaluate(AeroGeometry body, FinStrip strip, FlowCondition flow, AxisymmetricEdgeStateHistory bodyState) {
		return new UpwashResult(new Coordinate(), true, 0.2, "ZERO_UPWASH_LOW_CONFIDENCE_FALLBACK");
	}
}

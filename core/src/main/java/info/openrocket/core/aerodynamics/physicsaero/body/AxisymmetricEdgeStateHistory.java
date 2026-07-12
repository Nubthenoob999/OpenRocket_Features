package info.openrocket.core.aerodynamics.physicsaero.body;

import java.util.List;
import info.openrocket.core.aerodynamics.physicsaero.flow.FlowEvent;
import info.openrocket.core.aerodynamics.physicsaero.flow.SurfaceState;

public record AxisymmetricEdgeStateHistory(List<SurfaceState> states, List<FlowEvent> events) {
	public AxisymmetricEdgeStateHistory {
		states = List.copyOf(states); events = List.copyOf(events);
		for (int i = 1; i < states.size(); i++) if (states.get(i).xM() < states.get(i - 1).xM())
			throw new IllegalArgumentException("edge states must be axially ordered");
	}
}

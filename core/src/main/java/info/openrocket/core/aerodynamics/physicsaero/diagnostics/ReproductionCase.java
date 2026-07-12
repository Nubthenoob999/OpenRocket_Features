package info.openrocket.core.aerodynamics.physicsaero.diagnostics;

import java.util.Map;

public record ReproductionCase(int cellIndex, String geometryHash, Map<String, Double> state,
		FailureReason failureReason, String details) { public ReproductionCase { state = Map.copyOf(state); } }

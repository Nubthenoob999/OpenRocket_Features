package info.openrocket.core.aerodynamics.physicsaero.body;

import java.util.List;
import java.util.Map;
import info.openrocket.core.aerodynamics.physicsaero.force.*;

public record AxisymmetricBodyResult(AerodynamicCoefficients coefficients,
		List<ForceContribution> contributions, AxisymmetricEdgeStateHistory edgeStateHistory,
		List<AxisymmetricBodySegment> segments, List<BodyMethodSelector.Decision> methodDecisions,
		AreaRuleWaveDragCheck.Result areaRuleDiagnostic, Map<String, String> diagnostics) {
	public AxisymmetricBodyResult {
		contributions = List.copyOf(contributions); segments = List.copyOf(segments);
		methodDecisions = List.copyOf(methodDecisions); diagnostics = Map.copyOf(diagnostics);
	}
}

package info.openrocket.core.aerodynamics.physicsaero.fin;

import java.util.List;
import java.util.Map;
import info.openrocket.core.aerodynamics.physicsaero.force.AerodynamicCoefficients;
import info.openrocket.core.aerodynamics.physicsaero.force.ForceContribution;

public record FinResult(AerodynamicCoefficients coefficients, List<ForceContribution> contributions,
		List<FinLocalFlow> localFlows, Map<String, String> diagnostics) {
	public FinResult { contributions = List.copyOf(contributions); localFlows = List.copyOf(localFlows); diagnostics = Map.copyOf(diagnostics); }
}

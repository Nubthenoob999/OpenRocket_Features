package info.openrocket.core.aerodynamics.physicsaero.api;

import java.util.List;
import info.openrocket.core.aerodynamics.physicsaero.force.ForceContribution;

public record CorrelationResult(List<ForceContribution> contributions, ValidityAssessment validity,
		List<String> diagnostics) {
	public CorrelationResult {
		contributions = List.copyOf(contributions);
		diagnostics = List.copyOf(diagnostics);
	}
}

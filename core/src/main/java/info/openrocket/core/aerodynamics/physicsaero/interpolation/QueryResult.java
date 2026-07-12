package info.openrocket.core.aerodynamics.physicsaero.interpolation;

import java.util.List;
import java.util.Set;
import info.openrocket.core.aerodynamics.physicsaero.diagnostics.DiagnosticFlag;
import info.openrocket.core.aerodynamics.physicsaero.force.AerodynamicCoefficients;

public record QueryResult(AerodynamicCoefficients coefficients, Set<DiagnosticFlag> diagnosticFlags,
		List<String> validityFlags, List<String> methodIds, boolean interpolated) {
	public QueryResult { diagnosticFlags = Set.copyOf(diagnosticFlags); validityFlags = List.copyOf(validityFlags); methodIds = List.copyOf(methodIds); }
}

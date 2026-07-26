package info.openrocket.core.aerodynamics.physicsaero.interpolation;

import java.util.List;
import java.util.Set;
import info.openrocket.core.aerodynamics.physicsaero.diagnostics.DiagnosticFlag;
import info.openrocket.core.aerodynamics.physicsaero.force.AerodynamicCoefficients;
import info.openrocket.core.aerodynamics.physicsaero.force.AerodynamicDerivatives;
import java.util.Map;
import info.openrocket.core.aerodynamics.physicsaero.diagnostics.FailureReason;
import info.openrocket.core.aerodynamics.physicsaero.table.RuntimeCorrectionData;

public record QueryResult(AerodynamicCoefficients coefficients, Set<DiagnosticFlag> diagnosticFlags,
		List<String> validityFlags, List<String> methodIds, boolean interpolated,
		Map<String, AerodynamicCoefficients> componentTotals,
		Map<String, AerodynamicCoefficients> ownerTotals, AerodynamicDerivatives derivatives,
		Set<FailureReason> reasonCodes, double lowestConfidence,
		RuntimeCorrectionData runtimeCorrection) {
	public QueryResult {
		diagnosticFlags = Set.copyOf(diagnosticFlags);
		validityFlags = List.copyOf(validityFlags);
		methodIds = List.copyOf(methodIds);
		componentTotals = Map.copyOf(componentTotals);
		ownerTotals = Map.copyOf(ownerTotals);
		reasonCodes = Set.copyOf(reasonCodes);
		if (runtimeCorrection == null) throw new IllegalArgumentException("runtime correction data required");
		if (!Double.isNaN(lowestConfidence)
				&& (!Double.isFinite(lowestConfidence) || lowestConfidence < 0 || lowestConfidence > 1)) {
			throw new IllegalArgumentException("invalid confidence");
		}
	}
	public QueryResult(AerodynamicCoefficients coefficients, Set<DiagnosticFlag> diagnosticFlags,
			List<String> validityFlags, List<String> methodIds, boolean interpolated,
			Map<String, AerodynamicCoefficients> componentTotals,
			Map<String, AerodynamicCoefficients> ownerTotals, AerodynamicDerivatives derivatives,
			Set<FailureReason> reasonCodes, double lowestConfidence) {
		this(coefficients, diagnosticFlags, validityFlags, methodIds, interpolated,
				componentTotals, ownerTotals, derivatives, reasonCodes, lowestConfidence,
				RuntimeCorrectionData.rebuildRequired(0));
	}
	public QueryResult(AerodynamicCoefficients coefficients, Set<DiagnosticFlag> diagnosticFlags,
			List<String> validityFlags, List<String> methodIds, boolean interpolated,
			Map<String, AerodynamicCoefficients> componentTotals,
			Map<String, AerodynamicCoefficients> ownerTotals, AerodynamicDerivatives derivatives,
			Set<FailureReason> reasonCodes) {
		this(coefficients, diagnosticFlags, validityFlags, methodIds, interpolated,
				componentTotals, ownerTotals, derivatives, reasonCodes, Double.NaN,
				RuntimeCorrectionData.rebuildRequired(0));
	}
	public QueryResult(AerodynamicCoefficients coefficients, Set<DiagnosticFlag> diagnosticFlags,
			List<String> validityFlags, List<String> methodIds, boolean interpolated,
			Map<String, AerodynamicCoefficients> componentTotals,
			Map<String, AerodynamicCoefficients> ownerTotals, AerodynamicDerivatives derivatives) {
		this(coefficients, diagnosticFlags, validityFlags, methodIds, interpolated,
				componentTotals, ownerTotals, derivatives, Set.of(), Double.NaN,
				RuntimeCorrectionData.rebuildRequired(0));
	}
	public QueryResult(AerodynamicCoefficients coefficients, Set<DiagnosticFlag> diagnosticFlags,
			List<String> validityFlags, List<String> methodIds, boolean interpolated) {
		this(coefficients, diagnosticFlags, validityFlags, methodIds, interpolated,
				Map.of(), Map.of(), AerodynamicDerivatives.zero(), Set.of(), Double.NaN,
				RuntimeCorrectionData.rebuildRequired(0));
	}
}

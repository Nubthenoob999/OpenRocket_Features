package info.openrocket.core.aerodynamics.physicsaero.table;

import java.util.List;
import java.util.Map;
import info.openrocket.core.aerodynamics.physicsaero.diagnostics.CellDiagnostics;
import info.openrocket.core.aerodynamics.physicsaero.force.AerodynamicCoefficients;
import info.openrocket.core.aerodynamics.physicsaero.force.ReferenceState;

public record TableCell(AerodynamicCoefficients coefficients, Map<String, AerodynamicCoefficients> componentTotals,
		Map<String, AerodynamicCoefficients> ownerTotals, List<String> methodIds, double[] confidence,
		double[] uncertainty, List<String> validityFlags, ReferenceState referenceState,
		CellDiagnostics diagnostics, boolean directlyGenerated) {
	public TableCell {
		componentTotals = Map.copyOf(componentTotals); ownerTotals = Map.copyOf(ownerTotals); methodIds = List.copyOf(methodIds);
		confidence = confidence.clone(); uncertainty = uncertainty.clone(); validityFlags = List.copyOf(validityFlags);
		if (confidence.length != 6 || uncertainty.length != 6) throw new IllegalArgumentException("six confidence and uncertainty values required");
	}
	@Override public double[] confidence() { return confidence.clone(); } @Override public double[] uncertainty() { return uncertainty.clone(); }
}

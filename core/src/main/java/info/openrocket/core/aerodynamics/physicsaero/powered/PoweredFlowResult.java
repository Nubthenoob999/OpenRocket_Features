package info.openrocket.core.aerodynamics.physicsaero.powered;

import java.util.List;

/** Auditable powered-minus-coast drag decomposition. */
public record PoweredFlowResult(double totalDeltaCd, double baseDeltaCd, double boattailDeltaCd,
		double plumeAndInstallationDeltaCd, List<String> methodIds, List<String> validityFlags) {
	public PoweredFlowResult {
		if (!Double.isFinite(totalDeltaCd + baseDeltaCd + boattailDeltaCd + plumeAndInstallationDeltaCd)) {
			throw new IllegalArgumentException("NONFINITE_POWERED_FLOW_RESULT");
		}
		methodIds = List.copyOf(methodIds);
		validityFlags = List.copyOf(validityFlags);
	}
}

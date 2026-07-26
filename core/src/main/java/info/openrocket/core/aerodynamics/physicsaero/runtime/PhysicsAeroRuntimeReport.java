package info.openrocket.core.aerodynamics.physicsaero.runtime;

import java.util.Map;
import java.util.Set;

import info.openrocket.core.aerodynamics.physicsaero.diagnostics.FailureReason;

/** Immutable simulation-facing summary of table use and explicit diagnostic fallbacks. */
public record PhysicsAeroRuntimeReport(
		boolean tableValid,
		String schemaVersion,
		String geometryHash,
		String settingsHash,
		String contentHash,
		String certificationState,
		long totalQueries,
		long successfulTableQueries,
		long interpolatedQueries,
		long reynoldsCorrectedQueries,
		long fallbackCount,
		Map<FailureReason, Long> failureCounts,
		double lowestConfidence,
		Set<PhysicsAeroRuntimeFlag> runtimeFlags,
		Map<FailureReason, PhysicsAeroFailureOccurrence> firstOccurrences) {

	public PhysicsAeroRuntimeReport {
		schemaVersion = value(schemaVersion);
		geometryHash = value(geometryHash);
		settingsHash = value(settingsHash);
		contentHash = value(contentHash);
		certificationState = value(certificationState);
		failureCounts = Map.copyOf(failureCounts);
		runtimeFlags = Set.copyOf(runtimeFlags);
		firstOccurrences = Map.copyOf(firstOccurrences);
	}

	public double fallbackFraction() {
		return totalQueries == 0 ? 0 : (double) fallbackCount / totalQueries;
	}

	public static PhysicsAeroRuntimeReport disabled() {
		return new PhysicsAeroRuntimeReport(false, "", "", "", "", "NOT_READY",
				0, 0, 0, 0, 0, Map.of(), Double.NaN, Set.of(), Map.of());
	}

	private static String value(String value) { return value == null ? "" : value; }
}

package info.openrocket.core.aerodynamics.physicsaero.diagnostics;

import java.util.List;
import java.util.Set;

public record CellDiagnostics(Set<DiagnosticFlag> flags, Set<FailureReason> reasonCodes,
		boolean fallback, String fallbackMethodId, String fallbackReasonCode, List<String> messages) {
	public CellDiagnostics {
		flags = Set.copyOf(flags);
		reasonCodes = Set.copyOf(reasonCodes);
		messages = List.copyOf(messages);
	}
	public CellDiagnostics(Set<DiagnosticFlag> flags, boolean fallback, String fallbackMethodId,
			String fallbackReasonCode, List<String> messages) {
		this(flags, Set.of(), fallback, fallbackMethodId, fallbackReasonCode, messages);
	}
	public static CellDiagnostics direct() {
		return new CellDiagnostics(Set.of(DiagnosticFlag.DIRECT_GENERATION), Set.of(), false, null, null, List.of());
	}
}

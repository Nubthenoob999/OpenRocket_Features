package info.openrocket.core.aerodynamics.physicsaero.diagnostics;

import java.util.List;
import java.util.Set;

public record CellDiagnostics(Set<DiagnosticFlag> flags, boolean fallback, String fallbackMethodId,
		String fallbackReasonCode, List<String> messages) {
	public CellDiagnostics { flags = Set.copyOf(flags); messages = List.copyOf(messages); }
	public static CellDiagnostics direct() { return new CellDiagnostics(Set.of(DiagnosticFlag.DIRECT_GENERATION), false, null, null, List.of()); }
}

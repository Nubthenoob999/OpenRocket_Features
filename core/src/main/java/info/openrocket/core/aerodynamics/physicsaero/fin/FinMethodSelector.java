package info.openrocket.core.aerodynamics.physicsaero.fin;

import java.util.List;

/** Deterministic ownership selection; confidence never decides physical ownership. */
public final class FinMethodSelector {
	public Selection select(FinSectionFamily family, boolean pressureValid, boolean datcomValid) {
		if (pressureValid && (family == FinSectionFamily.SINGLE_WEDGE || family == FinSectionFamily.SYMMETRIC_DIAMOND))
			return new Selection(Method.SHOCK_EXPANSION, List.of(datcomValid ? Method.DATCOM_DIAGNOSTIC : Method.NONE));
		if (pressureValid && family == FinSectionFamily.FLAT_PLATE)
			return new Selection(Method.ACKERET, List.of(datcomValid ? Method.DATCOM_DIAGNOSTIC : Method.NONE));
		if (datcomValid) return new Selection(Method.DATCOM, List.of());
		return new Selection(Method.FAIL, List.of());
	}
	public enum Method { SHOCK_EXPANSION, ACKERET, DATCOM, DATCOM_DIAGNOSTIC, NONE, FAIL }
	public record Selection(Method authoritative, List<Method> diagnostics) { public Selection { diagnostics = List.copyOf(diagnostics); } }
}

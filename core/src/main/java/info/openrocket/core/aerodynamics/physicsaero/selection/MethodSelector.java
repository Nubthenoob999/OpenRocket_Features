package info.openrocket.core.aerodynamics.physicsaero.selection;

import java.util.Set;
import info.openrocket.core.aerodynamics.physicsaero.api.*;

public final class MethodSelector {
	public SelectionDecision select(AerodynamicCorrelation<CorrelationInput> method, CorrelationInput input, Set<MethodId> enabled) {
		if (!enabled.contains(method.metadata().methodId())) return new SelectionDecision(method.metadata().methodId(), false, SelectionReason.DISABLED, "method disabled");
		ValidityAssessment assessment = method.assessValidity(input);
		return assessment.isValid() ? new SelectionDecision(method.metadata().methodId(), true, SelectionReason.EXPLICITLY_ENABLED, "valid")
				: new SelectionDecision(method.metadata().methodId(), false, SelectionReason.OUTSIDE_VALIDITY, String.join(",", assessment.reasonCodes()));
	}
}

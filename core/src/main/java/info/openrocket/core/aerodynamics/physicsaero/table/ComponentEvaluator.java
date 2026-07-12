package info.openrocket.core.aerodynamics.physicsaero.table;

import java.util.List;
import info.openrocket.core.aerodynamics.physicsaero.api.*;
import info.openrocket.core.aerodynamics.physicsaero.force.*;

/** Headless single-component evaluation used by tests and CLI front-ends. */
public final class ComponentEvaluator {
	public Evaluation evaluate(AerodynamicCorrelation<CorrelationInput> correlation, CorrelationInput input) {
		ValidityAssessment validity = correlation.assessValidity(input);
		if (!validity.isValid()) return new Evaluation(validity, List.of(), null, correlation.metadata());
		CorrelationResult result = correlation.evaluate(input); ContributionLedger ledger = new ContributionLedger();
		result.contributions().stream().sorted().forEach(ledger::add);
		return new Evaluation(result.validity(), ledger.entries(), CoefficientAssembler.assemble(ledger, input.reference()), correlation.metadata());
	}
	public record Evaluation(ValidityAssessment validity, List<ForceContribution> contributions,
			AerodynamicCoefficients coefficients, CorrelationMetadata metadata) { public Evaluation { contributions = List.copyOf(contributions); } }
}

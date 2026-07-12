package info.openrocket.core.aerodynamics.physicsaero.api;

import java.util.*;
import info.openrocket.core.aerodynamics.physicsaero.force.ForceContribution;
import info.openrocket.core.util.Coordinate;

/** Phase-I pipeline correlation. It is deliberately not production pressure physics. */
public final class DeterministicTestCorrelation implements AerodynamicCorrelation<CorrelationInput> {
	public static final MethodId ID = new MethodId("phase1.deterministic-test-pressure");
	private final CorrelationMetadata metadata = new CorrelationMetadata(ID, "1.0.0", "Phase-I deterministic test pressure",
			"OpenRocket Physics-Based Aerodynamics Phase 1 plan", List.of("PIPELINE-SMOKE-1"),
			Set.of("NoseCone", "BodyTube", "Transition"), new ValidityDomain(1, 7, 0, Double.MAX_VALUE,
					-Math.toRadians(15), Math.toRadians(15), "axisymmetric", "any"), Set.of(PhysicalTerm.BODY_PRESSURE),
			OwnershipMode.REPLACES, List.of("constant CA=0.1 pipeline sentinel"), "TEST_ONLY");
	@Override public CorrelationMetadata metadata() { return metadata; }
	@Override public ValidityAssessment assessValidity(CorrelationInput input) {
		return metadata.supportedComponentTypes().contains(input.component().type()) && metadata.validityDomain().contains(
				input.flow().mach(), Double.MAX_VALUE / 2, input.flow().alphaRad()) ? ValidityAssessment.valid() : ValidityAssessment.invalid("OUTSIDE_TEST_CORRELATION_DOMAIN");
	}
	@Override public CorrelationResult evaluate(CorrelationInput input) {
		ValidityAssessment validity = assessValidity(input); if (!validity.isValid()) return new CorrelationResult(List.of(), validity, validity.reasonCodes());
		double dragN = 0.1 * input.reference().dynamicPressurePa() * input.reference().referenceAreaM2();
		PhysicalOwner owner = new PhysicalOwner(PhysicalTerm.BODY_PRESSURE, OwnershipMode.REPLACES, "whole-component", null);
		ForceContribution contribution = new ForceContribution(input.component().id(), owner, ID, new Coordinate(dragN, 0, 0),
				new Coordinate(), input.component().originM(), "whole-component", List.of(), 1, 0, null);
		return new CorrelationResult(List.of(contribution), validity, List.of("PHASE1_TEST_ONLY"));
	}
}

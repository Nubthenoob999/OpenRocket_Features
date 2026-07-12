package info.openrocket.core.aerodynamics.physicsaero.api;

import java.util.List;
import java.util.Set;

public record CorrelationMetadata(MethodId methodId, String semanticVersion, String name,
		String sourceCitation, List<String> equationIds, Set<String> supportedComponentTypes,
		ValidityDomain validityDomain, Set<PhysicalTerm> producedTerms, OwnershipMode ownershipMode,
		List<String> numericalAssumptions, String validationStatus) {
	public CorrelationMetadata {
		equationIds = List.copyOf(equationIds);
		supportedComponentTypes = Set.copyOf(supportedComponentTypes);
		producedTerms = Set.copyOf(producedTerms);
		numericalAssumptions = List.copyOf(numericalAssumptions);
	}
}

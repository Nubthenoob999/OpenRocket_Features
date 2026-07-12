package info.openrocket.core.aerodynamics.physicsaero.force;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import info.openrocket.core.aerodynamics.physicsaero.api.OwnershipMode;

/** Enforces physical ownership before any force reaches the assembler. */
public final class ContributionLedger {
	private final List<ForceContribution> entries = new ArrayList<>();
	private final Map<String, ForceContribution> authoritative = new HashMap<>();
	public void add(ForceContribution contribution) {
		String key = contribution.componentId() + '\u0000' + contribution.regionId() + '\u0000' + contribution.owner().term();
		if (contribution.owner().mode() == OwnershipMode.REPLACES) {
			ForceContribution previous = authoritative.putIfAbsent(key, contribution);
			if (previous != null) throw new IllegalStateException("duplicate authoritative owner: " + key);
		}
		if ((contribution.owner().mode() == OwnershipMode.RESIDUAL || contribution.owner().mode() == OwnershipMode.MODIFIES)
				&& entries.stream().noneMatch(e -> e.componentId().equals(contribution.componentId())
				&& e.regionId().equals(contribution.regionId())
				&& e.methodId().equals(contribution.owner().primaryMethod()))) {
			throw new IllegalStateException(contribution.owner().mode() + " contribution has no named primary contribution");
		}
		entries.add(contribution);
	}
	public List<ForceContribution> entries() { return entries.stream().sorted().toList(); }
}

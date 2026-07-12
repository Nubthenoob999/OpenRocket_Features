package info.openrocket.core.aerodynamics.physicsaero.api;

import java.util.Objects;

public record PhysicalOwner(PhysicalTerm term, OwnershipMode mode, String regionId, MethodId primaryMethod) {
	public PhysicalOwner {
		Objects.requireNonNull(term, "term");
		Objects.requireNonNull(mode, "mode");
		regionId = Objects.requireNonNull(regionId, "regionId").trim();
		if (regionId.isEmpty()) throw new IllegalArgumentException("region ID must not be blank");
		if ((mode == OwnershipMode.MODIFIES || mode == OwnershipMode.RESIDUAL) && primaryMethod == null) {
			throw new IllegalArgumentException(mode + " ownership requires a primary method");
		}
	}
}

package info.openrocket.core.aerodynamics.physicsaero.force;

import java.util.List;
import java.util.Objects;
import info.openrocket.core.aerodynamics.physicsaero.api.MethodId;
import info.openrocket.core.aerodynamics.physicsaero.api.PhysicalOwner;
import info.openrocket.core.util.Coordinate;

public record ForceContribution(String componentId, PhysicalOwner owner, MethodId methodId,
		Coordinate forceBodyN, Coordinate intrinsicMomentBodyNm, Coordinate applicationPointM,
		String regionId, List<String> validityFlags, double confidence, double uncertainty,
		String fallbackReason) implements Comparable<ForceContribution> {
	public ForceContribution {
		componentId = Objects.requireNonNull(componentId, "componentId");
		Objects.requireNonNull(owner, "owner"); Objects.requireNonNull(methodId, "methodId");
		Objects.requireNonNull(forceBodyN, "forceBodyN"); Objects.requireNonNull(intrinsicMomentBodyNm, "intrinsicMomentBodyNm");
		Objects.requireNonNull(applicationPointM, "applicationPointM");
		regionId = Objects.requireNonNull(regionId, "regionId"); validityFlags = List.copyOf(validityFlags);
		if (confidence < 0 || confidence > 1 || uncertainty < 0) throw new IllegalArgumentException("invalid confidence/uncertainty");
	}
	@Override public int compareTo(ForceContribution o) {
		int c = componentId.compareTo(o.componentId); if (c != 0) return c;
		c = owner.term().compareTo(o.owner.term()); if (c != 0) return c;
		c = regionId.compareTo(o.regionId); if (c != 0) return c;
		return methodId.compareTo(o.methodId);
	}
}

package info.openrocket.core.aerodynamics.physicsaero.body;

import java.util.ArrayList;
import java.util.List;

public final class BodyMethodSelector {
	public Decision select(AxisymmetricBodySegment segment, double mach) {
		List<Candidate> candidates = new ArrayList<>();
		for (String method : segment.eligibleMethods()) {
			boolean valid = mach >= 1.0 && mach <= 5.0;
			candidates.add(new Candidate(method, valid, valid ? "GEOMETRY_AND_MACH_VALID" : "OUTSIDE_PERFECT_GAS_PHASE2_RANGE"));
			if (valid) return new Decision(method, "FIRST_VALID_METADATA_ORDER", candidates);
		}
		return new Decision(null, "NO_VALID_BODY_METHOD", candidates);
	}
	public record Candidate(String methodId, boolean valid, String reason) {}
	public record Decision(String selectedMethodId, String reason, List<Candidate> candidates) {
		public Decision { candidates = List.copyOf(candidates); }
		public boolean selected() { return selectedMethodId != null; }
	}
}

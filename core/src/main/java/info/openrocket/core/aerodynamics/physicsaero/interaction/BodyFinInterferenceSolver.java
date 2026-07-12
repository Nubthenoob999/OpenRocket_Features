package info.openrocket.core.aerodynamics.physicsaero.interaction;

import java.util.*;
import info.openrocket.core.aerodynamics.physicsaero.api.*;
import info.openrocket.core.aerodynamics.physicsaero.force.ForceContribution;
import info.openrocket.core.aerodynamics.physicsaero.geometry.*;
import info.openrocket.core.util.Coordinate;

/** Applies PNK once, after isolated loading, and emits only typed incremental contributions. */
public final class BodyFinInterferenceSolver {
	public List<ForceContribution> evaluate(AeroGeometry geometry, double mach, List<ForceContribution> isolatedFinContributions) {
		Map<String, AeroComponent> components = new HashMap<>(); geometry.components().forEach(c -> components.put(c.id(), c));
		List<ForceContribution> increments = new ArrayList<>(); PnkInterferenceModel model = new PnkInterferenceModel();
		Map<String, List<ForceContribution>> byRegion = new LinkedHashMap<>();
		for (ForceContribution contribution : isolatedFinContributions) byRegion.computeIfAbsent(contribution.regionId(), key -> new ArrayList<>()).add(contribution);
		for (List<ForceContribution> regionContributions : byRegion.values()) {
			ForceContribution isolated = regionContributions.stream().filter(c -> c.owner().term() == PhysicalTerm.FIN_LIFT
					&& c.owner().mode() == OwnershipMode.REPLACES).findFirst().orElse(null);
			if (isolated == null) continue;
			int separator = isolated.componentId().indexOf(":fin:"); String setId = separator < 0 ? isolated.componentId() : isolated.componentId().substring(0, separator);
			AeroComponent component = components.get(setId); if (component == null || component.finGeometry() == null) continue;
			double radiusOverSemispan = component.rootRadiusM() / (component.rootRadiusM() + component.finGeometry().spanM());
			Coordinate attachedForce = new Coordinate();
			for (ForceContribution contribution : regionContributions) if (contribution.owner().term() == PhysicalTerm.FIN_LIFT
					|| contribution.owner().term() == PhysicalTerm.BODY_UPWASH_NORMAL_FORCE
					|| contribution.owner().term() == PhysicalTerm.SHOCK_IMPINGEMENT_PRESSURE) {
				attachedForce = new Coordinate(attachedForce.x + contribution.forceBodyN().x,
						attachedForce.y + contribution.forceBodyN().y, attachedForce.z + contribution.forceBodyN().z);
			}
			double magnitude = attachedForce.length(); if (magnitude <= 1e-12) continue;
			PnkInterferenceModel.Result result = model.evaluate(mach, radiusOverSemispan, magnitude, 0);
			if (!result.valid()) continue; double scale = result.interferenceIncrementN() / magnitude;
			Coordinate increment = (Coordinate) attachedForce.multiply(scale);
			increments.add(new ForceContribution(isolated.componentId(), new PhysicalOwner(
					PhysicalTerm.BODY_FIN_INTERFERENCE_NORMAL_FORCE, OwnershipMode.MODIFIES, isolated.regionId(), isolated.methodId()),
					new MethodId(result.methodId()), increment, new Coordinate(), isolated.applicationPointM(), isolated.regionId(),
					List.of("PNK_INCREMENT_ONLY", result.provenance()), .65, .25, null));
		}
		return List.copyOf(increments);
	}
}

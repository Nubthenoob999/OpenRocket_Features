package info.openrocket.core.aerodynamics.physicsaero.interaction;

import java.util.List;
import info.openrocket.core.aerodynamics.physicsaero.api.*;
import info.openrocket.core.aerodynamics.physicsaero.force.ForceContribution;
import info.openrocket.core.util.Coordinate;

/** Builds only the PNK increment and names the isolated fin method it modifies. */
public final class InterferenceContributionAssembler {
	public ForceContribution normalIncrement(String componentId, String regionId, MethodId isolatedMethod,
			Coordinate forceBodyN, Coordinate applicationPointM, double confidence) {
		PhysicalOwner owner = new PhysicalOwner(PhysicalTerm.BODY_FIN_INTERFERENCE_NORMAL_FORCE,
				OwnershipMode.MODIFIES, regionId, isolatedMethod);
		return new ForceContribution(componentId, owner, new MethodId(PnkInterferenceModel.METHOD_ID), forceBodyN,
				new Coordinate(), applicationPointM, regionId, List.of("PNK_INCREMENT_ONLY"), confidence,
				1 - confidence, null);
	}
}

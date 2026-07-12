package info.openrocket.core.aerodynamics.physicsaero.force;
import info.openrocket.core.aerodynamics.physicsaero.boundarylayer.BoundaryLayerHistory;
import info.openrocket.core.util.Coordinate;
public final class ViscousContributionAssembler {
	private final SkinFrictionForceIntegrator integrator = new SkinFrictionForceIntegrator();
	public ForceContribution assemble(BoundaryLayerHistory history, Coordinate referencePointM, ContributionLedger ledger) {
		ForceContribution contribution = integrator.integrate(history, referencePointM); ledger.add(contribution); return contribution;
	}
}

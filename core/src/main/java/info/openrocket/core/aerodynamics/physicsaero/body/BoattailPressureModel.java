package info.openrocket.core.aerodynamics.physicsaero.body;

import info.openrocket.core.aerodynamics.physicsaero.api.PhysicalTerm;
import info.openrocket.core.aerodynamics.physicsaero.force.ForceContribution;

public final class BoattailPressureModel {
	public ForceContribution integrate(AxisymmetricBodySegment segment, AxisymmetricEdgeStateHistory history,
			double freestreamPressurePa) {
		return new ForebodyPressureIntegrator().integrate(segment, history, freestreamPressurePa,
				PhysicalTerm.BOATTAIL_PRESSURE_DRAG);
	}
	public boolean separationRiskPendingBoundaryLayer(AxisymmetricBodySegment segment, double mach) {
		return Math.abs(segment.turnAngleRad()) > Math.toRadians(12) || mach < 1.5;
	}
}

package info.openrocket.core.aerodynamics.physicsaero.boundarylayer;
public final class LeadingEdgeInitializer {
	public double thetaM(BoundaryLayerStation station) {
		double reS = station.streamwiseVelocityMS() * station.sM() / station.kinematicViscosityM2S();
		if (station.sM() <= 0 || reS <= 0) throw new BoundaryLayerException(BoundaryLayerException.Reason.NONPHYSICAL_STATE, "leading-edge initialization requires finite s");
		return 0.664 * station.sM() / Math.sqrt(reS);
	}
}

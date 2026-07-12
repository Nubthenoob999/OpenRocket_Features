package info.openrocket.core.aerodynamics.physicsaero.boundarylayer;
/** Regular Hiemenz-limit initialization using theta=0.292 sqrt(nu/a), H=2.216. */
public final class StagnationInitializer {
	public double thetaM(BoundaryLayerStation station, double edgeStrainRatePerS) {
		if (edgeStrainRatePerS <= 0 || station.radiusM() <= 0) throw new BoundaryLayerException(BoundaryLayerException.Reason.NONPHYSICAL_STATE, "stagnation initialization needs positive strain and nose radius");
		return 0.292 * Math.sqrt(station.kinematicViscosityM2S() / edgeStrainRatePerS);
	}
}

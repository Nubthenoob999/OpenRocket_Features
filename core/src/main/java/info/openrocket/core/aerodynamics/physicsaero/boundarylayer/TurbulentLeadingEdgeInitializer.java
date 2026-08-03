package info.openrocket.core.aerodynamics.physicsaero.boundarylayer;

/**
 * Fully turbulent flat-plate leading-edge initialization.
 *
 * <p>The momentum thickness follows the one-fifth-power integral relation
 * {@code theta/x = 0.036 Re_x^-1/5}.  It supplies Head's method with a
 * turbulent virtual origin when the user explicitly requests all-turbulent
 * flow; natural and tripped transition retain their laminar history.</p>
 */
public final class TurbulentLeadingEdgeInitializer {
	public static final String METHOD_ID = "ONE_FIFTH_POWER_TURBULENT_MOMENTUM_THICKNESS_V1";
	public static final double EQUILIBRIUM_SHAPE_FACTOR = 1.4;

	public double thetaM(BoundaryLayerStation station) {
		double reynoldsS = station.streamwiseVelocityMS() * station.sM()
				/ station.kinematicViscosityM2S();
		if (!(station.sM() > 0) || !(reynoldsS > 0)
				|| !Double.isFinite(reynoldsS)) {
			throw new BoundaryLayerException(BoundaryLayerException.Reason.NONPHYSICAL_STATE,
					"turbulent leading-edge initialization requires finite s");
		}
		return 0.036 * station.sM() / Math.pow(reynoldsS, 0.2);
	}
}

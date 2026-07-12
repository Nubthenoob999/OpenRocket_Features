package info.openrocket.core.aerodynamics.physicsaero.boundarylayer;

/** Axisymmetric Thwaites march conserving r^2 Ue^6 theta^2; stagnation is initialized separately. */
public final class AxisymmetricThwaitesModel extends ThwaitesLaminarModel {
	public static final String METHOD_ID = "thwaites-axisymmetric-v1";
	@Override public double nextThetaSquared(BoundaryLayerStation a, BoundaryLayerStation b, double thetaSquared) {
		if (a.radiusM() <= 0 || b.radiusM() <= 0) throw new BoundaryLayerException(BoundaryLayerException.Reason.NONPHYSICAL_STATE, "axisymmetric march reached zero radius outside stagnation initializer");
		double ds = b.sM() - a.sM(), ua = Math.max(a.streamwiseVelocityMS(), 1e-9), ub = Math.max(b.streamwiseVelocityMS(), 1e-9);
		double ia = a.kinematicViscosityM2S() * a.radiusM() * a.radiusM() * Math.pow(ua, 5);
		double ib = b.kinematicViscosityM2S() * b.radiusM() * b.radiusM() * Math.pow(ub, 5);
		double numerator = a.radiusM() * a.radiusM() * Math.pow(ua, 6) * thetaSquared + 0.45 * 0.5 * ds * (ia + ib);
		return numerator / (b.radiusM() * b.radiusM() * Math.pow(ub, 6));
	}
}

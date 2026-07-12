package info.openrocket.core.aerodynamics.physicsaero.boundarylayer;

/** Conservative station-to-station planar Thwaites march. */
public class ThwaitesLaminarModel {
	public static final String METHOD_ID = "thwaites-planar-1949-v1";
	public double nextThetaSquared(BoundaryLayerStation a, BoundaryLayerStation b, double thetaSquared) {
		double ds = b.sM() - a.sM();
		double ua = Math.max(a.streamwiseVelocityMS(), 1e-9), ub = Math.max(b.streamwiseVelocityMS(), 1e-9);
		double integral = 0.5 * ds * (a.kinematicViscosityM2S() * Math.pow(ua, 5) + b.kinematicViscosityM2S() * Math.pow(ub, 5));
		return (Math.pow(ua, 6) * thetaSquared + 0.45 * integral) / Math.pow(ub, 6);
	}
}

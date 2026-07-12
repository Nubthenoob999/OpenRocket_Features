package info.openrocket.core.aerodynamics.physicsaero.boundarylayer;

/** One station step of Head's theta,Q integral equations using a midpoint predictor. */
public final class HeadEntrainmentModel {
	public static final String METHOD_ID = "head-entrainment-1958-v1";
	public record State(double thetaM, double qM2S, double h, double h1, double cf) { }
	private final HeadShapeFactorClosure shape = new HeadShapeFactorClosure();
	private final TurbulentSkinFrictionClosure friction = new TurbulentSkinFrictionClosure();
	public State initialize(double theta, double displacement, double velocity, double reynoldsTheta) {
		double h = displacement / theta, h1 = shape.h1(h);
		return new State(theta, velocity * theta * h1, h, h1, friction.skinFriction(h, reynoldsTheta));
	}
	public State step(BoundaryLayerStation a, BoundaryLayerStation b, State state) {
		double ds = b.sM() - a.sM(), u = 0.5 * (a.streamwiseVelocityMS() + b.streamwiseVelocityMS());
		double du = (b.streamwiseVelocityMS() - a.streamwiseVelocityMS()) / ds;
		double h1 = state.qM2S() / (Math.max(u, 1e-9) * state.thetaM()), h = shape.h(h1);
		double reTheta = u * state.thetaM() / (0.5 * (a.kinematicViscosityM2S() + b.kinematicViscosityM2S()));
		double cf = friction.skinFriction(h, reTheta);
		double dTheta = cf * 0.5 - (2 + h) * state.thetaM() * du / Math.max(u, 1e-9);
		double dQ = u * 0.0299 * Math.pow(h1 - 3, -0.6169);
		double theta = state.thetaM() + ds * dTheta, q = state.qM2S() + ds * dQ;
		if (theta <= 0 || q <= 0) throw new BoundaryLayerException(BoundaryLayerException.Reason.NONPHYSICAL_STATE, "Head march produced nonphysical state");
		double outH1 = q / (Math.max(b.streamwiseVelocityMS(), 1e-9) * theta), outH = shape.h(outH1);
		return new State(theta, q, outH, outH1, friction.skinFriction(outH, b.streamwiseVelocityMS() * theta / b.kinematicViscosityM2S()));
	}
}

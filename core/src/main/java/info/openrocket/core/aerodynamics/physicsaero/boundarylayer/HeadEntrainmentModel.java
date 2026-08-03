package info.openrocket.core.aerodynamics.physicsaero.boundarylayer;

/** One station step of Head's theta,Q integral equations using a midpoint predictor. */
public final class HeadEntrainmentModel {
	public static final String METHOD_ID = "head-entrainment-1958-v1";
	private static final double MAXIMUM_RELATIVE_STREAMWISE_STEP = 0.05;
	public record State(double thetaM, double qM2S, double h, double h1, double cf) { }
	private final HeadShapeFactorClosure shape = new HeadShapeFactorClosure();
	private final TurbulentSkinFrictionClosure friction = new TurbulentSkinFrictionClosure();
	public State initialize(double theta, double displacement, double velocity, double reynoldsTheta) {
		double h = displacement / theta, h1 = shape.h1(h);
		return new State(theta, velocity * theta * h1, h, h1, friction.skinFriction(h, reynoldsTheta));
	}
	public State step(BoundaryLayerStation a, BoundaryLayerStation b, State state) {
		double totalDs = b.sM() - a.sM();
		if (!(totalDs > 0)) {
			throw new BoundaryLayerException(BoundaryLayerException.Reason.NONPHYSICAL_STATE,
					"Head march requires increasing surface distance");
		}
		double s = a.sM();
		State current = state;
		while (s < b.sM()) {
			double maximumStep = MAXIMUM_RELATIVE_STREAMWISE_STEP * Math.max(s, 1.0e-8);
			double nextS = Math.min(b.sM(), s + maximumStep);
			double fraction0 = (s - a.sM()) / totalDs;
			double fraction1 = (nextS - a.sM()) / totalDs;
			double u0 = lerp(a.streamwiseVelocityMS(), b.streamwiseVelocityMS(), fraction0);
			double u1 = lerp(a.streamwiseVelocityMS(), b.streamwiseVelocityMS(), fraction1);
			double nu0 = lerp(a.kinematicViscosityM2S(), b.kinematicViscosityM2S(), fraction0);
			double nu1 = lerp(a.kinematicViscosityM2S(), b.kinematicViscosityM2S(), fraction1);
			current = step(s, u0, nu0, nextS, u1, nu1, current);
			s = nextS;
		}
		return current;
	}

	private State step(double s0, double u0, double nu0, double s1, double u1,
			double nu1, State state) {
		double ds = s1 - s0, u = 0.5 * (u0 + u1);
		double du = (u1 - u0) / ds;
		double h1 = state.qM2S() / (Math.max(u, 1e-9) * state.thetaM()), h = shape.h(h1);
		double reTheta = u * state.thetaM() / (0.5 * (nu0 + nu1));
		double cf = friction.skinFriction(h, reTheta);
		double dTheta = cf * 0.5 - (2 + h) * state.thetaM() * du / Math.max(u, 1e-9);
		double dQ = u * 0.0299 * Math.pow(h1 - 3, -0.6169);
		double theta = state.thetaM() + ds * dTheta, q = state.qM2S() + ds * dQ;
		if (theta <= 0 || q <= 0) throw new BoundaryLayerException(BoundaryLayerException.Reason.NONPHYSICAL_STATE, "Head march produced nonphysical state");
		double outH1 = q / (Math.max(u1, 1e-9) * theta), outH = shape.h(outH1);
		return new State(theta, q, outH, outH1,
				friction.skinFriction(outH, u1 * theta / nu1));
	}

	private static double lerp(double a, double b, double fraction) {
		return a + fraction * (b - a);
	}
}

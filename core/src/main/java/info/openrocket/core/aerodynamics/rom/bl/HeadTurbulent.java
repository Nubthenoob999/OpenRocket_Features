package info.openrocket.core.aerodynamics.rom.bl;

import info.openrocket.core.aerodynamics.rom.util.RK4Integrator;

public final class HeadTurbulent {

	private static final double MIN_EDGE_VELOCITY = 1.0e-3;
	private static final double MIN_MOMENTUM_THICKNESS = 1.0e-7;
	private static final double MIN_SHAPE_FACTOR = 1.35;
	private static final double MAX_SHAPE_FACTOR = 3.0;
	private static final double SEPARATION_SHAPE_FACTOR = 2.8;

	private HeadTurbulent() {
	}

	public static BoundaryLayerState advance(BoundaryLayerState state, double station, double edgeVelocity,
			double velocityGradient, double kinematicViscosity, double stepSize) {
		if (state == null || !state.isValid() || !Double.isFinite(station) || !Double.isFinite(edgeVelocity)
				|| !Double.isFinite(velocityGradient) || !Double.isFinite(kinematicViscosity)
				|| !Double.isFinite(stepSize) || kinematicViscosity <= 0.0 || stepSize < 0.0) {
			return BoundaryLayerState.invalid();
		}
		if (stepSize == 0.0) {
			return state;
		}

		double[] current = {
			Math.max(state.getMomentumThickness(), MIN_MOMENTUM_THICKNESS),
			clamp(state.getShapeFactor(), MIN_SHAPE_FACTOR, MAX_SHAPE_FACTOR)
		};

		double[] next = RK4Integrator.step((x, values, derivative) -> {
			double localVelocity = Math.max(MIN_EDGE_VELOCITY, edgeVelocity + velocityGradient * (x - station));
			double momentumThickness = Math.max(values[0], MIN_MOMENTUM_THICKNESS);
			double shapeFactor = clamp(values[1], MIN_SHAPE_FACTOR, MAX_SHAPE_FACTOR);
			double reynoldsTheta = localVelocity * momentumThickness / kinematicViscosity;
			double skinFrictionCoefficient = turbulentSkinFriction(reynoldsTheta, shapeFactor);
			double pressureGradientParameter = -momentumThickness * velocityGradient / localVelocity;
			double targetShapeFactor = clamp(1.38 + 18.0 * Math.max(0.0, pressureGradientParameter)
					- 6.0 * Math.max(0.0, -pressureGradientParameter), MIN_SHAPE_FACTOR, SEPARATION_SHAPE_FACTOR);
			double relaxationLength = Math.max(20.0 * momentumThickness, stepSize);

			derivative[0] = 0.5 * skinFrictionCoefficient
					- (shapeFactor + 2.0) * momentumThickness * velocityGradient / localVelocity;
			derivative[0] = Math.max(-0.8 * momentumThickness / stepSize, derivative[0]);
			derivative[1] = (targetShapeFactor - shapeFactor) / relaxationLength;
		}, station, current, stepSize);

		double nextEdgeVelocity = Math.max(MIN_EDGE_VELOCITY, edgeVelocity + velocityGradient * stepSize);
		double momentumThickness = Math.max(next[0], MIN_MOMENTUM_THICKNESS);
		double shapeFactor = clamp(next[1], MIN_SHAPE_FACTOR, MAX_SHAPE_FACTOR);
		double reynoldsTheta = nextEdgeVelocity * momentumThickness / kinematicViscosity;
		double skinFrictionCoefficient = turbulentSkinFriction(reynoldsTheta, shapeFactor);
		double stiffnessIndicator = ThwaitesLaminar.stiffnessIndicator(momentumThickness, shapeFactor,
				skinFrictionCoefficient, nextEdgeVelocity, velocityGradient);
		boolean separated = shapeFactor >= SEPARATION_SHAPE_FACTOR
				|| skinFrictionCoefficient < 1.0e-6 || nextEdgeVelocity <= 0.25;
		boolean valid = Double.isFinite(momentumThickness) && Double.isFinite(shapeFactor)
				&& Double.isFinite(skinFrictionCoefficient) && momentumThickness >= MIN_MOMENTUM_THICKNESS;

		return new BoundaryLayerState(momentumThickness, shapeFactor, true, separated,
				skinFrictionCoefficient, stiffnessIndicator, valid);
	}

	private static double turbulentSkinFriction(double reynoldsTheta, double shapeFactor) {
		double boundedReynoldsTheta = Math.max(reynoldsTheta, 100.0);
		double base = 0.246 * Math.pow(10.0, -0.678 * clamp(shapeFactor, MIN_SHAPE_FACTOR, MAX_SHAPE_FACTOR));
		return clamp(base / Math.pow(boundedReynoldsTheta, 0.268), 1.0e-6, 0.02);
	}

	private static double clamp(double value, double lowerBound, double upperBound) {
		return Math.max(lowerBound, Math.min(upperBound, value));
	}
}

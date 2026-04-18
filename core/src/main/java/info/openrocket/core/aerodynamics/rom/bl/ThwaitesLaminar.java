package info.openrocket.core.aerodynamics.rom.bl;

import info.openrocket.core.aerodynamics.rom.util.RK4Integrator;

public final class ThwaitesLaminar {

	private static final double MIN_EDGE_VELOCITY = 1.0e-3;
	private static final double LAMINAR_SEPARATION_LAMBDA = -0.09;

	private ThwaitesLaminar() {
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

		double thetaSquared = state.getMomentumThickness() * state.getMomentumThickness();
		double nextThetaSquared = RK4Integrator.step((x, currentThetaSquared) -> {
			double localVelocity = Math.max(MIN_EDGE_VELOCITY, edgeVelocity + velocityGradient * (x - station));
			double lambda = currentThetaSquared * velocityGradient / kinematicViscosity;
			return kinematicViscosity * (0.45 - 6.0 * lambda) / localVelocity;
		}, station, thetaSquared, stepSize);
		nextThetaSquared = Math.max(0.0, nextThetaSquared);

		double nextStation = station + stepSize;
		double nextEdgeVelocity = Math.max(MIN_EDGE_VELOCITY, edgeVelocity + velocityGradient * stepSize);
		double momentumThickness = Math.sqrt(nextThetaSquared);
		double lambda = nextThetaSquared * velocityGradient / kinematicViscosity;
		double shapeFactor = laminarShapeFactor(lambda);
		double reynoldsX = nextEdgeVelocity * Math.max(nextStation, stepSize) / kinematicViscosity;
		double skinFrictionCoefficient = laminarSkinFriction(lambda, reynoldsX);
		double stiffnessIndicator = stiffnessIndicator(momentumThickness, shapeFactor, skinFrictionCoefficient,
				nextEdgeVelocity, velocityGradient);
		boolean separated = lambda <= LAMINAR_SEPARATION_LAMBDA || shapeFactor >= 3.5;
		boolean valid = Double.isFinite(momentumThickness) && Double.isFinite(shapeFactor)
				&& Double.isFinite(skinFrictionCoefficient) && momentumThickness >= 0.0;

		return new BoundaryLayerState(momentumThickness, shapeFactor, false, separated,
				skinFrictionCoefficient, stiffnessIndicator, valid);
	}

	static double laminarShapeFactor(double lambda) {
		if (lambda < 0.0) {
			double denominator = lambda + 0.14;
			if (Math.abs(denominator) < 1.0e-8) {
				return 3.5;
			}
			return clamp(2.088 + 0.0731 / denominator, 2.0, 3.5);
		}
		return clamp(2.61 - 3.75 * lambda + 5.24 * lambda * lambda, 2.0, 2.61);
	}

	static double stiffnessIndicator(double momentumThickness, double shapeFactor, double skinFrictionCoefficient,
			double edgeVelocity, double velocityGradient) {
		double shearScale = 0.5 * Math.max(skinFrictionCoefficient, 1.0e-6);
		double pressureScale = Math.abs((shapeFactor + 2.0) * momentumThickness * velocityGradient
				/ Math.max(edgeVelocity, MIN_EDGE_VELOCITY));
		return pressureScale / shearScale;
	}

	private static double laminarSkinFriction(double lambda, double reynoldsX) {
		double correction = clamp(1.0 + 3.0 * lambda, 0.1, 1.5);
		return 0.664 * correction / Math.sqrt(Math.max(reynoldsX, 1.0));
	}

	private static double clamp(double value, double lowerBound, double upperBound) {
		return Math.max(lowerBound, Math.min(upperBound, value));
	}
}

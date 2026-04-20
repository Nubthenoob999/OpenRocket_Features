package info.openrocket.core.aerodynamics.rom.bl;

import info.openrocket.core.aerodynamics.rom.flow.EdgeState;
import info.openrocket.core.aerodynamics.rom.flow.FlowState;
import info.openrocket.core.aerodynamics.rom.math.ENTransition;
import info.openrocket.core.aerodynamics.rom.math.EckertReference;
import info.openrocket.core.aerodynamics.rom.math.WhiteChristophCf;

/**
 * Phase I boundary-layer scaffold driven by the ROM math utilities rather than
 * purely heuristic fits. The interface is intentionally unchanged so the
 * current calculator pipeline remains stable.
 */
public class BoundaryLayerMarcher {
	private static final double AIR_GAMMA = 1.4;
	private static final double DEFAULT_N_CRIT = 9.0;
	private static final double LAMINAR_SHAPE_FACTOR_BASE = 2.59;
	private static final double TURBULENT_SHAPE_FACTOR_BASE = 1.40;
	private static final double LAMINAR_SEPARATION_THRESHOLD = 3.50;
	private static final double TURBULENT_SEPARATION_THRESHOLD = 2.80;
	private static final int MIN_STEPS = 8;
	private static final int MAX_STEPS = 48;

	public BoundaryLayerState march(FlowState flowState, EdgeState edgeState, double pathLength,
			double referenceLength) {
		double length = Math.max(1.0e-6, pathLength);
		double refLength = Math.max(1.0e-6, referenceLength);

		double ue = Math.max(1.0e-3, edgeState.getEdgeVelocity());
		double nu = effectiveKinematicViscosity(flowState, edgeState);
		
		int steps = clamp((int) Math.ceil(length / Math.max(refLength / 12.0, 1.0e-3)), MIN_STEPS, MAX_STEPS);
		double dx = length / steps;

		double theta = Math.max(5.0e-7, 0.664 * Math.sqrt(nu * dx / ue));
		double shapeFactor = LAMINAR_SHAPE_FACTOR_BASE;

		double cf = 0.664 / Math.sqrt(Math.max(1.0, ue * dx / nu));
		
		boolean turbulent = false;
		boolean transitioned = false;
		boolean separated = false;
		double stiffness = 0.0;
		double amplificationFactor = 0.0;
		StringBuilder notes = new StringBuilder();

		for (int i = 1; i <= steps; i++) {
			double x = i * dx;
			double gradient = safeGradient(edgeState);
			
			double reTheta = ue * theta / Math.max(1.0e-9, nu);
			double reX = ue * x / Math.max(1.0e-9, nu);
			
			double deltaReTheta = Math.max(0.0, ue * dx / Math.max(1.0e-9, nu));
			double adiabaticWallTemperature = EckertReference.adiabaticWallTemp(flowState.getStaticTemperature(), edgeState.getEdgeMach(), AIR_GAMMA, turbulent);

			if (!turbulent) {
				amplificationFactor = ENTransition.stepN(amplificationFactor, shapeFactor, deltaReTheta);
			}
			if (!turbulent && shouldTransition(reTheta, reX)) {
				turbulent = true;
				transitioned = true;
				shapeFactor = Math.min(shapeFactor, 1.55);
				notes.append("michel-transition;");
			}

			// M9: e^N transition check — trigger transition when amplification
			// factor reaches the critical N value (typically 9.0)
			if (!turbulent && amplificationFactor >= DEFAULT_N_CRIT) {
				turbulent = true;
				transitioned = true;
				shapeFactor = Math.min(shapeFactor, 1.55);
				notes.append("eN-transition;");
			}

			if (turbulent) {
				theta = integrateTurbulentTheta(theta, ue, nu, gradient, dx);
				shapeFactor = turbulentShapeFactor(gradient, theta, ue, nu);
				cf = WhiteChristophCf.eckertCorrectedCf(reX, flowState.getStaticTemperature(), adiabaticWallTemperature, adiabaticWallTemperature, edgeState.getEdgeMach(), AIR_GAMMA);
				
				separated = separated || shapeFactor > TURBULENT_SEPARATION_THRESHOLD;
			} else {
				theta = integrateLaminarTheta(theta, ue, nu, gradient, dx);
				shapeFactor = laminarShapeFactor(theta, gradient, nu);
				cf = laminarSkinFriction(ue, x, nu);
				separated = separated || shapeFactor > LAMINAR_SEPARATION_THRESHOLD;
			}

			theta = clampPositive(theta, 1.0e-7);
			cf = Math.max(1.0e-6, cf);
			stiffness = Math.max(stiffness, stiffnessIndicator(gradient, shapeFactor, separated));
		}

		double deltaStar = clampPositive(shapeFactor * theta, theta);
		boolean valid = Double.isFinite(theta) && Double.isFinite(deltaStar) && Double.isFinite(shapeFactor) && Double.isFinite(cf);
		
		if (!valid) {
			return new BoundaryLayerState(0.0, 0.0, 10.0, 1.0e-6, turbulent, transitioned, true, false, 1.0, append(notes, "invalid-state"));
		}

		return new BoundaryLayerState(deltaStar, theta, shapeFactor, cf, turbulent, transitioned, separated, true, clamp01(stiffness), notes.toString());
	}

	private static double integrateLaminarTheta(double theta, double ue, double nu, double gradient, double dx) {
		double theta2 = theta * theta;
		
		double k1 = laminarTheta2Derivative(theta2, ue, nu, gradient);
		double k2 = laminarTheta2Derivative(theta2 + 0.5 * dx * k1, ue, nu, gradient);
		double k3 = laminarTheta2Derivative(theta2 + 0.5 * dx * k2, ue, nu, gradient);
		double k4 = laminarTheta2Derivative(theta2 + dx * k3, ue, nu, gradient);
		
		double nextTheta2 = theta2 + (dx / 6.0) * (k1 + 2.0 * k2 + 2.0 * k3 + k4);
		return Math.sqrt(clampPositive(nextTheta2, 1.0e-14));
	}

	private static double laminarTheta2Derivative(double theta2, double ue, double nu, double gradient) {
		double lambda = (theta2 * gradient) / Math.max(1.0e-9, nu);
		double boundedLambda = Math.max(-0.09, Math.min(0.20, lambda));
		return Math.max(1.0e-12, (0.45 - 6.0 * boundedLambda) * nu / Math.max(1.0e-9, ue));
	}

	private static double integrateTurbulentTheta(double theta, double ue, double nu, double gradient, double dx) {
		double reTheta = Math.max(1.0, ue * theta / Math.max(1.0e-9, nu));
		double cf = WhiteChristophCf.ludwiegTillmann(reTheta, TURBULENT_SHAPE_FACTOR_BASE);
		double growth = 0.5 * cf + Math.max(0.0, -gradient) * theta / Math.max(1.0e-9, ue);
		return theta + dx * Math.max(1.0e-7, growth);
	}

	private static boolean shouldTransition(double reTheta, double reX) {
		return reX > 0.0 && reTheta >= ENTransition.michelTransitionReTheta(reX);
	}

	private static double laminarShapeFactor(double theta, double gradient, double nu) {
		double lambda = theta * theta * gradient / Math.max(1.0e-9, nu);
		return clamp(2.30 + 12.0 * Math.max(0.0, -lambda), 2.10, 3.80);
	}

	private static double turbulentShapeFactor(double gradient, double theta, double ue, double nu) {
		double reTheta = Math.max(1.0, ue * theta / Math.max(1.0e-9, nu));
		double pressureTerm = Math.max(0.0, -gradient) * theta / Math.max(1.0e-9, ue);
		return clamp(1.35 + 4.0 * pressureTerm + 30.0 / Math.sqrt(reTheta), 1.25, 2.80);
	}

	private static double laminarSkinFriction(double ue, double x, double nu) {
		double reX = Math.max(1.0, ue * Math.max(x, 1.0e-6) / Math.max(1.0e-9, nu));
		return 0.664 / Math.sqrt(reX);
	}

	private static double effectiveKinematicViscosity(FlowState flowState, EdgeState edgeState) {
		double staticTemp = Math.max(1.0, flowState.getStaticTemperature());
		double referenceTemp = EckertReferenceTemperature.referenceTemperature(staticTemp, edgeState.getEdgeMach());
		double ratio = EckertReferenceTemperature.viscosityRatio(staticTemp, referenceTemp);
		return Math.max(1.0e-7, flowState.getViscosity() * ratio);
	}

	private static double safeGradient(EdgeState edgeState) {
		return edgeState.getVelocityGradient();
	}

	private static double stiffnessIndicator(double gradient, double shapeFactor, boolean separated) {
		double gradientTerm = Math.max(0.0, -gradient) * 0.4;
		double shapeFactorTerm = Math.max(0.0, shapeFactor - 1.8) / 2.0;
		double separationTerm = separated ? 0.4 : 0.0;
		return gradientTerm + shapeFactorTerm + separationTerm;
	}

	private static String append(StringBuilder notes, String value) {
		if (notes.length() > 0) {
			notes.append(value);
			return notes.toString();
		}
		return value;
	}

	private static double clampPositive(double value, double fallback) {
		return Double.isFinite(value) && value > 0.0 ? value : fallback;
	}

	private static double clamp01(double value) {
		return clamp(value, 0.0, 1.0);
	}

	private static double clamp(double value, double min, double max) {
		if (!Double.isFinite(value)) {
			return min;
		}
		return Math.max(min, Math.min(max, value));
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}

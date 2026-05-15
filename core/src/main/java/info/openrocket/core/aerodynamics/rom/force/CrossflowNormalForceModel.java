package info.openrocket.core.aerodynamics.rom.force;

import info.openrocket.core.aerodynamics.rom.flow.FlowState;
import info.openrocket.core.aerodynamics.rom.flow.MachTransitionMap;
import info.openrocket.core.aerodynamics.rom.geometry.GeometryFeatures;
import info.openrocket.core.aerodynamics.rom.math.JorgensenCrossflow;

public final class CrossflowNormalForceModel {
	private static final double START_ALPHA_DEG = 5.0;
	private static final double FULL_ALPHA_DEG = 12.0;
	private static final double EFFICIENCY = 0.90;
	private static final double CP_FRACTION_OF_BODY_LENGTH = 0.55;

	private CrossflowNormalForceModel() {
	}

	public static Result evaluate(GeometryFeatures geometry, FlowState flowState, double momentReferenceX) {
		if (geometry == null || flowState == null) {
			return Result.ZERO;
		}
		double alphaRad = flowState.getAngleOfAttackRad();
		double alphaDeg = Math.abs(flowState.getAngleOfAttackDeg());
		double ramp = alphaRamp(alphaDeg);
		if (ramp <= 0.0) {
			return Result.ZERO;
		}
		double planformArea = JorgensenCrossflow.planformArea(geometry);
		double referenceArea = Math.max(geometry.getReferenceArea(), 1.0e-12);
		double deltaCnMagnitude = JorgensenCrossflow.deltaCN(Math.abs(alphaRad), flowState.getMach(),
				planformArea, referenceArea, EFFICIENCY);
		double deltaCn = Math.signum(alphaRad) * ramp * deltaCnMagnitude;
		if (!Double.isFinite(deltaCn) || Math.abs(deltaCn) < 1.0e-12) {
			return Result.ZERO;
		}
		double xCp = CP_FRACTION_OF_BODY_LENGTH * Math.max(0.0, geometry.getBodyLength());
		double referenceLength = Math.max(geometry.getReferenceLength(), 1.0e-9);
		double deltaCm = -deltaCn * (xCp - momentReferenceX) / referenceLength;
		if (!Double.isFinite(deltaCm)) {
			deltaCm = 0.0;
		}
		return new Result(deltaCn, deltaCm, xCp, true);
	}

	public static boolean isActive(GeometryFeatures geometry, FlowState flowState) {
		return evaluate(geometry, flowState, 0.0).active();
	}

	private static double alphaRamp(double alphaDeg) {
		if (!Double.isFinite(alphaDeg) || alphaDeg <= START_ALPHA_DEG) {
			return 0.0;
		}
		double t = (alphaDeg - START_ALPHA_DEG) / (FULL_ALPHA_DEG - START_ALPHA_DEG);
		return MachTransitionMap.cubicHermiteUnit(t);
	}

	public record Result(double deltaCN, double deltaCm, double xCp, boolean active) {
		private static final Result ZERO = new Result(0.0, 0.0, 0.0, false);
	}
}

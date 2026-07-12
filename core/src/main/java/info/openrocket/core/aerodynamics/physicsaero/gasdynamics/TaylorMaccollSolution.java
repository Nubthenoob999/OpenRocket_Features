package info.openrocket.core.aerodynamics.physicsaero.gasdynamics;

import info.openrocket.core.aerodynamics.physicsaero.flow.GasState;
import info.openrocket.core.aerodynamics.physicsaero.flow.TotalState;

public record TaylorMaccollSolution(double freestreamMach, double coneHalfAngleRad,
		double shockAngleRad, GasState postShockState, GasState wallState,
		TotalState downstreamTotalState, double wallPressureCoefficient,
		double totalPressureRatio, ShockSolution.Attachment attachment,
		double wallTangencyResidual, int odeSteps, String methodId) {
	public static final String METHOD_ID = "TAYLOR_MACCOLL_PERFECT_GAS_V1";
	public boolean attached() { return attachment != ShockSolution.Attachment.DETACHED; }
	public static TaylorMaccollSolution detached(double mach, double coneAngle) {
		return new TaylorMaccollSolution(mach, coneAngle, Double.NaN, null, null, null,
				Double.NaN, Double.NaN, ShockSolution.Attachment.DETACHED, Double.NaN, 0, METHOD_ID);
	}
}

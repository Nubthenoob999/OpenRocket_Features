package info.openrocket.core.aerodynamics.physicsaero.boundarylayer;

import info.openrocket.core.aerodynamics.physicsaero.roughness.RoughnessRegime;
import info.openrocket.core.aerodynamics.physicsaero.transition.TransitionState;

public record BoundaryLayerState(double thetaM, double displacementThicknessM, double delta99M,
		double shapeFactor, double entrainmentShapeFactor, double skinFrictionCoefficient,
		double wallShearPa, double reynoldsS, double reynoldsTheta, double thwaitesLambda,
		double intermittency, TransitionState transition, AttachedFlowHealth health,
		RoughnessRegime roughnessRegime, double wallTemperatureK, String methodId) {
	public BoundaryLayerState {
		if (transition == null || health == null || roughnessRegime == null || methodId == null || methodId.isBlank()
				|| !finite(thetaM, displacementThicknessM, delta99M, shapeFactor, entrainmentShapeFactor,
				skinFrictionCoefficient, wallShearPa, reynoldsS, reynoldsTheta, thwaitesLambda, intermittency, wallTemperatureK)
				|| thetaM <= 0 || displacementThicknessM <= 0 || delta99M <= 0 || shapeFactor <= 1
				|| skinFrictionCoefficient < 0 || wallShearPa < 0 || intermittency < 0 || intermittency > 1)
			throw new IllegalArgumentException("nonphysical boundary-layer state");
	}
	private static boolean finite(double... values) { for (double v : values) if (!Double.isFinite(v)) return false; return true; }
}

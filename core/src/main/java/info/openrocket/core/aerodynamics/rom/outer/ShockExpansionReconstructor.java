package info.openrocket.core.aerodynamics.rom.outer;

import info.openrocket.core.aerodynamics.rom.flow.EdgeState;
import info.openrocket.core.aerodynamics.rom.flow.FlowState;
import info.openrocket.core.aerodynamics.rom.geometry.GeometryFeatures;
import info.openrocket.core.aerodynamics.rom.geometry.PathlineSeed;
import info.openrocket.core.aerodynamics.rom.math.GasDynamics;

public class ShockExpansionReconstructor implements OuterFlowReconstructor {
	private static final double AIR_GAMMA = 1.4;

	@Override
	public EdgeState reconstruct(PathlineSeed seed, GeometryFeatures geometry, FlowState flowState) {
		double radius = Math.max(1.0e-6, geometry.radiusAt(seed.getX()));
		double drdx = geometry.areaSlopeAt(seed.getX()) / Math.max(1.0e-6, 2.0 * Math.PI * radius);
		double localIncidence = flowState.getAngleOfAttackRad() + Math.atan(drdx);
		double deflection = clamp(localIncidence, -Math.toRadians(25.0), Math.toRadians(25.0));
		double freestreamMach = Math.max(1.2001, flowState.getMach());

		double cp;
		double edgeMach;
		double edgeVelocity;
		if (deflection >= 0.0) {
			double shockAngle = GasDynamics.obliqueShockAngle(freestreamMach, deflection, AIR_GAMMA);
			if (!Double.isFinite(shockAngle)) {
				double cpMax = GasDynamics.cpMax(freestreamMach, AIR_GAMMA);
				cp = GasDynamics.modifiedNewtonianCp(Math.min(Math.PI / 2.0, deflection), cpMax);
				edgeVelocity = flowState.getVelocity() * Math.sqrt(Math.max(0.05, 1.0 - 0.4 * cp));
				edgeMach = edgeVelocity / Math.max(1.0e-6, flowState.getSpeedOfSound());
			} else {
				double pressureRatio = GasDynamics.obliqueShockPressureRatio(freestreamMach, shockAngle, AIR_GAMMA);
				double temperatureRatio = GasDynamics.obliqueShockTempRatio(freestreamMach, shockAngle, AIR_GAMMA);
				edgeMach = GasDynamics.obliqueShockMach2(freestreamMach, shockAngle, deflection, AIR_GAMMA);
				double edgeSoundSpeed = flowState.getSpeedOfSound() * Math.sqrt(Math.max(0.05, temperatureRatio));
				edgeVelocity = edgeMach * edgeSoundSpeed;
				cp = 2.0 * (pressureRatio - 1.0) / (AIR_GAMMA * freestreamMach * freestreamMach);
			}
		} else {
			double nu1 = GasDynamics.prandtlMeyerAngle(freestreamMach, AIR_GAMMA);
			double edgeNu = nu1 + Math.abs(deflection);
			edgeMach = GasDynamics.prandtlMeyerMach(edgeNu, AIR_GAMMA);
			double pressureRatio = GasDynamics.isentropicPressureRatio(edgeMach, AIR_GAMMA)
					/ GasDynamics.isentropicPressureRatio(freestreamMach, AIR_GAMMA);
			double temperatureRatio = GasDynamics.isentropicTempRatio(edgeMach, AIR_GAMMA)
					/ GasDynamics.isentropicTempRatio(freestreamMach, AIR_GAMMA);
			double edgeSoundSpeed = flowState.getSpeedOfSound() * Math.sqrt(Math.max(0.05, temperatureRatio));
			edgeVelocity = edgeMach * edgeSoundSpeed;
			cp = 2.0 * (pressureRatio - 1.0) / (AIR_GAMMA * freestreamMach * freestreamMach);
		}

		return new EdgeState(edgeVelocity, edgeMach, clamp(cp, -1.50, 2.00), localIncidence,
				-drdx * flowState.getVelocity(), true);
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}

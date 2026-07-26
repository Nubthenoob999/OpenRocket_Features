package info.openrocket.core.simulation;

import info.openrocket.core.aerodynamics.FlightConditions;
import info.openrocket.core.motor.ThrustCurveMotor;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.util.MathUtil;

/**
 * Restores the ambient-pressure term for thrust curves referenced to sea-level
 * static pressure when an explicit nozzle exit diameter is available.
 */
final class AltitudePressureThrustModel {
	static final String METHOD_ID = "SEA_LEVEL_REFERENCED_NOZZLE_PRESSURE_THRUST_V1";
	static final double SEA_LEVEL_REFERENCE_PRESSURE_PA = 101_325.0;

	private AltitudePressureThrustModel() {
	}

	static double correction(SimulationStatus status, FlightConditions conditions,
			MotorClusterState motorState, double clusterThrustN) {
		if (!(clusterThrustN > 0) || conditions == null
				|| conditions.getAtmosphericConditions() == null
				|| !(motorState.getMotor() instanceof ThrustCurveMotor)
				|| !(motorState.getMount() instanceof RocketComponent mount)) {
			return 0;
		}
		double diameterM = status.getSimulationConditions()
				.getNozzleExitDiameterForStage(mount.getStageNumber());
		return correction(SEA_LEVEL_REFERENCE_PRESSURE_PA,
				conditions.getAtmosphericConditions().getPressure(), diameterM,
				motorState.getConfig().getMotorCount());
	}

	static double correction(double referencePressurePa, double ambientPressurePa,
			double exitDiameterM, int nozzleCount) {
		if (!Double.isFinite(referencePressurePa) || !Double.isFinite(ambientPressurePa)
				|| !Double.isFinite(exitDiameterM) || !(referencePressurePa > 0)
				|| ambientPressurePa < 0 || !(exitDiameterM > 0) || nozzleCount <= 0) {
			return 0;
		}
		double pressureDeltaPa = referencePressurePa - ambientPressurePa;
		if (!(pressureDeltaPa > 0)) return 0;
		double exitAreaM2 = Math.PI * MathUtil.pow2(exitDiameterM / 2);
		return nozzleCount * pressureDeltaPa * exitAreaM2;
	}
}

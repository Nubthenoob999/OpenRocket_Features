package info.openrocket.core.simulation;

import info.openrocket.core.aerodynamics.AerodynamicCalculator;
import info.openrocket.core.aerodynamics.SimulationAerodynamicsContext;
import info.openrocket.core.aerodynamics.SimulationAwareAerodynamicCalculator;

final class SimulationAerodynamicsContextHelper {
	private SimulationAerodynamicsContextHelper() { }

	static void apply(SimulationStatus status) {
		AerodynamicCalculator calculator = status.getSimulationConditions().getAerodynamicCalculator();
		if (!(calculator instanceof SimulationAwareAerodynamicCalculator aware)) return;
		double thrust = 0;
		for (MotorClusterState motor : status.getActiveMotors()) {
			thrust += Math.max(0, motor.getThrust(status.getSimulationTime()));
		}
		boolean powered = thrust > 0;
		double altitude = status.getRocketPosition().getZ()
				+ status.getSimulationConditions().getLaunchSite().getAltitude();
		double ambient = status.getSimulationConditions().getAtmosphericModel()
				.getConditions(altitude).getPressure();
		aware.setSimulationAerodynamicsContext(new SimulationAerodynamicsContext(
				status.getSimulationTime(), powered, thrust, powered ? 1 : 0, ambient));
	}
}

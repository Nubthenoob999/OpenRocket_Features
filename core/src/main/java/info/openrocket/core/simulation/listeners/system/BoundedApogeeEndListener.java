package info.openrocket.core.simulation.listeners.system;

import info.openrocket.core.simulation.FlightEvent;
import info.openrocket.core.simulation.SimulationStatus;
import info.openrocket.core.simulation.listeners.AbstractSimulationListener;

/**
 * Ends an apogee-only simulation with a bounded ballistic altitude remainder.
 *
 * <p>The listener refines the integration step below 2 m/s vertical speed and
 * stops once a drag-free ballistic trajectory could gain no more than 0.10 m.
 * Drag can only reduce that remainder.  This prevents the wind-angle coordinate
 * singularity at zero airspeed from requiring high-incidence aerodynamics in an
 * apogee benchmark while bounding the reported altitude truncation.</p>
 */
public final class BoundedApogeeEndListener extends AbstractSimulationListener {
	public static final double MAX_UNRESOLVED_APOGEE_HEIGHT_M = 0.10;
	private static final double CONSERVATIVE_GRAVITY_M_S2 = 9.5;
	private static final double APOGEE_LOCALIZATION_TIMESTEP_S = 0.001;
	private boolean observedAscent;

	@Override
	public boolean preStep(SimulationStatus status) {
		double verticalVelocity = status.getRocketVelocity().getZ();
		// Arm only after an unmistakable ascent.  A low-thrust vehicle can pass
		// through 1 m/s just after liftoff, where the ballistic-height criterion is
		// also small but is not an apogee condition.
		if (shouldArm(status.isLiftoff(), verticalVelocity)) observedAscent = true;
		if (!observedAscent) return true;
		if (verticalVelocity < 2.0) {
			status.getSimulationConditions().setTimeStep(Math.min(
					status.getSimulationConditions().getTimeStep(),
					APOGEE_LOCALIZATION_TIMESTEP_S));
		}
		double remainingBallisticHeight = verticalVelocity > 0
				? verticalVelocity * verticalVelocity / (2 * CONSERVATIVE_GRAVITY_M_S2)
				: 0;
		if (remainingBallisticHeight > MAX_UNRESOLVED_APOGEE_HEIGHT_M) return true;
		status.getEventQueue().add(new FlightEvent(FlightEvent.Type.SIMULATION_END,
				status.getSimulationTime()));
		return false;
	}

	@Override
	public boolean isSystemListener() {
		return true;
	}

	static boolean shouldArm(boolean liftoff, double verticalVelocity) {
		return liftoff && verticalVelocity > 10.0;
	}
}

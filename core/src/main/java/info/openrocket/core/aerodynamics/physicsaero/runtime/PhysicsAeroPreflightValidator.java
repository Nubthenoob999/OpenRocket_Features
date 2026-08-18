package info.openrocket.core.aerodynamics.physicsaero.runtime;

import java.util.Locale;

import info.openrocket.core.aerodynamics.physicsaero.table.AerodynamicTable;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.simulation.exception.SimulationException;

/**
 * Checks simulation-specific requirements that cannot be established when a
 * reusable table artifact is generated.
 */
public final class PhysicsAeroPreflightValidator {
	private static final double TOLERANCE = 1.0e-12;

	private PhysicsAeroPreflightValidator() { }

	public static void validate(AerodynamicTable table, FlightConfiguration configuration,
			PhysicsAeroMode mode) throws SimulationException {
		if (table == null || configuration == null || mode == null || mode == PhysicsAeroMode.OFF) {
			return;
		}
		if (!contains(table.axes().mach(), 0)
				|| !contains(table.axes().alphaRad(), 0)
				|| !contains(table.axes().betaRad(), 0)
				|| !contains(table.axes().poweredFraction(), 0)) {
			throw failure("table does not contain the nominal coast coordinate "
					+ "(Mach=0, alpha=0, beta=0, poweredFraction=0)");
		}
		if (mode == PhysicsAeroMode.STRICT && configuration.hasMotors()
				&& !contains(table.axes().poweredFraction(), 1)) {
			double[] powered = table.axes().poweredFraction();
			throw failure(String.format(Locale.ROOT,
					"strict mode cannot run this powered configuration: poweredFraction=1.0 "
							+ "is not covered by the table; allowed=[%.6g, %.6g]. Rebuild the table "
							+ "with the motor configuration active or select a diagnostic hybrid mode",
					powered[0], powered[powered.length - 1]));
		}
	}

	private static boolean contains(double[] axis, double value) {
		for (double node : axis) {
			if (Math.abs(node - value) <= TOLERANCE * Math.max(1, Math.abs(node))) return true;
		}
		return false;
	}

	private static SimulationException failure(String detail) {
		return new SimulationException("Physics-based aerodynamics preflight failed: " + detail);
	}
}

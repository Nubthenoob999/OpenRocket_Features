package info.openrocket.core.structures.loads;

public final class AxialLoadModel {
	public static final double STANDARD_GRAVITY = 9.80665;

	private AxialLoadModel() {
	}

	public static double conservativeAxialLoad(FlightLoadCase loadCase) {
		double mass = finiteOrZero(loadCase.getMass());
		double acceleration = loadCase.getAxialAcceleration();
		if (!Double.isFinite(acceleration)) {
			acceleration = STANDARD_GRAVITY;
		}

		// Conservative first-iteration force estimate. This intentionally replaces the
		// spreadsheet's dimensionally-invalid drag + thrust + mass assumption.
		return Math.abs(finiteOrZero(loadCase.getThrust())) +
				Math.abs(finiteOrZero(loadCase.getDrag())) +
				Math.abs(mass * acceleration);
	}

	private static double finiteOrZero(double value) {
		return Double.isFinite(value) ? value : 0.0;
	}
}

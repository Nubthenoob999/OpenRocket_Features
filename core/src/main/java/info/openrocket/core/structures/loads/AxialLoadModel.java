package info.openrocket.core.structures.loads;

public final class AxialLoadModel {
	public static final double STANDARD_GRAVITY = 9.80665;

	private AxialLoadModel() {
	}

	public static double conservativeAxialLoad(FlightLoadCase loadCase) {
		double mass = finiteOrZero(loadCase.getMass());

		// Match the workbook's thrust + drag + vehicle-weight envelope while keeping
		// the calculation dimensionally valid in SI.  Acceleration is not added here:
		// for a whole-vehicle free-body diagram it is a response to the applied forces,
		// so adding m*a would count the same launch load a second time.
		return Math.abs(finiteOrZero(loadCase.getThrust())) +
				Math.abs(finiteOrZero(loadCase.getDrag())) +
				Math.abs(mass * STANDARD_GRAVITY);
	}

	private static double finiteOrZero(double value) {
		return Double.isFinite(value) ? value : 0.0;
	}
}

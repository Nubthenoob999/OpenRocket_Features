package info.openrocket.core.structures.loads;

import java.util.ArrayList;
import java.util.List;

import info.openrocket.core.document.Simulation;
import info.openrocket.core.simulation.FlightData;
import info.openrocket.core.simulation.FlightDataBranch;
import info.openrocket.core.simulation.FlightDataType;

public final class SimulationLoadExtractor {
	public FlightLoadSeries extract(Simulation simulation) {
		if (simulation == null || simulation.getSimulatedData() == null) {
			return new FlightLoadSeries(new ArrayList<>());
		}
		FlightData data = simulation.getSimulatedData();
		if (data.getBranchCount() == 0) {
			return new FlightLoadSeries(new ArrayList<>());
		}

		FlightDataBranch branch = data.getBranch(0);
		List<Double> timeValues = branch.get(FlightDataType.TYPE_TIME);
		if (timeValues == null || timeValues.isEmpty()) {
			return new FlightLoadSeries(new ArrayList<>());
		}

		List<FlightLoadCase> cases = new ArrayList<>();
		for (int i = 0; i < timeValues.size(); i++) {
			double velocity = value(branch, FlightDataType.TYPE_VELOCITY_TOTAL, i);
			double density = value(branch, FlightDataType.TYPE_AIR_DENSITY, i);
			double dynamicPressure = Double.NaN;
			if (Double.isFinite(density) && Double.isFinite(velocity)) {
				dynamicPressure = 0.5 * density * velocity * velocity;
			}

			cases.add(new FlightLoadCase(
					value(branch, FlightDataType.TYPE_TIME, i),
					velocity,
					value(branch, FlightDataType.TYPE_MACH_NUMBER, i),
					density,
					dynamicPressure,
					value(branch, FlightDataType.TYPE_MASS, i),
					value(branch, FlightDataType.TYPE_CG_LOCATION, i),
					value(branch, FlightDataType.TYPE_THRUST_FORCE, i),
					value(branch, FlightDataType.TYPE_DRAG_FORCE, i),
					firstFinite(
							value(branch, FlightDataType.TYPE_ACCELERATION_BODYZ, i),
							value(branch, FlightDataType.TYPE_ACCELERATION_TOTAL, i),
							value(branch, FlightDataType.TYPE_ACCELERATION_Z, i)),
					value(branch, FlightDataType.TYPE_AOA, i),
					value(branch, FlightDataType.TYPE_AIR_PRESSURE, i),
					value(branch, FlightDataType.TYPE_SPEED_OF_SOUND, i)));
		}
		return new FlightLoadSeries(cases);
	}

	private static double value(FlightDataBranch branch, FlightDataType type, int index) {
		Double value = branch.getByIndex(type, index);
		return value == null ? Double.NaN : value;
	}

	private static double firstFinite(double first, double second, double third) {
		if (Double.isFinite(first)) {
			return first;
		}
		if (Double.isFinite(second)) {
			return second;
		}
		return third;
	}
}

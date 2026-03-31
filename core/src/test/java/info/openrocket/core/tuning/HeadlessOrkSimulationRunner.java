package info.openrocket.core.tuning;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.file.RocketLoadException;
import info.openrocket.core.rocketcomponent.FlightConfigurationId;
import info.openrocket.core.simulation.FlightData;
import info.openrocket.core.simulation.FlightDataBranch;
import info.openrocket.core.simulation.FlightDataType;
import info.openrocket.core.simulation.exception.SimulationException;
import info.openrocket.core.startup.Application;
import info.openrocket.core.startup.OpenRocketCore;

import java.io.File;
import java.util.List;

public final class HeadlessOrkSimulationRunner {
	private HeadlessOrkSimulationRunner() {
	}

	public static TelemetrySeries runFirstSimulation(File orkFile)
			throws RocketLoadException, SimulationException {
		ensureApplicationInjector();
		GeneralRocketLoader loader = new GeneralRocketLoader(orkFile);
		OpenRocketDocument document = loader.load();

		Simulation simulation;
		if (document.getSimulations().isEmpty()) {
			simulation = new Simulation(document.getRocket());
			FlightConfigurationId id = document.getRocket().getSelectedConfiguration().getFlightConfigurationID();
			simulation.setFlightConfigurationId(id);
		} else {
			simulation = document.getSimulations().get(0);
		}

		simulation.simulate();
		FlightData data = simulation.getSimulatedData();
		FlightDataBranch branch = data.getBranch(0);

		List<Double> time = branch.get(FlightDataType.TYPE_TIME);
		List<Double> altitude = branch.get(FlightDataType.TYPE_ALTITUDE);
		List<Double> velocityZ = branch.get(FlightDataType.TYPE_VELOCITY_Z);
		List<Double> accelZ = deriveVerticalAcceleration(time, velocityZ,
				branch.get(FlightDataType.TYPE_ACCELERATION_Z));
		List<Double> pressure = branch.get(FlightDataType.TYPE_AIR_PRESSURE);
		List<Double> temperature = branch.get(FlightDataType.TYPE_AIR_TEMPERATURE);

		TelemetrySeries series = new TelemetrySeries(TelemetrySchema.AB_IMU_INTERLEAVED);
		for (int i = 0; i < time.size(); i++) {
			series.addPoint(valueAt(time, i), valueAt(altitude, i), valueAt(velocityZ, i), null, null,
					valueAt(accelZ, i), valueAt(pressure, i), valueAt(temperature, i));
		}
		return series;
	}

	private static Double valueAt(List<Double> values, int index) {
		if (values == null || index >= values.size()) {
			return null;
		}
		return values.get(index);
	}

	static List<Double> deriveVerticalAcceleration(List<Double> time,
												 List<Double> velocityZ,
												 List<Double> fallbackAccelZ) {
		if (time == null || velocityZ == null) {
			return fallbackAccelZ;
		}

		java.util.ArrayList<Double> derived = new java.util.ArrayList<>(time.size());
		for (int i = 0; i < time.size(); i++) {
			Double acceleration = centralDifference(time, velocityZ, i);
			if (acceleration == null) {
				acceleration = valueAt(fallbackAccelZ, i);
			}
			derived.add(acceleration);
		}
		return derived;
	}

	private static Double centralDifference(List<Double> time, List<Double> velocityZ, int index) {
		Double previous = finiteDifference(time, velocityZ, index - 1, index);
		Double next = finiteDifference(time, velocityZ, index, index + 1);
		if (previous != null && next != null) {
			return 0.5 * (previous + next);
		}
		if (previous != null) {
			return previous;
		}
		return next;
	}

	private static Double finiteDifference(List<Double> time, List<Double> velocityZ, int i0, int i1) {
		if (i0 < 0 || i1 < 0 || i0 >= time.size() || i1 >= time.size()
				|| i0 >= velocityZ.size() || i1 >= velocityZ.size()) {
			return null;
		}
		Double t0 = time.get(i0);
		Double t1 = time.get(i1);
		Double v0 = velocityZ.get(i0);
		Double v1 = velocityZ.get(i1);
		if (t0 == null || t1 == null || v0 == null || v1 == null
				|| !Double.isFinite(t0) || !Double.isFinite(t1)
				|| !Double.isFinite(v0) || !Double.isFinite(v1)) {
			return null;
		}
		double dt = t1 - t0;
		if (dt <= 0.0 || !Double.isFinite(dt)) {
			return null;
		}
		return (v1 - v0) / dt;
	}

	private static synchronized void ensureApplicationInjector() {
		if (Application.getInjector() != null && OpenRocketCore.isInitialized()) {
			return;
		}
		try {
			OpenRocketCore.initialize();
		} catch (RuntimeException ex) {
			throw new IllegalStateException("Failed to initialize OpenRocket core services", ex);
		}
	}
}

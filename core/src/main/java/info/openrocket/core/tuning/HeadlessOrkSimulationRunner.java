package info.openrocket.core.tuning;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.file.RocketLoadException;
import info.openrocket.core.rocketcomponent.FlightConfigurationId;
import info.openrocket.core.simulation.FlightData;
import info.openrocket.core.simulation.FlightDataBranch;
import info.openrocket.core.simulation.FlightDataType;
import info.openrocket.core.simulation.SimulationOptions;
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
		return runFirstSimulationDetailed(orkFile).getSeries();
	}

	public static OrkSimulationResult runFirstSimulationDetailed(File orkFile)
			throws RocketLoadException, SimulationException {
		return runFirstSimulationDetailed(orkFile, null);
	}

	public static OrkSimulationResult runFirstSimulationDetailed(File orkFile,
																 SimulationConfigurator configurator)
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
		if (configurator != null) {
			configurator.configure(simulation);
		}

		return runSimulationDetailed(simulation);
	}

	public static OrkSimulationResult runSimulationDetailed(Simulation simulation)
			throws SimulationException {
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
		List<Double> mach = branch.get(FlightDataType.TYPE_MACH_NUMBER);

		TelemetrySeries rawSeries = new TelemetrySeries(TelemetrySchema.OPENROCKET_SIMULATION);
		TelemetryParserDiagnostics.Mutable diagnostics =
				new TelemetryParserDiagnostics.Mutable(TelemetrySchema.OPENROCKET_SIMULATION.name());
		for (int i = 0; i < time.size(); i++) {
			diagnostics.incRowsRead();
			rawSeries.addPoint(valueAt(time, i), valueAt(altitude, i), valueAt(velocityZ, i), null, null,
					valueAt(accelZ, i), valueAt(pressure, i), valueAt(temperature, i));
			diagnostics.incRowsAccepted();
		}
		rawSeries.setParserDiagnostics(diagnostics.freeze());
		VerticalKinematicsReconstructor.ReconstructionResult reconstruction =
				VerticalKinematicsReconstructor.reconstruct(rawSeries);

		SimulationOptions options = simulation.getOptions();
		return new OrkSimulationResult(
				reconstruction.getCorrectedSeries(),
				reconstruction.getRawSeries(),
				reconstruction.getDiagnostics(),
				describeRomRuntime(options),
				describePathlineSource(options),
				maxFinite(mach));
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

	private static String describeRomRuntime(SimulationOptions options) {
		if (options == null) {
			return "";
		}
		if (!options.isRomEnabled()) {
			return "BARROWMAN_ONLY";
		}
		return "PATHLINE_" + options.getRomMode().name();
	}

	private static String describePathlineSource(SimulationOptions options) {
		if (options == null) {
			return "";
		}
		if (!options.isRomEnabled()) {
			return "PATHLINE_DISABLED";
		}
		if (options.getRomAeroSurface4D() != null || options.getRomDragSurface() != null) {
			return "PATHLINE_ACTIVE_LEGACY_SURFACES_IGNORED";
		}
		return "PATHLINE_ACTIVE";
	}

	private static double maxFinite(List<Double> values) {
		double max = Double.NaN;
		if (values == null) {
			return max;
		}
		for (Double value : values) {
			if (value == null || !Double.isFinite(value)) {
				continue;
			}
			if (Double.isNaN(max) || value > max) {
				max = value;
			}
		}
		return max;
	}

	public static final class OrkSimulationResult {
		private final TelemetrySeries series;
		private final TelemetrySeries rawSeries;
		private final VerticalIntegratorDiagnostics integratorDiagnostics;
		private final String romMode;
		private final String romSurfaceSource;
		private final double maxMach;

		private OrkSimulationResult(TelemetrySeries series,
									TelemetrySeries rawSeries,
									VerticalIntegratorDiagnostics integratorDiagnostics,
									String romMode,
									String romSurfaceSource,
									double maxMach) {
			this.series = series;
			this.rawSeries = rawSeries;
			this.integratorDiagnostics = integratorDiagnostics == null ? VerticalIntegratorDiagnostics.EMPTY : integratorDiagnostics;
			this.romMode = romMode;
			this.romSurfaceSource = romSurfaceSource;
			this.maxMach = maxMach;
		}

		public TelemetrySeries getSeries() {
			return series;
		}

		public TelemetrySeries getRawSeries() {
			return rawSeries;
		}

		public VerticalIntegratorDiagnostics getIntegratorDiagnostics() {
			return integratorDiagnostics;
		}

		public String getRomMode() {
			return romMode;
		}

		public String getRomSurfaceSource() {
			return romSurfaceSource;
		}

		public double getMaxMach() {
			return maxMach;
		}
	}

	@FunctionalInterface
	public interface SimulationConfigurator {
		void configure(Simulation simulation);
	}
}

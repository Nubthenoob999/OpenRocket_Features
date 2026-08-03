package info.openrocket.core.tuning;

import com.google.gson.Gson;
import info.openrocket.core.aerodynamics.physicsaero.measurement.StandardAtmosphereBarometricAltimeter;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroMode;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroRuntimeReport;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.simulation.FlightData;
import info.openrocket.core.simulation.FlightDataBranch;
import info.openrocket.core.simulation.FlightDataType;
import info.openrocket.core.simulation.listeners.system.BoundedApogeeEndListener;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Runs the same recorded-flight ORK conditions through the baseline Barrowman
 * model and the offline physics-aero table.  This is intentionally a reporting
 * harness: it does not alter table correlations or hide misses with calibration.
 */
public final class OfflineTableFlightComparisonHarness {
	public static final String CSV_FILE_NAME = "offline-table-flight-comparison.csv";
	public static final String SUMMARY_FILE_NAME = "offline-table-flight-comparison-summary.txt";
	private static final double ZERO_APOGEE_EPSILON_METERS = 1.0e-9;
	private static final int RANDOM_SEED = 0x51A7EA;
	private static final Gson GSON = new Gson();

	private OfflineTableFlightComparisonHarness() {
	}

	public static ComparisonReport runDefault(Path outputDirectory) throws Exception {
		Path config = PhaseThreeTuningPaths.findDefaultConfig();
		if (config == null) {
			throw new IllegalStateException("Bundled Phase Three tuning config was not found");
		}
		return run(config, outputDirectory);
	}

	/**
	 * Command-line entry point for an intentionally persisted report.  With one
	 * argument it uses the bundled Phase Three config; with two, the first is a
	 * Phase Three config path and the second is the output directory.  In Gradle,
	 * run {@code ./gradlew :core:offlineTableFlightComparison}; the reports are
	 * written to {@code core/build/reports/offline-table-flight-comparison}.
	 */
	public static void main(String[] args) throws Exception {
		ComparisonReport report;
		if (args.length == 1) {
			report = runDefault(Path.of(args[0]));
		} else if (args.length == 2) {
			report = run(Path.of(args[0]), Path.of(args[1]));
		} else {
			throw new IllegalArgumentException(
					"Usage: OfflineTableFlightComparisonHarness <outputDir> OR <phase3-config.json> <outputDir>");
		}
		System.out.print(report.toSummary());
	}

	/**
	 * Uses every ORK-backed dataset in a Phase Three config.  The baseline and
	 * table runs each receive the same airbrake configuration; only the
	 * aerodynamic calculator selection differs.
	 */
	public static ComparisonReport run(Path configPath, Path outputDirectory) throws Exception {
		Path normalizedConfig = configPath.toAbsolutePath().normalize();
		Path configDirectory = normalizedConfig.getParent();
		PhaseThreeBatchRunConfig config = GSON.fromJson(
				Files.readString(normalizedConfig, StandardCharsets.UTF_8), PhaseThreeBatchRunConfig.class);
		if (config == null || config.getDatasets() == null || config.getDatasets().isEmpty()) {
			throw new IllegalArgumentException("No datasets defined in " + normalizedConfig);
		}

		List<PhaseThreeBatchDatasetConfig> datasets = new ArrayList<>(config.getDatasets());
		datasets.sort(Comparator.comparing(OfflineTableFlightComparisonHarness::datasetName));
		String selectedName = System.getProperty("offlineTableComparison.flightName", "").trim();
		if (!selectedName.isEmpty()) {
			datasets.removeIf(dataset -> !datasetName(dataset).equalsIgnoreCase(selectedName));
			if (datasets.isEmpty()) {
				throw new IllegalArgumentException("No dataset named " + selectedName);
			}
		}
		List<FlightComparison> comparisons = new ArrayList<>(datasets.size());
		for (PhaseThreeBatchDatasetConfig dataset : datasets) {
			String name = datasetName(dataset);
			System.err.println("offline-table-flight-comparison START " + name);
			FlightComparison comparison = compareDataset(dataset, configDirectory);
			comparisons.add(comparison);
			System.err.println("offline-table-flight-comparison END " + name
					+ " status=" + comparison.status());
		}

		ComparisonReport report = new ComparisonReport(comparisons);
		writeReport(report, outputDirectory);
		return report;
	}

	static FlightComparison compareDataset(PhaseThreeBatchDatasetConfig dataset, Path configDirectory) {
		String name = datasetName(dataset);
		Path truthPath;
		try {
			truthPath = resolveTruthPath(dataset, configDirectory);
			TelemetrySeries truth = TelemetryParsers.parse(truthPath);
			DerivedTelemetryQuantities.Quantities truthSummary = DerivedTelemetryQuantities.summarize(truth);
			double actualApogee = truthSummary.getApogeeAltitudeMeters();
			TruthTrajectoryMetrics truthTrajectory = truthTrajectoryMetrics(truth, truthSummary);
			if (!Double.isFinite(actualApogee) || Math.abs(actualApogee) <= ZERO_APOGEE_EPSILON_METERS) {
				return FlightComparison.unavailable(name, pathText(truthPath), "TRUTH_APOGEE_UNAVAILABLE");
			}
			if (isBlank(dataset.getOrkPath())) {
				return FlightComparison.noOrk(name, pathText(truthPath), actualApogee, truthTrajectory);
			}
			Path orkPath = PhaseThreeNativeAirbrakesConfigurer.resolvePath(configDirectory, dataset.getOrkPath());
			RunOutcome baseline = runBaseline(dataset, configDirectory, orkPath, truth.getSchema());
			RunOutcome table = runTable(dataset, configDirectory, orkPath, truth.getSchema());
			return FlightComparison.complete(name, pathText(truthPath), pathText(orkPath), truth.getSchema(),
					actualApogee, truthTrajectory, baseline, table);
		} catch (Exception exception) {
			return FlightComparison.unavailable(name, "", failureText(exception));
		}
	}

	private static RunOutcome runBaseline(PhaseThreeBatchDatasetConfig dataset, Path configDirectory, Path orkPath,
			TelemetrySchema truthSchema) {
		return runVariant(dataset, configDirectory, orkPath, truthSchema, false);
	}

	private static RunOutcome runTable(PhaseThreeBatchDatasetConfig dataset, Path configDirectory, Path orkPath,
			TelemetrySchema truthSchema) {
		return runVariant(dataset, configDirectory, orkPath, truthSchema, true);
	}

	private static RunOutcome runVariant(PhaseThreeBatchDatasetConfig dataset, Path configDirectory,
			Path orkPath, TelemetrySchema truthSchema, boolean strictTable) {
		Simulation simulation = null;
		try {
			TuningTestInfrastructure.ensureApplicationInjector();
			OpenRocketDocument document = new GeneralRocketLoader(orkPath.toFile()).load();
			if (document.getSimulations().isEmpty()) {
				return RunOutcome.failure(strictTable ? "TABLE_STRICT_FAILED: ORK has no simulation" : "BASELINE_FAILED: ORK has no simulation");
			}
			simulation = document.getSimulations().get(0);
			AbPluginExecutionResult airbrakeConfiguration = PhaseThreeNativeAirbrakesConfigurer.configure(
					dataset, configDirectory, simulation.getOptions());
			if (dataset.isAirbrakeEnabled()
					&& airbrakeConfiguration.getStatus() != AbPluginExecutionResult.Status.SUCCEEDED) {
				String prefix = strictTable ? "TABLE_STRICT_FAILED: " : "BASELINE_FAILED: ";
				return RunOutcome.failure(prefix + "AIRBRAKE_CONFIGURATION_REQUIRED: "
						+ airbrakeConfiguration.getMessage());
			}
			// The ORK files may carry a generated wind seed.  Pin one seed so the
			// baseline and strict-table variants see the identical turbulence history
			// and repeated comparison runs remain bit-for-bit reproducible.
			simulation.getOptions().setRandomSeed(RANDOM_SEED);
			// The configurer supplies airbrake/legacy conditions but defaults to hybrid.
			// Override it here so neither benchmark can inherit its aerodynamic mode.
			simulation.getOptions().setPhysicsAeroMode(strictTable ? PhysicsAeroMode.STRICT : PhysicsAeroMode.OFF);
			PhysicsAeroTuningSupport.prepareTableIfEnabled(simulation);
			// Apogee is the scored quantity.  Ending here prevents a post-apogee
			// descent state from turning an otherwise valid strict-table ascent into
			// an unrelated high-incidence runtime failure.
			simulation.simulate(new BoundedApogeeEndListener());
			FlightData data = simulation.getSimulatedData();
			if (data == null || !Double.isFinite(data.getMaxAltitude())) {
				return RunOutcome.failure(strictTable ? "TABLE_STRICT_FAILED: no flight apogee" : "BASELINE_FAILED: no flight apogee",
						runtimeSnapshot(simulation.getPhysicsAeroRuntimeReport()));
			}
			RuntimeSnapshot runtime = runtimeSnapshot(simulation.getPhysicsAeroRuntimeReport());
			MeasurementEstimate estimate = measurementEstimate(truthSchema, data);
			SimulationTrajectoryMetrics trajectory = simulationTrajectoryMetrics(data);
			if (strictTable) {
				return RunOutcome.strictTableSuccess(estimate, runtime, data.getMaxMachNumber(), trajectory);
			}
			return RunOutcome.baselineSuccess(estimate, runtime, data.getMaxMachNumber(), trajectory);
		} catch (Exception exception) {
			RuntimeSnapshot runtime = simulation == null ? RuntimeSnapshot.EMPTY
					: runtimeSnapshot(simulation.getPhysicsAeroRuntimeReport());
			String prefix = strictTable ? "TABLE_STRICT_FAILED: " : "BASELINE_FAILED: ";
			return RunOutcome.failure(prefix + failureText(exception), runtime);
		}
	}

	/**
	 * Converts the simulated atmosphere history into the same pressure-altitude
	 * measurement space as the physical altimeters in the tuning corpus.  The
	 * geometric trajectory is retained separately for diagnostics.
	 */
	static MeasurementEstimate measurementEstimate(TelemetrySchema truthSchema, FlightData data) {
		double geometricApogeeM = data.getMaxAltitude();
		if (truthSchema == TelemetrySchema.OPENROCKET_SIMULATION) {
			return new MeasurementEstimate(geometricApogeeM, geometricApogeeM,
					"DIRECT_GEOMETRIC_COMPARISON_OPENROCKET_SIMULATION");
		}
		if (truthSchema == TelemetrySchema.FLUCTUS_SEMICOLON) {
			// The parser intentionally selects Fluctus `dedrck-alti`, documented by
			// Silicdyne as dead-reckoning altitude, ahead of `baro-altitude`.
			// Applying a second pressure-altimeter transfer to that fused displacement
			// estimate would compare unlike quantities.
			return new MeasurementEstimate(geometricApogeeM, geometricApogeeM,
					"DIRECT_GEOMETRIC_COMPARISON_FLUCTUS_DEAD_RECKONING_ALTITUDE_V1");
		}
		if (truthSchema != TelemetrySchema.EASYMINI_ALTIMETER
				&& truthSchema != TelemetrySchema.STRATOLOGGER_COMMA
				&& truthSchema != TelemetrySchema.TAB_DELIMITED_ALTIMETER) {
			return new MeasurementEstimate(geometricApogeeM, geometricApogeeM,
					"DIRECT_GEOMETRIC_COMPARISON_NO_UNAMBIGUOUS_SENSOR_TRANSFER_MODEL");
		}
		FlightDataBranch branch = data.getBranch(0);
		List<Double> altitude = branch.get(FlightDataType.TYPE_ALTITUDE);
		List<Double> pressure = branch.get(FlightDataType.TYPE_AIR_PRESSURE);
		int samples = Math.min(altitude == null ? 0 : altitude.size(), pressure == null ? 0 : pressure.size());
		if (samples == 0) {
			throw new IllegalStateException("barometric comparison requires altitude and pressure histories");
		}
		int apogeeIndex = -1;
		for (int index = 0; index < samples; index++) {
			Double value = altitude.get(index);
			if (value != null && Double.isFinite(value)
					&& (apogeeIndex < 0 || value > altitude.get(apogeeIndex))) {
				apogeeIndex = index;
			}
		}
		if (apogeeIndex < 0 || pressure.get(apogeeIndex) == null
				|| !Double.isFinite(pressure.get(apogeeIndex))) {
			throw new IllegalStateException("barometric comparison requires a finite apogee pressure");
		}
		Double launchPressurePa = null;
		for (int index = 0; index <= apogeeIndex; index++) {
			Double value = pressure.get(index);
			if (value != null && Double.isFinite(value)) {
				launchPressurePa = value;
				break;
			}
		}
		if (launchPressurePa == null) {
			throw new IllegalStateException("barometric comparison requires a finite launch pressure");
		}
		double indicatedApogeeM = new StandardAtmosphereBarometricAltimeter()
				.heightAbovePadM(launchPressurePa, pressure.get(apogeeIndex));
		return new MeasurementEstimate(indicatedApogeeM, geometricApogeeM,
				StandardAtmosphereBarometricAltimeter.METHOD_ID);
	}

	public record MeasurementEstimate(double indicatedApogeeMeters, double geometricApogeeMeters,
			String methodId) {
	}

	private static TruthTrajectoryMetrics truthTrajectoryMetrics(TelemetrySeries truth,
			DerivedTelemetryQuantities.Quantities summary) {
		double launchTime = summary.getLaunchTimeSec();
		double apogeeTime = summary.getApogeeTimeSec();
		double timeToApogee = Double.isFinite(apogeeTime)
				? apogeeTime - (Double.isFinite(launchTime) ? launchTime : 0.0)
				: Double.NaN;
		double peakAscentVelocity = Double.NaN;
		List<Double> time = truth.getTimeSec();
		List<Double> velocity = truth.getVelocityZMetersPerSec();
		for (int index = 0; index < time.size() && index < velocity.size(); index++) {
			Double sampleTime = time.get(index);
			Double sampleVelocity = velocity.get(index);
			if (sampleTime == null || sampleVelocity == null || !Double.isFinite(sampleTime)
					|| !Double.isFinite(sampleVelocity)
					|| (Double.isFinite(launchTime) && sampleTime < launchTime)
					|| (Double.isFinite(apogeeTime) && sampleTime > apogeeTime)) {
				continue;
			}
			if (!Double.isFinite(peakAscentVelocity) || sampleVelocity > peakAscentVelocity) {
				peakAscentVelocity = sampleVelocity;
			}
		}
		return new TruthTrajectoryMetrics(peakAscentVelocity, timeToApogee);
	}

	private static SimulationTrajectoryMetrics simulationTrajectoryMetrics(FlightData data) {
		FlightDataBranch branch = data.getBranch(0);
		double peakAscentVelocity = branch.getMaximum(FlightDataType.TYPE_VELOCITY_Z);
		return new SimulationTrajectoryMetrics(peakAscentVelocity, data.getTimeToApogee());
	}

	public record TruthTrajectoryMetrics(double peakAscentVelocityMS, double timeToApogeeS) {
		private static final TruthTrajectoryMetrics EMPTY =
				new TruthTrajectoryMetrics(Double.NaN, Double.NaN);
	}

	private record SimulationTrajectoryMetrics(double peakAscentVelocityMS, double timeToApogeeS) {
		private static final SimulationTrajectoryMetrics EMPTY =
				new SimulationTrajectoryMetrics(Double.NaN, Double.NaN);
	}

	private static Path resolveTruthPath(PhaseThreeBatchDatasetConfig dataset, Path configDirectory) {
		String truthPath = !isBlank(dataset.getTruthCsv()) ? dataset.getTruthCsv() : dataset.getReferenceCsv();
		if (isBlank(truthPath)) {
			throw new IllegalArgumentException("Dataset has neither truthCsv nor referenceCsv");
		}
		return PhaseThreeNativeAirbrakesConfigurer.resolvePath(configDirectory, truthPath);
	}

	static void writeReport(ComparisonReport report, Path outputDirectory) throws IOException {
		Path output = outputDirectory.toAbsolutePath().normalize();
		Files.createDirectories(output);
		Files.writeString(output.resolve(CSV_FILE_NAME), report.toCsv(), StandardCharsets.UTF_8);
		Files.writeString(output.resolve(SUMMARY_FILE_NAME), report.toSummary(), StandardCharsets.UTF_8);
	}

	private static String datasetName(PhaseThreeBatchDatasetConfig dataset) {
		return isBlank(dataset.getName()) ? "unnamed-dataset" : dataset.getName().trim();
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private static String pathText(Path path) {
		return path == null ? "" : path.toAbsolutePath().normalize().toString();
	}

	private static String failureText(Exception exception) {
		String message = exception.getMessage();
		return exception.getClass().getSimpleName() + (isBlank(message) ? "" : ": " + message);
	}

	private static RuntimeSnapshot runtimeSnapshot(PhysicsAeroRuntimeReport report) {
		if (report == null) {
			return RuntimeSnapshot.EMPTY;
		}
		String failureCounts = report.failureCounts().entrySet().stream()
				.sorted(Comparator.comparing(entry -> entry.getKey().name()))
				.map(entry -> entry.getKey().name() + '=' + entry.getValue())
				.collect(Collectors.joining(";"));
		String runtimeFlags = report.runtimeFlags().stream()
				.map(Enum::name)
				.sorted()
				.collect(Collectors.joining(";"));
		FailureSnapshot firstFailure = report.firstOccurrences().entrySet().stream()
				.sorted(Comparator.comparingDouble((java.util.Map.Entry<info.openrocket.core.aerodynamics.physicsaero.diagnostics.FailureReason,
						info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroFailureOccurrence> entry) ->
						entry.getValue().simulationTimeSeconds())
						.thenComparing(entry -> entry.getKey().name()))
				.findFirst()
				.map(entry -> {
					var occurrence = entry.getValue();
					var coordinates = occurrence.coordinates();
					return new FailureSnapshot(entry.getKey().name(), occurrence.simulationTimeSeconds(),
							coordinates.mach(), Math.toDegrees(coordinates.alphaRad()), Math.toDegrees(coordinates.betaRad()),
							coordinates.poweredFraction(), coordinates.reynoldsNumber(), occurrence.detail());
				})
				.orElse(FailureSnapshot.EMPTY);
		return new RuntimeSnapshot(report.tableValid(), report.totalQueries(), report.successfulTableQueries(),
			report.fallbackCount(), failureCounts, runtimeFlags, firstFailure);
	}

	private record RuntimeSnapshot(boolean tableValid, long totalQueries, long successfulTableQueries,
							   long fallbackCount, String failureCounts, String runtimeFlags, FailureSnapshot firstFailure) {
		private static final RuntimeSnapshot EMPTY = new RuntimeSnapshot(false, 0, 0, 0, "", "", FailureSnapshot.EMPTY);
	}

	public record FailureSnapshot(String reason, double timeSeconds, double mach, double alphaDegrees,
							  double betaDegrees, double poweredFraction, double reynoldsNumber, String detail) {
		public static final FailureSnapshot EMPTY = new FailureSnapshot("", Double.NaN, Double.NaN,
				Double.NaN, Double.NaN, Double.NaN, Double.NaN, "");
	}

	public record RunOutcome(String status, double predictedApogeeMeters, double geometricApogeeMeters,
							 String measurementMethod, String runtimeMode, String tableSource,
							 double maxMach, double peakAscentVelocityMS, double timeToApogeeS,
							 boolean tableValid, long totalQueries, long successfulTableQueries,
							 long fallbackCount, String failureCounts, String runtimeFlags, FailureSnapshot firstFailure,
							 String failure) {
		static RunOutcome success(double apogee, String runtimeMode, String tableSource, double maxMach) {
			return new RunOutcome("SUCCESS", apogee, apogee, "TEST_DIRECT_APOGEE", runtimeMode, tableSource, maxMach,
					Double.NaN, Double.NaN, false, 0, 0, 0, "", "", FailureSnapshot.EMPTY, "");
		}

		static RunOutcome failure(String failure) {
			return failure(failure, RuntimeSnapshot.EMPTY);
		}

		static RunOutcome failure(String failure, RuntimeSnapshot runtime) {
			return new RunOutcome(failure.startsWith("TABLE_STRICT") ? "TABLE_STRICT_FAILED" : "FAILED",
					Double.NaN, Double.NaN, "", "", "", Double.NaN, Double.NaN, Double.NaN,
					runtime.tableValid(), runtime.totalQueries(),
					runtime.successfulTableQueries(), runtime.fallbackCount(), runtime.failureCounts(),
					runtime.runtimeFlags(), runtime.firstFailure(), failure);
		}

		static RunOutcome baselineSuccess(MeasurementEstimate estimate, RuntimeSnapshot runtime, double maxMach,
				SimulationTrajectoryMetrics trajectory) {
			return new RunOutcome("BASELINE_SUCCESS", estimate.indicatedApogeeMeters(),
					estimate.geometricApogeeMeters(), estimate.methodId(), "BARROWMAN_ONLY", "TABLE_DISABLED", maxMach,
					trajectory.peakAscentVelocityMS(), trajectory.timeToApogeeS(),
					runtime.tableValid(), runtime.totalQueries(), runtime.successfulTableQueries(), runtime.fallbackCount(),
					runtime.failureCounts(), runtime.runtimeFlags(), runtime.firstFailure(), "");
		}

		static RunOutcome strictTableSuccess(MeasurementEstimate estimate, RuntimeSnapshot runtime, double maxMach,
				SimulationTrajectoryMetrics trajectory) {
			if (!runtime.tableValid() || runtime.totalQueries() <= 0 || runtime.successfulTableQueries() <= 0
					|| runtime.fallbackCount() != 0 || !isBlank(runtime.failureCounts())) {
				return failure("TABLE_STRICT_FAILED: table validation/query requirement failed"
						+ " (valid=" + runtime.tableValid() + ", totalQueries=" + runtime.totalQueries()
						+ ", tableQueries=" + runtime.successfulTableQueries() + ", fallbacks="
						+ runtime.fallbackCount() + ", failures=" + runtime.failureCounts() + ')', runtime);
			}
			return new RunOutcome("TABLE_STRICT_SUCCESS", estimate.indicatedApogeeMeters(),
					estimate.geometricApogeeMeters(), estimate.methodId(), "PHYSICS_AERO_STRICT", "TABLE_CACHE_IDENTITY", maxMach,
					trajectory.peakAscentVelocityMS(), trajectory.timeToApogeeS(),
					runtime.tableValid(), runtime.totalQueries(), runtime.successfulTableQueries(), runtime.fallbackCount(),
					runtime.failureCounts(), runtime.runtimeFlags(), runtime.firstFailure(), "");
		}

		boolean succeeded() {
			return Double.isFinite(predictedApogeeMeters);
		}

		boolean isStrictTableSuccess() {
			return "TABLE_STRICT_SUCCESS".equals(status);
		}
	}

	public record FlightComparison(String flightName, String status, String truthSource, String orkPath,
							   TelemetrySchema truthSchema, double actualApogeeMeters,
							   TruthTrajectoryMetrics truthTrajectory,
							   RunOutcome baseline, RunOutcome table) {
		static FlightComparison complete(String name, String truthSource, String orkPath,
								 TelemetrySchema truthSchema, double actual,
								 TruthTrajectoryMetrics truthTrajectory,
									 RunOutcome baseline, RunOutcome table) {
			String status = baseline.succeeded() && table.isStrictTableSuccess() ? "COMPLETE" : "SIMULATION_FAILED";
			return new FlightComparison(name, status, truthSource, orkPath, truthSchema, actual,
					truthTrajectory, baseline, table);
		}

		static FlightComparison noOrk(String name, String truthSource, double actual,
				TruthTrajectoryMetrics truthTrajectory) {
			return new FlightComparison(name, "SKIPPED_NO_ORK", truthSource, "", null, actual, truthTrajectory,
					RunOutcome.failure("No orkPath configured"), RunOutcome.failure("No orkPath configured"));
		}

		static FlightComparison unavailable(String name, String truthSource, String failure) {
			return new FlightComparison(name, "TRUTH_UNAVAILABLE", truthSource, "", null, Double.NaN,
					TruthTrajectoryMetrics.EMPTY,
					RunOutcome.failure(failure), RunOutcome.failure(failure));
		}

		double signedErrorPercent(RunOutcome prediction) {
			if (!Double.isFinite(actualApogeeMeters) || Math.abs(actualApogeeMeters) <= ZERO_APOGEE_EPSILON_METERS
					|| prediction == null || !prediction.succeeded()) {
				return Double.NaN;
			}
			return 100.0 * (prediction.predictedApogeeMeters() - actualApogeeMeters) / actualApogeeMeters;
		}

		double absoluteErrorPercent(RunOutcome prediction) {
			double signed = signedErrorPercent(prediction);
			return Double.isFinite(signed) ? Math.abs(signed) : Double.NaN;
		}

		boolean withinPercent(RunOutcome prediction, double bandPercent) {
			double error = absoluteErrorPercent(prediction);
			return Double.isFinite(error) && error <= bandPercent;
		}
	}

	public record ComparisonReport(List<FlightComparison> flights) {
		public ComparisonReport {
			flights = List.copyOf(flights);
		}

		String toCsv() {
			StringBuilder csv = new StringBuilder();
			csv.append("flight_name,status,truth_source,truth_schema,ork_path,actual_apogee_m,")
					.append("truth_peak_ascent_velocity_z_m_s,truth_time_to_apogee_s,")
					.append("baseline_status,baseline_apogee_m,")
					.append("baseline_geometric_apogee_m,baseline_measurement_method,")
					.append("baseline_signed_error_percent,baseline_absolute_error_percent,baseline_within_3_percent,")
					.append("baseline_within_5_percent,")
					.append("baseline_within_10_percent,table_status,table_apogee_m,table_signed_error_percent,")
					.append("table_geometric_apogee_m,table_measurement_method,")
					.append("table_absolute_error_percent,table_within_3_percent,")
					.append("table_within_5_percent,table_within_10_percent,")
					.append("baseline_runtime_mode,table_runtime_mode,table_source,baseline_max_mach,table_max_mach,")
					.append("baseline_peak_ascent_velocity_z_m_s,table_peak_ascent_velocity_z_m_s,")
					.append("baseline_time_to_apogee_s,table_time_to_apogee_s,")
					.append("table_valid,table_total_queries,table_successful_queries,table_fallback_count,")
					.append("table_failure_counts,table_runtime_flags,table_first_failure_reason,table_first_failure_time_s,")
					.append("table_first_failure_mach,table_first_failure_alpha_deg,table_first_failure_beta_deg,")
					.append("table_first_failure_powered_fraction,table_first_failure_reynolds,table_first_failure_detail,")
					.append("baseline_failure,table_failure\n");
			for (FlightComparison flight : flights) {
				appendCsvRow(csv, flight);
			}
			return csv.toString();
		}

		String toSummary() {
			StringBuilder summary = new StringBuilder("Offline table flight comparison\n");
			summary.append("apogee_scoring=TRUTH_SCHEMA_MATCHED_INSTRUMENT_SPACE\n");
			summary.append("deterministic_random_seed=").append(RANDOM_SEED).append('\n');
			summary.append("flights_configured=").append(flights.size()).append('\n');
			appendMethodSummary(summary, "baseline", flights, false);
			appendMethodSummary(summary, "offline_table", flights, true);
			summary.append("table_strict_success=")
					.append(flights.stream().filter(flight -> flight.table().isStrictTableSuccess()).count()).append('\n');
			summary.append("table_strict_failed=")
					.append(flights.stream().filter(flight -> "TABLE_STRICT_FAILED".equals(flight.table().status())).count()).append('\n');
			summary.append("failed_or_skipped=")
					.append(flights.stream().filter(flight -> !"COMPLETE".equals(flight.status())).count()).append('\n');
			return summary.toString();
		}

		private static void appendMethodSummary(StringBuilder summary, String name,
				List<FlightComparison> flights, boolean table) {
			List<Double> errors = flights.stream()
					.map(flight -> flight.absoluteErrorPercent(table ? flight.table() : flight.baseline()))
					.filter(Double::isFinite)
					.toList();
			List<Double> signedErrors = flights.stream()
					.map(flight -> flight.signedErrorPercent(table ? flight.table() : flight.baseline()))
					.filter(Double::isFinite)
					.toList();
			long threePercent = flights.stream().filter(flight -> flight.withinPercent(table ? flight.table() : flight.baseline(), 3.0)).count();
			long fivePercent = flights.stream().filter(flight -> flight.withinPercent(table ? flight.table() : flight.baseline(), 5.0)).count();
			long tenPercent = flights.stream().filter(flight -> flight.withinPercent(table ? flight.table() : flight.baseline(), 10.0)).count();
			summary.append(name).append("_comparable_flights=").append(errors.size()).append('\n');
			summary.append(name).append("_mean_signed_error_percent=").append(number(mean(signedErrors))).append('\n');
			summary.append(name).append("_mean_absolute_error_percent=").append(number(mean(errors))).append('\n');
			summary.append(name).append("_within_3_percent=").append(threePercent).append('/').append(errors.size()).append('\n');
			summary.append(name).append("_within_5_percent=").append(fivePercent).append('/').append(errors.size()).append('\n');
			summary.append(name).append("_within_10_percent=").append(tenPercent).append('/').append(errors.size()).append('\n');
		}

		private static double mean(List<Double> values) {
			return values.isEmpty() ? Double.NaN : values.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
		}

		private static void appendCsvRow(StringBuilder csv, FlightComparison flight) {
			RunOutcome base = flight.baseline();
			RunOutcome table = flight.table();
			FailureSnapshot firstFailure = table.firstFailure();
			List<String> values = List.of(
					flight.flightName(), flight.status(), flight.truthSource(),
					flight.truthSchema() == null ? "" : flight.truthSchema().name(), flight.orkPath(), number(flight.actualApogeeMeters()),
					number(flight.truthTrajectory().peakAscentVelocityMS()), number(flight.truthTrajectory().timeToApogeeS()),
					base.status(), number(base.predictedApogeeMeters()), number(base.geometricApogeeMeters()),
					base.measurementMethod(), number(flight.signedErrorPercent(base)), number(flight.absoluteErrorPercent(base)),
					Boolean.toString(flight.withinPercent(base, 3.0)),
					Boolean.toString(flight.withinPercent(base, 5.0)), Boolean.toString(flight.withinPercent(base, 10.0)),
					table.status(), number(table.predictedApogeeMeters()), number(flight.signedErrorPercent(table)),
					number(table.geometricApogeeMeters()), table.measurementMethod(), number(flight.absoluteErrorPercent(table)),
					Boolean.toString(flight.withinPercent(table, 3.0)),
					Boolean.toString(flight.withinPercent(table, 5.0)), Boolean.toString(flight.withinPercent(table, 10.0)),
					base.runtimeMode(), table.runtimeMode(), table.tableSource(), number(base.maxMach()), number(table.maxMach()),
					number(base.peakAscentVelocityMS()), number(table.peakAscentVelocityMS()),
					number(base.timeToApogeeS()), number(table.timeToApogeeS()),
					Boolean.toString(table.tableValid()), Long.toString(table.totalQueries()), Long.toString(table.successfulTableQueries()),
					Long.toString(table.fallbackCount()), table.failureCounts(), table.runtimeFlags(), firstFailure.reason(),
					number(firstFailure.timeSeconds()), number(firstFailure.mach()), number(firstFailure.alphaDegrees()),
					number(firstFailure.betaDegrees()), number(firstFailure.poweredFraction()), number(firstFailure.reynoldsNumber()),
					firstFailure.detail(), base.failure(), table.failure());
			csv.append(values.stream().map(OfflineTableFlightComparisonHarness::escapeCsv)
					.collect(Collectors.joining(","))).append('\n');
		}
	}

	private static String number(double value) {
		return Double.isFinite(value) ? String.format(Locale.ROOT, "%.6f", value) : "";
	}

	private static String escapeCsv(String value) {
		String safe = value == null ? "" : value;
		return '"' + safe.replace("\"", "\"\"") + '"';
	}
}

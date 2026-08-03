package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import info.openrocket.core.aerodynamics.BarrowmanCalculator;
import info.openrocket.core.aerodynamics.AerodynamicForces;
import info.openrocket.core.aerodynamics.FlightConditions;
import info.openrocket.core.aerodynamics.physicsaero.body.AxisymmetricBodySolver;
import info.openrocket.core.aerodynamics.physicsaero.api.PhysicalTerm;
import info.openrocket.core.aerodynamics.physicsaero.config.SamplingConfiguration;
import info.openrocket.core.aerodynamics.physicsaero.flow.AtmosphereState;
import info.openrocket.core.aerodynamics.physicsaero.flow.PoweredFlowState;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.PerfectGasAir;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.GeometryExtractor;
import info.openrocket.core.aerodynamics.physicsaero.integration.PhysicsAeroTableService;
import info.openrocket.core.aerodynamics.physicsaero.integration.PhysicsAeroValidationGate;
import info.openrocket.core.aerodynamics.physicsaero.measurement.StandardAtmosphereBarometricAltimeter;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroMode;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroQueryException;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroRuntimeReport;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroTableCache;
import info.openrocket.core.aerodynamics.physicsaero.table.AerodynamicTable;
import info.openrocket.core.aerodynamics.physicsaero.validation.rasaero.RasaeroFlightDatasetReader;
import info.openrocket.core.aerodynamics.physicsaero.validation.rasaero.RasaeroFlightDatasetReader.RasaeroFlightRow;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.file.motor.RASPMotorLoader;
import info.openrocket.core.file.rasaero.RASAeroMotorsLoader;
import info.openrocket.core.logging.SimulationAbort;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.motor.ThrustCurveMotor;
import info.openrocket.core.models.atmosphere.AtmosphericConditions;
import info.openrocket.core.models.atmosphere.ExtendedISAModel;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.FlightConfigurationId;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.simulation.FlightData;
import info.openrocket.core.simulation.FlightDataBranch;
import info.openrocket.core.simulation.FlightDataType;
import info.openrocket.core.simulation.FlightEvent;
import info.openrocket.core.simulation.listeners.system.BoundedApogeeEndListener;
import info.openrocket.core.startup.OpenRocketCore;
import info.openrocket.core.startup.Application;

/**
 * Replays the public Rocket Flight Database against the current physics-aero
 * runtime. This is a comparison harness, not an accuracy gate: every result,
 * fallback, and unsupported case is written to machine-readable output.
 *
 * <p>Examples:
 * <pre>
 * ./gradlew core:flightDatabaseComparison -PflightId=8
 * ./gradlew core:flightDatabaseComparison -PflightIds=14,17,24
 * ./gradlew core:flightDatabaseComparison -PflightLimit=3
 * </pre>
 */
@Tag("benchmark")
@Tag("flight-database")
@ResourceLock("AERO_CPU_HEAVY")
class RocketFlightDatabaseComparisonTest {
	private static final double FT_PER_M = 3.280839895013123;
	private static final int RANDOM_SEED = 0x51A7EA;
	private static final String SETTINGS_HASH = "flight-database-powered-strict-table-v4";
	private static final long WALL_TIMEOUT_MS = 180_000;
	private static final Path REPORT_DIR =
			Path.of("build/reports/rocket-flight-database-comparison")
					.resolve(PhysicsAeroValidationGate.CODE_VERSION);
	private static final Map<Integer, String> MODEL_FILES = modelFiles();

	@BeforeAll
	static void initializeCore() {
		OpenRocketCore.initialize();
	}

	@Test
	void compareCurrentPhysicsAeroAgainstFlightDatabase() throws Exception {
		Path databaseRoot = resolveDirectory("flightDatabaseDir", "tmp/rocket-flight-database");
		Path referenceRoot = resolveDirectory("openRocketReferenceDir", "tmp/ref-supersonic");
		Path databaseCsv = databaseRoot.resolve("flight_comparison.csv");
		Path motorFile = referenceRoot.resolve("simvreal/rasp.eng");
		assertTrue(Files.isRegularFile(databaseCsv), "flight database CSV missing: " + databaseCsv);
		assertTrue(Files.isRegularFile(motorFile), "SimVReal motor file missing: " + motorFile);

		List<RasaeroFlightRow> selected = selectRows(new RasaeroFlightDatasetReader().read(databaseCsv));
		assertFalse(selected.isEmpty(), "flight filter selected no database rows");
		preloadRasaeroMotors(List.of(
				motorFile,
				referenceRoot.resolve("simvreal/Docs/Mesos/O4374_Sea_Level.eng"),
				referenceRoot.resolve("simvreal/Docs/Mesos/M787_Expanded_Nozzle_Sea_Level.eng")));

		Files.createDirectories(REPORT_DIR);
		writeInputManifest(REPORT_DIR.resolve("inputs.csv"), selected, referenceRoot);
		List<ComparisonRow> results = new ArrayList<>();
		for (RasaeroFlightRow row : selected) {
			String modelName = MODEL_FILES.get(row.flightId());
			if (modelName == null) {
				results.add(ComparisonRow.unsupported(row, "NO_PUBLIC_MODEL_MAPPING"));
				continue;
			}
			results.add(run(row, referenceRoot.resolve(modelName)));
		}

		writeCsv(REPORT_DIR.resolve("comparison.csv"), results);
		writeMarkdown(REPORT_DIR.resolve("summary.md"), results, databaseCsv, referenceRoot);

		List<ComparisonRow> attempted = results.stream().filter(ComparisonRow::supported).toList();
		for (ComparisonRow result : attempted) {
			assertTrue(result.errorMessage().isBlank(), result.vehicle() + ": " + result.errorMessage());
			assertTrue(Double.isFinite(result.currentApogeeFt()) && result.currentApogeeFt() > 0,
					result.vehicle() + " produced invalid apogee " + result.currentApogeeFt());
			assertTrue(result.totalQueries() > 0, result.vehicle() + " made no physics-aero runtime queries");
			assertTrue(result.tableQueries() > 0, result.vehicle() + " never used the physics-aero table");
			assertTrue(result.fallbackQueries() == 0,
					result.vehicle() + " strict table run used fallback queries: " + result.fallbackQueries());
			assertTrue(result.failureCounts().equals("{}"),
					result.vehicle() + " strict table run recorded failures: " + result.failureCounts());
		}
	}

	private static ComparisonRow run(RasaeroFlightRow row, Path model) {
		BaselineRun baseline = BaselineRun.EMPTY;
		if (!Files.isRegularFile(model)) return ComparisonRow.error(row, "MODEL_MISSING:" + model);
		try {
			OpenRocketDocument document = new GeneralRocketLoader(model.toFile()).load();
			Simulation simulation = selectOrSynthesizeSimulation(document, row.flightId());
			configureDeterministicApogeeRun(simulation, row.flightId());
			baseline = runBaseline(simulation.clone(false));
			if (!baseline.errorMessage().isBlank()) {
				return ComparisonRow.error(row, baseline,
						"BASELINE_SIMULATION_ERROR:" + baseline.errorMessage());
			}
			if (document.getRocket().getStageCount() != 1) {
				return ComparisonRow.unsupported(row, baseline,
						"PHYSICS_AERO_SINGLE_STAGE_ONLY");
			}
			/*
			 * Preserve the CDX1 launch temperature. RASAero uses standard-day
			 * pressure at the entered elevation when its optional barometric
			 * pressure is blank, but still anchors temperature and density to
			 * the entered temperature (User's Manual pp. 78-80).
			 */
			var launchConditions = new ExtendedISAModel(
					simulation.getOptions().getLaunchAltitude(),
					simulation.getOptions().getLaunchTemperature(),
					simulation.getOptions().getLaunchPressure(),
					simulation.getOptions().getLaunchRelativeHumidity())
					.getConditions(simulation.getOptions().getLaunchAltitude());
			AtmosphereState launchAtmosphere = new AtmosphereState(
					launchConditions.getPressure(), launchConditions.getTemperature(),
					launchConditions.getDensity(), launchConditions.getDynamicViscosity());
			boolean forceTurbulentBoundaryLayer =
					simulation.getOptions().isForceTurbulentBoundaryLayer();
			double nozzleExitDiameterM = simulation.getOptions().getNozzleExitDiameter();
			String settingsHash = referenceSettingsHash(
					launchAtmosphere, forceTurbulentBoundaryLayer, nozzleExitDiameterM);
			AeroGeometry geometry = new GeometryExtractor().extractWithComponentRoughness(
					simulation.getActiveConfiguration(), "ADIABATIC", settingsHash,
					forceTurbulentBoundaryLayer);
			writeGeometry(row.flightId(), geometry);
			writeBodyPressureSweep(row.flightId(), geometry, launchAtmosphere);
			TableArtifact artifact = loadOrBuildTable(geometry, settingsHash, launchAtmosphere,
					nozzleExitDiameterM);
			writeTableSweep(row.flightId(), artifact.table(),
					simulation.getActiveConfiguration(), launchConditions);
			simulation.getOptions().setPhysicsAeroTableIdentity(artifact.table(), artifact.contentHash());
			simulation.getOptions().setPhysicsAeroMode(PhysicsAeroMode.STRICT);

			AtomicReference<Throwable> thrown = new AtomicReference<>();
			Thread thread = new Thread(() -> {
				try {
				simulation.simulate(new BoundedApogeeEndListener());
				} catch (Throwable failure) {
					thrown.set(failure);
				}
			}, "flight-database-" + row.flightId());
			thread.setDaemon(true);
			thread.start();
			thread.join(WALL_TIMEOUT_MS);
			if (thread.isAlive()) {
				thread.interrupt();
				return ComparisonRow.error(row, baseline, runtimeReport(simulation),
						"TIMEOUT_AFTER_" + WALL_TIMEOUT_MS + "MS");
			}
			if (thrown.get() != null) {
				return ComparisonRow.error(row, baseline, runtimeReport(simulation),
						"SIMULATION_ERROR:" + message(thrown.get()));
			}

			FlightData data = simulation.getSimulatedData();
			if (data == null || data.getBranchCount() == 0) {
				return ComparisonRow.error(row, baseline, runtimeReport(simulation), "NO_FLIGHT_DATA");
			}
			FlightDataBranch branch = data.getBranch(0);
			writeTrajectory(row.flightId(), branch);
			TrajectoryMetrics metrics = trajectoryMetrics(branch);
			RasaeroTrajectoryMetrics referenceMetrics = rasaeroTrajectoryMetrics(model);
			PhysicsAeroRuntimeReport runtime = simulation.getPhysicsAeroRuntimeReport();
			double apogeeFt = data.getMaxAltitude() * FT_PER_M;
			MeasurementEstimate measurement =
					measurementEstimate(row, branch, apogeeFt);
			return ComparisonRow.success(row, baseline, apogeeFt,
					data.getMaxMachNumber(),
					terminalStatus(simulation, data), referenceMetrics, metrics,
					measurement, runtime);
		} catch (Throwable failure) {
			return ComparisonRow.error(row, baseline,
					failure.getClass().getSimpleName() + ":" + message(failure));
		}
	}

	/**
	 * The public sounding-rocket ORKs (rows 26--28) carry motor configurations
	 * and thrust curves but intentionally no saved {@link Simulation}.  Rebuild
	 * a run from the first motorized configuration rather than treating their
	 * flight truth as un-runnable.  Every active stage is retained for the
	 * established OpenRocket baseline; strict table aerodynamics remains
	 * explicitly single-stage only below.
	 */
	private static Simulation selectOrSynthesizeSimulation(OpenRocketDocument document,
			int flightId) {
		if (!document.getSimulations().isEmpty()) {
			return document.getSimulations().get(0);
		}
		FlightConfigurationId configurationId = document.getRocket().getIds().stream()
				.filter(id -> document.getRocket().getFlightConfiguration(id).hasMotors())
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("NO_MOTORIZED_CONFIGURATION"));
		document.getRocket().setSelectedConfiguration(configurationId);
		document.getRocket().getSelectedConfiguration().setAllStages();
		Simulation simulation = new Simulation(document, document.getRocket());
		document.addSimulation(simulation);
		// Explicitly bind the synthesized run: a new Simulation may otherwise
		// resolve to the empty default configuration despite the selected motors.
		simulation.setFlightConfigurationId(
				document.getRocket().getSelectedConfiguration().getFlightConfigurationID());
		applySoundingRocketLaunchConditions(flightId, simulation);
		return simulation;
	}

	/*
	 * These reconstruction values are source provenance, not tuning knobs:
	 * Bristol/NRC Black Brant V launch data gives 5 degrees and 30 m MSL;
	 * NACA TN 3739 Nike-Deacon flight sheets give 15 degrees from Wallops
	 * sea level.  The setup follows SoundingRocketCorpusV2Test from the
	 * companion Supersonic corpus, including first-motorized-config selection.
	 * They apply only to the no-simulation public ORKs above.
	 */
	private static void applySoundingRocketLaunchConditions(int flightId,
			Simulation simulation) {
		SoundingRocketLaunchConditions conditions = soundingRocketLaunchConditions(flightId);
		if (conditions == null) return;
		simulation.getOptions().setLaunchRodAngle(conditions.launchAngleRad());
		simulation.getOptions().setLaunchAltitude(conditions.launchAltitudeM());
	}

	static SoundingRocketLaunchConditions soundingRocketLaunchConditions(int flightId) {
		return switch (flightId) {
			case 26 -> new SoundingRocketLaunchConditions(Math.toRadians(5.0), 30.0);
			case 27, 28 -> new SoundingRocketLaunchConditions(Math.toRadians(15.0), 0.0);
			default -> null;
		};
	}

	private static void configureDeterministicApogeeRun(Simulation simulation,
			int flightId) {
		DeterministicAscentSettings settings = deterministicAscentSettings(flightId);
		simulation.getOptions().setTimeStep(settings.timeStepS());
		simulation.getOptions().setMaximumStepAngle(Math.toRadians(settings.maximumStepAngleDeg()));
		simulation.getOptions().setMaxSimulationTime(settings.maximumSimulationTimeS());
		simulation.getOptions().setRandomSeed(RANDOM_SEED);
	}

	/**
	 * Preserve the time integration used by the companion corpus for the three
	 * reconstructed sounding-rocket ascents.  The Black Brant's high
	 * acceleration needs 0.02 s; the Nike-Deacon ascent-only runs validated at
	 * 0.05 s / 5 degrees.  Other database rows retain the comparison suite's
	 * established deterministic settings.
	 */
	static DeterministicAscentSettings deterministicAscentSettings(int flightId) {
		return switch (flightId) {
			case 26 -> new DeterministicAscentSettings(0.02, 3.0, 900.0);
			case 27, 28 -> new DeterministicAscentSettings(0.05, 5.0, 320.0);
			default -> new DeterministicAscentSettings(0.05, 3.0, 2400.0);
		};
	}

	private static BaselineRun runBaseline(Simulation simulation) {
		try {
			simulation.getOptions().setPhysicsAeroMode(PhysicsAeroMode.OFF);
			AtomicReference<Throwable> thrown = new AtomicReference<>();
			Thread thread = new Thread(() -> {
				try {
				simulation.simulate(new BoundedApogeeEndListener());
				} catch (Throwable failure) {
					thrown.set(failure);
				}
			}, "flight-database-baseline");
			thread.setDaemon(true);
			thread.start();
			thread.join(WALL_TIMEOUT_MS);
			if (thread.isAlive()) {
				thread.interrupt();
				return BaselineRun.error("TIMEOUT_AFTER_" + WALL_TIMEOUT_MS + "MS");
			}
			if (thrown.get() != null) return BaselineRun.error(message(thrown.get()));
			FlightData data = simulation.getSimulatedData();
			if (data == null || data.getBranchCount() == 0) return BaselineRun.error("NO_FLIGHT_DATA");
			FlightDataBranch branch = data.getBranch(0);
			AoADiagnostics aoa = preApogeeAoADiagnostics(branch);
			return new BaselineRun(data.getMaxAltitude() * FT_PER_M,
					data.getMaxMachNumber(), aoa.rawMaximumDegrees(),
					aoa.maximumAtOrAbove100PaDegrees(), aoa.dynamicPressureTimeWeightedRmsDegrees(),
					terminalStatus(simulation, data), "");
		} catch (Throwable failure) {
			return BaselineRun.error(failure.getClass().getSimpleName() + ":" + message(failure));
		}
	}

	private static PhysicsAeroRuntimeReport runtimeReport(Simulation simulation) {
		try {
			return simulation.getPhysicsAeroRuntimeReport();
		} catch (RuntimeException ignored) {
			// A partially constructed simulation can fail before creating a report.
			return null;
		}
	}

	private static void writeTrajectory(int flightId, FlightDataBranch branch)
			throws IOException {
		Path directory = REPORT_DIR.resolve("trajectories");
		Files.createDirectories(directory);
		Path output = directory.resolve(String.format(Locale.ROOT,
				"flight-%02d.csv", flightId));
		try (BufferedWriter writer = Files.newBufferedWriter(output,
				StandardCharsets.UTF_8)) {
			writer.write("time_s,altitude_m,velocity_m_s,velocity_z_m_s,mach,"
					+ "thrust_N,drag_N,cd_total,cd_friction,cd_pressure,cd_base,"
					+ "mass_kg,cg_m,cp_m,stability_cal,aoa_deg,pitch_rate_deg_s,"
					+ "roll_rate_deg_s,yaw_rate_deg_s,air_pressure_Pa,air_density_kg_m3\n");
			for (int index = 0; index < branch.getLength(); index++) {
				writer.write(String.join(",",
						trajectoryValue(branch, FlightDataType.TYPE_TIME, index, 1),
						trajectoryValue(branch, FlightDataType.TYPE_ALTITUDE, index, 1),
						trajectoryValue(branch, FlightDataType.TYPE_VELOCITY_TOTAL, index, 1),
						trajectoryValue(branch, FlightDataType.TYPE_VELOCITY_Z, index, 1),
						trajectoryValue(branch, FlightDataType.TYPE_MACH_NUMBER, index, 1),
						trajectoryValue(branch, FlightDataType.TYPE_THRUST_FORCE, index, 1),
						trajectoryValue(branch, FlightDataType.TYPE_DRAG_FORCE, index, 1),
						trajectoryValue(branch, FlightDataType.TYPE_DRAG_COEFF, index, 1),
						trajectoryValue(branch, FlightDataType.TYPE_FRICTION_DRAG_COEFF, index, 1),
						trajectoryValue(branch, FlightDataType.TYPE_PRESSURE_DRAG_COEFF, index, 1),
						trajectoryValue(branch, FlightDataType.TYPE_BASE_DRAG_COEFF, index, 1),
						trajectoryValue(branch, FlightDataType.TYPE_MASS, index, 1),
						trajectoryValue(branch, FlightDataType.TYPE_CG_LOCATION, index, 1),
						trajectoryValue(branch, FlightDataType.TYPE_CP_LOCATION, index, 1),
						trajectoryValue(branch, FlightDataType.TYPE_STABILITY, index, 1),
						trajectoryValue(branch, FlightDataType.TYPE_AOA, index, 180 / Math.PI),
						trajectoryValue(branch, FlightDataType.TYPE_PITCH_RATE, index, 180 / Math.PI),
						trajectoryValue(branch, FlightDataType.TYPE_ROLL_RATE, index, 180 / Math.PI),
						trajectoryValue(branch, FlightDataType.TYPE_YAW_RATE, index, 180 / Math.PI),
						trajectoryValue(branch, FlightDataType.TYPE_AIR_PRESSURE, index, 1),
						trajectoryValue(branch, FlightDataType.TYPE_AIR_DENSITY, index, 1)));
				writer.newLine();
			}
		}
	}

	private static void writeGeometry(int flightId, AeroGeometry geometry)
			throws IOException {
		Path directory = REPORT_DIR.resolve("geometry");
		Files.createDirectories(directory);
		Path output = directory.resolve(String.format(Locale.ROOT,
				"flight-%02d.csv", flightId));
		try (BufferedWriter writer = Files.newBufferedWriter(output,
				StandardCharsets.UTF_8)) {
			writer.write("id,source_type,classification,start_m,end_m,"
					+ "root_radius_m,base_area_m2,fore_radius_m,aft_radius_m,"
					+ "minimum_slope,maximum_slope,fin_count,protuberance_type\n");
			for (var component : geometry.components()) {
				double minimumSlope = component.axisymmetricProfile() == null
						? Double.NaN : component.axisymmetricProfile().stations()
								.stream().mapToDouble(
										info.openrocket.core.aerodynamics.physicsaero.geometry.GeometryStation::slope)
								.min().orElse(Double.NaN);
				double maximumSlope = component.axisymmetricProfile() == null
						? Double.NaN : component.axisymmetricProfile().stations()
								.stream().mapToDouble(
										info.openrocket.core.aerodynamics.physicsaero.geometry.GeometryStation::slope)
								.max().orElse(Double.NaN);
				double finCount = component.finGeometry() == null
						? Double.NaN : component.finGeometry().count();
				String protuberanceType = component.protuberanceGeometry() == null
						? "" : component.protuberanceGeometry().type();
				writer.write(String.format(Locale.ROOT,
						"%s,%s,%s,%.9g,%.9g,%.9g,%.9g,%.9g,%.9g,"
								+ "%.9g,%.9g,%.9g,%s%n",
						csv(component.id()), csv(component.type()),
						csv(component.classification()),
						component.axialStartM(), component.axialEndM(),
						component.rootRadiusM(), component.baseAreaM2(),
						component.localReferences().getOrDefault(
								"foreRadiusM", Double.NaN),
						component.localReferences().getOrDefault(
								"aftRadiusM", Double.NaN),
						minimumSlope, maximumSlope, finCount,
						csv(protuberanceType)));
			}
		}
	}

	private static void writeBodyPressureSweep(int flightId,
			AeroGeometry geometry, AtmosphereState atmosphere)
			throws IOException {
		Path directory = REPORT_DIR.resolve("body-pressure-sweeps");
		Files.createDirectories(directory);
		Path output = directory.resolve(String.format(Locale.ROOT,
				"flight-%02d.csv", flightId));
		PerfectGasAir air = new PerfectGasAir();
		try (BufferedWriter writer = Files.newBufferedWriter(output,
				StandardCharsets.UTF_8)) {
			writer.write("mach,scope,component,owner,region,method,x_m,"
					+ "ca,pressure_ratio,mach_local\n");
			for (double mach : new double[] {1.2, 1.3, 1.5, 2, 3}) {
				var flow = info.openrocket.core.aerodynamics.physicsaero.flow.FlowCondition
						.fromAngles(mach, 0, 0, atmosphere, air, false,
								geometry.geometryHash());
				var result = new AxisymmetricBodySolver().evaluate(
						geometry, flow);
				double forceScale = flow.dynamicPressurePa()
						* geometry.references().referenceAreaM2();
				for (var contribution : result.contributions()) {
					PhysicalTerm term = contribution.owner().term();
					if (term != PhysicalTerm.BODY_PRESSURE_FOREBODY
							&& term != PhysicalTerm.BODY_PRESSURE_TRANSITION
							&& term != PhysicalTerm.BOATTAIL_PRESSURE_DRAG
							&& term != PhysicalTerm.BASE_PRESSURE_DRAG) {
						continue;
					}
					writer.write(String.format(Locale.ROOT,
							"%.9g,%s,%s,%s,%s,%s,%.9g,%.9g,,%n",
							mach, csv("contribution"),
							csv(contribution.componentId()),
							csv(term.name()), csv(contribution.regionId()),
							csv(contribution.methodId().value()),
							contribution.applicationPointM().x,
							contribution.forceBodyN().x / forceScale));
				}
				for (var state : result.edgeStateHistory().states()) {
					writer.write(String.format(Locale.ROOT,
							"%.9g,%s,,,%s,%s,%.9g,,%.9g,%.9g%n",
							mach, csv("edge-state"), csv("surface"),
							csv(state.methodId()), state.xM(),
							state.staticState().pressurePa()
									/ atmosphere.pressurePa(),
							state.staticState().mach()));
				}
			}
		}
	}

	private static void writeTableSweep(int flightId, AerodynamicTable table,
			FlightConfiguration configuration,
			AtmosphericConditions atmosphere)
			throws IOException {
		Path directory = REPORT_DIR.resolve("table-sweeps");
		Files.createDirectories(directory);
		Path output = directory.resolve(String.format(Locale.ROOT,
				"flight-%02d.csv", flightId));
		int alphaIndex = zeroAxisIndex(table.axes().alphaRad());
		int betaIndex = zeroAxisIndex(table.axes().betaRad());
		int coastIndex = zeroAxisIndex(table.axes().poweredFraction());
		int poweredIndex = maximumAxisIndex(table.axes().poweredFraction());
		BarrowmanCalculator establishedCalculator = new BarrowmanCalculator();
		try (BufferedWriter writer = Files.newBufferedWriter(output,
				StandardCharsets.UTF_8)) {
			writer.write("mach,scope,id,ca,cn,cy,cl,cm,cyaw,"
					+ "minimum_reynolds_ratio,methods,validity\n");
			double[] mach = table.axes().mach();
			for (int machIndex = 0; machIndex < mach.length; machIndex++) {
				FlightConditions establishedConditions =
						new FlightConditions(configuration);
				establishedConditions.setAtmosphericConditions(atmosphere);
				establishedConditions.setMach(mach[machIndex]);
				establishedConditions.setAOA(0);
				WarningSet establishedWarnings = new WarningSet();
				double establishedCa = establishedCalculator.getAerodynamicForces(
						configuration, establishedConditions,
						establishedWarnings).getCDaxial();
				writeCoefficientRow(writer, mach[machIndex],
						"established-total", "vehicle",
						new info.openrocket.core.aerodynamics.physicsaero.force.AerodynamicCoefficients(
								establishedCa, 0, 0, 0, 0, 0),
						Double.NaN, List.of("BARROWMAN_ESTABLISHED_COMPARATOR"),
						List.of("DIAGNOSTIC_ONLY"));
				Map<RocketComponent, AerodynamicForces> establishedComponents =
						establishedCalculator.getForceAnalysis(configuration,
								establishedConditions, establishedWarnings);
				for (var entry : establishedComponents.entrySet().stream()
						.sorted(java.util.Comparator.comparing(
								(Map.Entry<RocketComponent, AerodynamicForces> componentEntry) ->
								componentEntry.getKey().getComponentName() + ":"
										+ componentEntry.getKey().getName())).toList()) {
					RocketComponent component = entry.getKey();
					AerodynamicForces forces = entry.getValue();
					if (component == configuration.getRocket()) continue;
					String componentId = component.getComponentName() + ":" + component.getName();
					double instanceCount = component.getInstanceCount();
					writeEstablishedBreakdownRow(writer, mach[machIndex],
							"established-component", componentId,
							forces.getCD() * instanceCount);
					writeEstablishedBreakdownRow(writer, mach[machIndex],
							"established-pressure", componentId,
							forces.getPressureCD() * instanceCount);
					writeEstablishedBreakdownRow(writer, mach[machIndex],
							"established-friction", componentId,
							forces.getFrictionCD() * instanceCount);
					writeEstablishedBreakdownRow(writer, mach[machIndex],
							"established-base", componentId,
							forces.getBaseCD() * instanceCount);
				}
				var cell = table.cell(machIndex, alphaIndex, betaIndex, coastIndex);
				writeCoefficientRow(writer, mach[machIndex], "total", "vehicle",
						cell.coefficients(), cell.runtimeCorrection().minimumRatio(),
						cell.methodIds(), cell.validityFlags());
				for (var entry : cell.componentTotals().entrySet().stream()
						.sorted(Map.Entry.comparingByKey()).toList()) {
					writeCoefficientRow(writer, mach[machIndex], "component", entry.getKey(),
							entry.getValue(), cell.runtimeCorrection().minimumRatio(),
							cell.methodIds(), cell.validityFlags());
				}
				for (var entry : cell.ownerTotals().entrySet().stream()
						.sorted(Map.Entry.comparingByKey()).toList()) {
					writeCoefficientRow(writer, mach[machIndex], "owner", entry.getKey(),
							entry.getValue(), cell.runtimeCorrection().minimumRatio(),
							cell.methodIds(), cell.validityFlags());
				}
				if (poweredIndex != coastIndex) {
					var poweredCell = table.cell(machIndex, alphaIndex,
							betaIndex, poweredIndex);
					writeCoefficientRow(writer, mach[machIndex], "powered-total",
							"vehicle", poweredCell.coefficients(),
							poweredCell.runtimeCorrection().minimumRatio(),
							poweredCell.methodIds(), poweredCell.validityFlags());
					for (var entry : poweredCell.ownerTotals().entrySet().stream()
							.sorted(Map.Entry.comparingByKey()).toList()) {
						writeCoefficientRow(writer, mach[machIndex],
								"powered-owner", entry.getKey(),
								entry.getValue(),
								poweredCell.runtimeCorrection().minimumRatio(),
								poweredCell.methodIds(),
								poweredCell.validityFlags());
					}
				}
			}
		}
	}

	private static void writeEstablishedBreakdownRow(BufferedWriter writer,
			double mach, String scope, String componentId, double coefficient)
			throws IOException {
		if (!Double.isFinite(coefficient) || Math.abs(coefficient) < 1.0e-15) return;
		writeCoefficientRow(writer, mach, scope, componentId,
				new info.openrocket.core.aerodynamics.physicsaero.force.AerodynamicCoefficients(
						coefficient, 0, 0, 0, 0, 0),
				Double.NaN, List.of("BARROWMAN_ESTABLISHED_COMPONENT_BREAKDOWN"),
				List.of("DIAGNOSTIC_ONLY"));
	}

	private static void writeCoefficientRow(BufferedWriter writer, double mach,
			String scope, String id,
			info.openrocket.core.aerodynamics.physicsaero.force.AerodynamicCoefficients coefficients,
			double minimumReynoldsRatio, List<String> methods, List<String> validity)
			throws IOException {
		double[] values = coefficients.toArray();
		writer.write(String.format(Locale.ROOT,
				"%.9g,%s,%s,%.9g,%.9g,%.9g,%.9g,%.9g,%.9g,%.9g,%s,%s%n",
				mach, csv(scope), csv(id), values[0], values[1], values[2],
				values[3], values[4], values[5], minimumReynoldsRatio,
				csv(String.join("|", methods)), csv(String.join("|", validity))));
	}

	private static int zeroAxisIndex(double[] axis) {
		for (int index = 0; index < axis.length; index++) {
			if (axis[index] == 0) return index;
		}
		throw new IllegalStateException("required zero axis entry missing");
	}

	private static int maximumAxisIndex(double[] axis) {
		if (axis.length == 0) throw new IllegalStateException("required axis entry missing");
		int result = 0;
		for (int index = 1; index < axis.length; index++) {
			if (axis[index] > axis[result]) result = index;
		}
		return result;
	}

	private static String trajectoryValue(FlightDataBranch branch,
			FlightDataType type, int index, double scale) {
		List<Double> values = branch.get(type);
		if (values == null || index >= values.size()) return "";
		double value = values.get(index) * scale;
		return Double.isFinite(value) ? String.format(Locale.ROOT, "%.9g", value) : "";
	}

	private static TrajectoryMetrics trajectoryMetrics(FlightDataBranch branch) {
		List<Double> time = branch.get(FlightDataType.TYPE_TIME);
		List<Double> altitude = branch.get(FlightDataType.TYPE_ALTITUDE);
		List<Double> velocity = branch.get(FlightDataType.TYPE_VELOCITY_TOTAL);
		List<Double> drag = branch.get(FlightDataType.TYPE_DRAG_FORCE);
		List<Double> density = branch.get(FlightDataType.TYPE_AIR_DENSITY);
		if (time == null || time.isEmpty()) return TrajectoryMetrics.EMPTY;

		double burnoutTime = Double.NaN;
		FlightEvent burnout = branch.getLastEvent(FlightEvent.Type.BURNOUT);
		if (burnout != null) burnoutTime = burnout.getTime();
		double burnoutAltitudeFt = valueAtTime(time, altitude, burnoutTime) * FT_PER_M;
		double burnoutVelocity = valueAtTime(time, velocity, burnoutTime);

		double apogeeTime = Double.NaN;
		double maxAltitude = -Double.MAX_VALUE;
		double maxVelocity = Double.NaN;
		double maxDynamicPressure = Double.NaN;
		double dragImpulse = 0;
		double aerodynamicEnergyLoss = 0;
		int samples = minimumSize(time, altitude, velocity, drag, density);
		for (int index = 0; index < samples; index++) {
			double height = altitude.get(index);
			if (Double.isFinite(height) && height > maxAltitude) {
				maxAltitude = height;
				apogeeTime = time.get(index);
			}
			double dynamicPressure = 0.5 * density.get(index) * velocity.get(index) * velocity.get(index);
			if (Double.isFinite(velocity.get(index))
					&& (!Double.isFinite(maxVelocity) || velocity.get(index) > maxVelocity)) {
				maxVelocity = velocity.get(index);
			}
			if (Double.isFinite(dynamicPressure)
					&& (!Double.isFinite(maxDynamicPressure) || dynamicPressure > maxDynamicPressure)) {
				maxDynamicPressure = dynamicPressure;
			}
			if (index == 0) continue;
			double dt = time.get(index) - time.get(index - 1);
			if (!(dt > 0) || !Double.isFinite(dt)) continue;
			double dragAverage = 0.5 * (drag.get(index - 1) + drag.get(index));
			double powerBefore = drag.get(index - 1) * velocity.get(index - 1);
			double powerAfter = drag.get(index) * velocity.get(index);
			if (Double.isFinite(dragAverage)) dragImpulse += dragAverage * dt;
			if (Double.isFinite(powerBefore) && Double.isFinite(powerAfter)) {
				aerodynamicEnergyLoss += 0.5 * (powerBefore + powerAfter) * dt;
			}
		}
		return new TrajectoryMetrics(burnoutTime, burnoutAltitudeFt, burnoutVelocity,
				maxVelocity, maxDynamicPressure, apogeeTime, dragImpulse, aerodynamicEnergyLoss);
	}

	private static AoADiagnostics preApogeeAoADiagnostics(FlightDataBranch branch) {
		return preApogeeAoADiagnostics(
				branch.get(FlightDataType.TYPE_TIME),
				branch.get(FlightDataType.TYPE_ALTITUDE),
				branch.get(FlightDataType.TYPE_AOA),
				branch.get(FlightDataType.TYPE_VELOCITY_TOTAL),
				branch.get(FlightDataType.TYPE_AIR_DENSITY));
	}

	/**
	 * Computes pre-apogee AoA metrics in degrees.  The raw maximum is retained
	 * for diagnostics; the q>=100 Pa maximum and q-time-weighted RMS suppress
	 * the low-speed attitude singularity near apogee.
	 */
	static AoADiagnostics preApogeeAoADiagnostics(List<Double> time,
			List<Double> altitude, List<Double> aoa, List<Double> velocity,
			List<Double> density) {
		int samples = minimumSize(time, altitude, aoa, velocity, density);
		if (samples == 0) return AoADiagnostics.EMPTY;
		int apogeeIndex = 0;
		for (int index = 1; index < samples; index++) {
			if (isFinite(altitude.get(index))
					&& (!isFinite(altitude.get(apogeeIndex))
							|| altitude.get(index) > altitude.get(apogeeIndex))) {
				apogeeIndex = index;
			}
		}
		double rawMaximumRadians = Double.NaN;
		double q100MaximumRadians = Double.NaN;
		double weightedSquareIntegral = 0;
		double pressureTimeIntegral = 0;
		for (int index = 0; index <= apogeeIndex; index++) {
			Double alpha = aoa.get(index);
			double q = dynamicPressure(density.get(index), velocity.get(index));
			if (isFinite(alpha)) {
				double magnitude = Math.abs(alpha);
				if (!Double.isFinite(rawMaximumRadians) || magnitude > rawMaximumRadians) {
					rawMaximumRadians = magnitude;
				}
				if (q >= 100.0 && (!Double.isFinite(q100MaximumRadians)
						|| magnitude > q100MaximumRadians)) {
					q100MaximumRadians = magnitude;
				}
			}
			if (index == 0) continue;
			Double t0 = time.get(index - 1);
			Double t1 = time.get(index);
			Double alpha0 = aoa.get(index - 1);
			Double alpha1 = aoa.get(index);
			double q0 = dynamicPressure(density.get(index - 1), velocity.get(index - 1));
			double q1 = q;
			if (!isFinite(t0) || !isFinite(t1) || !isFinite(alpha0) || !isFinite(alpha1)
					|| !Double.isFinite(q0) || !Double.isFinite(q1)) {
				continue;
			}
			double dt = t1 - t0;
			if (!(dt > 0.0)) continue;
			pressureTimeIntegral += 0.5 * (q0 + q1) * dt;
			weightedSquareIntegral += 0.5 * (q0 * alpha0 * alpha0
					+ q1 * alpha1 * alpha1) * dt;
		}
		double rmsRadians = pressureTimeIntegral > 0.0
				? Math.sqrt(weightedSquareIntegral / pressureTimeIntegral) : Double.NaN;
		return new AoADiagnostics(toDegrees(rawMaximumRadians), toDegrees(q100MaximumRadians),
				toDegrees(rmsRadians));
	}

	private static double dynamicPressure(Double density, Double velocity) {
		if (!isFinite(density) || !isFinite(velocity)) return Double.NaN;
		double result = 0.5 * density * velocity * velocity;
		return Double.isFinite(result) && result >= 0.0 ? result : Double.NaN;
	}

	private static boolean isFinite(Double value) {
		return value != null && Double.isFinite(value);
	}

	private static double toDegrees(double radians) {
		return Double.isFinite(radians) ? Math.toDegrees(radians) : Double.NaN;
	}

	record AoADiagnostics(double rawMaximumDegrees,
			double maximumAtOrAbove100PaDegrees,
			double dynamicPressureTimeWeightedRmsDegrees) {
		private static final AoADiagnostics EMPTY =
				new AoADiagnostics(Double.NaN, Double.NaN, Double.NaN);
	}

	private static MeasurementEstimate measurementEstimate(
			RasaeroFlightRow source, FlightDataBranch branch,
			double geometricApogeeFt) {
		if (!isBarometricAltimeter(source.flightDataType())) {
			return new MeasurementEstimate(geometricApogeeFt,
					"DIRECT_GEOMETRIC_COMPARISON_NO_SENSOR_TRANSFER_MODEL");
		}
		List<Double> altitude = branch.get(FlightDataType.TYPE_ALTITUDE);
		List<Double> pressure = branch.get(FlightDataType.TYPE_AIR_PRESSURE);
		int samples = minimumSize(altitude, pressure);
		if (samples == 0) {
			throw new IllegalStateException(
					"barometric comparison requires altitude and pressure histories");
		}
		int apogeeIndex = 0;
		for (int index = 1; index < samples; index++) {
			if (altitude.get(index) > altitude.get(apogeeIndex)) {
				apogeeIndex = index;
			}
		}
		double indicatedM = barometricAltitudeAbovePadM(
				pressure.get(0), pressure.get(apogeeIndex));
		return new MeasurementEstimate(indicatedM * FT_PER_M,
				StandardAtmosphereBarometricAltimeter.METHOD_ID);
	}

	static boolean isBarometricAltimeter(String flightDataType) {
		return flightDataType != null
				&& "Barometric Altimeter".equalsIgnoreCase(flightDataType.trim());
	}

	/**
	 * Converts the simulation's static-pressure history into the observation
	 * space of an uncorrected standard-atmosphere barometric altimeter.  Keeping
	 * this separate from the aerodynamic run makes the scoring transfer
	 * independently testable and prevents it from becoming an aero correction.
	 */
	static double barometricAltitudeAbovePadM(double padPressurePa,
			double apogeePressurePa) {
		return new StandardAtmosphereBarometricAltimeter()
				.heightAbovePadM(padPressurePa, apogeePressurePa);
	}

	private static RasaeroTrajectoryMetrics rasaeroTrajectoryMetrics(Path model) throws IOException {
		if (!model.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cdx1")) {
			return RasaeroTrajectoryMetrics.EMPTY;
		}
		String xml = Files.readString(model, StandardCharsets.UTF_8);
		return new RasaeroTrajectoryMetrics(
				xmlValue(xml, "MaxVelocity") / FT_PER_M,
				xmlValue(xml, "TimetoApogee"));
	}

	private static double xmlValue(String xml, String element) {
		Matcher matcher = Pattern.compile("<" + element
				+ ">\\s*([-+]?\\d+(?:\\.\\d+)?(?:[Ee][-+]?\\d+)?)\\s*</" + element + ">")
				.matcher(xml);
		if (!matcher.find()) return Double.NaN;
		try {
			return Double.parseDouble(matcher.group(1));
		} catch (NumberFormatException ignored) {
			return Double.NaN;
		}
	}

	@SafeVarargs
	private static int minimumSize(List<Double>... values) {
		int size = Integer.MAX_VALUE;
		for (List<Double> value : values) {
			if (value == null) return 0;
			size = Math.min(size, value.size());
		}
		return size == Integer.MAX_VALUE ? 0 : size;
	}

	private static double valueAtTime(List<Double> time, List<Double> values, double requestedTime) {
		if (time == null || values == null || time.isEmpty() || values.isEmpty()
				|| !Double.isFinite(requestedTime)) return Double.NaN;
		int samples = Math.min(time.size(), values.size());
		if (requestedTime <= time.get(0)) return values.get(0);
		for (int index = 1; index < samples; index++) {
			double afterTime = time.get(index);
			if (requestedTime > afterTime) continue;
			double beforeTime = time.get(index - 1);
			double span = afterTime - beforeTime;
			if (!(span > 0)) return values.get(index);
			double fraction = (requestedTime - beforeTime) / span;
			return values.get(index - 1) + fraction * (values.get(index) - values.get(index - 1));
		}
		return values.get(samples - 1);
	}

	private static TableArtifact loadOrBuildTable(AeroGeometry geometry, String settingsHash,
			AtmosphereState atmosphere, double nozzleExitDiameterM) throws IOException {
		SamplingConfiguration sampling = SamplingConfiguration.flightDomainDefaults();
		double[] mach = sampling.mach();
		double[] alpha = sampling.alphaRad();
		double[] beta = sampling.betaRad();
		PhysicsAeroTableCache cache = new PhysicsAeroTableCache();
		PhysicsAeroTableCache.Key key = new PhysicsAeroTableCache.Key(geometry.geometryHash(), settingsHash,
				PhysicsAeroValidationGate.CODE_VERSION, PhysicsAeroValidationGate.REGISTRY_VERSION);
		try {
			var cached = cache.load(key, "");
			if (cached.isPresent()
					&& Arrays.equals(cached.get().axes().mach(), mach)
					&& Arrays.equals(cached.get().axes().alphaRad(), alpha)
					&& Arrays.equals(cached.get().axes().betaRad(), beta)) {
				return new TableArtifact(cached.get(), PhysicsAeroTableCache.contentHash(cache.tablePath(key)));
			}
		} catch (IOException | IllegalArgumentException ignored) {
			// A stale or corrupt cache entry is rebuilt below from the current code.
		}

		PerfectGasAir air = new PerfectGasAir();
		double pressurePa = atmosphere.pressurePa();
		PoweredFlowState poweredState;
		if (Double.isFinite(nozzleExitDiameterM) && nozzleExitDiameterM > 0) {
			double nozzleExitAreaM2 = Math.PI * nozzleExitDiameterM
					* nozzleExitDiameterM / 4;
			poweredState = PoweredFlowState.nozzleGeometryOnly(
					1, nozzleExitAreaM2, pressurePa);
		} else {
			poweredState = PoweredFlowState.unmodeledPoweredBaseline(pressurePa);
		}
		PoweredFlowState[] poweredStates = {
				PoweredFlowState.coast(pressurePa), poweredState
		};
		PhysicsAeroTableService.Result built = new PhysicsAeroTableService(cache).buildToCache(
				geometry, mach, alpha, beta, poweredStates, atmosphere, air, settingsHash,
				new AtomicBoolean(), ignored -> { });
		return new TableArtifact(built.table(), built.tableHash());
	}

	private static String referenceSettingsHash(AtmosphereState atmosphere,
			boolean forceTurbulentBoundaryLayer, double nozzleExitDiameterM) {
		return String.format(Locale.US, "%s-bl%s-p%.3f-t%.3f-rho%.8f-mu%.12g-nozzle%.9g",
				SETTINGS_HASH,
				forceTurbulentBoundaryLayer ? "fully-turbulent" : "natural-transition",
				atmosphere.pressurePa(), atmosphere.temperatureK(), atmosphere.densityKgM3(),
				atmosphere.dynamicViscosityPaS(), nozzleExitDiameterM);
	}

	private static List<RasaeroFlightRow> selectRows(List<RasaeroFlightRow> rows) {
		String idValue = System.getProperty("flightId", "").trim();
		Set<Integer> idValues = integerSetProperty("flightIds");
		if (!idValue.isEmpty()) idValues.add(Integer.parseInt(idValue));
		int limit = integerProperty("flightLimit", Integer.MAX_VALUE);
		return rows.stream()
				.filter(row -> row.flightId() <= 28)
				.filter(row -> idValues.isEmpty() || idValues.contains(row.flightId()))
				.limit(limit)
				.toList();
	}

	private static Set<Integer> integerSetProperty(String name) {
		Set<Integer> values = new LinkedHashSet<>();
		String configured = System.getProperty(name, "").trim();
		if (configured.isEmpty()) return values;
		for (String token : configured.split(",")) {
			try {
				int parsed = Integer.parseInt(token.trim());
				if (parsed <= 0) throw new NumberFormatException();
				values.add(parsed);
			} catch (NumberFormatException exception) {
				throw new IllegalArgumentException(name + " must be a comma-separated list of positive integers: "
						+ configured);
			}
		}
		return values;
	}

	private static void preloadRasaeroMotors(List<Path> motorFiles) throws Exception {
		RASAeroMotorsLoader.clearAllMotors();
		int loaded = 0;
		for (Path motorFile : motorFiles) {
			if (!Files.isRegularFile(motorFile)) continue;
			for (String definition : splitRaspDefinitions(Files.readAllLines(motorFile, StandardCharsets.UTF_8))) {
				try (InputStream input = new ByteArrayInputStream(definition.getBytes(StandardCharsets.UTF_8))) {
					for (ThrustCurveMotor.Builder builder : new RASPMotorLoader().load(input,
							motorFile.getFileName().toString())) {
						Application.getThrustCurveMotorSetDatabase().addMotor(builder.build());
						loaded++;
					}
				} catch (Exception ignored) {
					// Parse definitions independently so one malformed historical entry
					// cannot hide the usable corpus motors.
				}
			}
		}
		assertTrue(loaded > 0, "no usable RASAero motors loaded from " + motorFiles);
	}

	private static List<String> splitRaspDefinitions(List<String> lines) {
		List<String> definitions = new ArrayList<>();
		StringBuilder current = null;
		for (String line : lines) {
			String trimmed = line.trim();
			if (isRaspHeader(trimmed)) {
				if (current != null) definitions.add(current.append(";\n").toString());
				current = new StringBuilder(line).append('\n');
			} else if (current != null && !trimmed.startsWith(";") && !trimmed.isEmpty()) {
				current.append(line).append('\n');
			}
		}
		if (current != null) definitions.add(current.append(";\n").toString());
		return definitions;
	}

	private static boolean isRaspHeader(String line) {
		if (line.isEmpty() || line.startsWith(";")) return false;
		String[] tokens = line.split("\\s+");
		if (tokens.length < 7) return false;
		try {
			Double.parseDouble(tokens[1]);
			Double.parseDouble(tokens[2]);
			Double.parseDouble(tokens[4]);
			Double.parseDouble(tokens[5]);
			return true;
		} catch (NumberFormatException ignored) {
			return false;
		}
	}

	private static String terminalStatus(Simulation simulation, FlightData data) {
		for (int index = 0; index < data.getBranchCount(); index++) {
			FlightDataBranch branch = data.getBranch(index);
			FlightEvent abort = branch.getFirstEvent(FlightEvent.Type.SIM_ABORT);
			if (abort != null) {
				String cause = abort.getData() instanceof SimulationAbort simulationAbort
						? ":" + simulationAbort.getCause().name()
						: "";
				return "SIM_ABORT@" + format(abort.getTime()) + cause;
			}
			FlightEvent end = branch.getLastEvent(FlightEvent.Type.SIMULATION_END);
			boolean ground = branch.getLastEvent(FlightEvent.Type.GROUND_HIT) != null;
			if (end != null && !ground
					&& end.getTime() >= simulation.getOptions().getMaxSimulationTime()
							- simulation.getOptions().getTimeStep()) {
				return "MAXTIME@" + format(end.getTime());
			}
		}
		return "NORMAL";
	}

	/**
	 * Writes the exact imported-model and launch-option inputs used by both the
	 * baseline and strict-table runs.  The source-model checksum is deliberately
	 * included instead of copying values such as component mass and CG into a
	 * second hand-maintained fixture: the CDX1/ORK itself remains the single
	 * authoritative input for all vehicle properties.
	 */
	private static void writeInputManifest(Path output, List<RasaeroFlightRow> rows,
			Path referenceRoot) throws IOException {
		try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
			writer.write("flight_id,vehicle,measured_apogee_ft,flight_data_type,model_relative_path,"
					+ "model_sha256,stage_count,strict_table_eligible,flight_configuration_id,"
					+ "launch_rod_length_m,launch_rod_angle_deg,launch_rod_direction_deg,"
					+ "launch_altitude_m,launch_temperature_k,launch_pressure_pa,"
					+ "launch_relative_humidity,is_isa_atmosphere,wind_model_type,"
					+ "average_wind_speed_m_s,average_wind_stddev_m_s,"
					+ "force_turbulent_boundary_layer,nozzle_exit_diameter_m,"
					+ "time_step_s,maximum_step_angle_deg,maximum_simulation_time_s,"
					+ "random_seed,manifest_error\n");
			for (RasaeroFlightRow row : rows) {
				String relativeModel = MODEL_FILES.get(row.flightId());
				if (relativeModel == null) {
					writeInputManifestRow(writer, row, "", "", Double.NaN, false, "", null,
							"NO_PUBLIC_MODEL_MAPPING");
					continue;
				}
				Path model = referenceRoot.resolve(relativeModel);
				if (!Files.isRegularFile(model)) {
					writeInputManifestRow(writer, row, relativeModel, "", Double.NaN, false, "", null,
							"MODEL_MISSING:" + model);
					continue;
				}
				try {
					OpenRocketDocument document = new GeneralRocketLoader(model.toFile()).load();
					Simulation simulation = selectOrSynthesizeSimulation(document, row.flightId());
					configureDeterministicApogeeRun(simulation, row.flightId());
					writeInputManifestRow(writer, row, relativeModel, sha256(model),
							document.getRocket().getStageCount(),
							document.getRocket().getStageCount() == 1,
							simulation.getFlightConfigurationId().toFullKey(), simulation, "");
				} catch (Exception exception) {
					writeInputManifestRow(writer, row, relativeModel, "", Double.NaN,
							false, "", null, exception.getClass().getSimpleName() + ":" + message(exception));
				}
			}
		}
	}

	private static void writeInputManifestRow(BufferedWriter writer, RasaeroFlightRow row,
			String relativeModel, String modelHash, double stageCount, boolean strictEligible,
			String configurationId, Simulation simulation, String manifestError) throws IOException {
		if (simulation == null) {
			writer.write(String.join(",", Integer.toString(row.flightId()), csv(row.vehicle()),
					finite(row.measuredApogeeFt(), "%.3f"), csv(row.flightDataType()),
					csv(relativeModel), csv(modelHash), finite(stageCount, "%.0f"),
					Boolean.toString(strictEligible), csv(configurationId), "", "", "", "", "", "",
					"", "", "", "", "", "", "", "", "", "", "", "", csv(manifestError)) + "\n");
			return;
		}
		var options = simulation.getOptions();
		writer.write(String.join(",", Integer.toString(row.flightId()), csv(row.vehicle()),
				finite(row.measuredApogeeFt(), "%.3f"), csv(row.flightDataType()),
				csv(relativeModel), csv(modelHash), finite(stageCount, "%.0f"),
				Boolean.toString(strictEligible), csv(configurationId),
				finite(options.getLaunchRodLength(), "%.6f"),
				finite(Math.toDegrees(options.getLaunchRodAngle()), "%.6f"),
				finite(Math.toDegrees(options.getLaunchRodDirection()), "%.6f"),
				finite(options.getLaunchAltitude(), "%.3f"),
				finite(options.getLaunchTemperature(), "%.6f"),
				finite(options.getLaunchPressure(), "%.3f"),
				finite(options.getLaunchRelativeHumidity(), "%.6f"),
				Boolean.toString(options.isISAAtmosphere()),
				csv(options.getWindModelType().name()),
				finite(options.getAverageWindModel().getAverage(), "%.6f"),
				finite(options.getAverageWindModel().getStandardDeviation(), "%.6f"),
				Boolean.toString(options.isForceTurbulentBoundaryLayer()),
				finite(options.getNozzleExitDiameter(), "%.9f"),
				finite(options.getTimeStep(), "%.6f"),
				finite(Math.toDegrees(options.getMaximumStepAngle()), "%.6f"),
				finite(options.getMaxSimulationTime(), "%.6f"),
				Long.toString(options.getRandomSeed()), csv(manifestError)) + "\n");
	}

	private static void writeCsv(Path output, List<ComparisonRow> rows) throws IOException {
		try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
			writer.write("flight_id,vehicle,supported,reason,measured_apogee_ft,rasaero_apogee_ft,"
					+ "published_orp_apogee_ft,baseline_openrocket_apogee_ft,baseline_openrocket_error_pct,"
					+ "baseline_openrocket_peak_mach,baseline_raw_max_pre_apogee_aoa_deg,"
					+ "baseline_max_pre_apogee_aoa_q_ge_100_pa_deg,"
					+ "baseline_q_time_weighted_rms_pre_apogee_aoa_deg,"
					+ "baseline_terminal_status,current_apogee_ft,"
					+ "flight_data_type,measurement_model_id,"
					+ "current_instrument_comparable_apogee_ft,"
					+ "rasaero_error_pct,published_orp_error_pct,"
					+ "current_error_pct,current_vs_rasaero_pct,current_vs_published_pct,"
					+ "current_instrument_comparable_error_pct,"
					+ "current_instrument_comparable_abs_error_pct,"
					+ "current_instrument_comparable_within_3_percent,"
					+ "published_orp_peak_mach,current_peak_mach,"
					+ "current_vs_published_orp_peak_mach_pct,"
					+ "measured_apogee_abs_error_pct,"
					+ "reference_max_velocity_m_s,current_max_velocity_m_s,"
					+ "burnout_time_s,burnout_altitude_ft,burnout_velocity_m_s,max_dynamic_pressure_pa,"
					+ "reference_time_to_apogee_s,current_time_to_apogee_s,"
					+ "drag_impulse_n_s,aerodynamic_energy_loss_j,"
					+ "terminal_status,total_queries,table_queries,fallback_queries,table_coverage_pct,"
					+ "runtime_flags,failure_counts,first_failure_occurrences,error_message\n");
			for (ComparisonRow row : rows) writer.write(row.toCsv() + "\n");
		}
	}

	private static void writeMarkdown(Path output, List<ComparisonRow> rows,
			Path sourceCsv, Path referenceRoot) throws Exception {
		List<ComparisonRow> completed = rows.stream()
				.filter(row -> row.supported() && row.errorMessage().isBlank()).toList();
		List<ComparisonRow> baselineCompleted = rows.stream()
				.filter(row -> row.supported() && Double.isFinite(row.baselineApogeeFt())).toList();
		double baselineMae = baselineCompleted.stream()
				.mapToDouble(row -> Math.abs(row.baselineErrorPct())).average().orElse(Double.NaN);
		long baselineWithinFive = baselineCompleted.stream()
				.filter(row -> Math.abs(row.baselineErrorPct()) <= 5).count();
		long baselineWithinThree = baselineCompleted.stream()
				.filter(row -> Math.abs(row.baselineErrorPct()) <= 3).count();
		long baselineWithinTen = baselineCompleted.stream()
				.filter(row -> Math.abs(row.baselineErrorPct()) <= 10).count();
		double currentMae = completed.stream().mapToDouble(row -> Math.abs(row.currentErrorPct())).average().orElse(Double.NaN);
		double instrumentComparableMae = completed.stream()
				.mapToDouble(row -> Math.abs(
						row.instrumentComparableErrorPct()))
				.average().orElse(Double.NaN);
		double instrumentComparableBias = completed.stream()
				.mapToDouble(ComparisonRow::instrumentComparableErrorPct)
				.average().orElse(Double.NaN);
		List<ComparisonRow> rasaeroComparable = completed.stream()
				.filter(row -> Double.isFinite(row.currentVsRasaeroPct())).toList();
		double currentVsRasaeroMae = rasaeroComparable.stream()
				.mapToDouble(row -> Math.abs(row.currentVsRasaeroPct())).average().orElse(Double.NaN);
		double publishedMae = completed.stream().filter(row -> Double.isFinite(row.publishedErrorPct()))
				.mapToDouble(row -> Math.abs(row.publishedErrorPct())).average().orElse(Double.NaN);
		double publishedOrpMachDeltaMae = completed.stream()
				.mapToDouble(ComparisonRow::publishedOrpPeakMachDeltaPct)
				.filter(Double::isFinite).map(Math::abs).average().orElse(Double.NaN);
		double coverage = completed.stream().mapToDouble(ComparisonRow::tableCoveragePct).average().orElse(Double.NaN);
		long withinFive = completed.stream().filter(row -> Math.abs(row.currentErrorPct()) <= 5).count();
		long withinThree = completed.stream().filter(row -> Math.abs(row.currentErrorPct()) <= 3).count();
		long withinTen = completed.stream().filter(row -> Math.abs(row.currentErrorPct()) <= 10).count();
		long instrumentWithinFive = completed.stream()
				.filter(row -> Math.abs(
						row.instrumentComparableErrorPct()) <= 5).count();
		long instrumentWithinThree = completed.stream()
				.filter(row -> Math.abs(
						row.instrumentComparableErrorPct()) <= 3).count();
		long instrumentWithinTen = completed.stream()
				.filter(row -> Math.abs(
						row.instrumentComparableErrorPct()) <= 10).count();
		long withinOneRasaero = rasaeroComparable.stream()
				.filter(row -> Math.abs(row.currentVsRasaeroPct()) <= 1).count();
		long abnormal = completed.stream().filter(row -> !"NORMAL".equals(row.terminalStatus())).count();
		StringBuilder markdown = new StringBuilder();
		markdown.append("# Rocket Flight Database comparison\n\n")
				.append("- Database CSV SHA-256: `").append(sha256(sourceCsv)).append("`\n")
				.append("- Database repository commit: `")
				.append(gitRevision(sourceCsv.getParent())).append("`\n")
				.append("- Reference repository: `").append(referenceRoot.toAbsolutePath()).append("`\n")
				.append("- Reference repository commit: `").append(gitRevision(referenceRoot)).append("`\n")
				.append("- Current repository commit: `")
				.append(gitRevision(Path.of("").toAbsolutePath())).append("`\n")
				.append("- Runtime mode: `STRICT` table-only aerodynamics with coast and explicit powered states; any out-of-domain query fails the row\n")
				.append(String.format(Locale.US,
						"- Baseline OpenRocket: MAE %.3f%%; within +/-3%% %d/%d; within +/-5%% %d/%d; within +/-10%% %d/%d%n",
						baselineMae, baselineWithinThree, baselineCompleted.size(),
						baselineWithinFive, baselineCompleted.size(),
						baselineWithinTen, baselineCompleted.size()))
				.append("- Each table's reference Reynolds state is the imported launch-site atmosphere\n")
				.append("- Atmosphere preserves each CDX1 launch temperature; blank RASAero pressure uses standard-day pressure at launch elevation\n")
				.append("- Published measured apogees are compared directly, matching the Rocket Flight Database and RASAero benchmark convention\n")
				.append("- Instrument-comparable scoring additionally converts simulated static pressure to U.S. Standard Atmosphere pressure altitude and subtracts the pad indication for rows labeled `Barometric Altimeter`; all other sensor types retain geometric apogee because no source-specific transfer model is available\n")
				.append("- Geometric and instrument-comparable apogees are both retained; the pressure-altitude transfer changes measurement space, not vehicle dynamics or aerodynamic coefficients\n")
				.append("- Database `peak_mach` is a published OpenRocket Plus prediction, not a measured flight observable; it is reported only as a model-regression diagnostic and is excluded from accuracy scoring\n")
				.append("- Imported nozzle diameters retained for powered-flow modeling; "
						+ "when present, altitude thrust adds `(101325 Pa - ambient pressure) * exit area` "
						+ "using `SEA_LEVEL_REFERENCED_NOZZLE_PRESSURE_THRUST_V1`\n")
				.append("- RASAero nozzle-geometry-only inputs use the RASAero II v1.0.2.0 empirical power-on correction `delta CD = -F(M) * A_exit/A_reference`; resolved nozzle thermodynamics remain owned by the measured NACA RM L54D27 model over Mach 0.8-1.2\n")
				.append("- Supersonic conical diameter expansions use the RASAero II v1.0.2.0 empirical annular-area/Mach wave-drag correlation; steep terminal reducers additionally use its 17.5-degree separated-flow pressure-drag envelope\n")
				.append("- Supersonic assembled base drag is bounded by the RASAero II v1.0.2.0 Mach polynomial and cubic terminal-diameter recovery; the envelope only reduces a higher finned-base closure and never adds suction\n")
				.append("- Deterministic random seed: `").append(RANDOM_SEED).append("`\n\n")
				.append("- Simulation stops at apogee; runtime query coverage is therefore pre-apogee coverage\n\n")
				.append(String.format(Locale.US,
						"Completed %d of %d selected rows. Current-vs-RASAero MAE %.3f%%; "
								+ "within +/-1%% of RASAero %d/%d. Current-vs-measured MAE %.3f%%; "
								+ "instrument-comparable measured MAE %.3f%% with signed bias %+.3f%% "
								+ "(within +/-3%% %d/%d; within +/-5%% %d/%d; within +/-10%% %d/%d); "
								+ "published ORP-vs-measured MAE %.3f%%; "
								+ "within +/-3%% %d/%d; within +/-5%% %d/%d; within +/-10%% %d/%d; abnormal endings %d; "
								+ "current-vs-published-ORP peak-Mach diagnostic MAE %.3f%%; "
								+ "mean pre-apogee table coverage %.2f%%.%n%n",
						completed.size(), rows.size(), currentVsRasaeroMae, withinOneRasaero,
						rasaeroComparable.size(), currentMae, instrumentComparableMae,
						instrumentComparableBias, instrumentWithinThree, completed.size(),
						instrumentWithinFive, completed.size(),
						instrumentWithinTen, completed.size(), publishedMae, withinThree, completed.size(),
						withinFive, completed.size(), withinTen, completed.size(), abnormal,
						publishedOrpMachDeltaMae, coverage))
				.append("| ID | Vehicle | RASAero ft | Baseline OpenRocket ft / error / AoA raw / q>=100 / q-RMS | Current strict-table geometric ft | vs RASAero | "
						+ "Comparable ft | Real ft | Comparable / geometric error | Data | "
						+ "Published ORP/current Mach | Model delta | Table coverage | Status |\n")
				.append("|---:|---|---:|---:|---:|---:|---:|---:|---:|---|---:|---:|---:|---|\n");
		for (ComparisonRow row : rows) {
			markdown.append(String.format(Locale.US,
					"| %d | %s | %s | %s / %s / %s / %s / %s | %s | %s | %s | %.0f | %s / %s | %s | "
							+ "%s / %s | %s | %.2f%% | %s |%n",
					row.flightId(), row.vehicle().replace("|", "\\|"),
					finite(row.rasaeroApogeeFt(), "%.0f"),
					finite(row.baselineApogeeFt(), "%.0f"), finite(row.baselineErrorPct(), "%+.2f%%"),
					finite(row.baselineMaxPreApogeeAoADeg(), "%.2f deg"),
					finite(row.baselineMaxPreApogeeAoAAtQ100PaDeg(), "%.2f deg"),
					finite(row.baselineQTimeWeightedRmsPreApogeeAoADeg(), "%.2f deg"),
					finite(row.currentApogeeFt(), "%.0f"),
					finite(row.currentVsRasaeroPct(), "%+.2f%%"),
					finite(row.instrumentComparableApogeeFt(), "%.0f"),
					row.measuredApogeeFt(),
					finite(row.instrumentComparableErrorPct(), "%+.2f%%"),
					finite(row.currentErrorPct(), "%+.2f%%"),
					row.flightDataType().replace("|", "\\|"),
					finite(row.referencePeakMach(), "%.3f"), finite(row.currentPeakMach(), "%.3f"),
					finite(row.publishedOrpPeakMachDeltaPct(), "%+.2f%%"),
					row.tableCoveragePct(),
					row.supported() ? row.terminalStatus() : row.reason()));
		}
		markdown.append("\n## Trajectory diagnostics\n\n")
				.append("| ID | Vehicle | Burnout ft | Burnout m/s | Max velocity RASAero/current m/s | "
						+ "Max q Pa | Apogee RASAero/current s | Drag impulse N*s | Aero energy MJ |\n")
				.append("|---:|---|---:|---:|---:|---:|---:|---:|---:|\n");
		for (ComparisonRow row : rows) {
			markdown.append(String.format(Locale.US,
					"| %d | %s | %s | %s | %s / %s | %s | %s / %s | %s | %s |%n",
					row.flightId(), row.vehicle().replace("|", "\\|"),
					finite(row.burnoutAltitudeFt(), "%.0f"),
					finite(row.burnoutVelocityMS(), "%.1f"),
					finite(row.referenceMaxVelocityMS(), "%.1f"),
					finite(row.currentMaxVelocityMS(), "%.1f"),
					finite(row.maxDynamicPressurePa(), "%.0f"),
					finite(row.referenceTimeToApogeeS(), "%.1f"),
					finite(row.currentTimeToApogeeS(), "%.1f"),
					finite(row.dragImpulseNS(), "%.0f"),
					finite(row.aerodynamicEnergyLossJ() / 1_000_000, "%.2f")));
		}
		markdown.append("\n## First runtime failure states\n\n")
				.append("| ID | Vehicle | First occurrence by reason |\n")
				.append("|---:|---|---|\n");
		for (ComparisonRow row : rows) {
			markdown.append("| ").append(row.flightId()).append(" | ")
					.append(row.vehicle().replace("|", "\\|")).append(" | `")
					.append(row.firstFailureOccurrences().replace("`", "'"))
					.append("` |\n");
		}
		Files.writeString(output, markdown, StandardCharsets.UTF_8);
	}

	private static Path resolveDirectory(String property, String defaultRelative) {
		String configured = System.getProperty(property, "").trim();
		String requested = configured.isEmpty() ? defaultRelative : configured;
		Path raw = Paths.get(requested);
		if (raw.isAbsolute() && Files.isDirectory(raw)) return raw.normalize();
		Path current = Path.of("").toAbsolutePath().normalize();
		for (int depth = 0; depth < 8 && current != null; depth++, current = current.getParent()) {
			Path candidate = current.resolve(raw).normalize();
			if (Files.isDirectory(candidate)) return candidate;
		}
		fail("unable to resolve " + property + " directory: " + requested);
		return raw;
	}

	private static int integerProperty(String name, int fallback) {
		String value = System.getProperty(name, "").trim();
		if (value.isEmpty()) return fallback;
		try {
			int parsed = Integer.parseInt(value);
			if (parsed <= 0) throw new NumberFormatException();
			return parsed;
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException(name + " must be a positive integer: " + value);
		}
	}

	private static Map<Integer, String> modelFiles() {
		Map<Integer, String> files = new LinkedHashMap<>();
		String simVReal = "simvreal/RasAero Sims/";
		files.put(1, simVReal + "Thunder&Lightning.CDX1");
		files.put(2, simVReal + "Gibb.CDX1");
		files.put(3, simVReal + "CancerDescending.CDX1");
		files.put(4, simVReal + "EZI65-1.CDX1");
		files.put(5, simVReal + "CalIsp3.CDX1");
		files.put(6, simVReal + "CalIsp1.CDX1");
		files.put(7, simVReal + "CalIsp2.CDX1");
		files.put(8, simVReal + "Byrum.CDX1");
		files.put(9, simVReal + "IonDrive.CDX1");
		files.put(10, simVReal + "CalIsp5.CDX1");
		files.put(11, simVReal + "Blister.CDX1");
		files.put(12, simVReal + "CalIsp4.CDX1");
		files.put(13, simVReal + "Rabia-ShortFinCan.CDX1");
		files.put(14, simVReal + "Raven.CDX1");
		files.put(15, simVReal + "Rabia.CDX1");
		files.put(16, simVReal + "Torrent.CDX1");
		files.put(17, simVReal + "L500Roc.CDX1");
		files.put(18, simVReal + "Kinsel_P4935_A-601_Rocket.CDX1");
		files.put(19, simVReal + "Full Metal Jacket1.CDX1");
		files.put(20, simVReal + "Full Metal Jacket2.CDX1");
		files.put(21, simVReal + "Proteus6.CDX1");
		files.put(22, simVReal + "AeroPac104KStageOne&Two-2.CDX1");
		files.put(23, simVReal + "DontDebateThisN5800MinDia.CDX1");
		files.put(24, simVReal + "Qu8k.CDX1");
		files.put(25, "simvreal/Docs/Mesos/MESOS 293K Flight.CDX1");
		files.put(26, "paper/data/ork/sounding_rockets/bbv.ork");
		files.put(27, "paper/data/ork/sounding_rockets/nike_deacon_flight1.ork");
		files.put(28, "paper/data/ork/sounding_rockets/nike_deacon_flight2.ork");
		return Map.copyOf(files);
	}

	private static String sha256(Path path) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
	}

	private static String gitRevision(Path directory) {
		try {
			Process process = new ProcessBuilder("git", "-C", directory.toString(),
					"status", "--short", "--branch").redirectErrorStream(true).start();
			String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			if (process.waitFor() != 0) return "unavailable";
			String[] lines = output.lines().toArray(String[]::new);
			String branch = lines.length == 0 ? "" : lines[0].replaceFirst("^##\\s*", "");
			Process revision = new ProcessBuilder("git", "-C", directory.toString(),
					"rev-parse", "HEAD").redirectErrorStream(true).start();
			String hash = new String(revision.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
			if (revision.waitFor() != 0 || hash.isBlank()) return "unavailable";
			return hash + (lines.length > 1 ? "-dirty" : "") + (branch.isBlank() ? "" : " (" + branch + ")");
		} catch (IOException | InterruptedException exception) {
			if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
			return "unavailable";
		}
	}

	private static String message(Throwable failure) {
		List<String> chain = new ArrayList<>();
		for (Throwable current = failure; current != null
				&& chain.size() < 8; current = current.getCause()) {
			String detail = current.getMessage() == null
					? current.getClass().getName()
					: current.getClass().getSimpleName() + ":"
							+ current.getMessage();
			if (current instanceof PhysicsAeroQueryException queryException) {
				detail += " coordinates=" + queryException.coordinates();
			}
			chain.add(detail.replace('\n', ' ').replace('\r', ' '));
		}
		return String.join(" <- ", chain);
	}
	private static String format(double value) { return String.format(Locale.US, "%.1f", value); }
	private static String finite(double value, String format) {
		return Double.isFinite(value) ? String.format(Locale.US, format, value) : "";
	}
	private static String csv(String value) {
		String normalized = value == null ? "" : value;
		return "\"" + normalized.replace("\"", "\"\"") + "\"";
	}
	private static double errorPct(double prediction, double measured) {
		return Double.isFinite(prediction) ? 100 * (prediction - measured) / measured : Double.NaN;
	}

	private record TableArtifact(AerodynamicTable table, String contentHash) { }

	private record RasaeroTrajectoryMetrics(double maxVelocityMS, double timeToApogeeS) {
		private static final RasaeroTrajectoryMetrics EMPTY =
				new RasaeroTrajectoryMetrics(Double.NaN, Double.NaN);
	}

	private record TrajectoryMetrics(double burnoutTimeS, double burnoutAltitudeFt,
			double burnoutVelocityMS, double maxVelocityMS, double maxDynamicPressurePa, double timeToApogeeS,
			double dragImpulseNS, double aerodynamicEnergyLossJ) {
		private static final TrajectoryMetrics EMPTY = new TrajectoryMetrics(
				Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
				Double.NaN, Double.NaN, Double.NaN);
	}

	private record BaselineRun(double apogeeFt, double peakMach, double maxPreApogeeAoADeg,
			double maxPreApogeeAoAAtQ100PaDeg, double qTimeWeightedRmsPreApogeeAoADeg,
			String terminalStatus, String errorMessage) {
		private static final BaselineRun EMPTY = new BaselineRun(
				Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, "", "");

		private static BaselineRun error(String message) {
			return new BaselineRun(Double.NaN, Double.NaN, Double.NaN, Double.NaN,
					Double.NaN, "ERROR", message);
		}
	}

	record SoundingRocketLaunchConditions(double launchAngleRad,
			double launchAltitudeM) { }

	record DeterministicAscentSettings(double timeStepS,
			double maximumStepAngleDeg, double maximumSimulationTimeS) { }

	private record MeasurementEstimate(double comparableApogeeFt,
			String methodId) {
		private static final MeasurementEstimate EMPTY =
				new MeasurementEstimate(Double.NaN, "");
	}

	private record ComparisonRow(int flightId, String vehicle, boolean supported, String reason,
			double measuredApogeeFt, double rasaeroApogeeFt, double publishedApogeeFt,
			double baselineApogeeFt, double baselinePeakMach, double baselineMaxPreApogeeAoADeg,
			double baselineMaxPreApogeeAoAAtQ100PaDeg,
			double baselineQTimeWeightedRmsPreApogeeAoADeg,
			String baselineTerminalStatus,
			double currentApogeeFt,
			String flightDataType, String measurementModelId,
			double instrumentComparableApogeeFt,
			double referencePeakMach, double currentPeakMach,
			double referenceMaxVelocityMS, double currentMaxVelocityMS,
			double burnoutTimeS, double burnoutAltitudeFt, double burnoutVelocityMS,
			double maxDynamicPressurePa, double referenceTimeToApogeeS, double currentTimeToApogeeS, double dragImpulseNS,
			double aerodynamicEnergyLossJ,
			String terminalStatus, long totalQueries, long tableQueries, long fallbackQueries,
			String runtimeFlags, String failureCounts, String firstFailureOccurrences,
			String errorMessage) {
		static ComparisonRow success(RasaeroFlightRow source, BaselineRun baseline, double apogeeFt,
				double peakMach,
				String terminal, RasaeroTrajectoryMetrics referenceMetrics,
				TrajectoryMetrics metrics, MeasurementEstimate measurement,
				PhysicsAeroRuntimeReport runtime) {
			return base(source, true, "", baseline, apogeeFt, peakMach, terminal,
					referenceMetrics, metrics, measurement, runtime, "");
		}
		static ComparisonRow unsupported(RasaeroFlightRow source, String reason) {
			return base(source, false, reason, BaselineRun.EMPTY, Double.NaN, Double.NaN, "UNSUPPORTED",
					RasaeroTrajectoryMetrics.EMPTY, TrajectoryMetrics.EMPTY,
					MeasurementEstimate.EMPTY, null, "");
		}
		static ComparisonRow unsupported(RasaeroFlightRow source, BaselineRun baseline,
				String reason) {
			return base(source, false, reason, baseline, Double.NaN, Double.NaN, "UNSUPPORTED",
					RasaeroTrajectoryMetrics.EMPTY, TrajectoryMetrics.EMPTY,
					MeasurementEstimate.EMPTY, null, "");
		}
		static ComparisonRow error(RasaeroFlightRow source, String error) {
			return error(source, BaselineRun.EMPTY, error);
		}
		static ComparisonRow error(RasaeroFlightRow source, BaselineRun baseline, String error) {
			return error(source, baseline, null, error);
		}
		static ComparisonRow error(RasaeroFlightRow source, BaselineRun baseline,
				PhysicsAeroRuntimeReport runtime, String error) {
			return base(source, true, "", baseline, Double.NaN, Double.NaN, "ERROR",
					RasaeroTrajectoryMetrics.EMPTY, TrajectoryMetrics.EMPTY,
					MeasurementEstimate.EMPTY, runtime, error);
		}
		private static ComparisonRow base(RasaeroFlightRow source, boolean supported, String reason,
				BaselineRun baseline, double current, double peakMach, String terminal,
				RasaeroTrajectoryMetrics referenceMetrics, TrajectoryMetrics metrics,
				MeasurementEstimate measurement,
				PhysicsAeroRuntimeReport runtime, String error) {
			return new ComparisonRow(source.flightId(), source.vehicle(), supported, reason,
					source.measuredApogeeFt(),
					source.rasaeroApogeeFt() == null ? Double.NaN : source.rasaeroApogeeFt(),
					source.publishedThisWorkApogeeFt() == null ? Double.NaN : source.publishedThisWorkApogeeFt(),
					baseline.apogeeFt(), baseline.peakMach(), baseline.maxPreApogeeAoADeg(),
					baseline.maxPreApogeeAoAAtQ100PaDeg(),
					baseline.qTimeWeightedRmsPreApogeeAoADeg(),
					baseline.terminalStatus(),
					current, source.flightDataType(), measurement.methodId(),
					measurement.comparableApogeeFt(), source.peakMach(), peakMach,
					referenceMetrics.maxVelocityMS(), metrics.maxVelocityMS(),
					metrics.burnoutTimeS(), metrics.burnoutAltitudeFt(), metrics.burnoutVelocityMS(),
					metrics.maxDynamicPressurePa(), referenceMetrics.timeToApogeeS(),
					metrics.timeToApogeeS(), metrics.dragImpulseNS(),
					metrics.aerodynamicEnergyLossJ(), terminal,
					runtime == null ? 0 : runtime.totalQueries(),
					runtime == null ? 0 : runtime.successfulTableQueries(),
					runtime == null ? 0 : runtime.fallbackCount(),
					runtime == null ? "" : runtime.runtimeFlags().toString(),
					runtime == null ? "" : runtime.failureCounts().toString(),
					runtime == null ? "" : runtime.firstOccurrences().toString(), error);
		}
		double rasaeroErrorPct() { return errorPct(rasaeroApogeeFt, measuredApogeeFt); }
		double publishedErrorPct() { return errorPct(publishedApogeeFt, measuredApogeeFt); }
		double baselineErrorPct() { return errorPct(baselineApogeeFt, measuredApogeeFt); }
		double currentErrorPct() { return errorPct(currentApogeeFt, measuredApogeeFt); }
		double instrumentComparableErrorPct() {
			return errorPct(instrumentComparableApogeeFt, measuredApogeeFt);
		}
		double currentVsRasaeroPct() { return errorPct(currentApogeeFt, rasaeroApogeeFt); }
		double currentVsPublishedPct() { return errorPct(currentApogeeFt, publishedApogeeFt); }
		double publishedOrpPeakMachDeltaPct() {
			return errorPct(currentPeakMach, referencePeakMach);
		}
		double measuredApogeeAbsErrorPct() { return Math.abs(currentErrorPct()); }
		double instrumentComparableAbsErrorPct() {
			return Math.abs(instrumentComparableErrorPct());
		}
		boolean instrumentComparableWithinThreePercent() {
			return Double.isFinite(instrumentComparableErrorPct())
					&& instrumentComparableAbsErrorPct() <= 3.0;
		}
		double tableCoveragePct() { return totalQueries == 0 ? 0 : 100.0 * tableQueries / totalQueries; }
		String toCsv() {
			return String.join(",",
					Integer.toString(flightId), csv(vehicle), Boolean.toString(supported), csv(reason),
					finite(measuredApogeeFt, "%.3f"), finite(rasaeroApogeeFt, "%.3f"),
					finite(publishedApogeeFt, "%.3f"), finite(baselineApogeeFt, "%.3f"),
					finite(baselineErrorPct(), "%.6f"), finite(baselinePeakMach, "%.6f"),
					finite(baselineMaxPreApogeeAoADeg, "%.6f"),
					finite(baselineMaxPreApogeeAoAAtQ100PaDeg, "%.6f"),
					finite(baselineQTimeWeightedRmsPreApogeeAoADeg, "%.6f"),
					csv(baselineTerminalStatus), finite(currentApogeeFt, "%.3f"),
					csv(flightDataType), csv(measurementModelId),
					finite(instrumentComparableApogeeFt, "%.3f"),
					finite(rasaeroErrorPct(), "%.6f"), finite(publishedErrorPct(), "%.6f"),
					finite(currentErrorPct(), "%.6f"), finite(currentVsRasaeroPct(), "%.6f"),
					finite(currentVsPublishedPct(), "%.6f"),
					finite(instrumentComparableErrorPct(), "%.6f"),
					finite(instrumentComparableAbsErrorPct(), "%.6f"),
					Boolean.toString(instrumentComparableWithinThreePercent()),
					finite(referencePeakMach, "%.6f"), finite(currentPeakMach, "%.6f"),
					finite(publishedOrpPeakMachDeltaPct(), "%.6f"),
					finite(measuredApogeeAbsErrorPct(), "%.6f"),
					finite(referenceMaxVelocityMS, "%.6f"), finite(currentMaxVelocityMS, "%.6f"),
					finite(burnoutTimeS, "%.6f"), finite(burnoutAltitudeFt, "%.3f"),
					finite(burnoutVelocityMS, "%.6f"), finite(maxDynamicPressurePa, "%.3f"),
					finite(referenceTimeToApogeeS, "%.6f"), finite(currentTimeToApogeeS, "%.6f"),
					finite(dragImpulseNS, "%.6f"),
					finite(aerodynamicEnergyLossJ, "%.3f"),
					csv(terminalStatus), Long.toString(totalQueries), Long.toString(tableQueries),
					Long.toString(fallbackQueries), finite(tableCoveragePct(), "%.6f"),
					csv(runtimeFlags), csv(failureCounts), csv(firstFailureOccurrences),
					csv(errorMessage));
		}
	}
}

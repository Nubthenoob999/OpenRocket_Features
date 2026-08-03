package info.openrocket.core.tuning;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.motor.MotorConfiguration;
import info.openrocket.core.rocketcomponent.FlightConfigurationId;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.AxialStage;
import info.openrocket.core.rocketcomponent.StageSeparationConfiguration;
import info.openrocket.core.simulation.SimulationOptions;
import info.openrocket.core.simulation.FlightData;
import info.openrocket.core.simulation.FlightDataBranch;
import info.openrocket.core.simulation.FlightEvent;
import info.openrocket.core.simulation.exception.SimulationException;
import info.openrocket.core.startup.OpenRocketCore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

public class AboveMachOneAuditTest {

	private static final double METERS_TO_FEET = 3.28083989501312;

	@BeforeAll
	static void initializeCore() {
		OpenRocketCore.initialize();
	}

	@Test
	void auditDontDebateThisMachCase() throws Exception {
		auditCase(
				"DontDebateThisN5800MinDia",
				"DontDebateThisN5800MinDia_ROM.CDX1.ork",
				56573.0);
	}

	@Test
	void auditRabiaMachCase() throws Exception {
		auditCase(
				"Rabia",
				"Rabia_ROM.CDX1.ork",
				12745.0);
	}

	@Test
	void auditHubbubMachCase() throws Exception {
		auditCase(
				"Hubbub",
				"Hubbub.CDX1.ork",
				10750.0);
	}

	@Test
	void auditAeroPac104KTwoStageMachCase() throws Exception {
		auditCase(
				Path.of("Above_Mach_1", "AeroPac104kTwoStage", "AeroPac104KStageOne&Two-2_ROM.CDX1.ork"),
				104659.0,
				true);
	}

	private static void auditCase(String folder, String file, double expectedApogeeFt) throws Exception {
		auditCase(Path.of("Above_Mach_1", folder, file), expectedApogeeFt, false);
	}

	private static void auditCase(Path relativePath, double expectedApogeeFt, boolean runBaselineComparison) throws Exception {
		File orkFile = resolve(relativePath);
		Simulation simulation = loadFirstSimulation(orkFile);
		SimulationOptions options = simulation.getOptions();
		FlightConfiguration configuration = simulation.getActiveConfiguration();
		if (configuration.getRocket().getStageCount() == 1
				&& configuration.getActiveStages().size() == 1) {
			PhaseThreeNativeAirbrakesConfigurer.forceTableRuntime(options, null);
		} else {
			options.setPhysicsAeroEnabled(false);
		}

		System.out.printf(
				"AUDIT_INPUT path=%s name=%s useISA=%s launchAltitudeFt=%.1f launchPressurePa=%.3f launchTemperatureK=%.3f " +
						"launchHumidity=%.4f physicsAeroEnabled=%s physicsAeroMode=%s tableIdentityComplete=%s airbrakesEnabled=%s%n",
				relativePath,
				simulation.getName(),
				options.isISAAtmosphere(),
				options.getLaunchAltitude() * METERS_TO_FEET,
				options.getLaunchPressure(),
				options.getLaunchTemperature(),
				options.getLaunchRelativeHumidity(),
				options.getPhysicsAeroSettings().isEnabled(),
				options.getPhysicsAeroMode(),
				!options.getPhysicsAeroSettings().getTableContentHash().isBlank(),
				options.isAirbrakesEnabled());
		logConfiguration(relativePath, configuration);

		HeadlessOrkSimulationRunner.OrkSimulationResult result;
		try {
			result = HeadlessOrkSimulationRunner.runSimulationDetailed(simulation);
		} catch (Exception ex) {
			System.out.printf(
					"AUDIT_FAILURE path=%s name=%s exception=%s message=%s cause=%s causeMessage=%s%n",
					relativePath,
					simulation.getName(),
					ex.getClass().getName(),
					sanitize(ex.getMessage()),
					ex.getCause() == null ? "" : ex.getCause().getClass().getName(),
					ex.getCause() == null ? "" : sanitize(ex.getCause().getMessage()));
			if (ex instanceof SimulationException simulationException) {
				throw simulationException;
			}
			throw ex;
		}
		TelemetrySeries series = result.getSeries();
		FlightData flightData = simulation.getSimulatedData();

		double simulatedApogeeFt = maxFinite(series.getAltitudeMetersAgl()) * METERS_TO_FEET;
		double minPressurePa = minFinite(series.getPressurePa());
		double apogeeDeltaFt = simulatedApogeeFt - expectedApogeeFt;

		System.out.printf(
				"AUDIT_RESULT path=%s name=%s expectedApogeeFt=%.1f simulatedApogeeFt=%.1f deltaFt=%.1f maxMach=%.3f " +
						"physicsAeroMode=%s tableSource=%s points=%d minPressurePa=%.6f apogeeTimeSec=%.3f%n",
				relativePath,
				simulation.getName(),
				expectedApogeeFt,
				simulatedApogeeFt,
				apogeeDeltaFt,
				result.getMaxMach(),
				result.getPhysicsAeroMode(),
				result.getTableSource(),
				series.size(),
				minPressurePa,
				timeAt(series, series.estimateApogeeIndex()));
		logSimulationData(relativePath, simulation, flightData);
		if (runBaselineComparison) {
			logBaselineComparison(relativePath, orkFile, expectedApogeeFt);
		}

		assertNotNull(series);
		assertFalse(series.getTimeSec().isEmpty());
	}

	private static void logBaselineComparison(Path relativePath, File orkFile, double expectedApogeeFt) throws Exception {
		Simulation baselineSimulation = loadFirstSimulation(orkFile);
		baselineSimulation.getOptions().setPhysicsAeroEnabled(false);
		HeadlessOrkSimulationRunner.OrkSimulationResult baselineResult = HeadlessOrkSimulationRunner.runSimulationDetailed(baselineSimulation);
		TelemetrySeries baselineSeries = baselineResult.getSeries();
		double simulatedApogeeFt = maxFinite(baselineSeries.getAltitudeMetersAgl()) * METERS_TO_FEET;
		double minPressurePa = minFinite(baselineSeries.getPressurePa());
		double apogeeDeltaFt = simulatedApogeeFt - expectedApogeeFt;
		System.out.printf(
				"AUDIT_BASELINE_RESULT path=%s expectedApogeeFt=%.1f simulatedApogeeFt=%.1f deltaFt=%.1f maxMach=%.3f " +
						"physicsAeroMode=%s tableSource=%s points=%d minPressurePa=%.6f apogeeTimeSec=%.3f%n",
				relativePath,
				expectedApogeeFt,
				simulatedApogeeFt,
				apogeeDeltaFt,
				baselineResult.getMaxMach(),
				baselineResult.getPhysicsAeroMode(),
				baselineResult.getTableSource(),
				baselineSeries.size(),
				minPressurePa,
				timeAt(baselineSeries, baselineSeries.estimateApogeeIndex()));
		logSimulationData(relativePath, baselineSimulation, baselineSimulation.getSimulatedData());
	}

	private static void logConfiguration(Path relativePath, FlightConfiguration configuration) {
		StringBuilder stages = new StringBuilder();
		for (AxialStage stage : configuration.getAllStages()) {
			StageSeparationConfiguration separation = stage.getSeparationConfigurations().get(configuration.getFlightConfigurationID());
			appendPart(
					stages,
					String.format(
							"stage=%s#%d active=%s separation=%s delay=%.3f alt=%.3f",
							stage.getName(),
							stage.getStageNumber(),
							configuration.isStageActive(stage.getStageNumber()),
							separation.getSeparationEvent(),
							separation.getSeparationDelay(),
							separation.getSeparationAltitude()));
		}

		StringBuilder motors = new StringBuilder();
		for (MotorConfiguration motorConfiguration : configuration.getAllMotors()) {
			appendPart(
					motors,
					String.format(
							"motor=%s mount=%s stage=%d ignition=%s delay=%.3f",
							motorConfiguration.toMotorName(),
							motorConfiguration.getMount().getDebugName(),
							((info.openrocket.core.rocketcomponent.RocketComponent) motorConfiguration.getMount()).getStageNumber(),
							motorConfiguration.getIgnitionEvent(),
							motorConfiguration.getIgnitionDelay()));
		}

		System.out.printf(
				"AUDIT_CONFIG path=%s config=%s activeStages=%d stages=[%s] motors=[%s]%n",
				relativePath,
				configuration.getNameRaw(),
				configuration.getActiveStageCount(),
				stages,
				motors);
	}

	private static void logSimulationData(Path relativePath, Simulation simulation, FlightData flightData) {
		if (flightData == null) {
			System.out.printf("AUDIT_SIM path=%s status=%s warnings=%s branches=0%n",
					relativePath,
					simulation.getStatus(),
					"");
			return;
		}

		System.out.printf(
				"AUDIT_SIM path=%s status=%s warnings=%s branches=%d%n",
				relativePath,
				simulation.getStatus(),
				flightData.getWarningSet(),
				flightData.getBranchCount());

		for (FlightDataBranch branch : flightData.getBranches()) {
			StringBuilder events = new StringBuilder();
			for (FlightEvent event : branch.getEvents()) {
				appendPart(
						events,
						String.format(
								"%s@%.3f src=%s data=%s",
								event.getType(),
								event.getTime(),
								event.getSource() == null ? "" : event.getSource().getName(),
								sanitize(String.valueOf(event.getData()))));
			}
			System.out.printf(
					"AUDIT_BRANCH path=%s branch=%s events=[%s]%n",
					relativePath,
					branch.getName(),
					events);
		}
	}

	private static void appendPart(StringBuilder builder, String value) {
		if (builder.length() > 0) {
			builder.append(" | ");
		}
		builder.append(value);
	}

	private static Simulation loadFirstSimulation(File orkFile) throws Exception {
		GeneralRocketLoader loader = new GeneralRocketLoader(orkFile);
		OpenRocketDocument document = loader.load();
		if (document.getSimulations().isEmpty()) {
			Simulation simulation = new Simulation(document.getRocket());
			FlightConfigurationId id = document.getRocket().getSelectedConfiguration().getFlightConfigurationID();
			simulation.setFlightConfigurationId(id);
			return simulation;
		}
		return document.getSimulations().get(0);
	}

	private static File resolve(Path relativePath) {
		return Path.of("src", "test", "java", "info", "openrocket", "core", "tuning")
				.resolve(relativePath)
				.toFile();
	}

	private static String sanitize(String value) {
		if (value == null) {
			return "";
		}
		return value.replace('\r', ' ').replace('\n', ' ');
	}

	private static double maxFinite(List<Double> values) {
		double max = Double.NaN;
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

	private static double minFinite(List<Double> values) {
		double min = Double.NaN;
		for (Double value : values) {
			if (value == null || !Double.isFinite(value)) {
				continue;
			}
			if (Double.isNaN(min) || value < min) {
				min = value;
			}
		}
		return min;
	}

	private static double timeAt(TelemetrySeries series, int index) {
		if (series == null || index < 0 || index >= series.getTimeSec().size()) {
			return Double.NaN;
		}
		Double value = series.getTimeSec().get(index);
		return value == null ? Double.NaN : value;
	}
}

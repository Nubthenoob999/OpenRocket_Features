package info.openrocket.core.tuning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroMode;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroRuntimeReport;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.simulation.FlightData;
import info.openrocket.core.simulation.FlightDataBranch;
import info.openrocket.core.simulation.FlightDataType;
import info.openrocket.core.simulation.FlightEvent;
import info.openrocket.core.simulation.listeners.system.ApogeeEndListener;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Protects the comparison harness's important invariant: strict table runs use
 * the flight's configured airbrake controller rather than silently disabling it.
 */
class OfflineTableFlightComparisonAirbrakeTest {
	private static final Gson GSON = new Gson();
	private static final String DATASET_NAME = "government_work_launch_1_fluctus_vs_subscale_1_sim";
	private static final FlightDataType AIRBRAKE_EXTENSION =
			FlightDataType.getType("airbrakeExt", "airbrakeExt", info.openrocket.core.unit.UnitGroup.UNITS_RELATIVE);
	private static final double MATERIAL_APOGEE_DELTA_METERS = 1.0;

	@Test
	void strictTablePreparationPreservesConfiguredAirbrakesAndDeploymentChangesApogee() throws Exception {
		Path configPath = PhaseThreeTuningPaths.findDefaultConfig();
		assertNotNull(configPath, "Bundled recorded-flight config should be available");
		PhaseThreeBatchDatasetConfig dataset = configuredDataset(configPath, DATASET_NAME);
		Path configDir = configPath.getParent();
		Path orkPath = PhaseThreeNativeAirbrakesConfigurer.resolvePath(configDir, dataset.getOrkPath());

		StrictPreparation strict = prepareStrictTableWithAirbrakes(dataset, configDir, orkPath);
		FlightRun baselineEnabled = simulate(dataset, configDir, orkPath, false, PhysicsAeroMode.OFF);
		FlightRun baselineDisabled = simulate(dataset, configDir, orkPath, true, PhysicsAeroMode.OFF);

		assertTrue(strict.airbrakeConfigurationSucceeded(), strict.airbrakeConfigurationMessage());
		assertTrue(strict.airbrakesEnabled(), "Strict selection must preserve configured native airbrakes");
		assertTrue(Files.isRegularFile(Path.of(strict.cfdPath())), "Configured CFD surface must be present");
		assertEquals(PhysicsAeroMode.STRICT, strict.physicsAeroMode());
		assertTrue(strict.tableIdentityPrepared(), "Strict table preparation must not discard airbrake configuration");

		assertTrue(baselineEnabled.endedAtApogee(), "Harness comparison must stop the enabled variant at apogee");
		assertTrue(baselineDisabled.endedAtApogee(), "Harness comparison must stop the disabled variant at apogee");
		assertTrue(baselineEnabled.maxAirbrakeExtension() >= 0.99,
				"Configured burnout-only controller should command a deployed airbrake state");
		assertFalse(baselineDisabled.airbrakesEnabled(), "Controlled comparison must suppress only native airbrakes");
		assertTrue(Double.isNaN(baselineDisabled.maxAirbrakeExtension()),
				"Disabled case must not install an airbrake telemetry listener");
		double apogeeDelta = baselineDisabled.apogeeMeters() - baselineEnabled.apogeeMeters();
		assertTrue(apogeeDelta > MATERIAL_APOGEE_DELTA_METERS,
				"Deployment must materially reduce apogee. enabled=" + baselineEnabled.apogeeMeters()
						+ " disabled=" + baselineDisabled.apogeeMeters() + " delta=" + apogeeDelta);

		System.out.printf("Airbrake evidence: strictMode=%s tablePrepared=%s fullApogeeDelta=%.3f m "
					+ "maxExtension=%.2f%n", strict.physicsAeroMode(), strict.tableIdentityPrepared(),
				apogeeDelta, baselineEnabled.maxAirbrakeExtension());
	}

	private static StrictPreparation prepareStrictTableWithAirbrakes(PhaseThreeBatchDatasetConfig dataset,
			Path configDir, Path orkPath) throws Exception {
		TuningTestInfrastructure.ensureApplicationInjector();
		OpenRocketDocument document = new GeneralRocketLoader(orkPath.toFile()).load();
		Simulation simulation = document.getSimulations().get(0);
		AbPluginExecutionResult configuration = PhaseThreeNativeAirbrakesConfigurer.configure(
				dataset, configDir, simulation.getOptions());
		simulation.getOptions().setPhysicsAeroMode(PhysicsAeroMode.STRICT);
		PhysicsAeroTuningSupport.prepareTableIfEnabled(simulation);
		return new StrictPreparation(simulation.getOptions().isAirbrakesEnabled(),
				simulation.getOptions().getCfdDataFilePath(), configuration.getStatus() == AbPluginExecutionResult.Status.SUCCEEDED,
				configuration.getMessage(), simulation.getOptions().getPhysicsAeroMode(),
				!simulation.getOptions().getPhysicsAeroSettings().getTableContentHash().isBlank());
	}

	private static FlightRun simulate(PhaseThreeBatchDatasetConfig dataset, Path configDir,
			Path orkPath, boolean disableAirbrakes, PhysicsAeroMode mode) throws Exception {
		TuningTestInfrastructure.ensureApplicationInjector();
		OpenRocketDocument document = new GeneralRocketLoader(orkPath.toFile()).load();
		Simulation simulation = document.getSimulations().get(0);
		AbPluginExecutionResult configuration = PhaseThreeNativeAirbrakesConfigurer.configure(
				dataset, configDir, simulation.getOptions());
		if (disableAirbrakes) {
			simulation.getOptions().setAirbrakesEnabled(false);
		}
		simulation.getOptions().setPhysicsAeroMode(mode);
		boolean airbrakesEnabled = simulation.getOptions().isAirbrakesEnabled();
		String cfdPath = simulation.getOptions().getCfdDataFilePath();

		PhysicsAeroTuningSupport.prepareTableIfEnabled(simulation);
		simulation.simulate(ApogeeEndListener.INSTANCE);
		FlightData data = simulation.getSimulatedData();
		FlightDataBranch branch = data.getBranch(0);
		List<Double> extension = branch.get(AIRBRAKE_EXTENSION);
		return new FlightRun(
				data.getMaxAltitude(),
				maxFinite(extension),
				airbrakesEnabled,
				cfdPath == null ? "" : cfdPath,
				configuration.getStatus() == AbPluginExecutionResult.Status.SUCCEEDED,
				configuration.getMessage(),
				simulation.getPhysicsAeroRuntimeReport(),
				branch.getLastEvent(FlightEvent.Type.APOGEE) != null
						&& branch.getLastEvent(FlightEvent.Type.SIMULATION_END) != null);
	}

	private static PhaseThreeBatchDatasetConfig configuredDataset(Path configPath, String name) throws Exception {
		PhaseThreeBatchRunConfig config = GSON.fromJson(Files.readString(configPath, StandardCharsets.UTF_8),
				PhaseThreeBatchRunConfig.class);
		return config.getDatasets().stream()
				.filter(dataset -> name.equals(dataset.getName()))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Dataset not found: " + name));
	}

	private static double maxFinite(List<Double> values) {
		if (values == null) {
			return Double.NaN;
		}
		return values.stream().filter(value -> value != null && Double.isFinite(value))
				.mapToDouble(Double::doubleValue).max().orElse(Double.NaN);
	}

	private record FlightRun(double apogeeMeters, double maxAirbrakeExtension, boolean airbrakesEnabled,
							 String cfdPath, boolean airbrakeConfigurationSucceeded,
							 String airbrakeConfigurationMessage, PhysicsAeroRuntimeReport runtime,
							 boolean endedAtApogee) {
	}

	private record StrictPreparation(boolean airbrakesEnabled, String cfdPath,
								 boolean airbrakeConfigurationSucceeded, String airbrakeConfigurationMessage,
								 PhysicsAeroMode physicsAeroMode, boolean tableIdentityPrepared) {
	}
}

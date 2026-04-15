package info.openrocket.core.tuning;

import com.google.gson.Gson;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.rocketcomponent.FlightConfigurationId;
import info.openrocket.core.simulation.FlightData;
import info.openrocket.core.simulation.SimulationOptions;
import info.openrocket.core.startup.Application;
import info.openrocket.core.startup.OpenRocketCore;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PhaseThreeRawOrkApogeeAuditTest {
	private static final Gson GSON = new Gson();
	private static final double METER_TO_FEET = 3.280839895;
	private static final double FEET_TO_METER = 0.3048;
	private static final Path CONFIG_PATH = Path.of(
			"src", "test", "java", "info", "openrocket", "core", "tuning", "Phase3_tuning.json");
	private static final Path EXTERNAL_HAIL_MARY_ORK_PATH = Path.of(
			"C:\\Users\\Opteron92\\SchoolStuff\\7th - 8th Senior Design\\Hail Mary Launch\\NASA_26_PDF_Config.ork");
	private static final String EXTERNAL_HAIL_MARY_SIMULATION = "PDF Flight W/AB 4/4/2026";
	private static final List<String> CONFIG_DATASETS = List.of(
			"government_work_launch_1_fluctus_vs_subscale_1_sim",
			"jackpot_launch_1_fluctus_vs_openrocket",
			"jackpot_launch_2_fluctus_vs_openrocket",
			"jackpot_launch_3_fluctus_vs_openrocket",
			"pelencator_launch_1_stratologger_vs_vdf_sim",
			"pelencator_launch_4_perfectflite_vs_dolconfig_sim",
			"pelencator_launch_hunts_perfectflite_vs_dolconfig_sim");

	@Test
	public void emitRawOrkApogeesWithoutFallback() throws Exception {
		Assumptions.assumeTrue(isManualAuditEnabled(),
				"Manual validation helper; set runRawOrkAudit=true or RUN_RAW_ORK_AUDIT=true");

		Path configPath = CONFIG_PATH.toAbsolutePath().normalize();
		PhaseTwoRunConfig config = loadConfig(configPath);
		Path configDir = configPath.getParent();
		Map<String, PhaseTwoDatasetConfig> datasetsByName = indexDatasetsByName(config);
		List<ApogeeAuditResult> results = new ArrayList<>();

		for (String datasetName : CONFIG_DATASETS) {
			PhaseTwoDatasetConfig dataset = datasetsByName.get(datasetName);
			assertNotNull(dataset, "Missing Phase Three dataset: " + datasetName);
			results.add(runConfiguredDataset(configDir, dataset));
		}
		results.add(runGovernmentWorkLaunch2RomOnly(configDir));
		if (Files.exists(EXTERNAL_HAIL_MARY_ORK_PATH)) {
			results.add(runExternalHailMaryLaunch());
		}

		assertTrue(!results.isEmpty(), "Expected at least one ORK audit result");
		for (ApogeeAuditResult result : results) {
			printResult(result);
			assertTrue(Double.isFinite(result.apogeeMeters()) && result.apogeeMeters() > 0.0,
					"Expected a finite apogee for " + result.label() + " but got " + result.apogeeMeters());
		}
	}

	private static Map<String, PhaseTwoDatasetConfig> indexDatasetsByName(PhaseTwoRunConfig config) {
		Map<String, PhaseTwoDatasetConfig> datasetsByName = new LinkedHashMap<>();
		for (PhaseTwoDatasetConfig dataset : config.getDatasets()) {
			if (dataset.getName() != null && !dataset.getName().isBlank()) {
				datasetsByName.put(dataset.getName(), dataset);
			}
		}
		return datasetsByName;
	}

	private static PhaseTwoRunConfig loadConfig(Path configPath) throws IOException {
		String json = Files.readString(configPath, StandardCharsets.UTF_8);
		PhaseTwoRunConfig config = GSON.fromJson(json, PhaseTwoRunConfig.class);
		if (config == null || config.getDatasets() == null || config.getDatasets().isEmpty()) {
			throw new IllegalArgumentException("No datasets defined in config: " + configPath);
		}
		return config;
	}

	private static ApogeeAuditResult runConfiguredDataset(Path configDir, PhaseTwoDatasetConfig dataset) throws Exception {
		if (dataset.getOrkPath() == null || dataset.getOrkPath().isBlank()) {
			throw new IllegalArgumentException("Dataset does not define an orkPath: " + dataset.getName());
		}

		Path orkPath = resolvePath(configDir, dataset.getOrkPath()).toAbsolutePath().normalize();
		AtomicReference<AbPluginExecutionResult> pluginResultRef = new AtomicReference<>(defaultPluginResult());
		SimulationRun run = simulateFirstSimulation(
				orkPath.toFile(),
				simulation -> configureLikeBatchRunner(dataset, configDir, simulation.getOptions(), pluginResultRef));
		double referenceApogeeMeters = resolveReferenceApogeeMeters(configDir, dataset);

		return new ApogeeAuditResult(
				dataset.getName(),
				orkPath.toString(),
				run.apogeeMeters(),
				run.apogeeTimeSec(),
				referenceApogeeMeters,
				referenceSource(dataset),
				run.romMode(),
				run.romSurfaceSource(),
				run.maxMach(),
				pluginResultRef.get().getStatus().name(),
				pluginResultRef.get().getMessage());
	}

	private static void configureLikeBatchRunner(PhaseTwoDatasetConfig dataset,
													 Path configDir,
													 SimulationOptions options,
													 AtomicReference<AbPluginExecutionResult> pluginResultRef) {
		try {
			pluginResultRef.set(NativeAirbrakesConfigurer.configure(dataset, configDir, options));
		} catch (Exception ex) {
			options.setAirbrakesEnabled(false);
			pluginResultRef.set(new AbPluginExecutionResult(
					AbPluginExecutionResult.Status.SKIPPED,
					0,
					"Native airbrakes auto-disabled: " + ex.getClass().getSimpleName() + ": " + safeMessage(ex)));
		}
	}

	private static ApogeeAuditResult runGovernmentWorkLaunch2RomOnly(Path configDir) throws Exception {
		Path orkPath = resolvePath(configDir, "Government_Work_Launch_2/NASA_26_Subscale_2.ork")
				.toAbsolutePath()
				.normalize();
		SimulationRun run = simulateFirstSimulation(
				orkPath.toFile(),
				simulation -> simulation.getOptions().setAirbrakesEnabled(false));
		double referenceApogeeMeters = DerivedTelemetryQuantities.summarize(
				TelemetryParsers.parse(resolvePath(configDir, "Government_Work_Launch_2/EasyMini_launch_2.csv")))
				.getApogeeAltitudeMeters();

		return new ApogeeAuditResult(
				"government_work_launch_2_rom_only_direct_ork",
				orkPath.toString(),
				run.apogeeMeters(),
				run.apogeeTimeSec(),
				referenceApogeeMeters,
				"REFERENCE_SERIES",
				run.romMode(),
				run.romSurfaceSource(),
				run.maxMach(),
				AbPluginExecutionResult.Status.SKIPPED.name(),
				"Airbrakes forced off for direct ROM-only audit");
	}

	private static ApogeeAuditResult runExternalHailMaryLaunch() throws Exception {
		Simulation simulation = loadExternalHailMarySimulation().clone(false);
		SimulationRun run = simulateExistingSimulation(simulation);

		return new ApogeeAuditResult(
				"jackpot_launch_3_external_hail_mary_direct_ork",
				EXTERNAL_HAIL_MARY_ORK_PATH.toAbsolutePath().normalize().toString(),
				run.apogeeMeters(),
				run.apogeeTimeSec(),
				Double.NaN,
				"",
				run.romMode(),
				run.romSurfaceSource(),
				run.maxMach(),
				simulation.getOptions().isAirbrakesEnabled()
						? AbPluginExecutionResult.Status.SUCCEEDED.name()
						: AbPluginExecutionResult.Status.SKIPPED.name(),
				"Loaded directly from the external Hail Mary ORK");
	}

	private static Simulation loadExternalHailMarySimulation() throws Exception {
		ensureApplicationInjector();
		GeneralRocketLoader loader = new GeneralRocketLoader(EXTERNAL_HAIL_MARY_ORK_PATH.toFile());
		OpenRocketDocument document = loader.load();
		for (Simulation simulation : document.getSimulations()) {
			if (!EXTERNAL_HAIL_MARY_SIMULATION.equals(simulation.getName())) {
				continue;
			}
			if (!simulation.getOptions().isAirbrakesEnabled()) {
				continue;
			}
			if (simulation.getOptions().getRomSurfaceMode() == null
					|| !"FOUR_D".equals(simulation.getOptions().getRomSurfaceMode().name())) {
				continue;
			}
			return simulation;
		}
		throw new IllegalStateException("Could not find the expected external Hail Mary simulation");
	}

	private static SimulationRun simulateFirstSimulation(java.io.File orkFile,
															 SimulationConfigurator configurator) throws Exception {
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
		return simulateExistingSimulation(simulation);
	}

	private static SimulationRun simulateExistingSimulation(Simulation simulation) throws Exception {
		simulation.simulate();
		FlightData data = simulation.getSimulatedData();
		SimulationOptions options = simulation.getOptions();
		return new SimulationRun(
				data.getMaxAltitude(),
				data.getTimeToApogee(),
				options.getRomSurfaceMode() == null ? "" : options.getRomSurfaceMode().name(),
				describeRomSurfaceSource(options),
				data.getMaxMachNumber());
	}

	private static synchronized void ensureApplicationInjector() {
		if (Application.getInjector() != null && OpenRocketCore.isInitialized()) {
			return;
		}
		OpenRocketCore.initialize();
	}

	private static double resolveReferenceApogeeMeters(Path configDir, PhaseTwoDatasetConfig dataset) throws IOException {
		Double overrideFeet = dataset.getApogeeTargetFtOverride();
		if (overrideFeet != null && Double.isFinite(overrideFeet) && overrideFeet > 0.0) {
			return overrideFeet * FEET_TO_METER;
		}

		String truthPath = dataset.getTruthCsv();
		if (truthPath != null && !truthPath.isBlank()) {
			return DerivedTelemetryQuantities.summarize(TelemetryParsers.parse(resolvePath(configDir, truthPath)))
					.getApogeeAltitudeMeters();
		}

		if (dataset.getOrkPath() != null && !dataset.getOrkPath().isBlank()) {
			Path datasetDir = inferDatasetDirectory(configDir, dataset);
			return DerivedTelemetryQuantities.summarize(TelemetryTruthSelector.select(datasetDir).series())
					.getApogeeAltitudeMeters();
		}

		String referencePath = dataset.getReferenceCsv();
		if (referencePath == null || referencePath.isBlank()) {
			return Double.NaN;
		}
		return DerivedTelemetryQuantities.summarize(TelemetryParsers.parse(resolvePath(configDir, referencePath)))
				.getApogeeAltitudeMeters();
	}

	private static String referenceSource(PhaseTwoDatasetConfig dataset) {
		Double overrideFeet = dataset.getApogeeTargetFtOverride();
		if (overrideFeet != null && Double.isFinite(overrideFeet) && overrideFeet > 0.0) {
			return "OVERRIDE_FT";
		}
		if (dataset.getTruthCsv() != null && !dataset.getTruthCsv().isBlank()) {
			return "TRUTH_SERIES";
		}
		if (dataset.getOrkPath() != null && !dataset.getOrkPath().isBlank()) {
			return "TRUTH_SELECTOR";
		}
		if (dataset.getReferenceCsv() != null && !dataset.getReferenceCsv().isBlank()) {
			return "REFERENCE_SERIES";
		}
		return "";
	}

	private static String describeRomSurfaceSource(SimulationOptions options) {
		if (options == null) {
			return "";
		}
		if (options.getRomSurfaceMode() == info.openrocket.core.aerodynamics.rom.RomSurfaceMode.FOUR_D
				&& options.getRomAeroSurface4D() != null) {
			return "FOUR_D_ACTIVE";
		}
		if (options.getRomSurfaceMode() == info.openrocket.core.aerodynamics.rom.RomSurfaceMode.THREE_D
				&& options.getRomDragSurface() != null) {
			return "THREE_D_ACTIVE";
		}
		if (options.getRomAeroSurface4D() != null) {
			return "FOUR_D_AVAILABLE_INACTIVE";
		}
		if (options.getRomDragSurface() != null) {
			return "THREE_D_AVAILABLE_INACTIVE";
		}
		return "NONE";
	}

	private static Path inferDatasetDirectory(Path configDir, PhaseTwoDatasetConfig dataset) {
		for (String value : datasetPathCandidates(dataset)) {
			if (value == null || value.isBlank()) {
				continue;
			}
			Path path = resolvePath(configDir, value);
			Path parent = path.getParent();
			if (parent != null && Files.isDirectory(parent)) {
				return parent;
			}
		}
		return configDir.toAbsolutePath().normalize();
	}

	private static List<String> datasetPathCandidates(PhaseTwoDatasetConfig dataset) {
		List<String> values = new ArrayList<>(4);
		values.add(dataset.getTruthCsv());
		values.add(dataset.getReferenceCsv());
		values.add(dataset.getOrkPath());
		values.add(dataset.getCandidateCsv());
		return values;
	}

	private static Path resolvePath(Path baseDir, String value) {
		String normalized = normalizeWindowsDrivePath(value);
		Path path = Path.of(normalized);
		if (path.isAbsolute()) {
			return path;
		}

		Path anchor = baseDir == null ? Path.of(".") : baseDir.toAbsolutePath().normalize();
		Path cursor = anchor;
		while (cursor != null) {
			Path candidate = cursor.resolve(path).normalize();
			if (Files.exists(candidate)) {
				return candidate;
			}
			cursor = cursor.getParent();
		}
		return anchor.resolve(path).normalize();
	}

	private static String normalizeWindowsDrivePath(String value) {
		if (value == null) {
			return "";
		}
		if (value.matches("^[A-Za-z]:[^\\\\/].*")) {
			return value.substring(0, 2) + "/" + value.substring(2);
		}
		return value;
	}

	private static AbPluginExecutionResult defaultPluginResult() {
		return new AbPluginExecutionResult(
				AbPluginExecutionResult.Status.SKIPPED,
				0,
				"Native airbrakes not configured");
	}

	private static boolean isManualAuditEnabled() {
		return Boolean.getBoolean("runRawOrkAudit")
				|| "true".equalsIgnoreCase(System.getenv("RUN_RAW_ORK_AUDIT"));
	}

	private static void printResult(ApogeeAuditResult result) {
		System.out.printf(Locale.US,
				"RAW_ORK_AUDIT|label=%s|ork=%s|apogee_m=%s|apogee_ft=%s|reference_m=%s|reference_ft=%s|referenceSource=%s|apogeeTime_s=%s|romMode=%s|romSurface=%s|maxMach=%s|pluginStatus=%s|pluginMessage=%s%n",
				sanitize(result.label()),
				sanitize(result.orkPath()),
				formatDouble(result.apogeeMeters(), 3),
				formatDouble(result.apogeeMeters() * METER_TO_FEET, 1),
				formatDouble(result.referenceApogeeMeters(), 3),
				formatDouble(result.referenceApogeeMeters() * METER_TO_FEET, 1),
				sanitize(result.referenceSource()),
				formatDouble(result.apogeeTimeSec(), 3),
				sanitize(result.romMode()),
				sanitize(result.romSurfaceSource()),
				formatDouble(result.maxMach(), 3),
				sanitize(result.pluginStatus()),
				sanitize(result.pluginMessage()));
	}

	private static String formatDouble(double value, int decimals) {
		if (!Double.isFinite(value)) {
			return "";
		}
		return String.format(Locale.US, "%." + decimals + "f", value);
	}

	private static String sanitize(String value) {
		if (value == null) {
			return "";
		}
		return value.replace('|', '/')
				.replace('\r', ' ')
				.replace('\n', ' ')
				.trim();
	}

	private static String safeMessage(Exception ex) {
		String message = ex.getMessage();
		return message == null ? ex.getClass().getSimpleName() : message;
	}

	private record ApogeeAuditResult(
			String label,
			String orkPath,
			double apogeeMeters,
			double apogeeTimeSec,
			double referenceApogeeMeters,
			String referenceSource,
			String romMode,
			String romSurfaceSource,
			double maxMach,
			String pluginStatus,
			String pluginMessage) {
	}

	@FunctionalInterface
	private interface SimulationConfigurator {
		void configure(Simulation simulation) throws Exception;
	}

	private record SimulationRun(
			double apogeeMeters,
			double apogeeTimeSec,
			String romMode,
			String romSurfaceSource,
			double maxMach) {
	}
}

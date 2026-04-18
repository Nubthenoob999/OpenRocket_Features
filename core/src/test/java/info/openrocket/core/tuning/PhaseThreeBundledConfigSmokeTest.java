package info.openrocket.core.tuning;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.startup.Application;
import info.openrocket.core.startup.OpenRocketCore;
import info.openrocket.core.util.BaseTestCase;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PhaseThreeBundledConfigSmokeTest extends BaseTestCase {
	private static final Gson GSON = new Gson();
	private static final String JACKPOT_CSV_DATASET = "jackpot_launch_2_ab_self_check";

	@Test
	public void bundledPhaseThreeConfigReferencesResolvableAssets() throws Exception {
		PhaseTwoRunConfig config = loadBundledConfig();

		assertTrue(config.getSampleRateHz() > 0.0, "Expected a positive sample rate in the bundled config");
		assertEquals(InterpolationMode.CUBIC_HERMITE, config.getInterpolationMode());
		assertFalse(config.getDatasets().isEmpty(), "Expected bundled Phase 3 config to include datasets");

		Set<String> datasetNames = new HashSet<>();
		Set<Path> truthSelectorDirs = new HashSet<>();
		boolean sawOrkDataset = false;
		boolean sawCandidateCsvDataset = false;

		for (PhaseTwoDatasetConfig dataset : config.getDatasets()) {
			assertNotNull(dataset.getName(), "Dataset name should be present");
			assertFalse(dataset.getName().isBlank(), "Dataset name should not be blank");
			assertTrue(datasetNames.add(dataset.getName()), "Duplicate dataset name in bundled config: " + dataset.getName());

			Path referenceCsv = resolve(dataset.getReferenceCsv());
			assertTrue(Files.exists(referenceCsv), "Missing reference CSV for " + dataset.getName() + ": " + referenceCsv);
			assertTrue(TelemetryParsers.parse(referenceCsv).size() > 0,
					"Expected parseable reference telemetry for " + dataset.getName());

			if (dataset.getCandidateCsv() != null && !dataset.getCandidateCsv().isBlank()) {
				sawCandidateCsvDataset = true;
				Path candidateCsv = resolve(dataset.getCandidateCsv());
				assertTrue(Files.exists(candidateCsv), "Missing candidate CSV for " + dataset.getName() + ": " + candidateCsv);
				assertTrue(TelemetryParsers.parse(candidateCsv).size() > 0,
						"Expected parseable candidate telemetry for " + dataset.getName());
			}

			if (dataset.getOrkPath() != null && !dataset.getOrkPath().isBlank()) {
				sawOrkDataset = true;
				Path orkPath = resolve(dataset.getOrkPath());
				assertTrue(Files.exists(orkPath), "Missing ORK file for " + dataset.getName() + ": " + orkPath);
				truthSelectorDirs.add(orkPath.getParent());
			}

			PhaseTwoDatasetConfig.PluginConfig plugin = dataset.getPlugin();
			assertNotNull(plugin, "Plugin config should never be null");
			if (plugin.isEnabled()) {
				assertNotNull(plugin.getArgumentsFile(),
						"Enabled plugin should declare an arguments file for " + dataset.getName());
				assertFalse(plugin.getArgumentsFile().isBlank(),
						"Enabled plugin should use a non-blank arguments file for " + dataset.getName());
				Path argumentsFile = resolve(plugin.getArgumentsFile());
				assertTrue(Files.exists(argumentsFile),
						"Missing plugin arguments file for " + dataset.getName() + ": " + argumentsFile);
			}
		}

		assertTrue(sawOrkDataset, "Expected bundled Phase 3 config to include ORK-backed datasets");
		assertTrue(sawCandidateCsvDataset, "Expected bundled Phase 3 config to include candidate CSV datasets");

		for (Path datasetDir : truthSelectorDirs) {
			TelemetryTruthSelector.TruthSelection truth = TelemetryTruthSelector.select(datasetDir);
			assertTrue(Files.exists(truth.path()), "Truth selector should resolve a telemetry CSV in " + datasetDir);
			assertTrue(truth.series().size() > 0, "Truth selector should return parseable telemetry in " + datasetDir);
		}
	}

	@Test
	public void bundledPhaseThreeConfigRunsJackpotCsvDatasetThroughBatchRunner() throws Exception {
		JsonObject configRoot = loadBundledConfigJson();
		JsonObject datasetJson = new JsonObject();
		datasetJson.addProperty("name", JACKPOT_CSV_DATASET);
		String jackpotCsvPath = resolve("Jackpot_Launch_2/ab_jackpot_launch_2.csv").toString();
		datasetJson.addProperty("referenceCsv", jackpotCsvPath);
		datasetJson.addProperty("candidateCsv", jackpotCsvPath);
		datasetJson.addProperty("airbrakeEnabled", false);
		JsonObject pluginJson = new JsonObject();
		pluginJson.addProperty("enabled", false);
		pluginJson.add("arguments", new JsonArray());
		datasetJson.add("plugin", pluginJson);

		JsonObject singleDatasetConfig = new JsonObject();
		singleDatasetConfig.addProperty("sampleRateHz", configRoot.get("sampleRateHz").getAsDouble());
		singleDatasetConfig.addProperty("interpolationMode", configRoot.get("interpolationMode").getAsString());
		JsonArray datasets = new JsonArray();
		datasets.add(datasetJson.deepCopy());
		singleDatasetConfig.add("datasets", datasets);

		Path tempDir = Path.of("build", "tmp", "phase-three-bundled-config-smoke");
		Files.createDirectories(tempDir);
		Path configPath = tempDir.resolve("phase-three-single-dataset.json");
		Path outputDir = tempDir.resolve("reports");
		Files.writeString(configPath, GSON.toJson(singleDatasetConfig), StandardCharsets.UTF_8);

		PhaseTwoBatchResult batch = PhaseTwoBatchRunner.runFromConfig(configPath, outputDir);

		assertEquals(1, batch.getDatasets().size(), "Expected exactly one dataset run from the temp config");

		PhaseTwoDatasetResult result = batch.getDatasets().get(0);
		assertEquals(JACKPOT_CSV_DATASET, result.getDatasetName());
		assertEquals("CANDIDATE_CSV", result.getCandidateSource());
		assertEquals("PARSER_ONLY", result.getDatasetClass());
		assertTrue(Files.exists(Path.of(result.getTruthSource())), "Expected batch truth source to exist on disk");
		assertEquals("", result.getOrkProvenance(), "CSV-only smoke run should not report ORK provenance");
		assertTrue(result.getWindowScores().containsKey(FlightPhaseWindow.FULL),
				"Expected batch result to include the full-flight score");
		assertFalse(result.getPhaseResiduals().isEmpty(), "Expected residual metrics for an executed dataset");
		assertTrue(result.getWindowScores().get(FlightPhaseWindow.FULL).getTimelineSampleCount() > 0,
				"Expected timeline samples to be scored");
		assertTrue(Files.exists(outputDir.resolve("phase-two-summary.csv")));
		assertTrue(Files.exists(outputDir.resolve("phase-three-analysis.csv")));
		assertTrue(Files.exists(outputDir.resolve("phase-three-drag-residuals.csv")));
	}

	@Test
	public void phaseThreeSingleSimulationRunnerProducesMetricsForExistingDataset() throws Exception {
		Assumptions.assumeTrue(isClassPresent("info.openrocket.core.aerodynamics.rom.PathlineROMCalculator"),
				"Full ROM runtime is not on the current classpath");
		ensureApplicationInjector();

		Path orkPath = resolve("Government_Work_Launch_2/NASA_26_Subscale_2.ork");
		Path referenceCsv = resolve("Government_Work_Launch_2/Fluctus_launch_2.csv");
		OpenRocketDocument document = new GeneralRocketLoader(orkPath.toFile()).load();
		Simulation simulation = document.getSimulations().get(0);

		PhaseTwoDatasetResult result = PhaseThreeSingleSimulationRunner.compareCurrentSimulation(
				document,
				simulation,
				referenceCsv,
				20.0,
				InterpolationMode.CUBIC_HERMITE);

		assertEquals("CURRENT_SIMULATION", result.getCandidateSource());
		assertEquals(referenceCsv.toAbsolutePath().normalize().toString(), result.getTruthSource());
		assertTrue(result.getWindowScores().containsKey(FlightPhaseWindow.FULL),
				"Expected current simulation comparison to include a full-flight score");
		assertFalse(result.getPhaseResiduals().isEmpty(), "Expected phase residuals from current simulation comparison");
		assertTrue(result.getWindowScores().get(FlightPhaseWindow.FULL).getTimelineSampleCount() > 0,
				"Expected current simulation comparison to score timeline samples");
		assertTrue(Double.isFinite(result.getReferenceQuantities().getApogeeAltitudeMeters()));
		assertTrue(Double.isFinite(result.getCandidateQuantities().getApogeeAltitudeMeters()));
		assertTrue(Double.isFinite(result.getAlignmentQuality()));
		assertTrue(Double.isFinite(result.getAlignedApogeeTimeErrorSec()));
	}

	private static PhaseTwoRunConfig loadBundledConfig() throws Exception {
		return GSON.fromJson(Files.readString(configPath(), StandardCharsets.UTF_8), PhaseTwoRunConfig.class);
	}

	private static JsonObject loadBundledConfigJson() throws Exception {
		return GSON.fromJson(Files.readString(configPath(), StandardCharsets.UTF_8), JsonObject.class);
	}

	private static JsonObject findDataset(JsonObject root, String datasetName) {
		for (var element : root.getAsJsonArray("datasets")) {
			JsonObject dataset = element.getAsJsonObject();
			if (datasetName.equals(dataset.get("name").getAsString())) {
				return dataset;
			}
		}
		throw new IllegalArgumentException("Dataset not found in bundled config: " + datasetName);
	}

	private static Path configPath() {
		return tuningRoot().resolve("Phase3_tuning.json");
	}

	private static Path resolve(String relativePath) {
		return tuningRoot().resolve(relativePath).toAbsolutePath().normalize();
	}

	private static Path tuningRoot() {
		Path moduleRelative = Path.of("src", "test", "java", "info", "openrocket", "core", "tuning");
		if (Files.isDirectory(moduleRelative)) {
			return moduleRelative.toAbsolutePath().normalize();
		}

		Path repoRelative = Path.of("core", "src", "test", "java", "info", "openrocket", "core", "tuning");
		if (Files.isDirectory(repoRelative)) {
			return repoRelative.toAbsolutePath().normalize();
		}

		throw new IllegalStateException("Could not locate tuning test directory");
	}

	private static synchronized void ensureApplicationInjector() {
		if (Application.getInjector() != null && OpenRocketCore.isInitialized()) {
			return;
		}
		OpenRocketCore.initialize();
	}

	private static boolean isClassPresent(String className) {
		try {
			Class.forName(className);
			return true;
		} catch (ClassNotFoundException ex) {
			return false;
		}
	}
}

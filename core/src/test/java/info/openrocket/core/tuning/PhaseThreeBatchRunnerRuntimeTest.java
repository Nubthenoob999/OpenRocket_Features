package info.openrocket.core.tuning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class PhaseThreeBatchRunnerRuntimeTest {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	@Test
	public void testRuntimeBatchRunnerWritesReportsForBundledCandidateCsvDataset() throws Exception {
		Path defaultConfig = PhaseThreeTuningPaths.findDefaultConfig();
		assertNotNull(defaultConfig, "Bundled Phase 3 config should be discoverable");

		JsonObject root = GSON.fromJson(Files.readString(defaultConfig, StandardCharsets.UTF_8), JsonObject.class);
		JsonArray datasets = root.getAsJsonArray("datasets");
		assertNotNull(datasets);
		assertFalse(datasets.isEmpty(), "Bundled Phase 3 config should include datasets");

		JsonObject selected = null;
		for (int i = 0; i < datasets.size(); i++) {
			JsonObject candidate = datasets.get(i).getAsJsonObject();
			boolean hasCandidateCsv = candidate.has("candidateCsv") && !candidate.get("candidateCsv").getAsString().isBlank();
			if (hasCandidateCsv) {
				selected = candidate;
				break;
			}
		}
		assertNotNull(selected, "Expected at least one dataset with a candidateCsv in the bundled config");

		JsonObject singleConfig = new JsonObject();
		if (root.has("sampleRateHz")) {
			singleConfig.add("sampleRateHz", root.get("sampleRateHz"));
		}
		if (root.has("interpolationMode")) {
			singleConfig.add("interpolationMode", root.get("interpolationMode"));
		}
		JsonObject candidateCsvOnlyDataset = selected.deepCopy();
		candidateCsvOnlyDataset.remove("orkPath");
		rebasePath(candidateCsvOnlyDataset, "referenceCsv", defaultConfig.getParent());
		rebasePath(candidateCsvOnlyDataset, "truthCsv", defaultConfig.getParent());
		rebasePath(candidateCsvOnlyDataset, "candidateCsv", defaultConfig.getParent());
		if (candidateCsvOnlyDataset.has("plugin") && candidateCsvOnlyDataset.get("plugin").isJsonObject()) {
			rebasePath(candidateCsvOnlyDataset.getAsJsonObject("plugin"), "argumentsFile", defaultConfig.getParent());
		}
		JsonArray singleDatasetArray = new JsonArray();
		singleDatasetArray.add(candidateCsvOnlyDataset);
		singleConfig.add("datasets", singleDatasetArray);

		Path tempDir = Files.createTempDirectory("phase3-runtime-batch");
		Path tempConfig = tempDir.resolve("phase3-single-config.json");
		Files.writeString(tempConfig, GSON.toJson(singleConfig), StandardCharsets.UTF_8);
		Path reportDir = tempDir.resolve("reports");

		PhaseTwoBatchResult result = PhaseThreeBatchRunner.runFromConfig(tempConfig, reportDir);

		assertEquals(1, result.getDatasets().size());
		PhaseTwoDatasetResult datasetResult = result.getDatasets().get(0);
		assertEquals(selected.get("name").getAsString(), datasetResult.getDatasetName());
		assertEquals("CANDIDATE_CSV", datasetResult.getCandidateSource());
		assertTrue(Files.exists(reportDir.resolve("phase-two-summary.csv")));
		assertTrue(Files.exists(reportDir.resolve("phase-two-quantities.csv")));
		assertTrue(Files.exists(reportDir.resolve("phase-three-analysis.csv")));
		assertTrue(Files.exists(reportDir.resolve("phase-three-drag-residuals.csv")));
		assertTrue(Double.isFinite(datasetResult.getReferenceQuantities().getApogeeAltitudeMeters()));
		assertTrue(Double.isFinite(datasetResult.getCandidateQuantities().getApogeeAltitudeMeters()));
	}

	private static void rebasePath(JsonObject object, String key, Path baseDir) {
		if (!object.has(key) || object.get(key).getAsString().isBlank()) {
			return;
		}
		Path absolute = PhaseThreeNativeAirbrakesConfigurer.resolvePath(baseDir, object.get(key).getAsString())
				.toAbsolutePath()
				.normalize();
		object.addProperty(key, absolute.toString());
	}
}

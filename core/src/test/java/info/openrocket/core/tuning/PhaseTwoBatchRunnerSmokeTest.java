package info.openrocket.core.tuning;

import info.openrocket.core.util.BaseTestCase;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PhaseTwoBatchRunnerSmokeTest extends BaseTestCase {
	@Test
	public void runsBatchAndWritesArtifacts() throws Exception {
		Path tempDir = Files.createTempDirectory("phase-two-runner");
		Path config = tempDir.resolve("phase-two-smoke-temp.json");
		Path output = tempDir.resolve("reports");
		Path sourceDataset = Path.of("src", "test", "java", "info", "openrocket", "core", "tuning",
				"Jackpot_Launch_2", "ab_jackpot_launch_2.csv");
		if (!Files.exists(sourceDataset)) {
			sourceDataset = Path.of("core", "src", "test", "java", "info", "openrocket", "core", "tuning",
					"Jackpot_Launch_2", "ab_jackpot_launch_2.csv");
		}
		Path localDataset = tempDir.resolve("dataset.csv");
		Files.copy(sourceDataset, localDataset);

		String json = "{" +
				"\"sampleRateHz\":20.0," +
				"\"interpolationMode\":\"LINEAR\"," +
				"\"datasets\":[{" +
				"\"name\":\"ab_self\"," +
				"\"referenceCsv\":\"dataset.csv\"," +
				"\"candidateCsv\":\"dataset.csv\"," +
				"\"airbrakeEnabled\":true," +
				"\"plugin\":{\"enabled\":false,\"arguments\":[]}}]}";

		Files.writeString(config, json, StandardCharsets.UTF_8);

		PhaseTwoBatchResult result = PhaseTwoBatchRunner.runFromConfig(config, output);

		assertFalse(result.getDatasets().isEmpty());
		assertTrue(Files.exists(output.resolve("phase-two-summary.csv")));
		assertTrue(Files.exists(output.resolve("phase-two-quantities.csv")));
		assertTrue(Files.exists(output.resolve("phase-two-improvements.csv")));
		assertTrue(Files.exists(output.resolve("phase-three-analysis.csv")));
		assertTrue(Files.exists(output.resolve("phase-three-drag-residuals.csv")));
		assertTrue(Files.exists(output.resolve("phase-two-junit.xml")));
		assertTrue(Files.exists(output.resolve("phase-two-console.log")));
		assertTrue(Files.exists(output.resolve("phase-two-run-metadata.log")));
		assertTrue(result.getDatasets().get(0).getWindowScores().containsKey(FlightPhaseWindow.FULL));
		assertTrue(result.getDatasets().get(0).getPhaseResiduals().containsKey(FlightPhaseWindow.FULL));

		String summary = Files.readString(output.resolve("phase-two-summary.csv"));
		assertTrue(summary.contains("datasetClass"));
		assertTrue(summary.contains("alignedApogeeTimeDeltaSec"));
		assertTrue(summary.contains("truthSource"));
		assertTrue(summary.contains("alignmentChannel"));
		assertTrue(summary.contains("airbrakesStatus"));
		assertTrue(summary.contains("candidateAccelBiasEstimateMps2"));
		assertTrue(summary.contains("candidateDeploymentDetectedTimeSec"));
		assertTrue(summary.contains("ascentScore"));
		assertTrue(summary.contains("ascentLaneStatus"));
		assertTrue(summary.contains("reliabilityLaneStatus"));

		String quantities = Files.readString(output.resolve("phase-two-quantities.csv"));
		assertTrue(quantities.contains("alignedApogeeTimeSec"));
		assertTrue(quantities.contains("launchTimeSec"));
		assertTrue(quantities.contains("deploymentTimeSec"));

		String analysis = Files.readString(output.resolve("phase-three-analysis.csv"));
		assertTrue(analysis.contains("datasetClass"));
		assertTrue(analysis.contains("PARSER_ONLY"));
		assertTrue(analysis.contains("weakestResidualPhase"));
		assertTrue(analysis.contains("candidateAccelBiasEstimateMps2"));
		assertTrue(analysis.contains("ascentLaneStatus"));
		assertTrue(analysis.contains("reliabilityLaneStatus"));

		String residuals = Files.readString(output.resolve("phase-three-drag-residuals.csv"));
		assertTrue(residuals.contains("residualType"));
		assertTrue(residuals.contains("dragProxy"));
	}

	@Test
	public void supportsTelemetryInterpolationModeAlias() throws Exception {
		Path tempDir = Files.createTempDirectory("phase-two-runner-telemetry-mode");
		Path config = tempDir.resolve("phase-two-telemetry-mode.json");
		Path output = tempDir.resolve("reports");
		Path sourceDataset = Path.of("src", "test", "java", "info", "openrocket", "core", "tuning",
				"Jackpot_Launch_2", "ab_jackpot_launch_2.csv");
		if (!Files.exists(sourceDataset)) {
			sourceDataset = Path.of("core", "src", "test", "java", "info", "openrocket", "core", "tuning",
					"Jackpot_Launch_2", "ab_jackpot_launch_2.csv");
		}
		Path localDataset = tempDir.resolve("dataset.csv");
		Files.copy(sourceDataset, localDataset);

		String json = "{" +
				"\"sampleRateHz\":20.0," +
				"\"telemetryInterpolationMode\":\"LINEAR\"," +
				"\"datasets\":[{" +
				"\"name\":\"ab_self_alias\"," +
				"\"referenceCsv\":\"dataset.csv\"," +
				"\"candidateCsv\":\"dataset.csv\"," +
				"\"airbrakeEnabled\":false," +
				"\"plugin\":{\"enabled\":false,\"arguments\":[]}}]}";
		Files.writeString(config, json, StandardCharsets.UTF_8);

		PhaseTwoBatchRunner.runFromConfig(config, output);

		String metadata = Files.readString(output.resolve("phase-two-run-metadata.log"));
		assertTrue(metadata.contains("telemetryInterpolationMode=LINEAR"));
	}

	@Test
	public void skipsRedundantDatasetsFromConfig() throws Exception {
		Path tempDir = Files.createTempDirectory("phase-two-runner-dedup");
		Path config = tempDir.resolve("phase-two-dedup-temp.json");
		Path output = tempDir.resolve("reports");
		Path sourceDataset = Path.of("src", "test", "java", "info", "openrocket", "core", "tuning",
				"Jackpot_Launch_2", "ab_jackpot_launch_2.csv");
		if (!Files.exists(sourceDataset)) {
			sourceDataset = Path.of("core", "src", "test", "java", "info", "openrocket", "core", "tuning",
					"Jackpot_Launch_2", "ab_jackpot_launch_2.csv");
		}
		Path localDataset = tempDir.resolve("dataset.csv");
		Files.copy(sourceDataset, localDataset);

		String json = "{" +
				"\"sampleRateHz\":20.0," +
				"\"interpolationMode\":\"LINEAR\"," +
				"\"datasets\":[{" +
				"\"name\":\"ab_self_a\"," +
				"\"referenceCsv\":\"dataset.csv\"," +
				"\"candidateCsv\":\"dataset.csv\"," +
				"\"airbrakeEnabled\":true," +
				"\"plugin\":{\"enabled\":false,\"arguments\":[]}}," +
				"{\"name\":\"ab_self_b\"," +
				"\"referenceCsv\":\"dataset.csv\"," +
				"\"candidateCsv\":\"dataset.csv\"," +
				"\"airbrakeEnabled\":true," +
				"\"plugin\":{\"enabled\":false,\"arguments\":[]}}]}";

		Files.writeString(config, json, StandardCharsets.UTF_8);
		PhaseTwoBatchResult result = PhaseTwoBatchRunner.runFromConfig(config, output);

		assertEquals(1, result.getDatasets().size(), "Expected duplicate dataset signature to be skipped");
		String metadata = Files.readString(output.resolve("phase-two-run-metadata.log"));
		assertTrue(metadata.contains("datasetsSkippedAsRedundant=1"));
	}
}

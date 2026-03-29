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
				"\"pluginJarPath\":\"C:/Users/Opteron92/Projects/OpenRocket_Features/core/src/test/java/info/openrocket/core/tuning/Ab_jar/AirBrakes Plugin.jar\"," +
				"\"pluginTimeoutSeconds\":5," +
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
		assertTrue(Files.exists(output.resolve("phase-two-improvements.csv")));
		assertTrue(Files.exists(output.resolve("phase-two-junit.xml")));
		assertTrue(Files.exists(output.resolve("phase-two-console.log")));
		assertTrue(Files.exists(output.resolve("phase-two-run-metadata.log")));
		assertTrue(result.getDatasets().get(0).getWindowScores().containsKey(FlightPhaseWindow.FULL));
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
				"\"pluginJarPath\":\"C:/Users/Opteron92/Projects/OpenRocket_Features/core/src/test/java/info/openrocket/core/tuning/Ab_jar/AirBrakes Plugin.jar\"," +
				"\"pluginTimeoutSeconds\":5," +
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
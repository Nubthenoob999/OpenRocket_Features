package info.openrocket.core.tuning;

import info.openrocket.core.util.BaseTestCase;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PhaseTwoAllFilesBatchExecutionTest extends BaseTestCase {
	@Test
	public void runsAllConfiguredDatasetsAndWritesReports() throws Exception {
		Path projectRoot = Path.of(".").toAbsolutePath().normalize();
		Path defaultConfig = projectRoot.resolve("src/test/java/info/openrocket/core/tuning/phase-two-config.all-files.generated.json");
		Path defaultOutput = projectRoot.resolve("build/reports/phase-two-all-files");

		Path configPath = resolveFromProperty("phaseTwoConfig", defaultConfig);
		Path outputDir = resolveFromProperty("phaseTwoReportsDir", defaultOutput);

		assertTrue(Files.exists(configPath), "Config file not found: " + configPath);
		Files.createDirectories(outputDir);

		PhaseTwoBatchResult result = PhaseTwoBatchRunner.runFromConfig(configPath, outputDir);

		assertFalse(result.getDatasets().isEmpty(), "Expected at least one dataset to run");
		assertTrue(Files.exists(outputDir.resolve("phase-two-summary.csv")));
		assertTrue(Files.exists(outputDir.resolve("phase-two-quantities.csv")));
		assertTrue(Files.exists(outputDir.resolve("phase-two-improvements.csv")));
		assertTrue(Files.exists(outputDir.resolve("phase-two-junit.xml")));
		assertTrue(Files.exists(outputDir.resolve("phase-two-console.log")));
	}

	private static Path resolveFromProperty(String key, Path defaultValue) {
		String value = System.getProperty(key);
		if (value == null || value.isBlank()) {
			return defaultValue;
		}
		Path provided = Path.of(value);
		if (!provided.isAbsolute()) {
			return Path.of(".").toAbsolutePath().normalize().resolve(provided).normalize();
		}
		return provided.normalize();
	}
}

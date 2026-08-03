package info.openrocket.core.tuning;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public class PhaseThreeTuningAssetsTest {
	private static final Gson GSON = new Gson();

	@Test
	public void testBundledPhaseThreeConfigAndReferencedAssetsExist() throws Exception {
		Path configPath = PhaseThreeTuningPaths.findDefaultConfig();
		assertNotNull(configPath, "Default Phase 3 config path should be discoverable");
		assertTrue(Files.exists(configPath), "Default Phase 3 config should exist");

		PhaseThreeBatchRunConfig config = GSON.fromJson(Files.readString(configPath), PhaseThreeBatchRunConfig.class);
		assertNotNull(config);
		assertFalse(config.getDatasets().isEmpty(), "Bundled Phase 3 config should contain datasets");

		Path configDir = configPath.getParent();
		for (PhaseThreeBatchDatasetConfig dataset : config.getDatasets()) {
			assertFalse(safe(dataset.getName()).isBlank(), "Every dataset should have a name");
			assertTrue(exists(configDir, dataset.getReferenceCsv()) || exists(configDir, dataset.getTruthCsv()),
					"Dataset should resolve a truth/reference CSV: " + dataset.getName());
			if (!safe(dataset.getCandidateCsv()).isBlank()) {
				assertTrue(exists(configDir, dataset.getCandidateCsv()),
						"Candidate CSV should exist for " + dataset.getName());
			}
			if (!safe(dataset.getOrkPath()).isBlank()) {
				assertTrue(exists(configDir, dataset.getOrkPath()),
						"ORK file should exist for " + dataset.getName());
			}
			PhaseThreeBatchDatasetConfig.PluginConfig plugin = dataset.getPlugin();
			if (plugin != null && !safe(plugin.getArgumentsFile()).isBlank()) {
				assertTrue(exists(configDir, plugin.getArgumentsFile()),
						"Plugin arguments file should exist for " + dataset.getName());
			}
		}
	}

	private static boolean exists(Path baseDir, String value) {
		if (value == null || value.isBlank()) {
			return false;
		}
		return Files.exists(PhaseThreeNativeAirbrakesConfigurer.resolvePath(baseDir, value));
	}

	private static String safe(String value) {
		return value == null ? "" : value;
	}
}

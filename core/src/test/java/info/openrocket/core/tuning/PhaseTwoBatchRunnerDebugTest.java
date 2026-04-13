package info.openrocket.core.tuning;

import org.junit.jupiter.api.Test;
import com.google.gson.Gson;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PhaseTwoBatchRunnerDebugTest {
	private static final Gson GSON = new Gson();

	@Test
	public void runDatasetHandlesMissingOptionalPathsWhenInferringTruthDirectory() throws Exception {
		Path configPath = Path.of("src", "test", "java", "info", "openrocket", "core", "tuning", "Phase3_tuning.json");
		Path configDir = configPath.getParent().toAbsolutePath().normalize();
		Method loadConfig = PhaseTwoBatchRunner.class.getDeclaredMethod("loadConfig", Path.class);
		loadConfig.setAccessible(true);
		PhaseTwoRunConfig config = (PhaseTwoRunConfig) loadConfig.invoke(null, configPath);
		PhaseTwoDatasetConfig dataset = config.getDatasets().stream()
				.filter(item -> "government_work_launch_2_fluctus_vs_subscale_2_sim".equals(item.getName()))
				.findFirst()
				.orElseThrow();
		Method runDataset = PhaseTwoBatchRunner.class.getDeclaredMethod(
				"runDataset",
				PhaseTwoRunConfig.class,
				PhaseTwoDatasetConfig.class,
				Path.class,
				Path.class);
		runDataset.setAccessible(true);
		try {
			PhaseTwoDatasetResult result = (PhaseTwoDatasetResult) runDataset.invoke(
					null,
					config,
					dataset,
					configDir,
					Path.of("build", "reports", "debug-phase3"));
			assertNotNull(result);
			assertNotEquals("", result.getDatasetClass());
		} catch (InvocationTargetException ex) {
			throw (Exception) ex.getTargetException();
		}
	}

	@Test
	public void fusedApogeeTimeErrorUsesIntegratorDiagnosticsWhenAvailable() throws Exception {
		Method method = PhaseTwoBatchRunner.class.getDeclaredMethod(
				"fusedApogeeTimeErrorSec",
				double.class,
				VerticalIntegratorDiagnostics.class);
		method.setAccessible(true);

		double fused = (double) method.invoke(
				null,
				0.42,
				new VerticalIntegratorDiagnostics(
						Double.NaN,
						Double.NaN,
						0.42,
						0.10,
						Double.NaN,
						Double.NaN));

		assertTrue(fused < 0.42);
		assertTrue(fused > 0.10);
	}

	@Test
	public void pluginPipelineHealthIgnoresAutoDisabledSkippedPlugin() throws Exception {
		PhaseTwoDatasetConfig dataset = GSON.fromJson("{\n" +
				"  \"name\": \"ork_dataset\",\n" +
				"  \"orkPath\": \"candidate.ork\"\n" +
				"}", PhaseTwoDatasetConfig.class);

		Method method = PhaseTwoBatchRunner.class.getDeclaredMethod(
				"pluginPipelineFailureReason",
				PhaseTwoDatasetConfig.class,
				AbPluginExecutionResult.class);
		method.setAccessible(true);

		Object skipped = method.invoke(
				null,
				dataset,
				new AbPluginExecutionResult(
						AbPluginExecutionResult.Status.SKIPPED,
						0,
						"Native airbrakes auto-disabled: no CFD CSV could be resolved"));
		assertNull(skipped);

		Object failed = method.invoke(
				null,
				dataset,
				new AbPluginExecutionResult(
						AbPluginExecutionResult.Status.FAILED,
						-1,
						"plugin crashed"));
		assertTrue(failed instanceof String && !((String) failed).isBlank());
	}
}

package info.openrocket.core.tuning;

import com.google.gson.Gson;
import info.openrocket.core.aerodynamics.rom.RomFallbackMode;
import info.openrocket.core.aerodynamics.rom.RomMode;
import info.openrocket.core.aerodynamics.rom.RomSurfaceMode;
import info.openrocket.core.simulation.SimulationOptions;
import info.openrocket.core.util.BaseTestCase;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NativeAirbrakesConfigurerTest extends BaseTestCase {
	private static final Gson GSON = new Gson();

	@Test
	public void mapsLegacyArgumentsIntoNativeSimulationOptions() throws Exception {
		Path tempDir = Files.createTempDirectory("native-airbrakes");
		Path cfdCsv = tempDir.resolve("drag-curve.csv");
		Files.writeString(cfdCsv, "mach,extension,drag\n0.5,0.0,0.1\n", StandardCharsets.UTF_8);
		Path argsFile = tempDir.resolve("airbrakes-plugin-args.json");
		Files.writeString(argsFile, "{\n" +
				"  \"arguments\": [\n" +
				"    \"--cfd-csv\", \"" + cfdCsv.toString().replace("\\", "/") + "\",\n" +
				"    \"--airbrake-area-in2\", \"29.5\",\n" +
				"    \"--reference-length-ft\", \"234\",\n" +
				"    \"--target-apogee-ft\", \"4650\",\n" +
				"    \"--max-mach-for-deployment\", \"1.0\",\n" +
				"    \"--apogee-tolerance-ft\", \"16.4\",\n" +
				"    \"--burnout-only-deployment\", \"true\",\n" +
				"    \"--delay-after-burnout-s\", \"0.4\"\n" +
				"  ]\n" +
				"}\n", StandardCharsets.UTF_8);

		PhaseTwoDatasetConfig dataset = datasetFromJson("{\n" +
				"  \"name\": \"native_ab\",\n" +
				"  \"referenceCsv\": \"reference.csv\",\n" +
				"  \"orkPath\": \"candidate.ork\",\n" +
				"  \"airbrakeEnabled\": true,\n" +
				"  \"plugin\": {\n" +
				"    \"enabled\": true,\n" +
				"    \"argumentsFile\": \"" + argsFile.getFileName().toString() + "\",\n" +
				"    \"arguments\": []\n" +
				"  }\n" +
				"}");

		SimulationOptions options = new SimulationOptions();
		AbPluginExecutionResult result = NativeAirbrakesConfigurer.configure(dataset, tempDir, options);

		assertEquals(AbPluginExecutionResult.Status.SUCCEEDED, result.getStatus());
		assertTrue(options.isAirbrakesEnabled());
		assertEquals(cfdCsv.toAbsolutePath().normalize().toString(), options.getCfdDataFilePath());
		assertEquals(29.5 * 0.00064516, options.getReferenceArea(), 1.0e-9);
		assertEquals(234.0 * 0.3048, options.getReferenceLength(), 1.0e-9);
		assertEquals(4650.0 * 0.3048, options.getTargetApogee(), 1.0e-9);
		assertEquals(1.0, options.getMaxMachForDeployment(), 1.0e-9);
		assertEquals(16.4 * 0.3048, options.getApogeeToleranceMeters(), 1.0e-9);
		assertTrue(options.isDeployAfterBurnoutOnly());
		assertEquals(0.4, options.getDeployAfterBurnoutDelayS(), 1.0e-9);
		assertTrue(options.isRomEnabled());
		assertEquals(RomMode.STANDARD, options.getRomMode());
		assertEquals(RomSurfaceMode.THREE_D, options.getRomSurfaceMode());
		assertEquals(RomFallbackMode.FORCE_ROM, options.getRomFallbackMode());
		assertNull(options.getRomDragSurface());
		assertNull(options.getRomAeroSurface4D());
	}

	@Test
	public void disablesNativeAirbrakesWhenDatasetDisablesThem() throws Exception {
		PhaseTwoDatasetConfig dataset = datasetFromJson("{\n" +
				"  \"name\": \"native_ab_disabled\",\n" +
				"  \"referenceCsv\": \"reference.csv\",\n" +
				"  \"candidateCsv\": \"candidate.csv\",\n" +
				"  \"airbrakeEnabled\": false,\n" +
				"  \"plugin\": {\n" +
				"    \"enabled\": true,\n" +
				"    \"arguments\": []\n" +
				"  }\n" +
				"}");

		SimulationOptions options = new SimulationOptions();
		options.setAirbrakesEnabled(true);
		AbPluginExecutionResult result = NativeAirbrakesConfigurer.configure(dataset, Path.of("."), options);

		assertEquals(AbPluginExecutionResult.Status.SKIPPED, result.getStatus());
		assertFalse(options.isAirbrakesEnabled());
		assertTrue(options.isRomEnabled());
		assertEquals(RomMode.STANDARD, options.getRomMode());
		assertEquals(RomSurfaceMode.THREE_D, options.getRomSurfaceMode());
		assertEquals(RomFallbackMode.FORCE_ROM, options.getRomFallbackMode());
		assertNull(options.getRomDragSurface());
		assertNull(options.getRomAeroSurface4D());
	}

	@Test
	public void autoDisablesWhenNoCfdCanBeResolved() throws Exception {
		Path tempDir = Files.createTempDirectory("native-airbrakes-missing-cfd");
		Path argsFile = tempDir.resolve("airbrakes-plugin-args.json");
		Files.writeString(argsFile, "{\n" +
				"  \"arguments\": [\n" +
				"    \"--cfd-csv\", \"H:/missing/drag.csv\",\n" +
				"    \"--airbrake-area-in2\", \"29.5\"\n" +
				"  ]\n" +
				"}\n", StandardCharsets.UTF_8);

		PhaseTwoDatasetConfig dataset = datasetFromJson("{\n" +
				"  \"name\": \"native_ab_missing_cfd\",\n" +
				"  \"referenceCsv\": \"reference.csv\",\n" +
				"  \"orkPath\": \"candidate.ork\",\n" +
				"  \"airbrakeEnabled\": true,\n" +
				"  \"plugin\": {\n" +
				"    \"enabled\": true,\n" +
				"    \"argumentsFile\": \"" + argsFile.getFileName().toString() + "\",\n" +
				"    \"arguments\": []\n" +
				"  }\n" +
				"}");

		SimulationOptions options = new SimulationOptions();
		options.setAirbrakesEnabled(true);
		AbPluginExecutionResult result = NativeAirbrakesConfigurer.configure(dataset, tempDir, options);

		assertEquals(AbPluginExecutionResult.Status.SKIPPED, result.getStatus());
		assertFalse(options.isAirbrakesEnabled());
		assertTrue(result.getMessage().contains("auto-disabled"));
		assertTrue(options.isRomEnabled());
		assertEquals(RomMode.STANDARD, options.getRomMode());
		assertEquals(RomSurfaceMode.THREE_D, options.getRomSurfaceMode());
		assertEquals(RomFallbackMode.FORCE_ROM, options.getRomFallbackMode());
		assertNull(options.getRomDragSurface());
		assertNull(options.getRomAeroSurface4D());
	}

	private static PhaseTwoDatasetConfig datasetFromJson(String json) {
		return GSON.fromJson(json, PhaseTwoDatasetConfig.class);
	}
}

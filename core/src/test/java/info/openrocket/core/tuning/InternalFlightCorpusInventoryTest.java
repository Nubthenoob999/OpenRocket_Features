package info.openrocket.core.tuning;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the denominator of apogee tuning: sensor exports from one launch must not be
 * counted as independent flights, and known identity limitations stay visible to runners.
 */
class InternalFlightCorpusInventoryTest {
	@Test
	void inventoryCoversEveryActiveDatasetExactlyOnceAndGroupsSensorDuplicates() throws Exception {
		JsonObject config = readObject(tuningRoot().resolve("Phase3_tuning.json"));
		JsonObject inventory = readObject(inventoryPath());
		assertEquals(1, inventory.get("schemaVersion").getAsInt());

		Set<String> configuredNames = datasetNames(config.getAsJsonArray("datasets"));
		Map<String, String> ownerByDataset = new LinkedHashMap<>();
		for (JsonElement flightElement : inventory.getAsJsonArray("physicalFlights")) {
			JsonObject flight = flightElement.getAsJsonObject();
			String flightId = flight.get("id").getAsString();
			for (JsonElement dataset : flight.getAsJsonArray("configurationDatasets")) {
				String previous = ownerByDataset.put(dataset.getAsString(), flightId);
				assertTrue(previous == null, "Dataset belongs to two physical flights: " + dataset.getAsString());
			}
		}

		assertEquals(configuredNames, ownerByDataset.keySet(),
				"Every active config row must have one physical-flight owner");
		assertEquals(8, inventory.getAsJsonArray("physicalFlights").size(),
				"The active config has eight physical launches, not ten independent fitting samples");
		assertEquals("jackpot-launch-1", ownerByDataset.get("jackpot_launch_1_easymini_vs_vdf_sim"));
		assertEquals("jackpot-launch-1", ownerByDataset.get("jackpot_launch_1_fluctus_vs_openrocket"));
		assertEquals("jackpot-launch-2", ownerByDataset.get("jackpot_launch_2_easymini_vs_pdf_sim"));
		assertEquals("jackpot-launch-2", ownerByDataset.get("jackpot_launch_2_fluctus_vs_openrocket"));
	}

	@Test
	void inventoryAssetsAndTelemetryClassificationsMatchTheCorpus() throws Exception {
		JsonObject inventory = readObject(inventoryPath());
		for (JsonElement flightElement : inventory.getAsJsonArray("physicalFlights")) {
			JsonObject flight = flightElement.getAsJsonObject();
			for (JsonElement telemetryElement : flight.getAsJsonArray("referenceTelemetry")) {
				JsonObject telemetry = telemetryElement.getAsJsonObject();
				String header = firstDataHeader(resolve(telemetry.get("path").getAsString()));
				switch (telemetry.get("deploymentTelemetry").getAsString()) {
					case "RECOVERY_STATE_AND_CHANNEL_VOLTAGE" -> {
						assertTrue(header.contains("state_name"));
						assertTrue(header.contains("drogue_voltage"));
						assertTrue(header.contains("main_voltage"));
					}
					case "PYRO_CHANNEL_STATE" -> {
						assertTrue(header.contains("P1-state"));
						assertTrue(header.contains("P2-state"));
						assertTrue(header.contains("P3-state"));
					}
					case "NONE" -> assertFalse(header.toLowerCase().contains("deploy"),
							"A deployment channel was added; update the inventory classification");
					default -> throw new AssertionError("Unknown deployment telemetry type: "
							+ telemetry.get("deploymentTelemetry").getAsString());
				}
			}
			for (JsonElement controllerElement : flight.getAsJsonArray("airbrakeControllerTelemetry")) {
				assertTrue(firstDataHeader(resolve(controllerElement.getAsString())).contains("set_extension"),
						"Airbrake controller export must contain its deployment command channel");
			}
		}

		JsonObject unconfigured = inventory.getAsJsonArray("unconfiguredTelemetry").get(0).getAsJsonObject();
		assertTrue(Files.isRegularFile(resolve(unconfigured.get("path").getAsString())));
		assertTrue(firstDataHeader(resolve(unconfigured.get("path").getAsString())).contains("P1-state"));
		assertTrue(arrayContains(unconfigured.getAsJsonArray("qualityFlags"), "NOT_IN_ACTIVE_PHASE3_CONFIG"));
	}

	@Test
	void inventoryRecordsAirbrakeAndIdentityLimitationsInsteadOfTreatingThemAsIndependentPhysicsData() throws Exception {
		JsonObject config = readObject(tuningRoot().resolve("Phase3_tuning.json"));
		JsonObject inventory = readObject(inventoryPath());
		Map<String, JsonObject> configByName = datasetsByName(config.getAsJsonArray("datasets"));

		for (JsonElement flightElement : inventory.getAsJsonArray("physicalFlights")) {
			JsonObject flight = flightElement.getAsJsonObject();
			boolean anyAirbrakeEnabled = false;
			for (JsonElement datasetElement : flight.getAsJsonArray("configurationDatasets")) {
				JsonObject dataset = configByName.get(datasetElement.getAsString());
				assertTrue(dataset != null, "Inventory references a missing config dataset");
				anyAirbrakeEnabled |= dataset.get("airbrakeEnabled").getAsBoolean();
			}
			assertEquals(anyAirbrakeEnabled, flight.get("airbrakesConfigured").getAsBoolean(),
					"Airbrake state must be physical-flight metadata, not inferred per sensor");
		}

		assertTrue(flagsFor(inventory, "government-work-launch-1").contains("SOURCE_DIRECTORY_TYPO"));
		assertTrue(flagsFor(inventory, "pelencator-launch-2").contains("ORK_NAME_REUSED_FROM_LAUNCH_1"));
		assertTrue(flagsFor(inventory, "pelencator-launch-4-huntsville").contains("DATASET_LABEL_DIRECTORY_MISMATCH"));
		assertTrue(flagsFor(inventory, "pelencator-launch-4-huntsville").contains("ORK_NAME_REUSED_FROM_DOL"));
		assertTrue(flagsFor(inventory, "pelencator-launch-1").contains("AIRBRAKE_CONFIGURATION_HAS_NO_CONTROL_PARAMETERS"));
	}

	@Test
	void sharedPelicantorCfdInputIsRestoredUnchangedWithSourceIdentityPreserved() throws Exception {
		JsonObject inventory = readObject(inventoryPath());
		String surfaceName = "Drag Curve Pelicantor - Sheet1.csv";
		String expectedSha256 = "87180ab361fb2f632c8bd55f0ce49891bdf667d7d67700733a95a1aa99b0e539";
		List<AirbrakeCondition> restoredFlights = List.of(
				new AirbrakeCondition("dol-serial-13376-flight-0016", "DOL/DOLconfig.ork"),
				new AirbrakeCondition("pelencator-launch-1", "Pelencator_launch_1/VDF_Launch_1.ork"),
				new AirbrakeCondition("pelencator-launch-4-huntsville", "Pelencator_Launch_Hunts/DOLconfig.ork"));

		for (AirbrakeCondition flight : restoredFlights) {
			assertTrue(flagsFor(inventory, flight.id()).contains("AUTHENTICATED_CFD_CURVE_RESTORED"),
					"Restored CFD provenance must remain explicit for " + flight.id());
			String ork = readOrkMember(resolve(flight.orkPath()));
			assertTrue(ork.contains("<airbrakesenabled>true</airbrakesenabled>"));
			assertTrue(ork.contains("<airbrakescfddatafilepath>C:\\Users\\Opteron92\\Downloads\\"
					+ surfaceName + "</airbrakescfddatafilepath>"),
					"The recorded condition must keep its exact source identity");
			assertTrue(ork.contains("<airbrakesreferencearea>0.014516099999999999</airbrakesreferencearea>"),
					"The ORK reference-area anchor must not be rewritten during data restoration");

			Path restoredSurface = resolve(Path.of(flight.orkPath()).getParent().resolve(surfaceName).toString());
			assertTrue(Files.isRegularFile(restoredSurface), "Expected restored CFD surface for " + flight.id());
			assertEquals(expectedSha256, sha256(restoredSurface),
					"The corpus copy must remain byte-identical to the supplied CFD source");
		}
	}

	private static String sha256(Path path) throws IOException, NoSuchAlgorithmException {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
	}

	private static JsonObject readObject(Path path) throws IOException {
		return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
	}

	private static Set<String> datasetNames(JsonArray datasets) {
		Set<String> names = new LinkedHashSet<>();
		for (JsonElement element : datasets) {
			assertTrue(names.add(element.getAsJsonObject().get("name").getAsString()), "Duplicate active dataset name");
		}
		return names;
	}

	private static Map<String, JsonObject> datasetsByName(JsonArray datasets) {
		Map<String, JsonObject> result = new LinkedHashMap<>();
		for (JsonElement element : datasets) {
			JsonObject dataset = element.getAsJsonObject();
			result.put(dataset.get("name").getAsString(), dataset);
		}
		return result;
	}

	private static Set<String> flagsFor(JsonObject inventory, String flightId) {
		for (JsonElement flightElement : inventory.getAsJsonArray("physicalFlights")) {
			JsonObject flight = flightElement.getAsJsonObject();
			if (flightId.equals(flight.get("id").getAsString())) {
				Set<String> flags = new LinkedHashSet<>();
				for (JsonElement flag : flight.getAsJsonArray("qualityFlags")) {
					flags.add(flag.getAsString());
				}
				return flags;
			}
		}
		throw new AssertionError("No inventory row for " + flightId);
	}

	private static boolean arrayContains(JsonArray values, String expected) {
		for (JsonElement value : values) {
			if (expected.equals(value.getAsString())) {
				return true;
			}
		}
		return false;
	}

	private static String firstDataHeader(Path path) throws IOException {
		for (String line : Files.readAllLines(path)) {
			if (!line.isBlank() && !line.startsWith("sep=")) {
				return line;
			}
		}
		throw new AssertionError("No data header in " + path);
	}

	private static String readOrkMember(Path orkPath) throws IOException {
		try (ZipFile archive = new ZipFile(orkPath.toFile())) {
			var entry = archive.getEntry("rocket.ork");
			assertTrue(entry != null, "Recorded ORK must contain rocket.ork: " + orkPath);
			try (InputStream stream = archive.getInputStream(entry)) {
				return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
			}
		}
	}

	private record AirbrakeCondition(String id, String orkPath) {
	}

	private static Path inventoryPath() {
		return tuningRoot().resolve("internal-flight-corpus-inventory.yaml");
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
}

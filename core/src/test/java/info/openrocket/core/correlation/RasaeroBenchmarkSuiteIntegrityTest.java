package info.openrocket.core.correlation;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/** Guards the benchmark package that supplies correlation acceptance values. */
class RasaeroBenchmarkSuiteIntegrityTest {
	private static final int CANONICAL_CASE_COUNT = 135;
	private static final Pattern README_VERSION = Pattern.compile("(?i)\\bv(\\d+)\\.(\\d+)(?:\\.(\\d+))?\\b");

	@Test
	void canonicalJsonAndStreamingJsonlContainTheSameUniqueFiniteCases() {
		JsonObject canonicalDocument = RasaeroBenchmarkData.readObject(
				RasaeroBenchmarkData.suiteRoot().resolve("data/pointwise_test_cases.json"));
		List<RasaeroBenchmarkData.BenchmarkCase> canonical = RasaeroBenchmarkData.canonicalCases();
		List<RasaeroBenchmarkData.BenchmarkCase> streamed = RasaeroBenchmarkData.canonicalJsonlCases();
		JsonObject manifest = RasaeroBenchmarkData.manifest();
		JsonObject validation = RasaeroBenchmarkData.readObject(RasaeroBenchmarkData.suiteRoot()
				.resolve("manifests/validation_report_v0.3.json"));

		assertAll(
				() -> assertEquals(CANONICAL_CASE_COUNT, canonical.size(), "unexpected canonical case count"),
				() -> assertEquals(canonical, streamed, "JSON and JSONL views differ or changed order"),
				() -> assertEquals(CANONICAL_CASE_COUNT, canonicalDocument.get("case_count").getAsInt(),
						"canonical case_count changed"),
				() -> assertEquals(CANONICAL_CASE_COUNT, manifest.get("pointwise_case_count").getAsInt(),
						"manifest pointwise count changed"),
				() -> assertEquals(CANONICAL_CASE_COUNT, validation.get("pointwise_case_count").getAsInt(),
						"validation report pointwise count changed"),
				() -> assertEquals(CANONICAL_CASE_COUNT, validation.get("jsonl_lines_checked").getAsInt(),
						"validation report JSONL count changed"));

		Set<String> caseIds = new HashSet<>();
		for (RasaeroBenchmarkData.BenchmarkCase benchmarkCase : canonical) {
			String caseId = benchmarkCase.caseId();
			assertTrue(caseIds.add(caseId), () -> "duplicate case_id: " + caseId);
			assertEquals(1, benchmarkCase.expected().size(), () -> "expected output count: " + caseId);
			assertFiniteNumericLeaves(benchmarkCase.inputs(), caseId + ".inputs");
			assertFiniteNumericLeaves(benchmarkCase.expected(), caseId + ".expected");
			assertFiniteNumericLeaves(benchmarkCase.tolerance(), caseId + ".tolerance");

			JsonObject tolerance = benchmarkCase.tolerance();
			assertEquals(1, tolerance.size(),
					() -> "tolerance must contain exactly one field: " + caseId);
			int absoluteFields = (tolerance.has("abs") ? 1 : 0)
					+ (tolerance.has("coefficient_abs") ? 1 : 0);
			assertEquals(1, absoluteFields,
					() -> "case must declare exactly one absolute tolerance: " + caseId);
			double value = benchmarkCase.toleranceValue();
			assertTrue(Double.isFinite(value) && value > 0, () -> "invalid tolerance: " + caseId);

			assertTrue(Set.of("A", "B").contains(benchmarkCase.quality()),
					() -> "pointwise quality is not A or B: " + caseId);
			if (benchmarkCase.quality().equals("B")) {
				assertTrue(benchmarkCase.sourceAnchor() != null
						&& !benchmarkCase.sourceAnchor().isBlank(),
						() -> "quality-B point has no source anchor: " + caseId);
			}
		}
	}

	@Test
	void sourceAndCanonicalCaseIdsAreOneToOneAndAerobeeIsExplicitlyExcluded() {
		List<RasaeroBenchmarkData.BenchmarkCase> canonical = RasaeroBenchmarkData.canonicalCases();
		List<RasaeroBenchmarkData.BenchmarkCase> reconstructed = RasaeroBenchmarkData.reconstructedCases();
		Set<String> canonicalIds = new HashSet<>();
		Set<String> reconstructedIds = new HashSet<>();
		canonical.forEach(value -> assertTrue(canonicalIds.add(value.caseId()),
				() -> "duplicate canonical case: " + value.caseId()));
		reconstructed.forEach(value -> assertTrue(reconstructedIds.add(value.caseId()),
				() -> "source case reconstructed more than once: " + value.caseId()));
		assertEquals(canonicalIds, reconstructedIds,
				"eligible source and exact cases are not a one-to-one canonical inventory");

		JsonObject aerobee = RasaeroBenchmarkData.dataset("AST-E1R-13319");
		int excludedPoints = 0;
		for (JsonElement element : aerobee.getAsJsonArray("series")) {
			JsonObject series = element.getAsJsonObject();
			assertTrue(series.has("pointwise_regression")
					&& !series.get("pointwise_regression").getAsBoolean(),
					() -> "Aerobee series is no longer explicitly excluded: "
							+ series.get("series_id").getAsString());
			excludedPoints += series.getAsJsonArray("points").size();
		}
		assertEquals(27, excludedPoints, "Aerobee excluded sanity-point count changed");
		assertFalse(canonical.stream().anyMatch(value -> value.datasetId().equals("AST-E1R-13319")),
				"Aerobee sanity points leaked into the regression aggregate");
	}

	@Test
	void explicitPrimaryAndSecondaryAllowlistsHaveTheAuditedInventory() {
		Map<String, Long> primaryCounts = new HashMap<>();
		RasaeroBenchmarkData.primaryCases().forEach(value ->
				primaryCounts.merge(value.datasetId(), 1L, Long::sum));
		assertEquals(Map.of("A53D02", 22L, "D4013", 72L, "D4014", 23L, "L54D27", 4L),
				primaryCounts, "blocking primary allowlist inventory changed");
		assertEquals(121, RasaeroBenchmarkData.primaryCases().size(), "primary case count changed");

		Map<String, Long> secondaryCounts = new HashMap<>();
		RasaeroBenchmarkData.secondaryCases().forEach(value ->
				secondaryCounts.merge(value.datasetId(), 1L, Long::sum));
		assertEquals(Map.of("RASAERO-ARCAS-COMPARISON", 14L), secondaryCounts,
				"non-blocking secondary allowlist inventory changed");
		assertEquals(14, RasaeroBenchmarkData.secondaryCases().size(), "secondary case count changed");
	}

	@TestFactory
	Stream<DynamicTest> everyEligibleSourceCaseExactlyReconstructsItsCanonicalCase() {
		Map<String, RasaeroBenchmarkData.BenchmarkCase> canonical = new LinkedHashMap<>();
		for (RasaeroBenchmarkData.BenchmarkCase benchmarkCase : RasaeroBenchmarkData.canonicalCases()) {
			canonical.put(benchmarkCase.caseId(), benchmarkCase);
		}
		return RasaeroBenchmarkData.reconstructedCases().stream().map(reconstructed -> DynamicTest.dynamicTest(
				"source reconstruction " + reconstructed.caseId(), () -> {
					RasaeroBenchmarkData.BenchmarkCase aggregate = canonical.get(reconstructed.caseId());
					assertNotNull(aggregate, "source case is absent from canonical aggregate");
					assertEquals(reconstructed.toJson(), aggregate.toJson(),
							"source reconstruction mismatch; A53D02 Figure 16 currently has a known "
									+ "six-case defect where canonical inputs omit the series fixed Mach");
				}));
	}

	@Test
	void sourceSeriesHaveUniqueIdsCompleteVariablesAndNonconflictingInputs() {
		for (JsonObject dataset : RasaeroBenchmarkData.sourceDatasets()) {
			if (!dataset.has("series")) {
				continue;
			}
			String datasetId = dataset.get("dataset_id").getAsString();
			Set<String> seriesIds = new HashSet<>();
			for (JsonElement element : dataset.getAsJsonArray("series")) {
				JsonObject series = element.getAsJsonObject();
				String seriesId = series.get("series_id").getAsString();
				assertTrue(seriesIds.add(seriesId), () -> "duplicate series_id: " + datasetId + ':' + seriesId);
				String dependent = series.get("dependent_variable").getAsString();
				List<String> independents = declaredIndependentVariables(series);
				JsonObject fixed = series.has("fixed") ? series.getAsJsonObject("fixed") : new JsonObject();
				for (JsonElement pointElement : series.getAsJsonArray("points")) {
					JsonObject point = pointElement.getAsJsonObject();
					assertTrue(point.has(dependent),
							() -> "point omits dependent variable " + datasetId + ':' + seriesId + ':' + dependent);
					for (String independent : independents) {
						assertTrue(point.has(independent), () -> "point omits independent variable "
								+ datasetId + ':' + seriesId + ':' + independent);
					}
					for (Map.Entry<String, JsonElement> entry : fixed.entrySet()) {
						if (point.has(entry.getKey())) {
							assertEquals(entry.getValue(), point.get(entry.getKey()), () -> "fixed/point collision differs: "
									+ datasetId + ':' + seriesId + ':' + entry.getKey());
						}
					}
				}
			}
		}
	}

	@Test
	void manifestCoversExactlyTwentyFourSuiteArtifactsAndEveryDigestMatches()
			throws IOException, NoSuchAlgorithmException {
		Path root = RasaeroBenchmarkData.suiteRoot();
		JsonObject manifest = RasaeroBenchmarkData.manifest();
		JsonArray entries = manifest.getAsJsonArray("files");
		assertEquals(24, entries.size(), "manifest.files artifact count changed");
		assertEquals(25, manifest.get("file_count").getAsInt(),
				"file_count must include manifest.json in addition to manifest.files");

		Set<String> declared = new HashSet<>();
		for (JsonElement element : entries) {
			JsonObject entry = element.getAsJsonObject();
			String relative = entry.get("path").getAsString();
			Path relativePath = Path.of(relative);
			assertFalse(relative.isBlank() || relative.contains("\\"), () -> "unsafe manifest path: " + relative);
			assertFalse(relativePath.isAbsolute(), () -> "absolute manifest path: " + relative);
			assertEquals(relative, relativePath.normalize().toString(), () -> "unnormalized manifest path: " + relative);
			assertFalse(relativePath.startsWith(".."), () -> "escaping manifest path: " + relative);
			assertTrue(root.resolve(relativePath).normalize().startsWith(root),
					() -> "manifest path leaves suite: " + relative);
			assertTrue(declared.add(relative), () -> "duplicate manifest path: " + relative);

			byte[] bytes = Files.readAllBytes(root.resolve(relativePath));
			assertEquals(entry.get("bytes").getAsLong(), bytes.length, () -> "byte count changed: " + relative);
			assertEquals(entry.get("sha256").getAsString(), sha256(bytes), () -> "digest changed: " + relative);
		}

		Set<String> actual = new HashSet<>();
		try (var paths = Files.walk(root)) {
			paths.filter(Files::isRegularFile)
					.filter(path -> !path.equals(root.resolve("manifest.json")))
					.map(root::relativize).map(Path::toString).forEach(actual::add);
		}
		assertEquals(declared, actual, "undeclared or missing suite artifact");
	}

	@Test
	void sixPdfFilesMatchChecksumSourceManifestAndIngestionAudit()
			throws IOException, NoSuchAlgorithmException {
		Path pdfRoot = RasaeroBenchmarkData.sourcePdfsRoot();
		Map<String, String> checksums = readSha256Sums(pdfRoot.resolve("SHA256SUMS.txt"));
		assertEquals(6, checksums.size(), "SHA256SUMS PDF count changed");

		Map<String, JsonObject> sourceEntries = new HashMap<>();
		JsonObject sources = RasaeroBenchmarkData.readObject(RasaeroBenchmarkData.suiteRoot()
				.resolve("manifests/source_manifest.json")).getAsJsonObject("sources");
		for (Map.Entry<String, JsonElement> entry : sources.entrySet()) {
			JsonObject source = entry.getValue().getAsJsonObject();
			if (source.has("local_file")) {
				String name = Path.of(source.get("local_file").getAsString()).getFileName().toString();
				assertFalse(sourceEntries.containsKey(name), () -> "duplicate source-manifest PDF: " + name);
				sourceEntries.put(name, source);
			}
		}
		assertEquals(checksums.keySet(), sourceEntries.keySet(), "source-manifest PDF inventory differs");

		JsonObject audit = RasaeroBenchmarkData.readObject(RasaeroBenchmarkData.suiteRoot()
				.resolve("manifests/pdf_ingestion_audit_2026-07-13.json"));
		assertEquals(6, audit.get("pdfs_reviewed").getAsInt(), "audit reviewed-PDF count changed");
		Map<String, JsonObject> audited = new HashMap<>();
		for (JsonElement element : audit.getAsJsonArray("pdf_audit")) {
			JsonObject row = element.getAsJsonObject();
			String name = row.get("package_source_filename").getAsString();
			assertFalse(audited.containsKey(name), () -> "duplicate audited PDF: " + name);
			audited.put(name, row);
		}
		assertEquals(checksums.keySet(), audited.keySet(), "audit PDF inventory differs");

		Set<String> actualPdfNames = new HashSet<>();
		try (var paths = Files.list(pdfRoot)) {
			paths.filter(path -> path.getFileName().toString().endsWith(".pdf"))
					.map(path -> path.getFileName().toString()).forEach(actualPdfNames::add);
		}
		assertEquals(checksums.keySet(), actualPdfNames, "checked-in PDF inventory differs");

		for (Map.Entry<String, String> entry : checksums.entrySet()) {
			String name = entry.getKey();
			Path pdf = pdfRoot.resolve(name);
			String digest = sha256(Files.readAllBytes(pdf));
			JsonObject source = sourceEntries.get(name);
			JsonObject auditRow = audited.get(name);
			assertAll(name,
					() -> assertEquals(entry.getValue(), digest, "SHA256SUMS digest differs"),
					() -> assertEquals(entry.getValue(), source.get("sha256").getAsString(),
							"source-manifest digest differs"),
					() -> assertEquals(entry.getValue(), auditRow.get("sha256").getAsString(),
							"audit digest differs"),
					() -> assertEquals(Files.size(pdf), auditRow.get("size_bytes").getAsLong(),
							"audit size differs"),
					() -> assertFalse(auditRow.get("action").getAsString().isBlank(), "audit action is blank"),
					() -> assertEquals(
							auditRow.get("duplicate_pdf_already_in_v0_2").getAsBoolean()
									? "reused_existing_identical_pdf" : "added_new_source_pdf",
							auditRow.get("action").getAsString(), "audit action contradicts duplicate status"));
		}
	}

	@Test
	void version03IsTheSuiteSourceOfTruth() throws IOException {
		String readme = Files.readString(RasaeroBenchmarkData.packageRoot().resolve("README.md"),
				StandardCharsets.UTF_8);
		Matcher matcher = README_VERSION.matcher(readme);
		assertTrue(matcher.find(), "package README has no semantic version");
		String readmeVersion = matcher.group(1) + '.' + matcher.group(2) + '.'
				+ (matcher.group(3) == null ? "0" : matcher.group(3));
		assertAll(
				() -> assertEquals("0.3.1", readmeVersion, "package README must identify v0.3.1"),
				() -> assertEquals("0.3.1",
						RasaeroBenchmarkData.manifest().get("version").getAsString(),
						"suite manifest must identify v0.3.1 as the source of truth"));
	}

	@Test
	void r100CatalogAndExactSupportingValuesRemainComplete() {
		JsonObject r100 = RasaeroBenchmarkData.dataset("R100");
		JsonObject table = r100.getAsJsonObject("table_I_smooth_configurations");
		JsonArray rows = table.getAsJsonArray("rows");
		assertEquals(53, rows.size(), "R100 stored row-object count changed");
		assertEquals(60, table.get("row_count").getAsInt(), "R100 declared source configuration count changed");
		assertEquals(60, expandedR100ConfigurationCount(rows), "R100 grouped configuration expansion changed");
		assertEquals(177, r100.getAsJsonObject("catalog").get("configuration_count").getAsInt(),
				"R100 catalog model count changed");

		Map<String, Double> supporting = new HashMap<>();
		for (JsonElement element : r100.getAsJsonArray("exact_supporting_data")) {
			JsonObject value = element.getAsJsonObject();
			supporting.put(value.get("quantity").getAsString(), value.get("value").getAsDouble());
		}
		assertAll(
				() -> assertEquals(0.41, supporting.get("forward_facing_step_separation_pressure_coefficient")),
				() -> assertEquals(177.0, supporting.get("catalog_model_count")));
	}

	@Test
	void narrativeAssertionsHaveDatasetLocalIdsContractsAndSourceProvenance() {
		int assertionCount = 0;
		for (JsonObject dataset : RasaeroBenchmarkData.sourceDatasets()) {
			if (!dataset.has("assertions")) {
				continue;
			}
			String datasetId = dataset.get("dataset_id").getAsString();
			assertTrue(dataset.has("source") && dataset.get("source").isJsonObject()
					&& !dataset.getAsJsonObject("source").isEmpty(),
					() -> "dataset assertions have no source provenance: " + datasetId);
			Set<String> ids = new HashSet<>();
			for (JsonElement element : dataset.getAsJsonArray("assertions")) {
				JsonObject assertion = element.getAsJsonObject();
				String id = requiredNonblank(assertion, "id", datasetId);
				assertTrue(ids.add(id), () -> "duplicate assertion id: " + datasetId + ':' + id);
				requiredNonblank(assertion, "type", datasetId + ':' + id);
				String quality = requiredNonblank(assertion, "quality", datasetId + ':' + id);
				assertTrue(Set.of("A", "B", "C", "D").contains(quality),
						() -> "invalid assertion quality: " + datasetId + ':' + id);
				assertionCount++;
			}
		}
		assertEquals(34, assertionCount, "narrative/source assertion count changed");
	}

	private static List<String> declaredIndependentVariables(JsonObject series) {
		if (series.has("independent_variables")) {
			return series.getAsJsonArray("independent_variables").asList().stream()
					.map(JsonElement::getAsString).toList();
		}
		return series.has("independent_variable")
				? List.of(series.get("independent_variable").getAsString()) : List.of();
	}

	private static void assertFiniteNumericLeaves(JsonElement value, String context) {
		if (value.isJsonObject()) {
			value.getAsJsonObject().entrySet().forEach(entry ->
					assertFiniteNumericLeaves(entry.getValue(), context + '.' + entry.getKey()));
		} else if (value.isJsonArray()) {
			for (int index = 0; index < value.getAsJsonArray().size(); index++) {
				assertFiniteNumericLeaves(value.getAsJsonArray().get(index), context + '[' + index + ']');
			}
		} else if (value.isJsonPrimitive()) {
			JsonPrimitive primitive = value.getAsJsonPrimitive();
			if (primitive.isNumber()) {
				double numeric = primitive.getAsDouble();
				assertTrue(Double.isFinite(numeric), () -> "nonfinite numeric leaf: " + context);
			}
		}
	}

	private static Map<String, String> readSha256Sums(Path file) throws IOException {
		Map<String, String> result = new LinkedHashMap<>();
		for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
			if (line.isBlank()) {
				continue;
			}
			String[] fields = line.trim().split("\\s+", 2);
			assertEquals(2, fields.length, () -> "invalid SHA256SUMS line: " + line);
			assertFalse(result.containsKey(fields[1]), () -> "duplicate SHA256SUMS name: " + fields[1]);
			result.put(fields[1], fields[0]);
		}
		return result;
	}

	private static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
	}

	private static int expandedR100ConfigurationCount(JsonArray rows) {
		int count = 0;
		for (JsonElement element : rows) {
			JsonElement configuration = element.getAsJsonObject().get("configuration");
			if (configuration.isJsonPrimitive() && configuration.getAsJsonPrimitive().isString()
					&& configuration.getAsString().matches("\\d+-\\d+")) {
				String[] bounds = configuration.getAsString().split("-", 2);
				count += Integer.parseInt(bounds[1]) - Integer.parseInt(bounds[0]) + 1;
			} else {
				count++;
			}
		}
		return count;
	}

	private static String requiredNonblank(JsonObject object, String name, String context) {
		assertTrue(object.has(name) && object.get(name).isJsonPrimitive()
				&& object.get(name).getAsJsonPrimitive().isString(),
				() -> "missing string " + name + ": " + context);
		String value = object.get(name).getAsString();
		assertFalse(value.isBlank(), () -> "blank " + name + ": " + context);
		return value;
	}
}

package info.openrocket.core.correlation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Test-only access to the checked-in RASAero benchmark package. */
final class RasaeroBenchmarkData {
	static final Set<String> PRIMARY_DATASET_IDS = Set.of("A53D02", "D4013", "D4014", "L54D27");
	static final Set<String> SECONDARY_DATASET_IDS = Set.of("RASAERO-ARCAS-COMPARISON");
	private static final Gson GSON = new Gson();
	private static final String SUITE_PATH =
			"src/test/java/info/openrocket/core/correlation/rasaero_benchmark_package/benchmark_suite";

	private RasaeroBenchmarkData() {
	}

	static Path suiteRoot() {
		Path relative = Path.of(SUITE_PATH);
		for (Path directory = Path.of("").toAbsolutePath(); directory != null;
				directory = directory.getParent()) {
			Path fromCore = directory.resolve(relative);
			if (Files.isDirectory(fromCore)) {
				return fromCore.normalize();
			}
			Path fromRepository = directory.resolve("core").resolve(relative);
			if (Files.isDirectory(fromRepository)) {
				return fromRepository.normalize();
			}
		}
		throw new IllegalStateException("cannot locate " + SUITE_PATH);
	}

	static Path packageRoot() {
		return suiteRoot().getParent();
	}

	static Path sourcePdfsRoot() {
		return packageRoot().resolve("source_pdfs");
	}

	static JsonObject readObject(Path path) {
		try {
			return GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), JsonObject.class);
		} catch (IOException exception) {
			throw new UncheckedIOException("cannot read " + path, exception);
		}
	}

	static JsonObject manifest() {
		return readObject(suiteRoot().resolve("manifest.json"));
	}

	static List<BenchmarkCase> canonicalCases() {
		JsonArray cases = readObject(suiteRoot().resolve("data/pointwise_test_cases.json"))
				.getAsJsonArray("cases");
		return cases.asList().stream().map(element -> benchmarkCase(element.getAsJsonObject())).toList();
	}

	static List<BenchmarkCase> canonicalJsonlCases() {
		try {
			List<BenchmarkCase> result = new ArrayList<>();
			for (String line : Files.readAllLines(
					suiteRoot().resolve("data/pointwise_test_cases.jsonl"), StandardCharsets.UTF_8)) {
				if (!line.isBlank()) {
					result.add(benchmarkCase(GSON.fromJson(line, JsonObject.class)));
				}
			}
			return List.copyOf(result);
		} catch (IOException exception) {
			throw new UncheckedIOException("cannot read canonical JSONL", exception);
		}
	}

	static BenchmarkCase caseById(String caseId) {
		return canonicalCases().stream()
				.filter(benchmarkCase -> benchmarkCase.caseId().equals(caseId))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("unknown benchmark case: " + caseId));
	}

	static List<BenchmarkCase> casesForDataset(String datasetId) {
		return canonicalCases().stream()
				.filter(benchmarkCase -> benchmarkCase.datasetId().equals(datasetId))
				.toList();
	}

	static List<BenchmarkCase> primaryCases() {
		return canonicalCases().stream()
				.filter(benchmarkCase -> PRIMARY_DATASET_IDS.contains(benchmarkCase.datasetId()))
				.toList();
	}

	static List<BenchmarkCase> secondaryCases() {
		return canonicalCases().stream()
				.filter(benchmarkCase -> SECONDARY_DATASET_IDS.contains(benchmarkCase.datasetId()))
				.toList();
	}

	static List<JsonObject> sourceDatasets() {
		try (var paths = Files.list(suiteRoot().resolve("data"))) {
			return paths.filter(path -> path.getFileName().toString().endsWith(".json"))
					.filter(path -> !path.getFileName().toString().equals("pointwise_test_cases.json"))
					.map(RasaeroBenchmarkData::readObject)
					.filter(object -> object.has("dataset_id"))
					.sorted(Comparator.comparing(object -> object.get("dataset_id").getAsString()))
					.toList();
		} catch (IOException exception) {
			throw new UncheckedIOException("cannot enumerate benchmark datasets", exception);
		}
	}

	static JsonObject dataset(String datasetId) {
		return sourceDatasets().stream()
				.filter(object -> datasetId.equals(object.get("dataset_id").getAsString()))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("unknown benchmark dataset: " + datasetId));
	}

	static JsonObject sourceSeries(String datasetId, String seriesId) {
		JsonObject dataset = dataset(datasetId);
		if (!dataset.has("series")) {
			throw new IllegalArgumentException("dataset has no series: " + datasetId);
		}
		for (JsonElement element : dataset.getAsJsonArray("series")) {
			JsonObject series = element.getAsJsonObject();
			if (seriesId.equals(series.get("series_id").getAsString())) {
				return series;
			}
		}
		throw new IllegalArgumentException("unknown benchmark series: " + datasetId + ':' + seriesId);
	}

	/**
	 * Returns a solver-facing case whose inputs include source-series fixed values.
	 * The canonical aggregate is never modified or silently repaired.
	 */
	static BenchmarkCase solverCase(BenchmarkCase canonical) {
		if (canonical.seriesId() == null) {
			return canonical.copy();
		}
		JsonObject series = sourceSeries(canonical.datasetId(), canonical.seriesId());
		JsonObject inputs = series.has("fixed")
				? series.getAsJsonObject("fixed").deepCopy() : new JsonObject();
		for (Map.Entry<String, JsonElement> entry : canonical.inputs().entrySet()) {
			if (inputs.has(entry.getKey()) && !inputs.get(entry.getKey()).equals(entry.getValue())) {
				throw new IllegalStateException("fixed/canonical input collision for " + canonical.caseId()
						+ ": " + entry.getKey());
			}
			inputs.add(entry.getKey(), entry.getValue().deepCopy());
		}
		return canonical.withInputs(inputs);
	}

	/** Reconstructs all pointwise-regression series and declared exact cases. */
	static List<BenchmarkCase> reconstructedCases() {
		List<BenchmarkCase> result = new ArrayList<>();
		for (JsonObject source : sourceDatasets()) {
			String datasetId = source.get("dataset_id").getAsString();
			if (source.has("series")) {
				for (JsonElement element : source.getAsJsonArray("series")) {
					JsonObject series = element.getAsJsonObject();
					if (series.has("pointwise_regression")
							&& !series.get("pointwise_regression").getAsBoolean()) {
						continue;
					}
					String seriesId = series.get("series_id").getAsString();
					String dependent = series.get("dependent_variable").getAsString();
					JsonObject fixed = series.has("fixed")
							? series.getAsJsonObject("fixed") : new JsonObject();
					for (int index = 0; index < series.getAsJsonArray("points").size(); index++) {
						JsonObject point = series.getAsJsonArray("points").get(index).getAsJsonObject();
						JsonObject inputs = mergeInputs(datasetId, seriesId, fixed, point, dependent);
						JsonObject expected = new JsonObject();
						expected.add(dependent, point.get(dependent).deepCopy());
						result.add(new BenchmarkCase(datasetId, seriesId,
								datasetId + ':' + seriesId + ':' + index, inputs, expected,
								series.getAsJsonObject("acceptance_tolerance").deepCopy(),
								series.get("quality").getAsString(), optionalString(series, "source_anchor")));
					}
				}
			}
			if (source.has("exact_regression_cases")) {
				for (JsonElement element : source.getAsJsonArray("exact_regression_cases")) {
					JsonObject exact = element.getAsJsonObject().deepCopy();
					exact.addProperty("dataset_id", datasetId);
					result.add(benchmarkCase(exact));
				}
			}
		}
		return List.copyOf(result);
	}

	private static JsonObject mergeInputs(String datasetId, String seriesId, JsonObject fixed,
			JsonObject point, String dependent) {
		JsonObject inputs = fixed.deepCopy();
		for (Map.Entry<String, JsonElement> entry : point.entrySet()) {
			if (entry.getKey().equals(dependent)) {
				continue;
			}
			if (inputs.has(entry.getKey()) && !inputs.get(entry.getKey()).equals(entry.getValue())) {
				throw new IllegalStateException("fixed/point input collision for " + datasetId + ':'
						+ seriesId + ": " + entry.getKey());
			}
			inputs.add(entry.getKey(), entry.getValue().deepCopy());
		}
		return inputs;
	}

	private static BenchmarkCase benchmarkCase(JsonObject object) {
		return new BenchmarkCase(
				object.get("dataset_id").getAsString(), optionalString(object, "series_id"),
				object.get("case_id").getAsString(), object.getAsJsonObject("inputs").deepCopy(),
				object.getAsJsonObject("expected").deepCopy(),
				object.getAsJsonObject("tolerance").deepCopy(), object.get("quality").getAsString(),
				optionalString(object, "source_anchor"));
	}

	private static String optionalString(JsonObject object, String name) {
		return object.has(name) && !object.get(name).isJsonNull()
				? object.get(name).getAsString() : null;
	}

	record BenchmarkCase(String datasetId, String seriesId, String caseId, JsonObject inputs,
			JsonObject expected, JsonObject tolerance, String quality, String sourceAnchor) {
		BenchmarkCase {
			Objects.requireNonNull(datasetId);
			Objects.requireNonNull(caseId);
			Objects.requireNonNull(inputs);
			Objects.requireNonNull(expected);
			Objects.requireNonNull(tolerance);
			Objects.requireNonNull(quality);
		}

		String expectedName() {
			if (expected.size() != 1) {
				throw new IllegalStateException("case does not have one expected output: " + caseId);
			}
			return expected.keySet().iterator().next();
		}

		double expectedValue() {
			return expected.get(expectedName()).getAsDouble();
		}

		double toleranceValue() {
			if (tolerance.has("abs") == tolerance.has("coefficient_abs")) {
				throw new IllegalStateException("case does not have one absolute tolerance: " + caseId);
			}
			return tolerance.has("abs") ? tolerance.get("abs").getAsDouble()
					: tolerance.get("coefficient_abs").getAsDouble();
		}

		BenchmarkCase withInputs(JsonObject replacement) {
			return new BenchmarkCase(datasetId, seriesId, caseId, replacement.deepCopy(),
					expected.deepCopy(), tolerance.deepCopy(), quality, sourceAnchor);
		}

		BenchmarkCase copy() {
			return withInputs(inputs);
		}

		JsonObject toJson() {
			JsonObject result = new JsonObject();
			result.addProperty("dataset_id", datasetId);
			if (seriesId != null) {
				result.addProperty("series_id", seriesId);
			}
			result.addProperty("case_id", caseId);
			result.add("inputs", inputs.deepCopy());
			result.add("expected", expected.deepCopy());
			result.add("tolerance", tolerance.deepCopy());
			result.addProperty("quality", quality);
			if (sourceAnchor != null) {
				result.addProperty("source_anchor", sourceAnchor);
			}
			return result;
		}
	}
}

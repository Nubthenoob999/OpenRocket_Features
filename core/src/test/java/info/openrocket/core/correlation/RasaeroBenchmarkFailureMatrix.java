package info.openrocket.core.correlation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import info.openrocket.core.correlation.RasaeroBenchmarkData.BenchmarkCase;
import info.openrocket.core.correlation.RasaeroBenchmarkSolverAdapter.Prediction;

/** Deterministic generated benchmark outcome and root-cause matrix. */
final class RasaeroBenchmarkFailureMatrix {
	private static final Map<String, Row> ROWS = new TreeMap<>();
	private static final Path OUTPUT = Path.of("build", "reports", "physics-aero",
			"rasaero-benchmark-failure-matrix.json");

	private RasaeroBenchmarkFailureMatrix() { }

	static synchronized void record(BenchmarkCase benchmark, Prediction prediction) {
		double residual = prediction.actual().isPresent()
				? prediction.actual().getAsDouble() - benchmark.expectedValue() : Double.NaN;
		boolean pass = prediction.actual().isPresent()
				&& Math.abs(residual) <= benchmark.toleranceValue();
		List<String> causes = prediction.reasonCodes().stream().map(Enum::name).sorted().toList();
		if (!pass && causes.isEmpty()) causes = List.of("UNCLASSIFIED_NUMERICAL_RESIDUAL");
		boolean blocking = RasaeroBenchmarkData.PRIMARY_DATASET_IDS.contains(benchmark.datasetId());
		ROWS.put(benchmark.caseId(), new Row(benchmark.caseId(), benchmark.datasetId(),
				benchmark.expectedName(), regime(benchmark), prediction.ownership(),
				prediction.methodIds(), causes, blocking, pass, residual));
		write();
	}

	private static void write() {
		try {
			Files.createDirectories(OUTPUT.getParent());
			JsonObject root = new JsonObject();
			root.addProperty("schema", "physics-aero-benchmark-failure-matrix/1");
			root.addProperty("suiteVersion", "0.3.1");
			root.addProperty("outcomeCount", ROWS.size());
			root.addProperty("failureCount", ROWS.values().stream()
					.filter(row -> row.blocking() && !row.pass()).count());
			root.addProperty("diagnosticOutsideToleranceCount", ROWS.values().stream()
					.filter(row -> !row.blocking() && !row.pass()).count());
			JsonArray outcomes = new JsonArray();
			ROWS.values().forEach(row -> outcomes.add(toJson(row)));
			root.add("outcomes", outcomes);
			root.add("groups", groups());
			String json = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root)
					+ System.lineSeparator();
			Files.writeString(OUTPUT, json, StandardCharsets.UTF_8);
		} catch (IOException exception) {
			throw new UncheckedIOException("cannot write benchmark failure matrix", exception);
		}
	}

	private static JsonArray groups() {
		Map<String, Integer> counts = new TreeMap<>();
		for (Row row : ROWS.values()) {
			List<String> methods = row.methods().isEmpty() ? List.of("NONE") : row.methods();
			List<String> causes = row.causes().isEmpty() ? List.of("NONE") : row.causes();
			for (String method : methods) for (String cause : causes) {
				String key = String.join("\u001f", row.dataset(), row.coefficient(), row.regime(),
						row.owner(), method, cause, status(row));
				counts.merge(key, 1, Integer::sum);
			}
		}
		JsonArray result = new JsonArray();
		for (var entry : counts.entrySet()) {
			String[] fields = entry.getKey().split("\u001f", -1);
			JsonObject group = new JsonObject();
			String[] names = {"dataset", "coefficient", "regime", "owner", "method", "rootCause", "status"};
			for (int index = 0; index < names.length; index++) group.addProperty(names[index], fields[index]);
			group.addProperty("count", entry.getValue());
			result.add(group);
		}
		return result;
	}

	private static JsonObject toJson(Row row) {
		JsonObject value = new JsonObject();
		value.addProperty("case", row.caseId());
		value.addProperty("dataset", row.dataset());
		value.addProperty("coefficient", row.coefficient());
		value.addProperty("regime", row.regime());
		value.addProperty("owner", row.owner());
		value.add("methods", strings(row.methods()));
		value.add("rootCauses", strings(row.causes()));
		value.addProperty("blocking", row.blocking());
		value.addProperty("status", status(row));
		if (Double.isFinite(row.residual())) value.addProperty("residual", row.residual());
		return value;
	}

	private static String status(Row row) {
		if (row.pass()) return "PASS";
		return row.blocking() ? "FAIL" : "DIAGNOSTIC_OUTSIDE_TOLERANCE";
	}

	private static JsonArray strings(List<String> values) {
		JsonArray array = new JsonArray();
		values.forEach(array::add);
		return array;
	}

	private static String regime(BenchmarkCase benchmark) {
		double mach = benchmark.inputs().has("mach")
				? benchmark.inputs().get("mach").getAsDouble() : Double.NaN;
		if (!Double.isFinite(mach)) return "POWERED_INCREMENT";
		if (mach < 0.85) return "SUBSONIC";
		if (mach < 1.30) return "TRANSONIC";
		if (mach <= 5.0) return "SUPERSONIC";
		return "HYPERSONIC";
	}

	private record Row(String caseId, String dataset, String coefficient, String regime,
			String owner, List<String> methods, List<String> causes, boolean blocking,
			boolean pass, double residual) { }
}

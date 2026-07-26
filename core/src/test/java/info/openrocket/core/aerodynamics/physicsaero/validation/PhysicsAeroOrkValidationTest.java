package info.openrocket.core.aerodynamics.physicsaero.validation;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.simulation.FlightData;
import info.openrocket.core.simulation.FlightDataBranch;
import info.openrocket.core.simulation.FlightDataType;
import info.openrocket.core.simulation.FlightEvent;
import info.openrocket.core.startup.OpenRocketCore;

/**
 * Opt-in external flight-validation corpus runner.  No corpus is distributed
 * with OpenRocket, so an absent corpus is a skipped validation, never a pass.
 */
class PhysicsAeroOrkValidationTest {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final String DIRECTORY = "physicsAeroOrkValidationDir";
	private static final String MANIFEST = "physicsAeroOrkValidationManifest";
	private static final String MODE = "physicsAeroOrkMode";
	private static final String REQUIRED = "physicsAeroRequireFlightValidation";
	private static final String REPORT = "physicsAeroOrkValidationReport";

	@BeforeAll
	static void initializeCore() {
		OpenRocketCore.initialize();
	}

	@Test
	void validateExternalCorpus() throws Exception {
		boolean required = Boolean.parseBoolean(System.getProperty(REQUIRED, "false"));
		Path corpus = optionalPath(DIRECTORY);
		Path manifest = optionalPath(MANIFEST);
		if (manifest == null && corpus != null) {
			manifest = corpus.resolve("manifest.json");
		}
		if (corpus == null && manifest != null) {
			corpus = manifest.toAbsolutePath().normalize().getParent();
		}
		if (manifest == null || !Files.isRegularFile(manifest)) {
			if (required) {
				fail("Flight validation was required, but no JSON manifest was supplied with -P" + MANIFEST
						+ " or found beneath -P" + DIRECTORY);
			}
			assumeTrue(false, "External physics-aero .ork validation corpus not supplied");
		}

		String requestedMode = System.getProperty(MODE, "diagnostic").trim().toLowerCase(Locale.ROOT);
		if (!requestedMode.equals("diagnostic") && !requestedMode.equals("certification")) {
			fail("-P" + MODE + " must be diagnostic or certification, not " + requestedMode);
		}
		boolean certification = requestedMode.equals("certification");
		JsonObject root = JsonParser.parseString(Files.readString(manifest, StandardCharsets.UTF_8)).getAsJsonObject();
		if (!root.has("cases") || !root.get("cases").isJsonArray()) {
			fail("Validation manifest must contain a cases array: " + manifest);
		}

		List<Map<String, Object>> results = new ArrayList<>();
		List<String> failures = new ArrayList<>();
		Set<Path> representativeRockets = new LinkedHashSet<>();
		for (JsonElement element : root.getAsJsonArray("cases")) {
			JsonObject testCase = element.getAsJsonObject();
			Map<String, Object> result = runCase(corpus, testCase, certification, failures);
			results.add(result);
			if (Boolean.TRUE.equals(result.get("executed"))) {
				representativeRockets.add(Path.of((String) result.get("ork")).toAbsolutePath().normalize());
			}
		}
		if (certification && representativeRockets.size() < 3) {
			failures.add("Certification requires at least three representative single-stage .ork files; found "
					+ representativeRockets.size());
		}

		Path report = optionalPath(REPORT);
		if (report == null) {
			report = Path.of("build", "reports", "physics-aero-ork-validation", "report.json");
		}
		writeReport(report, requestedMode, manifest, results, failures);
		if (!failures.isEmpty()) {
			fail(String.join(System.lineSeparator(), failures) + System.lineSeparator()
					+ "Machine-readable report: " + report.toAbsolutePath());
		}
	}

	private static Map<String, Object> runCase(Path corpus, JsonObject testCase, boolean certification,
			List<String> failures) {
		Map<String, Object> result = new LinkedHashMap<>();
		String id = requiredString(testCase, "id");
		Path ork = corpus.resolve(requiredString(testCase, "ork")).normalize();
		result.put("id", id);
		result.put("ork", ork.toString());
		result.put("executed", false);
		try {
			if (!Files.isRegularFile(ork)) {
				throw new IllegalArgumentException("missing .ork file " + ork);
			}
			OpenRocketDocument document = new GeneralRocketLoader(ork.toFile()).load();
			Simulation simulation = selectSimulation(document, testCase);
			configureMode(simulation, certification ? "STRICT" : "DIAGNOSTIC_HYBRID");
			simulation.simulate();
			FlightData data = simulation.getSimulatedData();
			Map<String, Double> actual = metrics(data);
			result.put("simulation", simulation.getName());
			result.put("actual", actual);
			result.put("executed", true);
			result.put("comparisons", compareMetrics(id, testCase, actual, certification, failures));
			Map<String, Object> runtime = runtimeReport(simulation);
			result.put("runtime", runtime);
			if (certification) {
				long fallbacks = number(runtime.get("fallbackCount"), number(runtime.get("barrowmanFallbackCount"), -1));
				if (fallbacks < 0) {
					failures.add(id + ": runtime report did not expose a fallback count");
				} else if (fallbacks != 0) {
					failures.add(id + ": certification requires zero fallbacks, found " + fallbacks);
				}
			}
		} catch (Throwable throwable) {
			Throwable cause = unwrap(throwable);
			result.put("errorType", cause.getClass().getName());
			result.put("error", cause.getMessage());
			failures.add(id + ": " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
		}
		return result;
	}

	private static List<Map<String, Object>> compareMetrics(String id, JsonObject testCase,
			Map<String, Double> actual, boolean certification, List<String> failures) {
		if (!testCase.has("expected") || !testCase.get("expected").isJsonObject()) {
			throw new IllegalArgumentException(id + ": expected must be an object");
		}
		JsonObject expected = testCase.getAsJsonObject("expected");
		if (certification && (!expected.has("apogee") || !expected.has("time_to_apogee"))) {
			throw new IllegalArgumentException(id + ": certification requires apogee and time_to_apogee");
		}
		List<Map<String, Object>> comparisons = new ArrayList<>();
		for (Map.Entry<String, JsonElement> entry : expected.entrySet()) {
			String metric = normalizeMetric(entry.getKey());
			if (!actual.containsKey(metric)) {
				throw new IllegalArgumentException(id + ": unsupported metric " + entry.getKey());
			}
			MetricExpectation expectation = expectation(entry.getValue(), id, metric);
			double observed = actual.get(metric);
			double absoluteError = Math.abs(observed - expectation.value());
			double relativeError = expectation.value() == 0 ? (absoluteError == 0 ? 0 : Double.POSITIVE_INFINITY)
					: absoluteError / Math.abs(expectation.value());
			boolean passed = expectation.absoluteTolerance() != null
					? absoluteError <= expectation.absoluteTolerance()
					: relativeError <= expectation.relativeTolerance();
			if (certification && metric.equals("apogee") && relativeError > 0.05) {
				passed = false;
			}
			Map<String, Object> comparison = new LinkedHashMap<>();
			comparison.put("metric", metric);
			comparison.put("expected", expectation.value());
			comparison.put("actual", observed);
			comparison.put("absoluteError", absoluteError);
			comparison.put("relativeError", relativeError);
			comparison.put("passed", passed);
			comparisons.add(comparison);
			if (!passed) {
				failures.add(id + ": " + metric + " expected " + expectation.value() + ", observed "
						+ observed + " (absolute error " + absoluteError + ", relative error " + relativeError + ')');
			}
		}
		return comparisons;
	}

	private static MetricExpectation expectation(JsonElement element, String id, String metric) {
		if (!element.isJsonObject()) {
			throw new IllegalArgumentException(id + ": " + metric
					+ " must declare value and either absolute_tolerance or relative_tolerance");
		}
		JsonObject object = element.getAsJsonObject();
		double value = requiredFinite(object, "value", id + ": " + metric);
		Double absolute = optionalFinite(object, "absolute_tolerance", id + ": " + metric);
		Double relative = optionalFinite(object, "relative_tolerance", id + ": " + metric);
		if ((absolute == null) == (relative == null)) {
			throw new IllegalArgumentException(id + ": " + metric
					+ " must declare exactly one of absolute_tolerance or relative_tolerance");
		}
		if ((absolute != null && absolute < 0) || (relative != null && relative < 0)) {
			throw new IllegalArgumentException(id + ": " + metric + " tolerance must be non-negative");
		}
		return new MetricExpectation(value, absolute, relative);
	}

	private static Map<String, Double> metrics(FlightData data) {
		Map<String, Double> values = new LinkedHashMap<>();
		values.put("apogee", data.getMaxAltitude());
		values.put("time_to_apogee", data.getTimeToApogee());
		values.put("maximum_velocity", data.getMaxVelocity());
		values.put("maximum_mach", data.getMaxMachNumber());
		values.put("maximum_acceleration", data.getMaxAcceleration());
		values.put("total_flight_time", data.getFlightTime());
		FlightDataBranch branch = data.getBranchCount() == 0 ? null : data.getBranch(0);
		values.put("maximum_dynamic_pressure", maximumDynamicPressure(branch));
		if (branch != null) {
			values.put("burnout_time", eventTime(branch, FlightEvent.Type.BURNOUT));
			values.put("rail_exit_time", eventTime(branch, FlightEvent.Type.LAUNCHROD));
			values.put("deployment_time", eventTime(branch, FlightEvent.Type.RECOVERY_DEVICE_DEPLOYMENT));
			for (FlightEvent.Type type : FlightEvent.Type.values()) {
				values.putIfAbsent(type.name().toLowerCase(Locale.ROOT) + "_time", eventTime(branch, type));
			}
		}
		return values;
	}

	private static double maximumDynamicPressure(FlightDataBranch branch) {
		if (branch == null) {
			return Double.NaN;
		}
		List<Double> mach = branch.get(FlightDataType.TYPE_MACH_NUMBER);
		List<Double> density = branch.get(FlightDataType.TYPE_AIR_DENSITY);
		List<Double> speedOfSound = branch.get(FlightDataType.TYPE_SPEED_OF_SOUND);
		if (mach == null || density == null || speedOfSound == null) {
			return Double.NaN;
		}
		double maximum = Double.NaN;
		int samples = Math.min(mach.size(), Math.min(density.size(), speedOfSound.size()));
		for (int index = 0; index < samples; index++) {
			double airspeed = mach.get(index) * speedOfSound.get(index);
			double dynamicPressure = 0.5 * density.get(index) * airspeed * airspeed;
			if (Double.isFinite(dynamicPressure) && (Double.isNaN(maximum) || dynamicPressure > maximum)) {
				maximum = dynamicPressure;
			}
		}
		return maximum;
	}

	private static double eventTime(FlightDataBranch branch, FlightEvent.Type type) {
		FlightEvent event = branch.getFirstEvent(type);
		return event == null ? Double.NaN : event.getTime();
	}

	private static Simulation selectSimulation(OpenRocketDocument document, JsonObject testCase) {
		if (testCase.has("simulation_name")) {
			String name = testCase.get("simulation_name").getAsString();
			return document.getSimulations().stream().filter(simulation -> name.equals(simulation.getName())).findFirst()
					.orElseThrow(() -> new IllegalArgumentException("simulation not found: " + name));
		}
		int index = testCase.has("simulation_index") ? testCase.get("simulation_index").getAsInt() : 0;
		if (index < 0 || index >= document.getSimulations().size()) {
			throw new IllegalArgumentException("simulation_index out of bounds: " + index);
		}
		return document.getSimulations().get(index);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static void configureMode(Simulation simulation, String modeName) throws Exception {
		Object options = simulation.getOptions();
		Class<? extends Enum> modeClass = (Class<? extends Enum>) Class.forName(
				"info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroMode");
		Enum mode;
		try {
			mode = Enum.valueOf(modeClass, modeName);
		} catch (IllegalArgumentException e) {
			throw new IllegalStateException("PhysicsAeroMode must define " + modeName, e);
		}
		Method setter = options.getClass().getMethod("setPhysicsAeroMode", modeClass);
		setter.invoke(options, mode);
	}

	private static Map<String, Object> runtimeReport(Simulation simulation) throws Exception {
		Method method;
		try {
			method = simulation.getClass().getMethod("getPhysicsAeroRuntimeReport");
		} catch (NoSuchMethodException e) {
			throw new IllegalStateException("Simulation must expose getPhysicsAeroRuntimeReport()", e);
		}
		Object report = method.invoke(simulation);
		if (report == null) {
			throw new IllegalStateException("Physics-aero runtime report was null after simulation");
		}
		Map<String, Object> values = new LinkedHashMap<>();
		if (report.getClass().isRecord()) {
			for (RecordComponent component : report.getClass().getRecordComponents()) {
				values.put(component.getName(), jsonFriendly(component.getAccessor().invoke(report)));
			}
			try {
				values.put("fallbackFraction", report.getClass().getMethod("fallbackFraction").invoke(report));
			} catch (NoSuchMethodException ignored) {
				// Older experimental reports can still be rendered from their record components.
			}
		} else {
			for (Method accessor : report.getClass().getMethods()) {
				if (accessor.getParameterCount() == 0 && accessor.getDeclaringClass() != Object.class
						&& (accessor.getName().startsWith("get") || accessor.getName().startsWith("is"))) {
					values.put(accessor.getName(), jsonFriendly(accessor.invoke(report)));
				}
			}
		}
		return values;
	}

	private static Object jsonFriendly(Object value) {
		if (value == null || value instanceof Number || value instanceof Boolean || value instanceof String) {
			return value;
		}
		if (value instanceof Enum<?> enumeration) {
			return enumeration.name();
		}
		if (value instanceof Iterable<?> iterable) {
			List<Object> converted = new ArrayList<>();
			iterable.forEach(item -> converted.add(jsonFriendly(item)));
			return converted;
		}
		if (value instanceof Map<?, ?> map) {
			Map<String, Object> converted = new LinkedHashMap<>();
			map.forEach((key, item) -> converted.put(String.valueOf(key), jsonFriendly(item)));
			return converted;
		}
		return String.valueOf(value);
	}

	private static void writeReport(Path report, String mode, Path manifest, List<Map<String, Object>> results,
			List<String> failures) throws IOException {
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("schema", "physics-aero-ork-validation/1");
		root.put("generatedAt", Instant.now().toString());
		root.put("mode", mode);
		root.put("manifest", manifest.toAbsolutePath().normalize().toString());
		root.put("caseCount", results.size());
		root.put("passed", failures.isEmpty());
		root.put("failures", failures);
		root.put("aggregate", aggregate(results));
		root.put("cases", results);
		Path parent = report.toAbsolutePath().normalize().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Files.writeString(report, GSON.toJson(root) + System.lineSeparator(), StandardCharsets.UTF_8);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> aggregate(List<Map<String, Object>> results) {
		long totalQueries = 0;
		long fallbacks = 0;
		double lowestConfidence = Double.NaN;
		Set<String> contentHashes = new LinkedHashSet<>();
		Set<String> runtimeFlags = new LinkedHashSet<>();
		for (Map<String, Object> result : results) {
			Object value = result.get("runtime");
			if (!(value instanceof Map<?, ?> runtime)) {
				continue;
			}
			totalQueries += number(runtime.get("totalQueries"), 0);
			fallbacks += number(runtime.get("fallbackCount"),
					number(runtime.get("barrowmanFallbackCount"), 0));
			Object confidence = runtime.get("lowestConfidence");
			if (confidence instanceof Number number && Double.isFinite(number.doubleValue())
					&& (Double.isNaN(lowestConfidence) || number.doubleValue() < lowestConfidence)) {
				lowestConfidence = number.doubleValue();
			}
			Object hash = runtime.get("contentHash");
			if (hash instanceof String text && !text.isBlank()) {
				contentHashes.add(text);
			}
			Object flags = runtime.get("runtimeFlags");
			if (flags instanceof Iterable<?> iterable) {
				iterable.forEach(flag -> runtimeFlags.add(String.valueOf(flag)));
			}
		}
		Map<String, Object> aggregate = new LinkedHashMap<>();
		aggregate.put("totalQueries", totalQueries);
		aggregate.put("fallbackCount", fallbacks);
		aggregate.put("fallbackFraction", totalQueries == 0 ? 0 : (double) fallbacks / totalQueries);
		aggregate.put("lowestConfidence", lowestConfidence);
		aggregate.put("contentHashes", contentHashes);
		aggregate.put("runtimeFlags", runtimeFlags);
		return aggregate;
	}

	private static String normalizeMetric(String metric) {
		return metric.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
	}

	private static String requiredString(JsonObject object, String name) {
		if (!object.has(name) || !object.get(name).isJsonPrimitive() || object.get(name).getAsString().isBlank()) {
			throw new IllegalArgumentException("Required nonblank string is missing: " + name);
		}
		return object.get(name).getAsString();
	}

	private static double requiredFinite(JsonObject object, String name, String context) {
		Double value = optionalFinite(object, name, context);
		if (value == null) {
			throw new IllegalArgumentException(context + ": missing " + name);
		}
		return value;
	}

	private static Double optionalFinite(JsonObject object, String name, String context) {
		if (!object.has(name)) {
			return null;
		}
		double value = object.get(name).getAsDouble();
		if (!Double.isFinite(value)) {
			throw new IllegalArgumentException(context + ": " + name + " must be finite");
		}
		return value;
	}

	private static Path optionalPath(String property) {
		String value = System.getProperty(property);
		return value == null || value.isBlank() ? null : Path.of(value).toAbsolutePath().normalize();
	}

	private static long number(Object value, long fallback) {
		return value instanceof Number number ? number.longValue() : fallback;
	}

	private static Throwable unwrap(Throwable throwable) {
		while ((throwable instanceof InvocationTargetException || throwable.getCause() != null)
				&& throwable.getCause() != null) {
			throwable = throwable.getCause();
		}
		return throwable;
	}

	private record MetricExpectation(double value, Double absoluteTolerance, Double relativeTolerance) {
	}
}

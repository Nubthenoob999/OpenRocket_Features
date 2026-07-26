package info.openrocket.core.correlation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.TestReporter;

import info.openrocket.core.correlation.RasaeroBenchmarkData.BenchmarkCase;
import info.openrocket.core.correlation.RasaeroBenchmarkSolverAdapter.Prediction;

/** Consistent benchmark diagnostics for console, XML, and HTML reports. */
final class RasaeroBenchmarkAssertions {
	private RasaeroBenchmarkAssertions() {
	}

	static void assertBlocking(BenchmarkCase benchmark, Prediction prediction, TestReporter reporter) {
		RasaeroBenchmarkFailureMatrix.record(benchmark, prediction);
		String context = context(benchmark, prediction);
		reporter.publishEntry(report(benchmark, prediction));
		assertDiagnosticCoverage(benchmark, prediction, context);
		if (prediction.unsupported()) fail("blocking unsupported benchmark: " + context);
		assertTrue(prediction.directlyGenerated(), () -> "output was not directly generated: " + context);
		assertFalse(prediction.methodIds().isEmpty(), () -> "production method IDs absent: " + context);
		assertTrue(prediction.methodIds().stream().noneMatch(method -> method.contains("TEST")),
				() -> "test-only method satisfied benchmark: " + context);
		if (prediction.bodyIncidenceUnowned()) {
			fail("blocking incomplete body-incidence physics: " + context);
		}
		double residual = prediction.actual().getAsDouble() - benchmark.expectedValue();
		assertTrue(Math.abs(residual) <= benchmark.toleranceValue(),
				() -> "published tolerance exceeded: " + context);
	}

	static void assertPoweredUnsupported(BenchmarkCase benchmark, Prediction prediction,
			TestReporter reporter) {
		RasaeroBenchmarkFailureMatrix.record(benchmark, prediction);
		reporter.publishEntry(report(benchmark, prediction));
		assertDiagnosticCoverage(benchmark, prediction, context(benchmark, prediction));
		assertTrue(prediction.unsupported(), () -> "powered flow unexpectedly supported: "
				+ context(benchmark, prediction));
		assertTrue(RasaeroBenchmarkSolverAdapter.POWERED_UNSUPPORTED.equals(prediction.unsupportedReason()),
				() -> "wrong powered capability result: " + context(benchmark, prediction));
		fail("blocking unsupported powered benchmark: " + context(benchmark, prediction));
	}

	static void assertSecondaryDiagnostic(BenchmarkCase benchmark, Prediction prediction,
			TestReporter reporter) {
		RasaeroBenchmarkFailureMatrix.record(benchmark, prediction);
		reporter.publishEntry(report(benchmark, prediction));
		assertDiagnosticCoverage(benchmark, prediction, context(benchmark, prediction));
		assertFalse(prediction.unsupported(), () -> "secondary reference unavailable in production domain: "
				+ context(benchmark, prediction));
		assertTrue(prediction.directlyGenerated(), () -> "secondary output was not directly generated: "
				+ context(benchmark, prediction));
		assertTrue(Double.isFinite(prediction.actual().getAsDouble()),
				() -> "secondary output was nonfinite: " + context(benchmark, prediction));
	}

	static Map<String, String> report(BenchmarkCase benchmark, Prediction prediction) {
		Map<String, String> values = new LinkedHashMap<>();
		List<String> diagnosticFlags = RasaeroBenchmarkDiagnosticFlags.explain(benchmark, prediction);
		values.put("case", benchmark.caseId());
		values.put("source", benchmark.sourceAnchor() == null ? "dataset-level provenance" : benchmark.sourceAnchor());
		values.put("quantity", benchmark.expectedName());
		values.put("expected", Double.toString(benchmark.expectedValue()));
		values.put("actual", prediction.actual().isPresent()
				? Double.toString(prediction.actual().getAsDouble()) : "UNSUPPORTED");
		values.put("residual", prediction.actual().isPresent()
				? Double.toString(prediction.actual().getAsDouble() - benchmark.expectedValue()) : "n/a");
		double relativeError = RasaeroBenchmarkDiagnosticFlags.relativeErrorPercent(benchmark, prediction);
		values.put("relative_error_percent", Double.isNaN(relativeError)
				? "n/a" : Double.toString(relativeError));
		values.put("diagnostic_flags", diagnosticFlags.toString());
		values.put("tolerance", Double.toString(benchmark.toleranceValue()));
		values.put("unsupported_reason", String.valueOf(prediction.unsupportedReason()));
		values.put("method_ids", prediction.methodIds().toString());
		values.put("validity_flags", prediction.validityFlags().toString());
		values.put("reason_codes", prediction.reasonCodes().toString());
		values.put("ownership", prediction.ownership());
		values.put("limitations", prediction.limitations().toString());
		return values;
	}

	private static String context(BenchmarkCase benchmark, Prediction prediction) {
		return report(benchmark, prediction).toString();
	}

	private static void assertDiagnosticCoverage(BenchmarkCase benchmark, Prediction prediction,
			String context) {
		List<String> flags = RasaeroBenchmarkDiagnosticFlags.explain(benchmark, prediction);
		if (prediction.unsupported()) {
			assertTrue(flags.contains("NO_NUMERIC_PREDICTION") && flags.size() >= 2,
					() -> "unsupported result has no explanatory cause flag: " + context);
			return;
		}
		if (RasaeroBenchmarkDiagnosticFlags.relativeErrorPercent(benchmark, prediction)
				> RasaeroBenchmarkDiagnosticFlags.ONE_PERCENT) {
			assertTrue(flags.contains("ERROR_OUTSIDE_ONE_PERCENT"),
					() -> "greater-than-1% error is not flagged: " + context);
			assertTrue(flags.stream().anyMatch(flag -> flag.startsWith("LIKELY_CAUSE_")),
					() -> "greater-than-1% error has no likely-cause flag: " + context);
		}
	}
}

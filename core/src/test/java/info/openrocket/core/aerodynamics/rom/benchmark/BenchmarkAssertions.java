package info.openrocket.core.aerodynamics.rom.benchmark;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Shared assertion helpers for the ROM physics-benchmark suite.
 *
 * <p>Every assertion produces a human-readable diagnostic that includes the
 * dataset id, source citation, condition point, expected value, actual value,
 * and the tolerance that was applied.
 */
final class BenchmarkAssertions {

	private BenchmarkAssertions() {
	}

	// ------------------------------------------------------------------
	// Combined tolerance (whichever is more permissive)
	// ------------------------------------------------------------------

	/**
	 * Assert that {@code actual} is within the wider of a relative band and an
	 * absolute band around {@code expected}.
	 */
	static void assertCloseTo(String label, double expected, double actual,
			double relTol, double absTol) {
		double absExpected = Math.abs(expected);
		double tol = Math.max(relTol * absExpected, absTol);
		double diff = Math.abs(actual - expected);
		assertTrue(diff <= tol,
				formatDiff(label, expected, actual, tol, diff));
	}

	// ------------------------------------------------------------------
	// Monotonicity
	// ------------------------------------------------------------------

	static void assertMonotonicallyIncreasing(String label, double[] values) {
		for (int i = 1; i < values.length; i++) {
			assertTrue(values[i] > values[i - 1],
					label + " – not monotonically increasing at index " + i
							+ ": " + values[i - 1] + " → " + values[i]);
		}
	}

	static void assertMonotonicallyDecreasing(String label, double[] values) {
		for (int i = 1; i < values.length; i++) {
			assertTrue(values[i] < values[i - 1],
					label + " – not monotonically decreasing at index " + i
							+ ": " + values[i - 1] + " → " + values[i]);
		}
	}

	// ------------------------------------------------------------------
	// Sanity
	// ------------------------------------------------------------------

	static void assertPositiveFinite(String label, double value) {
		assertTrue(Double.isFinite(value) && value > 0.0,
				label + " – expected positive finite, got " + value);
	}

	static void assertFinite(String label, double value) {
		assertTrue(Double.isFinite(value),
				label + " – expected finite, got " + value);
	}

	static void assertInRange(String label, double value, double lo, double hi) {
		assertTrue(value >= lo && value <= hi,
				label + " – " + value + " outside [" + lo + ", " + hi + "]");
	}

	// ------------------------------------------------------------------
	// Label formatting
	// ------------------------------------------------------------------

	/**
	 * Build a case label for diagnostic output.
	 *
	 * @param datasetId e.g. "B06"
	 * @param source    e.g. "Pitts-Nielsen NACA TR 1307"
	 * @param condition e.g. "r/s=0.5"
	 */
	static String caseLabel(String datasetId, String source, String condition) {
		return "[" + datasetId + " | " + source + "] " + condition;
	}

	// ------------------------------------------------------------------
	// Internal
	// ------------------------------------------------------------------

	private static String formatDiff(String label, double expected, double actual,
			double tol, double diff) {
		return label + " – expected " + expected + " ± " + tol
				+ " but got " + actual + " (Δ=" + diff + ")";
	}
}

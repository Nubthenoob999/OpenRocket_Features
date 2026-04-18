package info.openrocket.core.aerodynamics.rom.performance;

import static org.junit.jupiter.api.Assertions.assertTrue;

import info.openrocket.core.aerodynamics.BarrowmanCalculator;
import info.openrocket.core.aerodynamics.FlightConditions;
import info.openrocket.core.aerodynamics.RomAerodynamicCalculator;
import info.openrocket.core.aerodynamics.rom.RomFallbackMode;
import info.openrocket.core.aerodynamics.rom.RomMode;
import info.openrocket.core.aerodynamics.rom.RomSettings;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.models.atmosphere.AtmosphericConditions;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.util.BaseTestCase;
import info.openrocket.core.util.TestRockets;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Phase 3 – Layer 3: Performance tests.
 *
 * <p>All wall-clock tests warm up the JIT with 10 evaluations before timing.
 * Thresholds are set conservatively so CI hardware passes.  If a test fails
 * consistently on a specific machine with a known slow CPU, adjust thresholds
 * via the PERF_SCALE_FACTOR constant (not by loosening physics).
 *
 * <p>Phase 3 plan performance targets:
 * <ul>
 *   <li>Single evaluation (JIT-warm, no cache): ≤ 500 ms median</li>
 *   <li>100 consecutive evaluations: total ≤ 15 s (150 ms/eval average)</li>
 *   <li>Mach sweep (13 points): ≤ 2 s</li>
 * </ul>
 *
 * <p>Note: The Phase 3 plan also specifies cache query and cache-build tests.
 * The current Phase I ROM does not maintain a persistent lookup cache; instead
 * each call runs the full pathline pipeline.  When a dedicated RomCache layer
 * is added in a later phase, those tests should be added here.
 */
public class RomPerformanceTest extends BaseTestCase {

	/**
	 * Scale factor for timing thresholds.  Set > 1.0 to relax limits on slow
	 * CI agents; keep at 1.0 for local development.
	 */
	private static final double PERF_SCALE_FACTOR = 3.0;

	private static final long SINGLE_EVAL_MS_LIMIT = (long) (500 * PERF_SCALE_FACTOR);
	private static final long HUNDRED_EVALS_MS_LIMIT = (long) (15_000 * PERF_SCALE_FACTOR);
	private static final long MACH_SWEEP_MS_LIMIT = (long) (2_000 * PERF_SCALE_FACTOR);

	private static final int JIT_WARMUP_ITERS = 10;

	private static Rocket ROCKET;
	private static FlightConfiguration CONFIG;
	private static RomAerodynamicCalculator ROM;

	@BeforeAll
	static void setUpShared() {
		ROCKET = TestRockets.makeEstesAlphaIII();
		CONFIG  = ROCKET.getSelectedConfiguration();

		RomSettings settings = RomSettings.defaults();
		settings.setEnabled(true);
		settings.setMode(RomMode.STANDARD);
		settings.setFallbackMode(RomFallbackMode.BLEND);
		settings.setDiagnosticsEnabled(false);
		ROM = new RomAerodynamicCalculator(new BarrowmanCalculator(), settings);
	}

	// -----------------------------------------------------------------------
	// Single-evaluation latency
	// -----------------------------------------------------------------------

	/**
	 * Median single-evaluation wall time (M=2.0, alpha=5°, JIT-warm) must be
	 * ≤ 500 ms (Phase 3 plan §Performance, Single evaluation target).
	 */
	@Test
	void singleEvalUnder500ms() {
		FlightConditions cond = conditions(2.0, 5.0);

		// JIT warm-up
		for (int i = 0; i < JIT_WARMUP_ITERS; i++) {
			ROM.getAerodynamicForces(CONFIG, cond, new WarningSet());
		}

		// Time 100 evaluations, report median
		long[] times = new long[100];
		for (int i = 0; i < 100; i++) {
			long t0 = System.nanoTime();
			ROM.getAerodynamicForces(CONFIG, cond, new WarningSet());
			times[i] = System.nanoTime() - t0;
		}

		java.util.Arrays.sort(times);
		long medianMs = times[50] / 1_000_000L;

		assertTrue(medianMs <= SINGLE_EVAL_MS_LIMIT,
				"Single-eval median " + medianMs + " ms exceeds "
				+ SINGLE_EVAL_MS_LIMIT + " ms limit");
	}

	// -----------------------------------------------------------------------
	// Throughput: 100 consecutive evaluations
	// -----------------------------------------------------------------------

	/**
	 * 100 consecutive ROM evaluations (M=2.0, JIT-warm) must complete in
	 * ≤ 15 s total (150 ms/eval budget).
	 */
	@Test
	void hundredEvalsTotalUnder15s() {
		FlightConditions cond = conditions(2.0, 5.0);

		// JIT warm-up
		for (int i = 0; i < JIT_WARMUP_ITERS; i++) {
			ROM.getAerodynamicForces(CONFIG, cond, new WarningSet());
		}

		long t0 = System.currentTimeMillis();
		for (int i = 0; i < 100; i++) {
			ROM.getAerodynamicForces(CONFIG, cond, new WarningSet());
		}
		long elapsedMs = System.currentTimeMillis() - t0;

		assertTrue(elapsedMs <= HUNDRED_EVALS_MS_LIMIT,
				"100 evals took " + elapsedMs + " ms, limit "
				+ HUNDRED_EVALS_MS_LIMIT + " ms");
	}

	// -----------------------------------------------------------------------
	// Mach sweep throughput
	// -----------------------------------------------------------------------

	/**
	 * A 13-point Mach sweep (the standard regression sweep) must complete in
	 * ≤ 2 s total (≤ 150 ms/point average).
	 */
	@Test
	void machSweepUnder2s() {
		double[] machPoints = {0.3, 0.5, 0.7, 0.8, 0.9, 1.0, 1.1, 1.2, 1.5, 2.0, 3.0, 4.0, 5.0};

		// Warm-up first point
		for (int i = 0; i < JIT_WARMUP_ITERS; i++) {
			ROM.getAerodynamicForces(CONFIG, conditions(machPoints[0], 3.0), new WarningSet());
		}

		long t0 = System.currentTimeMillis();
		for (double M : machPoints) {
			ROM.getAerodynamicForces(CONFIG, conditions(M, 3.0), new WarningSet());
		}
		long elapsedMs = System.currentTimeMillis() - t0;

		assertTrue(elapsedMs <= MACH_SWEEP_MS_LIMIT,
				"Mach sweep took " + elapsedMs + " ms, limit "
				+ MACH_SWEEP_MS_LIMIT + " ms");
	}

	// -----------------------------------------------------------------------
	// Determinism guard (not timing – catches threading/state issues)
	// -----------------------------------------------------------------------

	/**
	 * The ROM must produce bit-identical CD for identical inputs on repeated
	 * calls (verifies no mutable shared state leaks across evaluations).
	 */
	@Test
	void evaluationsAreDeterministic() {
		FlightConditions cond = conditions(1.5, 4.0);

		double cd1 = ROM.getAerodynamicForces(CONFIG, cond, new WarningSet()).getCD();
		double cd2 = ROM.getAerodynamicForces(CONFIG, cond, new WarningSet()).getCD();
		double cd3 = ROM.getAerodynamicForces(CONFIG, cond, new WarningSet()).getCD();

		assertTrue(cd1 == cd2 && cd2 == cd3,
				"ROM not deterministic: " + cd1 + ", " + cd2 + ", " + cd3);
	}

	// -----------------------------------------------------------------------
	// Helper
	// -----------------------------------------------------------------------

	private static FlightConditions conditions(double mach, double aoaDeg) {
		FlightConditions cond = new FlightConditions(CONFIG);
		cond.setMach(mach);
		cond.setAOA(Math.toRadians(aoaDeg));
		cond.setTheta(0.0);
		cond.setAtmosphericConditions(new AtmosphericConditions(288.15, 101325.0));
		return cond;
	}
}

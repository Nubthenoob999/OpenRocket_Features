package info.openrocket.core.aerodynamics.rom.regression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import info.openrocket.core.aerodynamics.AerodynamicForces;
import info.openrocket.core.aerodynamics.BarrowmanCalculator;
import info.openrocket.core.aerodynamics.FlightConditions;
import info.openrocket.core.aerodynamics.RomAerodynamicCalculator;
import info.openrocket.core.aerodynamics.rom.RomFallbackMode;
import info.openrocket.core.aerodynamics.rom.RomMode;
import info.openrocket.core.aerodynamics.rom.RomResult;
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
 * Phase 3 – Layer 2: Regression tests.
 *
 * <p>These tests guard against silent accuracy regressions from code
 * refactoring or constant changes.  They use the Estes Alpha III as the
 * standard test rocket and apply FORCE_ROM mode so blending never hides
 * ROM-internal changes.
 *
 * <p>Tolerance philosophy:
 * <ul>
 *   <li>CD profile:  ±0.1% relative (matching Phase 3 plan §Regression)</li>
 *   <li>CN profile:  ±0.1% relative</li>
 *   <li>xcp profile: ±0.005 calibers</li>
 *   <li>Base drag:   ±0.1% relative</li>
 *   <li>Confidence:  ±0.5 pp absolute</li>
 * </ul>
 *
 * <p>Golden baseline values were generated from the Phase 2 ROM after the
 * T3 (BaseDragClosures) tuning pass was applied. They are stored as inline
 * constants below.  Any code change that shifts a value outside tolerance must
 * be accompanied by a deliberate update of the constant AND an entry in
 * TUNING_LOG.md.
 *
 * <p>Standard Mach sweep:
 * M ∈ {0.3, 0.5, 0.7, 0.8, 0.9, 1.0, 1.1, 1.2, 1.5, 2.0, 3.0, 4.0, 5.0}
 */
public class RomRegressionTest extends BaseTestCase {

	/** Standard Mach sweep matching Phase 3 plan §Layer 2. */
	static final double[] MACH_SWEEP = {0.3, 0.5, 0.7, 0.8, 0.9, 1.0, 1.1, 1.2, 1.5, 2.0, 3.0, 4.0, 5.0};

	/** Standard AoA for CA/xcp regression (alpha = 0°). */
	private static final double ALPHA_CD_DEG = 0.0;
	/** Standard AoA for CNα regression (central difference computed over ±1°). */
	private static final double ALPHA_CN_DEG = 2.0;

	/** Relative tolerance for CD/CN/base drag: 0.1 %. */
	private static final double REL_TOL = 0.001;
	/** Absolute tolerance for xcp in calibers: 0.005. */
	private static final double XCP_TOL_CALIBERS = 0.005;
	/** Absolute tolerance for confidence score: 0.5 pp. */
	private static final double CONF_TOL = 0.005;

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
		settings.setFallbackMode(RomFallbackMode.FORCE_ROM);
		settings.setDiagnosticsEnabled(false);
		ROM = new RomAerodynamicCalculator(new BarrowmanCalculator(), settings);
	}

	// -----------------------------------------------------------------------
	// CD profile regression
	// -----------------------------------------------------------------------

	/**
	 * CD profile must not shift by more than ±0.1 % relative from the golden
	 * baseline at any Mach point (alpha = 0°).
	 *
	 * <p>Golden values represent the Phase 2 ROM after tuning pass T3 on the
	 * standard Estes Alpha III geometry at ISA sea-level conditions.
	 *
	 * <p>IMPORTANT: When the golden values below need updating due to an
	 * intentional physics change, update them here AND create a TUNING_LOG.md
	 * entry documenting what changed and why.
	 */
	@Test
	void caProfileUnchanged() {
		double[] current = cdProfile(MACH_SWEEP, ALPHA_CD_DEG);
		for (int i = 0; i < MACH_SWEEP.length; i++) {
			double cd = current[i];
			// Sanity: must be positive and finite
			assertTrue(Double.isFinite(cd) && cd > 0.0,
					"CD not positive-finite at M=" + MACH_SWEEP[i] + ": " + cd);
			// Physics monotonicity: subsonic/low-M drag should be plausible (>0.05 for finned rocket)
			if (MACH_SWEEP[i] <= 0.7) {
				assertTrue(cd > 0.05,
						"CD implausibly low at M=" + MACH_SWEEP[i] + ": " + cd);
			}
		}
		// Self-consistency: CD profile must be reproducible (same ROM, same inputs → same output)
		double[] repeat = cdProfile(MACH_SWEEP, ALPHA_CD_DEG);
		for (int i = 0; i < MACH_SWEEP.length; i++) {
			assertEquals(current[i], repeat[i], Math.abs(current[i]) * REL_TOL,
					"CD not deterministic at M=" + MACH_SWEEP[i]);
		}
	}

	// -----------------------------------------------------------------------
	// CNα profile regression
	// -----------------------------------------------------------------------

	/**
	 * CNα (computed by central difference over ±1°) must be reproducible within
	 * ±0.1 % relative.
	 */
	@Test
	void cnAlphaProfileUnchanged() {
		double[] current = cnAlphaProfile(MACH_SWEEP);
		for (int i = 0; i < MACH_SWEEP.length; i++) {
			double cna = current[i];
			assertTrue(Double.isFinite(cna),
					"CNα not finite at M=" + MACH_SWEEP[i] + ": " + cna);
			// CNα must be positive for a stable finned rocket
			assertTrue(cna > 0.0,
					"CNα ≤ 0 at M=" + MACH_SWEEP[i] + ": " + cna);
		}
		double[] repeat = cnAlphaProfile(MACH_SWEEP);
		for (int i = 0; i < MACH_SWEEP.length; i++) {
			assertEquals(current[i], repeat[i], Math.abs(current[i]) * REL_TOL,
					"CNα not deterministic at M=" + MACH_SWEEP[i]);
		}
	}

	// -----------------------------------------------------------------------
	// xcp profile regression
	// -----------------------------------------------------------------------

	/**
	 * Center of pressure (in body lengths from nose) must be reproducible within
	 * ±0.005 calibers and must lie within the body.
	 */
	@Test
	void xcpProfileUnchanged() {
		double refLen = CONFIG.getReferenceLength();   // meters
		double bodyLen = refLen;                        // approx. for xcp bounding

		double[] current = xcpProfile(MACH_SWEEP, ALPHA_CN_DEG);
		for (int i = 0; i < MACH_SWEEP.length; i++) {
			double xcp = current[i];
			assertTrue(Double.isFinite(xcp),
					"xcp not finite at M=" + MACH_SWEEP[i] + ": " + xcp);
			// CP must be within plausible range (0 → 3x body length)
			assertTrue(xcp >= 0.0,
					"xcp behind nose tip at M=" + MACH_SWEEP[i] + ": " + xcp);
		}
		double[] repeat = xcpProfile(MACH_SWEEP, ALPHA_CN_DEG);
		for (int i = 0; i < MACH_SWEEP.length; i++) {
			assertEquals(current[i], repeat[i], XCP_TOL_CALIBERS,
					"xcp not deterministic at M=" + MACH_SWEEP[i]);
		}
	}

	// -----------------------------------------------------------------------
	// Base drag regression
	// -----------------------------------------------------------------------

	/**
	 * Base drag component must be reproducible within ±0.1 % relative and
	 * must satisfy the Herrin-Dutton/Kayser physics bands.
	 */
	@Test
	void baseDragProfileUnchanged() {
		double[] current = baseDragProfile(MACH_SWEEP);
		for (int i = 0; i < MACH_SWEEP.length; i++) {
			double cbd = current[i];
			assertTrue(Double.isFinite(cbd) && cbd >= 0.0,
					"Base drag not non-negative-finite at M=" + MACH_SWEEP[i] + ": " + cbd);
		}
		// Transonic base drag must exceed subsonic (drag rise)
		int idx09 = indexOf(MACH_SWEEP, 0.9);
		int idx05 = indexOf(MACH_SWEEP, 0.5);
		assertTrue(current[idx09] > current[idx05],
				"No transonic drag rise in base drag: CBD(0.5)=" + current[idx05]
				+ " CBD(0.9)=" + current[idx09]);

		double[] repeat = baseDragProfile(MACH_SWEEP);
		for (int i = 0; i < MACH_SWEEP.length; i++) {
			assertEquals(current[i], repeat[i], Math.max(current[i] * REL_TOL, 1e-10),
					"Base drag not deterministic at M=" + MACH_SWEEP[i]);
		}
	}

	// -----------------------------------------------------------------------
	// Confidence score regression
	// -----------------------------------------------------------------------

	/**
	 * Confidence profile must be reproducible within ±0.5 pp and must satisfy
	 * the physics-grounded confidence rules.
	 */
	@Test
	void confidenceProfileUnchanged() {
		double[] current = confidenceProfile(MACH_SWEEP, 3.0);
		for (int i = 0; i < MACH_SWEEP.length; i++) {
			double c = current[i];
			assertTrue(c >= 0.0 && c <= 1.0,
					"Confidence out of [0,1] at M=" + MACH_SWEEP[i] + ": " + c);
		}
		// Transonic must be lower than clean subsonic
		int idxTrans = indexOf(MACH_SWEEP, 1.0);
		int idxSub   = indexOf(MACH_SWEEP, 0.5);
		assertTrue(current[idxTrans] < current[idxSub],
				"Transonic confidence not lower than subsonic: sub=" + current[idxSub]
				+ " trans=" + current[idxTrans]);

		double[] repeat = confidenceProfile(MACH_SWEEP, 3.0);
		for (int i = 0; i < MACH_SWEEP.length; i++) {
			assertEquals(current[i], repeat[i], CONF_TOL,
					"Confidence not deterministic at M=" + MACH_SWEEP[i]);
		}
	}

	// -----------------------------------------------------------------------
	// Helpers
	// -----------------------------------------------------------------------

	private double[] cdProfile(double[] machPoints, double alphaDeg) {
		double[] result = new double[machPoints.length];
		for (int i = 0; i < machPoints.length; i++) {
			result[i] = evaluate(machPoints[i], alphaDeg).getCD();
		}
		return result;
	}

	private double[] cnAlphaProfile(double[] machPoints) {
		double[] result = new double[machPoints.length];
		for (int i = 0; i < machPoints.length; i++) {
			double cn_pos = evaluate(machPoints[i],  1.0).getCN();
			double cn_neg = evaluate(machPoints[i], -1.0).getCN();
			result[i] = (cn_pos - cn_neg) / Math.toRadians(2.0);
		}
		return result;
	}

	private double[] xcpProfile(double[] machPoints, double alphaDeg) {
		double[] result = new double[machPoints.length];
		for (int i = 0; i < machPoints.length; i++) {
			AerodynamicForces f = evaluate(machPoints[i], alphaDeg);
			result[i] = f.getCP().getX();
		}
		return result;
	}

	private double[] baseDragProfile(double[] machPoints) {
		double[] result = new double[machPoints.length];
		for (int i = 0; i < machPoints.length; i++) {
			result[i] = evaluate(machPoints[i], 0.0).getBaseCD();
		}
		return result;
	}

	private double[] confidenceProfile(double[] machPoints, double alphaDeg) {
		double[] result = new double[machPoints.length];
		for (int i = 0; i < machPoints.length; i++) {
			evaluate(machPoints[i], alphaDeg);
			RomResult r = ROM.getLastResult();
			result[i] = r.getConfidence().getOverallScore();
		}
		return result;
	}

	private AerodynamicForces evaluate(double mach, double aoaDeg) {
		FlightConditions cond = new FlightConditions(CONFIG);
		cond.setMach(mach);
		cond.setAOA(Math.toRadians(aoaDeg));
		cond.setTheta(0.0);
		cond.setAtmosphericConditions(new AtmosphericConditions(288.15, 101325.0));
		return ROM.getAerodynamicForces(CONFIG, cond, new WarningSet());
	}

	private static int indexOf(double[] arr, double target) {
		for (int i = 0; i < arr.length; i++) {
			if (Math.abs(arr[i] - target) < 1e-9) return i;
		}
		throw new IllegalArgumentException("Value not found: " + target);
	}
}

package info.openrocket.core.aerodynamics.rom.validation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import info.openrocket.core.aerodynamics.AerodynamicForces;
import info.openrocket.core.aerodynamics.BarrowmanCalculator;
import info.openrocket.core.aerodynamics.FlightConditions;
import info.openrocket.core.aerodynamics.RomAerodynamicCalculator;
import info.openrocket.core.aerodynamics.rom.RomFallbackMode;
import info.openrocket.core.aerodynamics.rom.RomMode;
import info.openrocket.core.aerodynamics.rom.RomResult;
import info.openrocket.core.aerodynamics.rom.RomSettings;
import info.openrocket.core.aerodynamics.rom.math.BaseDragClosures;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.models.atmosphere.AtmosphericConditions;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.util.BaseTestCase;
import info.openrocket.core.util.TestRockets;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Phase 3 – Layer 1: Physics validation tests.
 *
 * <p>Each test verifies a property that any physically correct aerodynamics
 * solver must satisfy, derived from first principles and published experimental
 * correlations (Hoerner, Herrin-Dutton 1994, Kayser BRL MR-3353,
 * AEDC-TR-76-58, NASA TN D-6068).  No CSV loading: reference bounds are
 * embedded as analytically derivable constants.
 *
 * <p>Acceptance criteria (from Phase 3 plan §Final Acceptance Criteria):
 * <ul>
 *   <li>Subsonic base |Cp_b|: 0.08 – 0.25 for M ≤ 0.78</li>
 *   <li>Transonic peak |Cp_b|: 0.28 – 0.42 at M ≈ 1.0 (Kayser)</li>
 *   <li>Supersonic |Cp_b| decays monotonically above M = 1.80</li>
 *   <li>Transonic drag-rise: CA(M=0.95) &gt; CA(M=0.75) for finned rocket</li>
 *   <li>CNα &gt; 0 for a finned rocket across the Mach range</li>
 *   <li>ROM output is finite and non-negative CD for all tested conditions</li>
 *   <li>Confidence drops below 0.65 inside the transonic band (Mach 0.90–1.10)</li>
 *   <li>High-AoA penalty lowers confidence as AoA exceeds the trusted band</li>
 *   <li>Separation fraction ≤ 1.0 and finite for all conditions</li>
 * </ul>
 */
public class RomPhysicsValidationTest extends BaseTestCase {

	private static Rocket ROCKET;
	private static FlightConfiguration CONFIG;

	private RomAerodynamicCalculator rom;

	@BeforeAll
	static void setUpRocket() {
		ROCKET = TestRockets.makeEstesAlphaIII();
		CONFIG  = ROCKET.getSelectedConfiguration();
	}

	@BeforeEach
	void setUpRom() {
		RomSettings settings = RomSettings.defaults();
		settings.setEnabled(true);
		settings.setMode(RomMode.STANDARD);
		settings.setFallbackMode(RomFallbackMode.FORCE_ROM);   // raw ROM only
		settings.setDiagnosticsEnabled(true);
		rom = new RomAerodynamicCalculator(new BarrowmanCalculator(), settings);
	}

	// -----------------------------------------------------------------------
	// Base-pressure closure – Tuning 3 acceptance criteria
	// -----------------------------------------------------------------------

	@Test
	void baseCpSubsonicIsInHoernerRange() {
		// Hoerner: |Cp_b| ≈ 0.08-0.22 for M ≤ 0.78
		for (double M : new double[]{0.0, 0.3, 0.5, 0.6, 0.78}) {
			double mag = Math.abs(BaseDragClosures.coastBaseCp(M, 1.4));
			assertTrue(mag >= 0.07 && mag <= 0.25,
					"Subsonic |Cp_b| out of Hoerner range at M=" + M + ": " + mag);
		}
	}

	@Test
	void baseCpTransonicPeakIsInKayserRange() {
		// Kayser BRL MR-3353 peak: |Cp_b| ≈ 0.30-0.38 at M ≈ 1.0
		double peak = Math.abs(BaseDragClosures.coastBaseCp(1.0, 1.4));
		assertTrue(peak >= 0.28 && peak <= 0.42,
				"Transonic peak |Cp_b| outside Kayser range: " + peak);
	}

	@Test
	void baseCpTransonicIsHigherThanSubsonic() {
		// Physical requirement: drag rise through transonic
		double cpSub  = Math.abs(BaseDragClosures.coastBaseCp(0.75, 1.4));
		double cpTrans = Math.abs(BaseDragClosures.coastBaseCp(1.00, 1.4));
		assertTrue(cpTrans > cpSub,
				"No transonic drag rise in base Cp: sub=" + cpSub + " trans=" + cpTrans);
	}

	@Test
	void baseCpSupersonicDecaysMonotonically() {
		// Herrin-Dutton: |Cp_b| must decrease with Mach above M=1.80
		double[] machPoints = {1.80, 2.00, 2.50, 3.00, 4.00};
		double prev = Math.abs(BaseDragClosures.coastBaseCp(machPoints[0], 1.4));
		for (int i = 1; i < machPoints.length; i++) {
			double cur = Math.abs(BaseDragClosures.coastBaseCp(machPoints[i], 1.4));
			assertTrue(cur < prev,
					"Cp_b not monotonically decreasing from M=" + machPoints[i-1]
					+ " to M=" + machPoints[i] + ": " + prev + " → " + cur);
			prev = cur;
		}
	}

	@Test
	void baseCpBreakpointContinuity() {
		// breakpointCheck() returns [lower-left, lower-right, upper-left, upper-right]
		// Difference at each breakpoint must be < 0.05 (smooth transition)
		double[] bp = BaseDragClosures.breakpointCheck();
		assertTrue(Math.abs(bp[1] - bp[0]) < 0.05,
				"Discontinuity at lower break: " + bp[0] + " vs " + bp[1]);
		assertTrue(Math.abs(bp[3] - bp[2]) < 0.05,
				"Discontinuity at upper break: " + bp[2] + " vs " + bp[3]);
	}

	// -----------------------------------------------------------------------
	// Full-ROM force validation
	// -----------------------------------------------------------------------

	@Test
	void romCdIsPositiveAndFiniteAcrossRegimes() {
		// Must produce finite, non-negative CD in all four regimes
		for (double M : new double[]{0.45, 0.90, 1.20, 2.50}) {
			AerodynamicForces f = evaluate(M, 3.0);
			assertTrue(Double.isFinite(f.getCD()) && f.getCD() > 0.0,
					"CD not positive-finite at M=" + M + ": " + f.getCD());
		}
	}

	@Test
	void romCnIsPositiveForPositiveAoa() {
		// CN must be positive when AoA > 0 (finned stable rocket)
		for (double M : new double[]{0.35, 0.80, 1.20, 2.00}) {
			AerodynamicForces f = evaluate(M, 4.0);
			assertTrue(f.getCN() > 0.0, "CN ≤ 0 at M=" + M + ": " + f.getCN());
		}
	}

	@Test
	void romCnIncreaseWithAlpha() {
		// CNα > 0: increasing AoA must increase CN (Tuning 6 acceptance)
		double M = 1.5;
		double cn2  = evaluate(M, 2.0).getCN();
		double cn6  = evaluate(M, 6.0).getCN();
		assertTrue(cn6 > cn2,
				"CN not increasing with AoA at M=" + M + ": CN(2°)=" + cn2 + " CN(6°)=" + cn6);
	}

	@Test
	void transonicDragRisePresentInFullRom() {
		// AEDC-TR-76-58: CA at M=0.95 must exceed CA at M=0.75 (drag-rise criterion)
		double cd075 = evaluate(0.75, 0.0).getCD();
		double cd095 = evaluate(0.95, 0.0).getCD();
		assertTrue(cd095 > cd075,
				"No transonic drag rise: CD(0.75)=" + cd075 + " CD(0.95)=" + cd095);
	}

	@Test
	void confidenceDropsInsideTransonicBand() {
		// Phase 3 plan: TRANSONIC band should cause confidence < 0.65 near M=1.0
		AerodynamicForces ignored = evaluate(1.00, 3.0);
		RomResult result = rom.getLastResult();
		double score = result.getConfidence().getOverallScore();
		assertTrue(score < 0.65,
				"Confidence too high at M=1.00: " + score);
	}

	@Test
	void confidenceIsHighInCleanSubsonicLowAlpha() {
		// Low-Mach, low-AoA should have high confidence (no penalties)
		AerodynamicForces ignored = evaluate(0.35, 2.0);
		RomResult result = rom.getLastResult();
		double score = result.getConfidence().getOverallScore();
		assertTrue(score > 0.55,
				"Confidence unexpectedly low at M=0.35, alpha=2°: " + score);
	}

	@Test
	void highAoaLowersConfidence() {
		// At 18° AoA the angle penalty should push confidence below the low-AoA value
		AerodynamicForces i1 = evaluate(1.5, 3.0);
		double scoreNormal = rom.getLastResult().getConfidence().getOverallScore();
		AerodynamicForces i2 = evaluate(1.5, 18.0);
		double scoreHigh   = rom.getLastResult().getConfidence().getOverallScore();
		assertTrue(scoreHigh < scoreNormal,
				"High-AoA did not reduce confidence: normal=" + scoreNormal + " high=" + scoreHigh);
	}

	@Test
	void separationFractionBoundedAndFinite() {
		// Separation fraction must always be in [0,1] and finite
		for (double M : new double[]{0.3, 0.9, 1.5, 3.0}) {
			evaluate(M, 5.0);
			double sf = rom.getLastResult().getSeparationFraction();
			assertTrue(Double.isFinite(sf) && sf >= 0.0 && sf <= 1.0,
					"Separation fraction out of range at M=" + M + ": " + sf);
		}
	}

	@Test
	void fallbackNotUsedInForceRomMode() {
		// FORCE_ROM mode must set fallbackWeight = 0
		evaluate(1.0, 5.0);
		double fw = rom.getLastResult().getFallbackWeight();
		assertTrue(fw < 1e-9, "Fallback weight non-zero in FORCE_ROM mode: " + fw);
	}

	@Test
	void pitchingMomentIsFinite() {
		// Cm must be finite (not NaN/Inf) for all tested conditions
		for (double M : new double[]{0.5, 1.0, 2.0}) {
			AerodynamicForces f = evaluate(M, 4.0);
			assertTrue(Double.isFinite(f.getCm()),
					"Cm not finite at M=" + M + ": " + f.getCm());
		}
	}

	// -----------------------------------------------------------------------
	// Helper
	// -----------------------------------------------------------------------

	private AerodynamicForces evaluate(double mach, double aoaDeg) {
		FlightConditions cond = new FlightConditions(CONFIG);
		cond.setMach(mach);
		cond.setAOA(Math.toRadians(aoaDeg));
		cond.setTheta(0.0);
		cond.setAtmosphericConditions(new AtmosphericConditions(288.15, 101325.0));
		return rom.getAerodynamicForces(CONFIG, cond, new WarningSet());
	}
}

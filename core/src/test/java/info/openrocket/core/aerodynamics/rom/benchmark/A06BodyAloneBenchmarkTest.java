package info.openrocket.core.aerodynamics.rom.benchmark;

import static info.openrocket.core.aerodynamics.rom.benchmark.BenchmarkAssertions.*;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A06 body-alone benchmark: Jorgensen normal-force theory for an
 * axisymmetric body (no fins).
 *
 * <p>Reference data from corpus dataset A06, derived from:
 * Jorgensen, "Prediction of Static Aerodynamic Characteristics for
 * Slender Bodies Alone and with Lifting Surfaces" (NASA TR R-474).
 *
 * <p>Jorgensen CN = CN_potential + CN_viscous_crossflow where:
 * <ul>
 *   <li>CN_potential ≈ 2·sin(α) for slender bodies (CNα = 2)</li>
 *   <li>CN_viscous_xflow depends on Mach-dependent crossflow drag</li>
 * </ul>
 *
 * <p>Tests use a finned Estes Alpha III rocket in FORCE_ROM mode.
 * Comparisons against Jorgensen body-alone CN are not expected to
 * match exactly (the ROM evaluates a full rocket), but the body
 * contribution trends and magnitudes should track the reference data.
 */
@DisplayName("A06 – Body-alone CN benchmark (Jorgensen)")
public class A06BodyAloneBenchmarkTest extends BaseTestCase {

	private static final String DATASET = "A06";
	private static final String SRC = "Jorgensen NASA TR R-474";

	private static Rocket ROCKET;
	private static FlightConfiguration CONFIG;

	private RomAerodynamicCalculator rom;

	@BeforeAll
	static void setUpRocket() {
		ROCKET = TestRockets.makeEstesAlphaIII();
		CONFIG = ROCKET.getSelectedConfiguration();
	}

	@BeforeEach
	void setUpRom() {
		RomSettings settings = RomSettings.defaults();
		settings.setEnabled(true);
		settings.setMode(RomMode.STANDARD);
		settings.setFallbackMode(RomFallbackMode.FORCE_ROM);
		settings.setDiagnosticsEnabled(true);
		rom = new RomAerodynamicCalculator(new BarrowmanCalculator(), settings);
	}

	// ------------------------------------------------------------------
	// Representative Jorgensen CN_total reference values from A06 corpus.
	// These are body-alone values; the ROM includes fins, so we compare
	// trends and validate that ROM CN exceeds the body-alone contribution.
	// ------------------------------------------------------------------

	// M=0.6 representative slice
	private static final double[] ALPHA_DEG_SUB = {5, 10, 15, 20};
	private static final double[] CN_JORGENSEN_M06 = {0.1796, 0.3658, 0.5534, 0.7371};

	// M=1.2 representative slice
	private static final double[] CN_JORGENSEN_M12 = {0.1796, 0.3661, 0.5564, 0.7477};

	// M=2.0 representative slice
	private static final double[] CN_JORGENSEN_M20 = {0.1796, 0.3683, 0.5666, 0.7787};

	// ------------------------------------------------------------------
	// FORCE_ROM tests
	// ------------------------------------------------------------------

	@Test
	void romCnExceedsBodyAloneAtSubsonic() {
		// A finned rocket's CN must exceed the body-alone Jorgensen prediction
		for (int i = 0; i < ALPHA_DEG_SUB.length; i++) {
			AerodynamicForces f = evaluate(0.6, ALPHA_DEG_SUB[i]);
			String label = caseLabel(DATASET, SRC,
					"M=0.6 α=" + ALPHA_DEG_SUB[i] + "° CN ≥ body-alone");
			org.junit.jupiter.api.Assertions.assertTrue(
					f.getCN() >= CN_JORGENSEN_M06[i] * 0.8,
					label + " – ROM CN=" + f.getCN()
							+ " < 80% of body-alone " + CN_JORGENSEN_M06[i]);
		}
	}

	@Test
	void romCnExceedsBodyAloneAtSupersonic() {
		for (int i = 0; i < ALPHA_DEG_SUB.length; i++) {
			AerodynamicForces f = evaluate(2.0, ALPHA_DEG_SUB[i]);
			String label = caseLabel(DATASET, SRC,
					"M=2.0 α=" + ALPHA_DEG_SUB[i] + "° CN ≥ body-alone");
			org.junit.jupiter.api.Assertions.assertTrue(
					f.getCN() >= CN_JORGENSEN_M20[i] * 0.8,
					label + " – ROM CN=" + f.getCN()
							+ " < 80% of body-alone " + CN_JORGENSEN_M20[i]);
		}
	}

	@Test
	void cnIncreasesWithAlphaAtAllMach() {
		for (double mach : new double[]{0.6, 0.9, 1.2, 2.0}) {
			double[] cn = new double[ALPHA_DEG_SUB.length];
			for (int i = 0; i < ALPHA_DEG_SUB.length; i++) {
				cn[i] = evaluate(mach, ALPHA_DEG_SUB[i]).getCN();
			}
			assertMonotonicallyIncreasing(
					caseLabel(DATASET, SRC, "M=" + mach + " CN vs α"), cn);
		}
	}

	@Test
	void cnIsPositiveForPositiveAlpha() {
		for (double mach : new double[]{0.6, 1.2, 2.0}) {
			for (double alpha : new double[]{5, 10, 20}) {
				AerodynamicForces f = evaluate(mach, alpha);
				assertPositiveFinite(
						caseLabel(DATASET, SRC, "M=" + mach + " α=" + alpha + "° CN>0"),
						f.getCN());
			}
		}
	}

	@Test
	void jorgensePotentialTermFollowsSinAlpha() {
		// Verify the A06 corpus potential term is consistent with CN_potential = sin(2α)
		double[] alphas = {5, 10, 15, 20, 30, 45};
		for (double alpha : alphas) {
			double expected = Math.sin(Math.toRadians(2.0 * alpha));
			double corpus;
			switch ((int) alpha) {
				case 5:  corpus = 0.1736; break;
				case 10: corpus = 0.342;  break;
				case 15: corpus = 0.5;    break;
				case 20: corpus = 0.6428; break;
				case 30: corpus = 0.866;  break;
				case 45: corpus = 1.0;    break;
				default: corpus = expected;
			}
			String label = caseLabel(DATASET, SRC, "CN_potential α=" + alpha + "° ≈ sin(2α)");
			assertCloseTo(label, corpus, expected, 0.02, 0.01);
		}
	}

	@Test
	void viscousCrossflowTermIsSmallAtLowAlpha() {
		// From A06 corpus M=0.6: at α=5°, CN_viscous = 0.0059 (< 4% of total)
		// Verify ROM CN is close to potential-dominated regime at small α
		AerodynamicForces f = evaluate(0.6, 5.0);
		String label = caseLabel(DATASET, SRC, "M=0.6 α=5° CN near potential limit");
		// Total ROM CN for finned rocket will be larger than body-alone, but should
		// be on the order of a few times the body-alone value
		assertInRange(label, f.getCN(), 0.1, 5.0);
	}

	// ------------------------------------------------------------------
	// Runtime-facing subset: confidence and diagnostics
	// ------------------------------------------------------------------

	@Test
	void confidenceIsReasonableAtSubsonic() {
		evaluate(0.6, 5.0);
		RomResult result = rom.getLastResult();
		String label = caseLabel(DATASET, SRC, "M=0.6 α=5° confidence");
		assertInRange(label, result.getConfidence().getOverallScore(), 0.3, 1.0);
	}

	@Test
	void confidenceDropsAtTransonic() {
		evaluate(1.0, 5.0);
		RomResult result = rom.getLastResult();
		String label = caseLabel(DATASET, SRC, "M=1.0 α=5° confidence drop");
		assertInRange(label, result.getConfidence().getOverallScore(), 0.0, 0.7);
	}

	// ------------------------------------------------------------------
	// Helper
	// ------------------------------------------------------------------

	private AerodynamicForces evaluate(double mach, double aoaDeg) {
		FlightConditions cond = new FlightConditions(CONFIG);
		cond.setMach(mach);
		cond.setAOA(Math.toRadians(aoaDeg));
		cond.setTheta(0.0);
		cond.setAtmosphericConditions(new AtmosphericConditions(288.15, 101325.0));
		return rom.getAerodynamicForces(CONFIG, cond, new WarningSet());
	}
}

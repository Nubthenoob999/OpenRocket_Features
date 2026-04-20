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
import info.openrocket.core.aerodynamics.rom.math.PittsNielsenKaattari;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.models.atmosphere.AtmosphericConditions;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.util.BaseTestCase;
import info.openrocket.core.util.TestRockets;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * A07 body-wing-tail benchmark: integrated-force validation of a finned
 * rocket combining Jorgensen body-alone CN with Pitts-Nielsen-Kaattari
 * fin-body interference factors.
 *
 * <p>Reference data from corpus datasets A07 (body-wing-tail loads) and
 * B06 (PNK interference factors).  The Estes Alpha III is used as the
 * benchmark geometry because it is a standard finned model rocket whose
 * body and fin proportions closely match the PNK and Jorgensen applicability
 * domain.
 *
 * <p>Key physics: CN_total should reflect body CN (Jorgensen) plus fin CN
 * augmented by K_WB and K_BW interference carryover.  The ROM in FORCE_ROM
 * mode should produce values that are at least consistent with these
 * additive components.
 */
@DisplayName("A07 – Body-wing-tail integrated benchmark")
public class A07BodyWingTailBenchmarkTest extends BaseTestCase {

	private static final String DATASET = "A07";
	private static final String SRC = "Jorgensen + Pitts-Nielsen NACA TR 1307";

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
	// Jorgensen body-alone CN reference (from A06/A07 corpus)
	// ------------------------------------------------------------------
	private static final double[] ALPHA_DEG = {5, 10, 15, 20};
	private static final double[] BODY_CN_M06 = {0.1796, 0.3658, 0.5534, 0.7371};
	private static final double[] BODY_CN_M12 = {0.1796, 0.3661, 0.5564, 0.7477};
	private static final double[] BODY_CN_M20 = {0.1796, 0.3683, 0.5666, 0.7787};

	// ------------------------------------------------------------------
	// Integrated-force FORCE_ROM tests
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("Integrated CN behavior")
	class IntegratedCN {

		@Test
		void finnedRocketCNExceedsBodyAlone() {
			// With fins, total CN must exceed body-alone at all conditions
			for (int i = 0; i < ALPHA_DEG.length; i++) {
				AerodynamicForces f = evaluate(0.6, ALPHA_DEG[i]);
				String label = caseLabel(DATASET, SRC,
						"M=0.6 α=" + ALPHA_DEG[i] + "° CN > body-alone");
				org.junit.jupiter.api.Assertions.assertTrue(
						f.getCN() > BODY_CN_M06[i],
						label + " – ROM CN=" + f.getCN()
								+ " ≤ body-alone " + BODY_CN_M06[i]);
			}
		}

		@Test
		void cnIncreasesWithAlphaAtSubsonic() {
			double[] cn = new double[ALPHA_DEG.length];
			for (int i = 0; i < ALPHA_DEG.length; i++) {
				cn[i] = evaluate(0.6, ALPHA_DEG[i]).getCN();
			}
			assertMonotonicallyIncreasing(
					caseLabel(DATASET, SRC, "M=0.6 CN vs α"), cn);
		}

		@Test
		void cnIncreasesWithAlphaAtTransonic() {
			double[] cn = new double[ALPHA_DEG.length];
			for (int i = 0; i < ALPHA_DEG.length; i++) {
				cn[i] = evaluate(1.2, ALPHA_DEG[i]).getCN();
			}
			assertMonotonicallyIncreasing(
					caseLabel(DATASET, SRC, "M=1.2 CN vs α"), cn);
		}

		@Test
		void cnIncreasesWithAlphaAtSupersonic() {
			double[] cn = new double[ALPHA_DEG.length];
			for (int i = 0; i < ALPHA_DEG.length; i++) {
				cn[i] = evaluate(2.0, ALPHA_DEG[i]).getCN();
			}
			assertMonotonicallyIncreasing(
					caseLabel(DATASET, SRC, "M=2.0 CN vs α"), cn);
		}

		@Test
		void cnIsPositiveAcrossAllConditions() {
			for (double mach : new double[]{0.6, 0.9, 1.2, 2.0}) {
				for (double alpha : ALPHA_DEG) {
					AerodynamicForces f = evaluate(mach, alpha);
					assertPositiveFinite(
							caseLabel(DATASET, SRC, "M=" + mach + " α=" + alpha + "° CN>0"),
							f.getCN());
				}
			}
		}
	}

	// ------------------------------------------------------------------
	// PNK interference factor consistency
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("PNK interference consistency")
	class PNKConsistency {

		@Test
		void interferenceAugmentsCNAtSubsonic() {
			// Estimate expected fin contribution: CN_fin_alpha ~ 4π/β for thin delta
			// with PNK carryover. The ROM's total CN should be in the right ballpark.
			double mach = 0.6;
			AerodynamicForces f = evaluate(mach, 10.0);

			// Body-alone CN at M=0.6 α=10° is 0.3658
			// With fins and interference, expect CN well above the body-alone value,
			// but still within a broad missile-class envelope for this implementation.
			String label = caseLabel(DATASET, SRC, "M=0.6 α=10° total CN range");
			assertInRange(label, f.getCN(), 0.5, 12.0);
		}

		@Test
		void kWBAndKBWSumExceedsUnity() {
			// For the Estes Alpha III geometry: typical r/s ≈ 0.2-0.4
			// K_WB + K_BW > 1 when body is present (enhanced lift)
			for (double rOverS : new double[]{0.2, 0.3, 0.4}) {
				double sOverA = 1.0 + 1.0 / rOverS;
				double kwb = PittsNielsenKaattari.kWB(sOverA);
				double kbw = PittsNielsenKaattari.kBW(sOverA);
				String label = caseLabel(DATASET, SRC,
						"r/s=" + rOverS + " K_WB+K_BW > 1");
				org.junit.jupiter.api.Assertions.assertTrue(
						kwb + kbw > 1.0,
						label + " – K_WB=" + kwb + " K_BW=" + kbw
								+ " sum=" + (kwb + kbw));
			}
		}
	}

	// ------------------------------------------------------------------
	// Moment and CP behavior
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("Moment and CP")
	class MomentBehavior {

		@Test
		void pitchingMomentIsFiniteAcrossRegimes() {
			for (double mach : new double[]{0.6, 1.0, 1.5, 2.0}) {
				AerodynamicForces f = evaluate(mach, 5.0);
				assertFinite(
						caseLabel(DATASET, SRC, "M=" + mach + " Cm finite"),
						f.getCm());
			}
		}

		@Test
		void cdIsPositiveAtAllConditions() {
			for (double mach : new double[]{0.6, 1.0, 1.5, 2.0}) {
				AerodynamicForces f = evaluate(mach, 5.0);
				assertPositiveFinite(
						caseLabel(DATASET, SRC, "M=" + mach + " CD>0"),
						f.getCD());
			}
		}

		@Test
		void transonicDragRisePresent() {
			double cdSub = evaluate(0.75, 0.0).getCD();
			double cdTrans = evaluate(0.95, 0.0).getCD();
			String label = caseLabel(DATASET, SRC, "transonic drag rise");
			org.junit.jupiter.api.Assertions.assertTrue(
					cdTrans > cdSub,
					label + " – CD(0.75)=" + cdSub + " CD(0.95)=" + cdTrans);
		}
	}

	// ------------------------------------------------------------------
	// Runtime-facing: confidence and continuity
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("Runtime diagnostics")
	class RuntimeDiagnostics {

		@Test
		void fallbackWeightIsZeroInForceROM() {
			evaluate(1.5, 5.0);
			RomResult result = rom.getLastResult();
			String label = caseLabel(DATASET, SRC, "FORCE_ROM fallback=0");
			assertCloseTo(label, 0.0, result.getFallbackWeight(), 0.01, 1e-9);
		}

		@Test
		void cnIsContinuousAcrossTransonicBand() {
			// Check CN doesn't jump by more than 50% between adjacent Mach points
			double[] machs = {0.7, 0.8, 0.9, 1.0, 1.1, 1.2, 1.3};
			double prevCN = evaluate(machs[0], 5.0).getCN();
			for (int i = 1; i < machs.length; i++) {
				double cn = evaluate(machs[i], 5.0).getCN();
				double ratio = cn / Math.max(1e-6, prevCN);
				String label = caseLabel(DATASET, SRC,
						"CN continuity M=" + machs[i - 1] + "→" + machs[i]);
				assertInRange(label, ratio, 0.5, 2.0);
				prevCN = cn;
			}
		}

		@Test
		void cdIsContinuousAcrossTransonicBand() {
			double[] machs = {0.7, 0.8, 0.9, 1.0, 1.1, 1.2, 1.3};
			double prevCD = evaluate(machs[0], 2.0).getCD();
			for (int i = 1; i < machs.length; i++) {
				double cd = evaluate(machs[i], 2.0).getCD();
				double ratio = cd / Math.max(1e-6, prevCD);
				String label = caseLabel(DATASET, SRC,
						"CD continuity M=" + machs[i - 1] + "→" + machs[i]);
				assertInRange(label, ratio, 0.5, 2.0);
				prevCD = cd;
			}
		}
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

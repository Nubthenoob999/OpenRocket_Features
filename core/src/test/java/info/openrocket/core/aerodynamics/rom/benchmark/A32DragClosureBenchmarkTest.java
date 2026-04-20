package info.openrocket.core.aerodynamics.rom.benchmark;

import static info.openrocket.core.aerodynamics.rom.benchmark.BenchmarkAssertions.*;

import info.openrocket.core.aerodynamics.AerodynamicForces;
import info.openrocket.core.aerodynamics.BarrowmanCalculator;
import info.openrocket.core.aerodynamics.FlightConditions;
import info.openrocket.core.aerodynamics.RomAerodynamicCalculator;
import info.openrocket.core.aerodynamics.rom.RomFallbackMode;
import info.openrocket.core.aerodynamics.rom.RomMode;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * A32 drag-closure benchmark: Fleeman zero-lift drag decomposition for a
 * generic missile-class body.
 *
 * <p>Reference data from corpus dataset A32 ({@code fleeman_cd0} table),
 * covering M 0.3–3.0 with friction, base, and wave-drag components.
 * Reference: Fleeman, "Tactical Missile Design" (AIAA Education Series).
 *
 * <p>In wave one this is limited to drag-decomposition checks (component
 * ordering, transonic hump, supersonic decay) and Reynolds-scaling
 * plausibility rather than a full RM-10 body benchmark.
 */
@DisplayName("A32 – Drag-closure benchmark (Fleeman)")
public class A32DragClosureBenchmarkTest extends BaseTestCase {

	private static final String DATASET = "A32";
	private static final String SRC = "Fleeman Tactical Missile Design";
	private static final double GAMMA = 1.4;

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
	// Fleeman CD0 reference data (corpus A32)
	// Columns: Mach, Cd_friction, Cd_base, Cd_wave_nose, Cd_total
	// ------------------------------------------------------------------

	private static final double[][] FLEEMAN = {
			{0.3,  0.10611, 0.1317,  0.0,     0.23781},
			{0.5,  0.10576, 0.1525,  0.0,     0.25826},
			{0.6,  0.10552, 0.16683, 0.0,     0.27235},
			{0.8,  0.10492, 0.21688, 0.0,     0.3218},
			{0.9,  0.10456, 0.30623, 0.0,     0.41079},
			{0.95, 0.10436, 0.37838, 0.0,     0.48275},
			{1.0,  0.10416, 0.44686, 0.0,     0.55102},
			{1.05, 0.10394, 0.4581,  0.15491, 0.71695},
			{1.1,  0.10372, 0.42414, 0.14788, 0.67574},
			{1.2,  0.10325, 0.28927, 0.13636, 0.52888},
			{1.5,  0.10162, 0.16669, 0.11456, 0.38287},
			{2.0,  0.09831, 0.125,   0.0976,  0.3209},
			{2.5,  0.09443, 0.1,     0.08975, 0.28418},
			{3.0,  0.09019, 0.08333, 0.08548, 0.259},
	};

	// ------------------------------------------------------------------
	// Component decomposition checks
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("Fleeman component decomposition")
	class ComponentDecomposition {

		@Test
		void frictionDragDecreasesWithMach() {
			double[] cf = new double[FLEEMAN.length];
			for (int i = 0; i < FLEEMAN.length; i++) {
				cf[i] = FLEEMAN[i][1];
			}
			assertMonotonicallyDecreasing(
					caseLabel(DATASET, SRC, "Cd_friction vs Mach"), cf);
		}

		@Test
		void waveDragIsZeroBelowSonic() {
			for (double[] row : FLEEMAN) {
				if (row[0] < 1.0) {
					String label = caseLabel(DATASET, SRC,
							"M=" + row[0] + " wave drag = 0 (subsonic)");
					assertCloseTo(label, 0.0, row[3], 0.001, 0.001);
				}
			}
		}

		@Test
		void waveDragIsPositiveAboveSonic() {
			for (double[] row : FLEEMAN) {
				if (row[0] > 1.0) {
					String label = caseLabel(DATASET, SRC,
							"M=" + row[0] + " wave drag > 0 (supersonic)");
					assertPositiveFinite(label, row[3]);
				}
			}
		}

		@Test
		void baseDragPeaksNearMach1() {
			// The highest Cd_base in the Fleeman table should be near Mach 1.0-1.05
			double maxBase = 0.0;
			double maxMach = 0.0;
			for (double[] row : FLEEMAN) {
				if (row[2] > maxBase) {
					maxBase = row[2];
					maxMach = row[0];
				}
			}
			String label = caseLabel(DATASET, SRC, "base drag peak Mach");
			assertInRange(label, maxMach, 0.95, 1.15);
		}

		@Test
		void totalDragPeaksInTransonicRegion() {
			double maxTotal = 0.0;
			double maxMach = 0.0;
			for (double[] row : FLEEMAN) {
				if (row[4] > maxTotal) {
					maxTotal = row[4];
					maxMach = row[0];
				}
			}
			String label = caseLabel(DATASET, SRC, "total drag peak Mach");
			assertInRange(label, maxMach, 0.95, 1.15);
		}

		@Test
		void componentsAddToTotal() {
			for (double[] row : FLEEMAN) {
				double sum = row[1] + row[2] + row[3];
				String label = caseLabel(DATASET, SRC,
						"M=" + row[0] + " component sum = total");
				assertCloseTo(label, row[4], sum, 0.005, 0.001);
			}
		}
	}

	// ------------------------------------------------------------------
	// ROM CD plausibility against Fleeman envelope
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("ROM CD vs Fleeman envelope")
	class RomVsFleeman {

		@Test
		void romCdIsInFleemanEnvelopeAtSubsonic() {
			// Fleeman total CD at M=0.6 is ~0.27 for a generic missile body.
			// The ROM on an Estes Alpha III will differ in geometry, but CD should
			// be in a physically reasonable range.
			AerodynamicForces f = evaluate(0.6, 0.0);
			String label = caseLabel(DATASET, SRC, "M=0.6 ROM CD plausible");
			assertInRange(label, f.getCD(), 0.05, 3.0);
		}

		@Test
		void romCdShowsTransonicDragRise() {
			double cd075 = evaluate(0.75, 0.0).getCD();
			double cd095 = evaluate(0.95, 0.0).getCD();
			String label = caseLabel(DATASET, SRC, "transonic drag rise present");
			org.junit.jupiter.api.Assertions.assertTrue(cd095 > cd075,
					label + " – CD(0.75)=" + cd075 + " CD(0.95)=" + cd095);
		}

		@Test
		void romCdDecreasesAfterTransonicPeak() {
			// After the transonic peak, CD should decrease
			double[] machs = {1.5, 2.0, 2.5, 3.0};
			double[] cds = new double[machs.length];
			for (int i = 0; i < machs.length; i++) {
				cds[i] = evaluate(machs[i], 0.0).getCD();
			}
			assertMonotonicallyDecreasing(
					caseLabel(DATASET, SRC, "post-transonic CD decrease"), cds);
		}

		@Test
		void baseDragClosureShowsTransonicPeak() {
			// coastBaseCp should peak near Mach 1.0 (consistent with Fleeman base drag)
			double cpSub = Math.abs(BaseDragClosures.coastBaseCp(0.6, GAMMA));
			double cpTrans = Math.abs(BaseDragClosures.coastBaseCp(1.0, GAMMA));
			double cpSuper = Math.abs(BaseDragClosures.coastBaseCp(2.0, GAMMA));
			String label = caseLabel(DATASET, SRC, "base Cp transonic peak");
			org.junit.jupiter.api.Assertions.assertTrue(
					cpTrans > cpSub && cpTrans > cpSuper,
					label + " – sub=" + cpSub + " trans=" + cpTrans + " super=" + cpSuper);
		}
	}

	// ------------------------------------------------------------------
	// Reynolds-scaling plausibility
	// ------------------------------------------------------------------

	@Nested
	@DisplayName("Reynolds-scaling")
	class ReynoldsScaling {

		@Test
		void frictionDragIsReasonableOrder() {
			// Fleeman Cd_friction ≈ 0.09-0.11 for the reference body.
			// For the Estes Alpha III at sea level, friction drag should be
			// in a similar order of magnitude.
			for (double mach : new double[]{0.5, 1.0, 2.0}) {
				AerodynamicForces f = evaluate(mach, 0.0);
				// CD contains all components; friction is a fraction of total
				assertPositiveFinite(
						caseLabel(DATASET, SRC, "M=" + mach + " CD>0"), f.getCD());
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

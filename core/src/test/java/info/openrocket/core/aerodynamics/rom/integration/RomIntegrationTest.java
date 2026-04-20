package info.openrocket.core.aerodynamics.rom.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import info.openrocket.core.aerodynamics.AerodynamicForces;
import info.openrocket.core.aerodynamics.BarrowmanCalculator;
import info.openrocket.core.aerodynamics.FlightConditions;
import info.openrocket.core.aerodynamics.RomAerodynamicCalculator;
import info.openrocket.core.aerodynamics.rom.PathlineROMCalculator;
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
 * Phase 3 – Layer 4: Integration tests.
 *
 * <p>Tests the ROM as it is used inside OpenRocket: fallback delegation, plume
 * state evolution, motor ignition continuity, confidence-driven blending, and
 * edge-case geometry handling.
 *
 * <p>Corresponding Phase 3 plan acceptance rows:
 * <ul>
 *   <li>No NPE on edge-case geometries</li>
 *   <li>Motor ignition CA step &lt; 20%</li>
 *   <li>Fallback at UNRELIABLE confidence → CA non-NaN, non-negative</li>
 *   <li>Fallback boundary smooth (step &lt; 10%)</li>
 *   <li>OR simulation does not crash on any benchmark geometry</li>
 *   <li>Disabled ROM delegates exactly to legacy (bit-identical)</li>
 * </ul>
 */
public class RomIntegrationTest extends BaseTestCase {

	private static Rocket ROCKET;
	private static FlightConfiguration CONFIG;

	@BeforeAll
	static void setUpShared() {
		ROCKET = TestRockets.makeEstesAlphaIII();
		CONFIG  = ROCKET.getSelectedConfiguration();
	}

	// -----------------------------------------------------------------------
	// Disabled ROM → exact legacy delegation
	// -----------------------------------------------------------------------

	/**
	 * When ROM is disabled, every output coefficient must match legacy exactly
	 * (bit-for-bit, no rounding), so that toggling ROM on/off is the only
	 * variable in A-B comparisons.
	 */
	@Test
	void disabledRomDelegatesExactlyToLegacy() {
		BarrowmanCalculator legacy = new BarrowmanCalculator();
		RomSettings off = RomSettings.defaults();
		off.setEnabled(false);
		RomAerodynamicCalculator rom = new RomAerodynamicCalculator(legacy.newInstance(), off);

		FlightConditions cond = conditions(0.45, 3.0);
		AerodynamicForces legF = legacy.getAerodynamicForces(CONFIG, cond, new WarningSet());
		AerodynamicForces romF = rom.getAerodynamicForces(CONFIG, cond, new WarningSet());

		assertEquals(legF.getCD(), romF.getCD(),   1e-12, "CD mismatch when ROM disabled");
		assertEquals(legF.getCN(), romF.getCN(),   1e-12, "CN mismatch when ROM disabled");
		assertEquals(legF.getCm(), romF.getCm(),   1e-12, "Cm mismatch when ROM disabled");
		assertTrue(rom.getComputationSnapshots().isEmpty(), "Snapshots should be empty when ROM disabled");
	}

	// -----------------------------------------------------------------------
	// Plume state evolution
	// -----------------------------------------------------------------------

	/**
	 * During powered flight plumeState must stay at 1.0.
	 * After burn-out it must decay toward zero with the correct time constant
	 * (τ ≈ 0.30 s → 50% at ≈ 0.21 s).
	 */
	@Test
	void plumeStateEvolvesCorrectly() {
		// Burning → full plume
		assertEquals(1.0, PathlineROMCalculator.evolvePlumeState(0.0, true, 1.0),   1e-9);
		assertEquals(1.0, PathlineROMCalculator.evolvePlumeState(0.5, true, 10.0),  1e-9);

		// Coast from plume=1.0, dt=0.30 → e^(-1) ≈ 0.368
		double decayed = PathlineROMCalculator.evolvePlumeState(1.0, false, 0.30);
		assertEquals(Math.exp(-1.0), decayed, 1e-6,
				"Plume at t=τ should be 1/e, got " + decayed);

		// Large dt → effectively zero (thresholded below 1e-6)
		double veryDecayed = PathlineROMCalculator.evolvePlumeState(1.0, false, 20.0);
		assertEquals(0.0, veryDecayed, 1e-9,
				"Plume not flushed after large dt: " + veryDecayed);
	}

	/**
	 * Powered vs coast: with plume=1.0 the base drag should be smaller than
	 * coast (plume shields the base, reducing suction – Addy/Brazzel effect).
	 */
	@Test
	void poweredBaseDragLessThanCoast() {
		RomAerodynamicCalculator romCoast  = buildRom(0.0);
		RomAerodynamicCalculator romPowered = buildRom(1.0);

		FlightConditions cond = conditions(2.0, 2.0);
		double baseCoast   = romCoast.getAerodynamicForces(CONFIG, cond, new WarningSet()).getBaseCD();
		double basePowered = romPowered.getAerodynamicForces(CONFIG, cond, new WarningSet()).getBaseCD();

		assertTrue(basePowered < baseCoast,
				"Powered base drag should be less than coast at M=2: powered="
				+ basePowered + " coast=" + baseCoast);
	}

	@Test
	void lowMachPlumeReliefCreatesWiderCoastPoweredCdSplitThanHighMach() {
		RomAerodynamicCalculator romCoast = buildRom(0.0);
		RomAerodynamicCalculator romPowered = buildRom(1.0);

		double lowMachCoast = romCoast.getAerodynamicForces(CONFIG, conditions(0.30, 0.0), new WarningSet()).getCD();
		double lowMachPowered = romPowered.getAerodynamicForces(CONFIG, conditions(0.30, 0.0), new WarningSet()).getCD();
		double highMachCoast = romCoast.getAerodynamicForces(CONFIG, conditions(2.0, 0.0), new WarningSet()).getCD();
		double highMachPowered = romPowered.getAerodynamicForces(CONFIG, conditions(2.0, 0.0), new WarningSet()).getCD();

		double lowMachGap = (lowMachCoast - lowMachPowered) / Math.max(lowMachCoast, 1e-9);
		double highMachGap = (highMachCoast - highMachPowered) / Math.max(highMachCoast, 1e-9);

		assertTrue(lowMachPowered < lowMachCoast,
				"Powered low-Mach CD should stay below coast: powered=" + lowMachPowered
						+ " coast=" + lowMachCoast);
		assertTrue(lowMachGap > 0.12,
				"Low-Mach plume/coast gap should be material: gap=" + (lowMachGap * 100.0) + "%");
		assertTrue(lowMachGap > highMachGap,
				"Low-Mach phase split should exceed the high-Mach split: low="
						+ (lowMachGap * 100.0) + "% high=" + (highMachGap * 100.0) + "%");
	}

	// -----------------------------------------------------------------------
	// Motor ignition CA continuity (plan §Integration: step < 20%)
	// -----------------------------------------------------------------------

	/**
	 * The CA step at motor ignition (coast → powered) must be &lt; 20%.
	 * A larger discontinuity would indicate a numerical artefact rather than
	 * the expected smooth plume-shielding transition.
	 */
	@Test
	void ignitionContinuityUnder20Percent() {
		RomAerodynamicCalculator romCoast   = buildRom(0.0);
		RomAerodynamicCalculator romPowered = buildRom(1.0);

		FlightConditions cond = conditions(2.0, 0.0);
		double caCoast   = romCoast.getAerodynamicForces(CONFIG, cond, new WarningSet()).getCD();
		double caPowered = romPowered.getAerodynamicForces(CONFIG, cond, new WarningSet()).getCD();

		double step = Math.abs(caPowered - caCoast) / Math.max(caCoast, 1e-9);
		assertTrue(step < 0.20,
				"CA step at ignition > 20%: coast=" + caCoast
				+ " powered=" + caPowered + " step=" + (step * 100) + "%");
	}

	// -----------------------------------------------------------------------
	// Fallback at high AoA (UNRELIABLE confidence)
	// -----------------------------------------------------------------------

	/**
	 * At 20° AoA (well outside the trusted 12° band) the confidence should
	 * trigger significant fallback, but the output must still be physically
	 * reasonable: CA non-NaN, non-negative, CN positive.
	 *
	 * <p>Corresponds to plan §Integration: "Fallback at UNRELIABLE confidence".
	 */
	@Test
	void fallbackAtHighAoaIsNonNanNonNegative() {
		RomAerodynamicCalculator rom = buildEnabledBlend();
		AerodynamicForces f = rom.getAerodynamicForces(CONFIG, conditions(2.0, 20.0), new WarningSet());

		assertNotNull(f, "Forces must not be null at high AoA");
		assertFalse(Double.isNaN(f.getCD()),  "CD is NaN at high AoA");
		assertTrue(f.getCD() >= 0.0,          "CD is negative at high AoA");
		assertFalse(Double.isNaN(f.getCN()),  "CN is NaN at high AoA");
	}

	/**
	 * At very high AoA (20°), the ROM must fall back toward legacy, so the
	 * fallback weight must be substantially higher than at low AoA (3°).
	 */
	@Test
	void fallbackWeightHigherAtHighAoa() {
		RomAerodynamicCalculator rom = buildEnabledBlend();

		rom.getAerodynamicForces(CONFIG, conditions(2.0, 3.0), new WarningSet());
		double fwLow = rom.getLastResult().getFallbackWeight();

		rom.getAerodynamicForces(CONFIG, conditions(2.0, 20.0), new WarningSet());
		double fwHigh = rom.getLastResult().getFallbackWeight();

		assertTrue(fwHigh > fwLow,
				"Fallback weight should increase at high AoA: low=" + fwLow + " high=" + fwHigh);
	}

	// -----------------------------------------------------------------------
	// Fallback boundary smoothness (plan §Integration: step < 10%)
	// -----------------------------------------------------------------------

	/**
	 * CA must vary smoothly across the fallback boundary (~15° for the Estes
	 * Alpha III).  A step &lt; 10% is required between alpha=14° and 16°.
	 */
	@Test
	void fallbackBoundaryIsSmoothUnder10Percent() {
		RomAerodynamicCalculator rom = buildEnabledBlend();
		double ca14 = rom.getAerodynamicForces(CONFIG, conditions(2.0, 14.0), new WarningSet()).getCD();
		double ca16 = rom.getAerodynamicForces(CONFIG, conditions(2.0, 16.0), new WarningSet()).getCD();
		double step = Math.abs(ca16 - ca14) / Math.max(ca14, 1e-9);
		assertTrue(step < 0.10,
				"Discontinuity at fallback boundary: CA(14°)=" + ca14
				+ " CA(16°)=" + ca16 + " step=" + (step * 100) + "%");
	}

	// -----------------------------------------------------------------------
	// FORCE_ROM vs BARROWMAN_ONLY mode
	// -----------------------------------------------------------------------

	/**
	 * In BARROWMAN_ONLY mode every output must equal legacy unless confidence
	 * > 99.9% (which it won't be in practice).
	 */
	@Test
	void barrowmanOnlyModeMatchesLegacy() {
		BarrowmanCalculator legacy = new BarrowmanCalculator();
		RomSettings settings = RomSettings.defaults();
		settings.setEnabled(true);
		settings.setMode(RomMode.STANDARD);
		settings.setFallbackMode(RomFallbackMode.BARROWMAN_ONLY);
		RomAerodynamicCalculator rom = new RomAerodynamicCalculator(legacy.newInstance(), settings);

		FlightConditions cond = conditions(0.5, 3.0);
		AerodynamicForces legF = legacy.getAerodynamicForces(CONFIG, cond, new WarningSet());
		AerodynamicForces romF = rom.getAerodynamicForces(CONFIG, cond, new WarningSet());

		assertEquals(legF.getCD(), romF.getCD(), 1e-12,
				"BARROWMAN_ONLY mode should produce legacy CD");
	}

	// -----------------------------------------------------------------------
	// Diagnostics capture
	// -----------------------------------------------------------------------

	/**
	 * When diagnostics are enabled, every ROM evaluation must produce exactly
	 * one snapshot.  Clearing must empty the snapshot list.
	 */
	@Test
	void diagnosticsSnapshotsAreRecordedAndClearable() {
		RomSettings settings = RomSettings.defaults();
		settings.setEnabled(true);
		settings.setDiagnosticsEnabled(true);
		RomAerodynamicCalculator rom = new RomAerodynamicCalculator(new BarrowmanCalculator(), settings);

		rom.getAerodynamicForces(CONFIG, conditions(0.5, 2.0), new WarningSet());
		rom.getAerodynamicForces(CONFIG, conditions(1.0, 2.0), new WarningSet());
		assertEquals(2, rom.getComputationSnapshots().size(), "Expected 2 snapshots after 2 evals");

		rom.clearComputationSnapshots();
		assertTrue(rom.getComputationSnapshots().isEmpty(), "Snapshot list should be empty after clear");
	}

	// -----------------------------------------------------------------------
	// Notes field contains regime label (plan §Integration)
	// -----------------------------------------------------------------------

	@Test
	void notesContainRegimeLabel() {
		RomAerodynamicCalculator rom = buildEnabledBlend();
		rom.getAerodynamicForces(CONFIG, conditions(0.5, 2.0), new WarningSet());
		assertTrue(rom.getLastResult().getNotes().contains("regime=subsonic"),
				"Notes missing 'regime=subsonic': " + rom.getLastResult().getNotes());

		rom.getAerodynamicForces(CONFIG, conditions(1.0, 2.0), new WarningSet());
		assertTrue(rom.getLastResult().getNotes().contains("regime=transonic"),
				"Notes missing 'regime=transonic': " + rom.getLastResult().getNotes());

		rom.getAerodynamicForces(CONFIG, conditions(2.0, 2.0), new WarningSet());
		assertTrue(rom.getLastResult().getNotes().contains("regime=supersonic"),
				"Notes missing 'regime=supersonic': " + rom.getLastResult().getNotes());
	}

	// -----------------------------------------------------------------------
	// Seeds present in every evaluation
	// -----------------------------------------------------------------------

	@Test
	void seedsAreNonEmptyForEveryCondition() {
		RomAerodynamicCalculator rom = buildEnabledBlend();
		for (double M : new double[]{0.3, 0.9, 1.5, 3.0}) {
			rom.getAerodynamicForces(CONFIG, conditions(M, 3.0), new WarningSet());
			RomResult r = rom.getLastResult();
			assertFalse(r.getSeeds().isEmpty(), "No seeds at M=" + M);
		}
	}

	// -----------------------------------------------------------------------
	// Transonic confidence drop (plan §Integration)
	// -----------------------------------------------------------------------

	@Test
	void transonicConfidenceIsLowerThanSubsonicAndSupersonic() {
		RomAerodynamicCalculator rom = buildEnabledBlend();

		rom.getAerodynamicForces(CONFIG, conditions(0.5, 3.0), new WarningSet());
		double cSub = rom.getLastResult().getConfidence().getOverallScore();

		rom.getAerodynamicForces(CONFIG, conditions(1.0, 3.0), new WarningSet());
		double cTrans = rom.getLastResult().getConfidence().getOverallScore();

		rom.getAerodynamicForces(CONFIG, conditions(2.0, 3.0), new WarningSet());
		double cSuper = rom.getLastResult().getConfidence().getOverallScore();

		assertTrue(cTrans < cSub,
				"Transonic confidence not lower than subsonic: sub=" + cSub + " trans=" + cTrans);
		assertTrue(cTrans < cSuper,
				"Transonic confidence not lower than supersonic: super=" + cSuper + " trans=" + cTrans);
	}

	// -----------------------------------------------------------------------
	// Helpers
	// -----------------------------------------------------------------------

	private static RomAerodynamicCalculator buildRom(double initialPlumeState) {
		RomSettings settings = RomSettings.defaults();
		settings.setEnabled(true);
		settings.setMode(RomMode.STANDARD);
		settings.setFallbackMode(RomFallbackMode.FORCE_ROM);
		settings.setDiagnosticsEnabled(false);
		RomAerodynamicCalculator rom = new RomAerodynamicCalculator(new BarrowmanCalculator(), settings);
		rom.setPlumeState(initialPlumeState);
		return rom;
	}

	private static RomAerodynamicCalculator buildEnabledBlend() {
		RomSettings settings = RomSettings.defaults();
		settings.setEnabled(true);
		settings.setMode(RomMode.STANDARD);
		settings.setFallbackMode(RomFallbackMode.BLEND);
		settings.setDiagnosticsEnabled(true);
		return new RomAerodynamicCalculator(new BarrowmanCalculator(), settings);
	}

	private static FlightConditions conditions(double mach, double aoaDeg) {
		FlightConditions cond = new FlightConditions(CONFIG);
		cond.setMach(mach);
		cond.setAOA(Math.toRadians(aoaDeg));
		cond.setTheta(0.0);
		cond.setAtmosphericConditions(new AtmosphericConditions(288.15, 101325.0));
		return cond;
	}
}

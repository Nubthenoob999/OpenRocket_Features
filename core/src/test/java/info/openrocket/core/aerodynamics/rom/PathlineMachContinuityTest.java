package info.openrocket.core.aerodynamics.rom;

import static org.junit.jupiter.api.Assertions.assertTrue;

import info.openrocket.core.aerodynamics.AerodynamicForces;
import info.openrocket.core.aerodynamics.BarrowmanCalculator;
import info.openrocket.core.aerodynamics.FlightConditions;
import info.openrocket.core.aerodynamics.RomAerodynamicCalculator;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.models.atmosphere.AtmosphericConditions;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.util.BaseTestCase;
import info.openrocket.core.util.TestRockets;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PathlineMachContinuityTest extends BaseTestCase {
	private static Rocket rocket;
	private static FlightConfiguration configuration;

	private RomAerodynamicCalculator rom;

	@BeforeAll
	static void setUpRocket() {
		rocket = TestRockets.makeEstesAlphaIII();
		configuration = rocket.getSelectedConfiguration();
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

	@Test
	public void coefficientsRemainFiniteAndNonNegativeThroughMachZeroToOne() {
		for (double mach = 0.05; mach <= 0.99; mach += 0.02) {
			AerodynamicForces forces = evaluate(mach, 6.0);

			assertTrue(Double.isFinite(forces.getCD()), "CD not finite at M=" + mach);
			assertTrue(Double.isFinite(forces.getCN()), "CN not finite at M=" + mach);
			assertTrue(Double.isFinite(forces.getCm()), "Cm not finite at M=" + mach);
			assertTrue(forces.getCD() >= 0.0, "CD negative at M=" + mach + ": " + forces.getCD());
		}
	}

	@Test
	public void coefficientFirstDifferencesAreBoundedAtMachBandEdges() {
		assertLocalContinuity(0.29, 0.30, 0.31, 4.0);
		assertLocalContinuity(0.79, 0.80, 0.81, 4.0);
		assertLocalContinuity(0.95, 0.98, 0.99, 4.0);
	}

	@Test
	public void diagnosticsIdentifyPresonicAndCrossflowActivity() {
		evaluate(0.90, 12.0);
		RomResult result = rom.getLastResult();

		assertTrue(result.getNotes().contains("machBand=presonic_transonic"));
		assertTrue(result.getNotes().contains("presonicBlend="));
		assertTrue(result.getNotes().contains("crossflow=active"));
	}

	private void assertLocalContinuity(double leftMach, double centerMach, double rightMach, double alphaDeg) {
		AerodynamicForces left = evaluate(leftMach, alphaDeg);
		AerodynamicForces center = evaluate(centerMach, alphaDeg);
		AerodynamicForces right = evaluate(rightMach, alphaDeg);

		double cdScale = Math.max(0.05, Math.max(Math.abs(center.getCD()), Math.abs(left.getCD())));
		assertTrue(Math.abs(center.getCD() - left.getCD()) / cdScale < 0.35,
				"CD jump too large from M=" + leftMach + " to M=" + centerMach);
		cdScale = Math.max(0.05, Math.max(Math.abs(center.getCD()), Math.abs(right.getCD())));
		assertTrue(Math.abs(right.getCD() - center.getCD()) / cdScale < 0.35,
				"CD jump too large from M=" + centerMach + " to M=" + rightMach);

		double cnScale = Math.max(0.05, Math.max(Math.abs(center.getCN()), Math.abs(left.getCN())));
		assertTrue(Math.abs(center.getCN() - left.getCN()) / cnScale < 0.35,
				"CN jump too large from M=" + leftMach + " to M=" + centerMach);
		cnScale = Math.max(0.05, Math.max(Math.abs(center.getCN()), Math.abs(right.getCN())));
		assertTrue(Math.abs(right.getCN() - center.getCN()) / cnScale < 0.35,
				"CN jump too large from M=" + centerMach + " to M=" + rightMach);
	}

	private AerodynamicForces evaluate(double mach, double aoaDeg) {
		FlightConditions conditions = new FlightConditions(configuration);
		conditions.setMach(mach);
		conditions.setAOA(Math.toRadians(aoaDeg));
		conditions.setTheta(0.0);
		conditions.setAtmosphericConditions(new AtmosphericConditions(288.15, 101325.0));
		return rom.getAerodynamicForces(configuration, conditions, new WarningSet());
	}
}

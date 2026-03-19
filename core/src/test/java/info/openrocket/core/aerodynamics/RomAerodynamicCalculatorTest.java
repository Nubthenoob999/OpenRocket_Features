package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.rom.DragSurface;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.simulation.SimulationConditions;
import info.openrocket.core.util.BaseTestCase;
import info.openrocket.core.util.TestRockets;

public class RomAerodynamicCalculatorTest extends BaseTestCase {

	private static DragSurface constantSurface(double cdOff, double cdOn) {
		double[] mach = new double[] { 0.0, 2.0 };
		double[] logRe = new double[] { 4.0, 8.0 };
		double[] alpha = new double[] { 0.0, 10.0 };

		double[][][] off = new double[mach.length][logRe.length][alpha.length];
		double[][][] on = new double[mach.length][logRe.length][alpha.length];
		for (int i = 0; i < mach.length; i++) {
			for (int j = 0; j < logRe.length; j++) {
				for (int k = 0; k < alpha.length; k++) {
					off[i][j][k] = cdOff;
					on[i][j][k] = cdOn;
				}
			}
		}

		return new DragSurface(mach, logRe, alpha, off, on,
				"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", 0.0);
	}

	private static FlightConditions makeConditions(FlightConfiguration config) {
		FlightConditions conditions = new FlightConditions(config);
		conditions.setMach(0.8);
		conditions.setAOA(Math.toRadians(5.0));
		return conditions;
	}

	@Test
	public void testInstallSurfaceAndCloneInstance() {
		RomAerodynamicCalculator calc = new RomAerodynamicCalculator();
		assertFalse(calc.hasSurface());

		DragSurface surface = constantSurface(0.40, 0.30);
		calc.installSurface(surface);
		assertTrue(calc.hasSurface());
		assertNotNull(calc.getInstalledSurface());

		RomAerodynamicCalculator copy = calc.newInstance();
		assertTrue(copy.hasSurface());
		assertNotNull(copy.getInstalledSurface());

		calc.installSurface(null);
		assertFalse(calc.hasSurface());
	}

	@Test
	public void testFallbackMatchesBarrowmanWhenNoSurface() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		FlightConfiguration config = rocket.getSelectedConfiguration();
		FlightConditions conditions = makeConditions(config);

		WarningSet warningsA = new WarningSet();
		WarningSet warningsB = new WarningSet();

		BarrowmanCalculator barrowman = new BarrowmanCalculator();
		RomAerodynamicCalculator rom = new RomAerodynamicCalculator();

		AerodynamicForces expected = barrowman.getAerodynamicForces(config, conditions, warningsA);
		AerodynamicForces actual = rom.getAerodynamicForces(config, conditions, warningsB);

		assertEquals(expected.getCD(), actual.getCD(), 1e-12);
	}

	@Test
	public void testRomCdUsesPlumeBlendState() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		FlightConfiguration config = rocket.getSelectedConfiguration();
		FlightConditions conditions = makeConditions(config);

		RomAerodynamicCalculator calc = new RomAerodynamicCalculator();
		calc.installSurface(constantSurface(0.50, 0.20));

		calc.updatePlumeState(false, 0.0);
		double cdOff = calc.getAerodynamicForces(config, conditions, new WarningSet()).getCD();

		calc.updatePlumeState(true, 0.1);
		double cdOn = calc.getAerodynamicForces(config, conditions, new WarningSet()).getCD();

		calc.updatePlumeState(false, 0.3);
		double cdDecayed = calc.getAerodynamicForces(config, conditions, new WarningSet()).getCD();

		assertEquals(0.50, cdOff, 1e-9);
		assertEquals(0.20, cdOn, 1e-9);
		assertTrue(cdDecayed > cdOn);
		assertTrue(cdDecayed < cdOff);
	}

	@Test
	public void testRomCdScalesAxialDragWithCdRatio() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		FlightConfiguration config = rocket.getSelectedConfiguration();
		FlightConditions conditions = makeConditions(config);

		BarrowmanCalculator baselineCalc = new BarrowmanCalculator();
		AerodynamicForces baselineForces = baselineCalc.getAerodynamicForces(config, conditions, new WarningSet());

		RomAerodynamicCalculator rom = new RomAerodynamicCalculator();
		rom.installSurface(constantSurface(0.45, 0.25));
		rom.updatePlumeState(false, 0.0);
		AerodynamicForces romForces = rom.getAerodynamicForces(config, conditions, new WarningSet());

		double expectedAxial = baselineForces.getCDaxial() * (romForces.getCD() / baselineForces.getCD());
		assertEquals(0.45, romForces.getCD(), 1e-9);
		assertEquals(expectedAxial, romForces.getCDaxial(), 1e-9);
	}

	@Test
	public void testSimulationConditionsPrefersRomCalculatorOnlyWithSurface() {
		SimulationConditions conditions = new SimulationConditions();
		BarrowmanCalculator barrowman = new BarrowmanCalculator();
		RomAerodynamicCalculator rom = new RomAerodynamicCalculator();

		conditions.setAerodynamicCalculator(barrowman);
		conditions.setRomAerodynamicCalculator(rom);

		assertSame(barrowman, conditions.getAerodynamicCalculator());

		rom.installSurface(constantSurface(0.33, 0.22));
		assertSame(rom, conditions.getAerodynamicCalculator());
	}
}
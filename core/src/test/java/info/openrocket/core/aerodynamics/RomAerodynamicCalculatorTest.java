package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.rom.adapter.SurfaceAdapter;
import info.openrocket.core.aerodynamics.rom.DragSurface;
import info.openrocket.core.aerodynamics.rom.core.surface.AeroSurface4D;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.models.atmosphere.AtmosphericConditions;
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

	private static AeroSurface4D thetaSensitiveSurface() {
		double[] mach = new double[] { 0.0, 2.0 };
		double[] logRe = new double[] { 4.0, 8.0 };
		double[] alpha = new double[] { 0.0, 10.0 };
		double[] beta = new double[] { 0.0, 45.0 };

		double[][][][] off = new double[mach.length][logRe.length][alpha.length][beta.length];
		double[][][][] on = new double[mach.length][logRe.length][alpha.length][beta.length];
		double[][][][] body = new double[mach.length][logRe.length][alpha.length][beta.length];
		double[][][][] cn = new double[mach.length][logRe.length][alpha.length][beta.length];
		double[][][][] cm = new double[mach.length][logRe.length][alpha.length][beta.length];

		for (int im = 0; im < mach.length; im++) {
			for (int ir = 0; ir < logRe.length; ir++) {
				off[im][ir][0][0] = 0.35;
				off[im][ir][1][0] = 0.35;
				off[im][ir][0][1] = 1.25;
				off[im][ir][1][1] = 1.25;
				on[im][ir][0][0] = 0.25;
				on[im][ir][1][0] = 0.25;
				on[im][ir][0][1] = 1.15;
				on[im][ir][1][1] = 1.15;
				body[im][ir][0][0] = 0.30;
				body[im][ir][1][0] = 0.30;
				body[im][ir][0][1] = 1.20;
				body[im][ir][1][1] = 1.20;
			}
		}

		return new AeroSurface4D(mach, logRe, alpha, beta, off, on, body, cn, cm,
				"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", 4);
	}

	private static FlightConditions makeConditions(FlightConfiguration config) {
		FlightConditions conditions = new FlightConditions(config);
		conditions.setAtmosphericConditions(new AtmosphericConditions());
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
	public void testFourDSurfaceUsesThetaSymmetryAndComponentQueries() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		FlightConfiguration config = rocket.getSelectedConfiguration();
		AeroSurface4D surface4D = thetaSensitiveSurface();

		FlightConditions theta0 = makeConditions(config);
		theta0.setTheta(0.0);
		theta0.setVelocity(80.0);

		FlightConditions theta45 = makeConditions(config);
		theta45.setTheta(Math.toRadians(45.0));
		theta45.setVelocity(80.0);

		FlightConditions theta90 = makeConditions(config);
		theta90.setTheta(Math.toRadians(90.0));
		theta90.setVelocity(80.0);

		RomAerodynamicCalculator rom4D = new RomAerodynamicCalculator();
		rom4D.installSurface4D(surface4D);
		rom4D.updatePlumeState(false, 0.0);
		double cd4d0 = rom4D.getAerodynamicForces(config, theta0, new WarningSet()).getCD();
		double cd4d45 = rom4D.getAerodynamicForces(config, theta45, new WarningSet()).getCD();
		double cd4d90 = rom4D.getAerodynamicForces(config, theta90, new WarningSet()).getCD();

		RomAerodynamicCalculator rom3D = new RomAerodynamicCalculator();
		rom3D.installSurface(SurfaceAdapter.toBetaZeroDragSurface(surface4D));
		rom3D.updatePlumeState(false, 0.0);
		double cd3d0 = rom3D.getAerodynamicForces(config, theta0, new WarningSet()).getCD();
		double cd3d45 = rom3D.getAerodynamicForces(config, theta45, new WarningSet()).getCD();
		double cd3d90 = rom3D.getAerodynamicForces(config, theta90, new WarningSet()).getCD();

		assertTrue(cd4d0 > 0.0);
		assertTrue(cd4d45 > cd4d0);
		assertEquals(0.45, cd4d90, 1e-9);
		assertEquals(cd3d0, cd3d45, 1e-12);
		assertEquals(cd3d0, cd3d90, 1e-12);
		assertEquals(cd4d0, cd3d0, 1e-12);
		assertTrue(Math.abs(cd4d45 - cd3d45) > 1e-6);
		assertEquals(3, rom4D.getComputationSnapshots().size());
		RomAerodynamicCalculator.RomComputationSnapshot snapshot0 = rom4D.getComputationSnapshots().get(0);
		RomAerodynamicCalculator.RomComputationSnapshot snapshot45 = rom4D.getComputationSnapshots().get(1);
		RomAerodynamicCalculator.RomComputationSnapshot snapshot90 = rom4D.getComputationSnapshots().get(2);
		assertEquals(1.0, snapshot0.getBlendWeight(), 1e-9);
		assertEquals(1.0, snapshot45.getBlendWeight(), 1e-9);
		assertEquals(1.0, snapshot90.getBlendWeight(), 1e-9);
		assertEquals(5.0, snapshot0.getAlphaDeg(), 1e-9);
		assertEquals(snapshot0.getAlphaDeg(), snapshot45.getAlphaDeg(), 1e-12);
		assertEquals(snapshot0.getAlphaDeg(), snapshot90.getAlphaDeg(), 1e-12);
		assertEquals(5.0, snapshot0.getQueryAlphaDeg(), 1e-9);
		assertEquals(0.0, snapshot0.getQueryBetaDeg(), 1e-9);
		assertTrue(snapshot45.getQueryAlphaDeg() < snapshot0.getQueryAlphaDeg());
		assertTrue(snapshot45.getQueryBetaDeg() > snapshot0.getQueryBetaDeg());
		assertEquals(0.0, snapshot90.getQueryAlphaDeg(), 1e-9);
		assertEquals(5.0, snapshot90.getQueryBetaDeg(), 1e-9);
		assertTrue(snapshot0.getReynoldsLength() > 0.0);
		assertTrue(snapshot0.getMach() > 0.0);
		assertEquals(cd4d0, snapshot0.getCdPlumeOff(), 1e-9);
		assertEquals(cd4d45, snapshot45.getCdPlumeOff(), 1e-9);
		assertEquals(cd4d90, snapshot90.getCdPlumeOff(), 1e-9);
	}

	@Test
	public void testHighReLowMachUsesRomBlend() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		FlightConfiguration config = rocket.getSelectedConfiguration();
		FlightConditions conditions = new FlightConditions(config);
		conditions.setAtmosphericConditions(new AtmosphericConditions());
		conditions.setAOA(Math.toRadians(5.0));
		conditions.setVelocity(80.0);

		RomAerodynamicCalculator rom = new RomAerodynamicCalculator();
		rom.installSurface(constantSurface(0.62, 0.40));
		rom.updatePlumeState(false, 0.0);

		AerodynamicForces romForces = rom.getAerodynamicForces(config, conditions, new WarningSet());

		assertEquals(0.62, romForces.getCD(), 1e-9);
		assertEquals(1, rom.getComputationSnapshots().size());
		assertEquals(1.0, rom.getComputationSnapshots().get(0).getBlendWeight(), 1e-9);
		assertEquals(5.0, rom.getComputationSnapshots().get(0).getAlphaDeg(), 1e-9);
		assertTrue(Double.isFinite(rom.getComputationSnapshots().get(0).getMach()));
		assertTrue(rom.getComputationSnapshots().get(0).getReynoldsLength() > 0.0);
	}

	@Test
	public void testLowReLowMachKeepsBarrowmanFallback() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		FlightConfiguration config = rocket.getSelectedConfiguration();
		FlightConditions conditions = new FlightConditions(config);
		conditions.setAtmosphericConditions(new AtmosphericConditions());
		conditions.setAOA(Math.toRadians(5.0));
		conditions.setVelocity(5.0);

		BarrowmanCalculator baselineCalc = new BarrowmanCalculator();
		AerodynamicForces baselineForces = baselineCalc.getAerodynamicForces(config, conditions, new WarningSet());

		RomAerodynamicCalculator rom = new RomAerodynamicCalculator();
		rom.installSurface(constantSurface(0.62, 0.40));
		rom.updatePlumeState(false, 0.0);
		AerodynamicForces romForces = rom.getAerodynamicForces(config, conditions, new WarningSet());

		assertEquals(baselineForces.getCD(), romForces.getCD(), 1e-9);
		assertEquals(1, rom.getComputationSnapshots().size());
		assertEquals(0.0, rom.getComputationSnapshots().get(0).getBlendWeight(), 1e-9);
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

	@Test
	public void testComputationSnapshotsCaptureBeforeAndAfterCd() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		FlightConfiguration config = rocket.getSelectedConfiguration();
		FlightConditions conditions = makeConditions(config);

		BarrowmanCalculator baselineCalc = new BarrowmanCalculator();
		double baselineCd = baselineCalc.getAerodynamicForces(config, conditions, new WarningSet()).getCD();

		RomAerodynamicCalculator rom = new RomAerodynamicCalculator();
		rom.installSurface(constantSurface(0.41, 0.29));
		rom.setCurrentSimulationTime(1.25);
		rom.updatePlumeState(false, 0.0);
		rom.getAerodynamicForces(config, conditions, new WarningSet());

		assertEquals(1, rom.getComputationSnapshots().size());
		RomAerodynamicCalculator.RomComputationSnapshot snapshot = rom.getComputationSnapshots().get(0);
		assertEquals(1.25, snapshot.getTimeSeconds(), 1e-12);
		assertEquals(baselineCd, snapshot.getCdBefore(), 1e-9);
		assertEquals(0.41, snapshot.getCdAfter(), 1e-9);
		assertEquals(1.0, snapshot.getBlendWeight(), 1e-9);
		assertEquals(5.0, snapshot.getAlphaDeg(), 1e-9);
		assertTrue(Double.isFinite(snapshot.getMach()));
		assertTrue(snapshot.getReynoldsLength() > 0.0);

		rom.clearComputationSnapshots();
		assertTrue(rom.getComputationSnapshots().isEmpty());
	}
}

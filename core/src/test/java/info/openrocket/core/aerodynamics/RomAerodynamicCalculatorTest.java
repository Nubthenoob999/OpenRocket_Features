package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.rom.adapter.SurfaceAdapter;
import info.openrocket.core.aerodynamics.rom.DragSurface;
import info.openrocket.core.aerodynamics.rom.core.surface.AeroSurface4DInterpolator;
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
				cn[im][ir][0][0] = 0.08;
				cn[im][ir][1][0] = 0.28;
				cn[im][ir][0][1] = 0.18;
				cn[im][ir][1][1] = 0.38;
				cm[im][ir][0][0] = -0.02;
				cm[im][ir][1][0] = -0.12;
				cm[im][ir][0][1] = -0.05;
				cm[im][ir][1][1] = -0.15;
			}
		}

		return new AeroSurface4D(mach, logRe, alpha, beta, off, on, body, cn, cm,
				"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", 4);
	}

	private static AeroSurface4D cnCmSurface() {
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
				for (int ia = 0; ia < alpha.length; ia++) {
					for (int ib = 0; ib < beta.length; ib++) {
						double baseCd = 0.40 + 0.02 * alpha[ia] + 0.03 * beta[ib];
						off[im][ir][ia][ib] = baseCd;
						on[im][ir][ia][ib] = baseCd - 0.10;
						body[im][ir][ia][ib] = Math.max(0.001, baseCd - 0.03);
						cn[im][ir][ia][ib] = 0.10 + 0.04 * alpha[ia] + 0.02 * beta[ib];
						cm[im][ir][ia][ib] = -0.03 - 0.01 * alpha[ia] - 0.02 * beta[ib];
					}
				}
			}
		}

		return new AeroSurface4D(mach, logRe, alpha, beta, off, on, body, cn, cm,
				"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", 4);
	}

	private static FlightConditions makeConditions(FlightConfiguration config) {
		FlightConditions conditions = new FlightConditions(config);
		conditions.setAtmosphericConditions(new AtmosphericConditions());
		conditions.setMach(0.3);
		conditions.setVelocity(150.0);
		conditions.setAOA(Math.toRadians(5.0));
		return conditions;
	}

	private static FlightConditions makeConditions(FlightConfiguration config, double aoaDeg, double thetaDeg) {
		FlightConditions conditions = makeConditions(config);
		conditions.setAOA(Math.toRadians(aoaDeg));
		conditions.setTheta(Math.toRadians(thetaDeg));
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
		assertEquals(RomAerodynamicCalculator.RomCoefficientMode.HYBRID_4D, snapshot0.getCoefficientMode());
	}

	@Test
	public void testFourDSurfaceKeepsBarrowmanCnAndCmWhileRecordingRomValues() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		FlightConfiguration config = rocket.getSelectedConfiguration();
		AeroSurface4D surface4D = cnCmSurface();

		FlightConditions conditions = makeConditions(config, 10.0, 30.0);
		conditions.setMach(2.0);
		conditions.setVelocity(650.0);

		BarrowmanCalculator baselineCalc = new BarrowmanCalculator();
		AerodynamicForces baselineForces = baselineCalc.getAerodynamicForces(config, conditions, new WarningSet());

		RomAerodynamicCalculator rom4D = new RomAerodynamicCalculator();
		rom4D.installSurface4D(surface4D);
		rom4D.updatePlumeState(false, 0.0);

		AerodynamicForces forces = rom4D.getAerodynamicForces(config, conditions, new WarningSet());
		RomAerodynamicCalculator.RomComputationSnapshot snapshot = rom4D.getComputationSnapshots().get(0);
		AeroSurface4DInterpolator interpolator = new AeroSurface4DInterpolator(surface4D);
		AeroSurface4DInterpolator.QueryResult expected = interpolator
				.query(snapshot.getQueryMach(), snapshot.getQueryReynoldsLength(),
						snapshot.getQueryAlphaDeg(), snapshot.getQueryBetaDeg());
		AeroSurface4DInterpolator.QueryResult betaZero = interpolator
				.query(snapshot.getQueryMach(), snapshot.getQueryReynoldsLength(),
						snapshot.getAlphaDeg(), 0.0);

		assertTrue(snapshot.getBlendWeight() < 1.0);
		double lower = Math.min(baselineForces.getCD(), betaZero.cdPlumeOff);
		double upper = Math.max(baselineForces.getCD(), betaZero.cdPlumeOff);
		assertTrue(forces.getCD() > lower);
		assertTrue(forces.getCD() < upper);
		assertEquals(baselineForces.getCN(), forces.getCN(), 1e-9);
		assertEquals(baselineForces.getCm(), forces.getCm(), 1e-9);
		assertEquals(expected.CN, snapshot.getQueriedCN(), 1e-9);
		assertEquals(baselineForces.getCN(), snapshot.getEffectiveCN(), 1e-9);
		assertEquals(expected.Cm, snapshot.getQueriedCm(), 1e-9);
		assertEquals(baselineForces.getCm(), snapshot.getEffectiveCm(), 1e-9);
		assertEquals(RomAerodynamicCalculator.RomCoefficientMode.HYBRID_4D, snapshot.getCoefficientMode());
	}

	@Test
	public void testThreeDSurfaceRemainsDragOnlyForCnAndCm() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		FlightConfiguration config = rocket.getSelectedConfiguration();
		FlightConditions conditions = makeConditions(config, 7.5, 0.0);

		BarrowmanCalculator baselineCalc = new BarrowmanCalculator();
		AerodynamicForces baselineForces = baselineCalc.getAerodynamicForces(config, conditions, new WarningSet());

		RomAerodynamicCalculator rom3D = new RomAerodynamicCalculator();
		rom3D.installSurface(SurfaceAdapter.toBetaZeroDragSurface(cnCmSurface()));
		rom3D.updatePlumeState(false, 0.0);
		AerodynamicForces romForces = rom3D.getAerodynamicForces(config, conditions, new WarningSet());

		assertEquals(baselineForces.getCN(), romForces.getCN(), 1e-9);
		assertEquals(baselineForces.getCm(), romForces.getCm(), 1e-9);
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
		AerodynamicForces baselineForces = new BarrowmanCalculator().getAerodynamicForces(config, conditions, new WarningSet());

		assertEquals(0.62, romForces.getCD(), 1e-9);
		assertEquals(baselineForces.getCN(), romForces.getCN(), 1e-9);
		assertEquals(baselineForces.getCm(), romForces.getCm(), 1e-9);
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
		assertEquals(baselineForces.getCN(), romForces.getCN(), 1e-9);
		assertEquals(baselineForces.getCm(), romForces.getCm(), 1e-9);
		assertEquals(1, rom.getComputationSnapshots().size());
		assertEquals(0.0, rom.getComputationSnapshots().get(0).getBlendWeight(), 1e-9);
	}

	@Test
	public void testLowReLowMachKeepsBarrowmanCnAndCmForFourDSurface() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		FlightConfiguration config = rocket.getSelectedConfiguration();
		FlightConditions conditions = makeConditions(config, 10.0, 30.0);
		conditions.setVelocity(5.0);

		BarrowmanCalculator baselineCalc = new BarrowmanCalculator();
		AerodynamicForces baselineForces = baselineCalc.getAerodynamicForces(config, conditions, new WarningSet());

		RomAerodynamicCalculator rom = new RomAerodynamicCalculator();
		rom.installSurface4D(cnCmSurface());
		rom.updatePlumeState(false, 0.0);
		AerodynamicForces romForces = rom.getAerodynamicForces(config, conditions, new WarningSet());
		RomAerodynamicCalculator.RomComputationSnapshot snapshot = rom.getComputationSnapshots().get(0);

		assertEquals(baselineForces.getCN(), romForces.getCN(), 1e-9);
		assertEquals(baselineForces.getCm(), romForces.getCm(), 1e-9);
		assertEquals(0.0, snapshot.getBlendWeight(), 1e-9);
		assertEquals(RomAerodynamicCalculator.RomCoefficientMode.HYBRID_4D, snapshot.getCoefficientMode());
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
		assertTrue(Double.isNaN(snapshot.getQueriedCN()));
		assertEquals(RomAerodynamicCalculator.RomCoefficientMode.DRAG_ONLY_3D, snapshot.getCoefficientMode());

		rom.clearComputationSnapshots();
		assertTrue(rom.getComputationSnapshots().isEmpty());
	}

	@Test
	public void testComputationSnapshotExposesCnCmAndModeMarker() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		FlightConfiguration config = rocket.getSelectedConfiguration();
		FlightConditions conditions = makeConditions(config);
		BarrowmanCalculator baselineCalc = new BarrowmanCalculator();
		AerodynamicForces baselineForces = baselineCalc.getAerodynamicForces(config, conditions, new WarningSet());
		RomAerodynamicCalculator rom = new RomAerodynamicCalculator();
		rom.installSurface4D(cnCmSurface());
		rom.updatePlumeState(false, 0.0);
		rom.getAerodynamicForces(config, conditions, new WarningSet());

		RomAerodynamicCalculator.RomComputationSnapshot snapshot = rom.getComputationSnapshots().get(0);
		assertTrue(Double.isFinite(snapshot.getQueriedCN()));
		assertTrue(Double.isFinite(snapshot.getEffectiveCN()));
		assertTrue(Double.isFinite(snapshot.getQueriedCm()));
		assertTrue(Double.isFinite(snapshot.getEffectiveCm()));
		assertEquals(baselineForces.getCN(), snapshot.getEffectiveCN(), 1e-9);
		assertEquals(baselineForces.getCm(), snapshot.getEffectiveCm(), 1e-9);
		assertEquals(RomAerodynamicCalculator.RomCoefficientMode.HYBRID_4D, snapshot.getCoefficientMode());
	}

	@Test
	public void testBoostGuardrailFallbackDoesNotLeakIntoCoastSegment() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		FlightConfiguration config = rocket.getSelectedConfiguration();
		FlightConditions conditions = makeConditions(config);

		double baselineCd = new BarrowmanCalculator()
				.getAerodynamicForces(config, conditions, new WarningSet())
				.getCD();
		double boostTriggerCd = baselineCd * 1.75;

		RomAerodynamicCalculator rom = new RomAerodynamicCalculator();
		rom.installSurface(constantSurface(baselineCd * 1.22, baselineCd * 1.22));

		rom.setFlightRegime(RomAerodynamicCalculator.FlightRegime.POWERED_ASCENT);
		rom.setCurrentSimulationTime(0.80);
		AerodynamicForces boostForces = rom.getAerodynamicForces(config, conditions, new WarningSet());
		RomAerodynamicCalculator.RomComputationSnapshot boostSnapshot = rom.getComputationSnapshots().get(0);

		assertTrue(rom.isPoweredAscentSegmentFallbackActive());
		assertFalse(rom.isCoastAscentSegmentFallbackActive());
		assertTrue(boostSnapshot.isAscentSegmentFallbackActive());
		assertTrue(boostSnapshot.isGuardrailTriggered());
		assertEquals(RomAerodynamicCalculator.GuardrailReason.BOOST_DRAG_IMBALANCE,
				boostSnapshot.getGuardrailReason());
		assertEquals(0.0, boostSnapshot.getBlendWeight(), 1e-12);
		assertEquals(baselineCd, boostForces.getCD(), 1e-9);

		rom.clearComputationSnapshots();
		rom.setFlightRegime(RomAerodynamicCalculator.FlightRegime.COAST_ASCENT);
		rom.setCurrentSimulationTime(1.20);
		AerodynamicForces coastForces = rom.getAerodynamicForces(config, conditions, new WarningSet());
		RomAerodynamicCalculator.RomComputationSnapshot coastSnapshot = rom.getComputationSnapshots().get(0);

		assertFalse(coastSnapshot.isAscentSegmentFallbackActive());
		assertFalse(coastSnapshot.isGuardrailTriggered());
		assertFalse(rom.isCoastAscentSegmentFallbackActive());
		assertTrue(coastForces.getCD() > baselineCd);
	}

	@Test
	public void testCoastGuardrailImbalanceKeepsRomInfluenceWithoutSegmentFallback() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		FlightConfiguration config = rocket.getSelectedConfiguration();
		FlightConditions conditions = makeConditions(config);

		double baselineCd = new BarrowmanCalculator()
				.getAerodynamicForces(config, conditions, new WarningSet())
				.getCD();

		RomAerodynamicCalculator rom = new RomAerodynamicCalculator();
		rom.installSurface(constantSurface(baselineCd * 1.70, baselineCd * 1.70));
		rom.setResidualPilotEnabled(false);
		rom.setFlightRegime(RomAerodynamicCalculator.FlightRegime.COAST_ASCENT);
		rom.setCurrentSimulationTime(3.0);

		AerodynamicForces coastForces = rom.getAerodynamicForces(config, conditions, new WarningSet());
		RomAerodynamicCalculator.RomComputationSnapshot snapshot = rom.getComputationSnapshots().get(0);

		assertTrue(snapshot.isGuardrailTriggered());
		assertEquals(RomAerodynamicCalculator.GuardrailReason.COAST_DRAG_IMBALANCE,
				snapshot.getGuardrailReason());
		assertFalse(snapshot.isAscentSegmentFallbackActive());
		assertFalse(rom.isCoastAscentSegmentFallbackActive());
		assertTrue(snapshot.getBlendWeight() > 0.0);
		assertTrue(coastForces.getCD() > baselineCd);
	}

	@Test
	public void testResidualPilotAppliesBoundedCorrectionInAscent() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		FlightConfiguration config = rocket.getSelectedConfiguration();
		FlightConditions conditions = makeConditions(config);

		double baselineCd = new BarrowmanCalculator()
				.getAerodynamicForces(config, conditions, new WarningSet())
				.getCD();
		double moderateRomCd = baselineCd * 1.20;

		RomAerodynamicCalculator withPilot = new RomAerodynamicCalculator();
		withPilot.installSurface(constantSurface(moderateRomCd, moderateRomCd));
		withPilot.setFlightRegime(RomAerodynamicCalculator.FlightRegime.COAST_ASCENT);
		withPilot.setCurrentSimulationTime(2.0);
		AerodynamicForces pilotForces = withPilot.getAerodynamicForces(config, conditions, new WarningSet());
		RomAerodynamicCalculator.RomComputationSnapshot pilotSnapshot = withPilot.getComputationSnapshots().get(0);

		RomAerodynamicCalculator withoutPilot = new RomAerodynamicCalculator();
		withoutPilot.installSurface(constantSurface(moderateRomCd, moderateRomCd));
		withoutPilot.setResidualPilotEnabled(false);
		withoutPilot.setFlightRegime(RomAerodynamicCalculator.FlightRegime.COAST_ASCENT);
		withoutPilot.setCurrentSimulationTime(2.0);
		AerodynamicForces noPilotForces = withoutPilot.getAerodynamicForces(config, conditions, new WarningSet());
		RomAerodynamicCalculator.RomComputationSnapshot noPilotSnapshot = withoutPilot.getComputationSnapshots().get(0);

		assertFalse(pilotSnapshot.isAscentSegmentFallbackActive());
		assertTrue(pilotSnapshot.getResidualPilotConfidence() > 0.0);
		assertTrue(pilotSnapshot.getResidualPilotConfidence() <= 1.0);
		assertTrue(pilotSnapshot.getResidualPilotCorrection() < 0.0);
		assertTrue(pilotForces.getCD() < noPilotForces.getCD());
		assertEquals(0.0, noPilotSnapshot.getResidualPilotCorrection(), 1e-12);
		assertEquals(0.0, noPilotSnapshot.getResidualPilotConfidence(), 1e-12);
	}

	@Test
	public void testBoundaryTransitionTapersRomTrustNearAscentEvents() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		FlightConfiguration config = rocket.getSelectedConfiguration();
		FlightConditions conditions = makeConditions(config);

		double baselineCd = new BarrowmanCalculator()
				.getAerodynamicForces(config, conditions, new WarningSet())
				.getCD();
		double moderateRomCd = baselineCd * 1.18;

		RomAerodynamicCalculator rom = new RomAerodynamicCalculator();
		rom.installSurface(constantSurface(moderateRomCd, moderateRomCd));
		rom.setFlightRegime(RomAerodynamicCalculator.FlightRegime.COAST_ASCENT);

		rom.setCurrentSimulationTime(4.00);
		rom.setBoundaryEvent(RomAerodynamicCalculator.BoundaryEvent.APOGEE, 4.02);
		rom.getAerodynamicForces(config, conditions, new WarningSet());
		RomAerodynamicCalculator.RomComputationSnapshot nearBoundary = rom.getComputationSnapshots().get(0);

		rom.clearComputationSnapshots();
		rom.setCurrentSimulationTime(4.00);
		rom.setBoundaryEvent(RomAerodynamicCalculator.BoundaryEvent.APOGEE, 6.00);
		rom.getAerodynamicForces(config, conditions, new WarningSet());
		RomAerodynamicCalculator.RomComputationSnapshot farBoundary = rom.getComputationSnapshots().get(0);

		assertTrue(nearBoundary.getBlendWeight() < farBoundary.getBlendWeight());
	}

	@Test
	public void testBoundaryTransitionTrustIsSymmetricAroundEventTime() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		FlightConfiguration config = rocket.getSelectedConfiguration();
		FlightConditions conditions = makeConditions(config);

		double baselineCd = new BarrowmanCalculator()
				.getAerodynamicForces(config, conditions, new WarningSet())
				.getCD();
		double moderateRomCd = baselineCd * 1.18;

		RomAerodynamicCalculator rom = new RomAerodynamicCalculator();
		rom.installSurface(constantSurface(moderateRomCd, moderateRomCd));
		rom.setFlightRegime(RomAerodynamicCalculator.FlightRegime.COAST_ASCENT);
		rom.setBoundaryEvent(RomAerodynamicCalculator.BoundaryEvent.APOGEE, 4.02);

		rom.setCurrentSimulationTime(4.00);
		rom.getAerodynamicForces(config, conditions, new WarningSet());
		RomAerodynamicCalculator.RomComputationSnapshot beforeEvent = rom.getComputationSnapshots().get(0);

		rom.clearComputationSnapshots();
		rom.setCurrentSimulationTime(4.04);
		rom.getAerodynamicForces(config, conditions, new WarningSet());
		RomAerodynamicCalculator.RomComputationSnapshot afterEvent = rom.getComputationSnapshots().get(0);

		assertEquals(beforeEvent.getBlendWeight(), afterEvent.getBlendWeight(), 1e-12);
	}

	@Test
	public void testCoastHandoffDampsBlendAfterPoweredFallback() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		FlightConfiguration config = rocket.getSelectedConfiguration();
		FlightConditions conditions = makeConditions(config);

		double baselineCd = new BarrowmanCalculator()
				.getAerodynamicForces(config, conditions, new WarningSet())
				.getCD();
		double triggerCd = baselineCd * 1.62;

		RomAerodynamicCalculator rom = new RomAerodynamicCalculator();
		rom.installSurface(constantSurface(triggerCd, triggerCd));

		rom.setFlightRegime(RomAerodynamicCalculator.FlightRegime.POWERED_ASCENT);
		rom.setCurrentSimulationTime(0.90);
		rom.getAerodynamicForces(config, conditions, new WarningSet());
		assertTrue(rom.isPoweredAscentSegmentFallbackActive());
		rom.installSurface(constantSurface(baselineCd * 1.12, baselineCd * 1.12));

		rom.clearComputationSnapshots();
		rom.setFlightRegime(RomAerodynamicCalculator.FlightRegime.COAST_ASCENT);
		rom.setBoundaryEvent(RomAerodynamicCalculator.BoundaryEvent.BURNOUT, 1.00);
		rom.setCurrentSimulationTime(1.02);
		AerodynamicForces nearForces = rom.getAerodynamicForces(config, conditions, new WarningSet());
		RomAerodynamicCalculator.RomComputationSnapshot nearSnapshot = rom.getComputationSnapshots().get(0);

		rom.clearComputationSnapshots();
		rom.setBoundaryEvent(RomAerodynamicCalculator.BoundaryEvent.BURNOUT, 1.00);
		rom.setCurrentSimulationTime(2.20);
		AerodynamicForces farForces = rom.getAerodynamicForces(config, conditions, new WarningSet());
		RomAerodynamicCalculator.RomComputationSnapshot farSnapshot = rom.getComputationSnapshots().get(0);

		assertTrue(nearSnapshot.getBlendWeight() < farSnapshot.getBlendWeight());
		assertTrue(nearForces.getCD() <= farForces.getCD() + 1.0e-9);
	}

	@Test
	public void testBoostEnvelopeCapsCdWithoutGuardrailFallback() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		FlightConfiguration config = rocket.getSelectedConfiguration();
		FlightConditions conditions = makeConditions(config);

		double baselineCd = new BarrowmanCalculator()
				.getAerodynamicForces(config, conditions, new WarningSet())
				.getCD();

		RomAerodynamicCalculator rom = new RomAerodynamicCalculator();
		rom.installSurface(constantSurface(baselineCd * 1.15, baselineCd * 1.15));
		rom.setResidualPilotEnabled(false);
		rom.setFlightRegime(RomAerodynamicCalculator.FlightRegime.POWERED_ASCENT);
		rom.setCurrentSimulationTime(2.0);

		AerodynamicForces forces = rom.getAerodynamicForces(config, conditions, new WarningSet());
		RomAerodynamicCalculator.RomComputationSnapshot snapshot = rom.getComputationSnapshots().get(0);

		assertFalse(snapshot.isAscentSegmentFallbackActive());
		assertFalse(snapshot.isGuardrailTriggered());
		assertEquals(RomAerodynamicCalculator.GuardrailReason.NONE, snapshot.getGuardrailReason());
		double boostRatio = forces.getCD() / baselineCd;
		assertTrue(boostRatio > 1.0);
		assertTrue(boostRatio <= 1.16);
	}

	@Test
	public void testCoastEnvelopeFloorsCdWithoutGuardrailFallback() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		FlightConfiguration config = rocket.getSelectedConfiguration();
		FlightConditions conditions = makeConditions(config);

		double baselineCd = new BarrowmanCalculator()
				.getAerodynamicForces(config, conditions, new WarningSet())
				.getCD();

		RomAerodynamicCalculator rom = new RomAerodynamicCalculator();
		rom.installSurface(constantSurface(baselineCd * 0.85, baselineCd * 0.85));
		rom.setResidualPilotEnabled(false);
		rom.setFlightRegime(RomAerodynamicCalculator.FlightRegime.COAST_ASCENT);
		rom.setCurrentSimulationTime(3.0);

		AerodynamicForces forces = rom.getAerodynamicForces(config, conditions, new WarningSet());
		RomAerodynamicCalculator.RomComputationSnapshot snapshot = rom.getComputationSnapshots().get(0);

		assertFalse(snapshot.isAscentSegmentFallbackActive());
		assertFalse(snapshot.isGuardrailTriggered());
		assertEquals(RomAerodynamicCalculator.GuardrailReason.NONE, snapshot.getGuardrailReason());
		double coastRatio = forces.getCD() / baselineCd;
		assertTrue(coastRatio >= 0.88);
		assertTrue(coastRatio < 0.90);
	}
}

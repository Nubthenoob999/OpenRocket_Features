package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.rom.core.surface.AeroSurface4D;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.util.BaseTestCase;
import info.openrocket.core.util.TestRockets;

public class RomAerodynamicCalculator4DTest extends BaseTestCase {

	private static final String HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

	private static AeroSurface4D interpolatedSurface4D() {
		double[] mach = new double[] { 0.0, 1.0 };
		double[] re = new double[] { 4.0, 8.0 };
		double[] alpha = new double[] { 0.0, 10.0 };
		double[] beta = new double[] { 0.0, 10.0 };

		double[][][][] off = new double[mach.length][re.length][alpha.length][beta.length];
		double[][][][] on = new double[mach.length][re.length][alpha.length][beta.length];
		double[][][][] body = new double[mach.length][re.length][alpha.length][beta.length];
		double[][][][] cn = new double[mach.length][re.length][alpha.length][beta.length];
		double[][][][] cm = new double[mach.length][re.length][alpha.length][beta.length];

		for (int im = 0; im < mach.length; im++) {
			for (int ir = 0; ir < re.length; ir++) {
				for (int ia = 0; ia < alpha.length; ia++) {
					for (int ib = 0; ib < beta.length; ib++) {
						double baseCd = 0.40 + 0.01 * alpha[ia] + 0.02 * beta[ib];
						off[im][ir][ia][ib] = baseCd;
						on[im][ir][ia][ib] = baseCd - 0.10;
						body[im][ir][ia][ib] = Math.max(0.001, baseCd - 0.03);
					}
				}
			}
		}

		return new AeroSurface4D(mach, re, alpha, beta, off, on, body, cn, cm, HASH, 4);
	}

	private static FlightConditions makeConditions(FlightConfiguration config, double aoaDeg, double thetaDeg) {
		FlightConditions conditions = new FlightConditions(config);
		conditions.setMach(0.8);
		conditions.setAOA(Math.toRadians(aoaDeg));
		conditions.setTheta(Math.toRadians(thetaDeg));
		return conditions;
	}

	@Test
	public void testFourDQueryUsesAoAThetaComponentDecomposition() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		FlightConfiguration config = rocket.getSelectedConfiguration();
		FlightConditions conditions = makeConditions(config, 10.0, 30.0);

		RomAerodynamicCalculator calc = new RomAerodynamicCalculator();
		calc.installSurface4D(interpolatedSurface4D());
		calc.updatePlumeState(false, 0.0);

		AerodynamicForces forces = calc.getAerodynamicForces(config, conditions, new WarningSet());
		RomAerodynamicCalculator.RomComputationSnapshot snapshot = calc.getComputationSnapshots().get(0);

		double expectedAlpha = 10.0 * Math.cos(Math.toRadians(30.0));
		double expectedBeta = 10.0 * Math.sin(Math.toRadians(30.0));
		double expectedCd = 0.40 + 0.01 * expectedAlpha + 0.02 * expectedBeta;

		assertEquals(expectedAlpha, snapshot.getQueryAlphaDeg(), 1e-9);
		assertEquals(expectedBeta, snapshot.getQueryBetaDeg(), 1e-9);
		assertEquals(expectedBeta, snapshot.getBetaDeg(), 1e-9);
		assertFalse(snapshot.isAlphaClamped());
		assertFalse(snapshot.isBetaClamped());
		assertFalse(snapshot.isReynoldsClamped());
		assertFalse(snapshot.isReynoldsLengthClamped());
		assertEquals(expectedCd, forces.getCD(), 1e-9);
	}

	@Test
	public void testFourDQueryClampsOversizedBetaComponent() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		FlightConfiguration config = rocket.getSelectedConfiguration();
		FlightConditions conditions = makeConditions(config, 20.0, 90.0);

		RomAerodynamicCalculator calc = new RomAerodynamicCalculator();
		calc.installSurface4D(interpolatedSurface4D());
		calc.updatePlumeState(false, 0.0);

		AerodynamicForces forces = calc.getAerodynamicForces(config, conditions, new WarningSet());
		RomAerodynamicCalculator.RomComputationSnapshot snapshot = calc.getComputationSnapshots().get(0);

		assertEquals(0.0, snapshot.getQueryAlphaDeg(), 1e-9);
		assertEquals(10.0, snapshot.getQueryBetaDeg(), 1e-9);
		assertFalse(snapshot.isAlphaClamped());
		assertTrue(snapshot.isBetaClamped());
		assertEquals(snapshot.isReynoldsClamped(), snapshot.isReynoldsLengthClamped());
		assertEquals(0.60, forces.getCD(), 1e-9);
	}
}

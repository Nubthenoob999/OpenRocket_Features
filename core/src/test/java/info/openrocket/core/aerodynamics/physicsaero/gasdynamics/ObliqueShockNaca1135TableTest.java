package info.openrocket.core.aerodynamics.physicsaero.gasdynamics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import info.openrocket.core.aerodynamics.physicsaero.flow.GasState;

/**
 * Theta-beta-Mach and detachment values from NACA Report 1135, Charts 2-4.
 *
 * <p>Adapted from AidanSYu/openrocketsupersonic,
 * {@code supersonic-aero-dev}.</p>
 */
class ObliqueShockNaca1135TableTest {
	private static final PerfectGasAir AIR = new PerfectGasAir();

	@ParameterizedTest(name = "NACA 1135 weak shock M={0}, theta={1}")
	@CsvSource({
			"2.0, 10.0, 39.31",
			"2.0, 15.0, 45.34",
			"2.0, 20.0, 53.42",
			"3.0, 5.0, 23.13",
			"3.0, 10.0, 27.38",
			"3.0, 20.0, 37.76",
			"3.0, 25.0, 44.14",
			"5.0, 10.0, 19.38",
			"5.0, 20.0, 29.80",
			"5.0, 30.0, 42.34",
			"5.0, 35.0, 49.86"
	})
	void weakBranchMatchesChart(double mach, double turnDegrees,
			double expectedShockDegrees) {
		ShockSolution solution = ObliqueShockCalculator.solve(state(mach),
				Math.toRadians(turnDegrees), ShockSolution.Branch.WEAK, AIR);

		assertEquals(expectedShockDegrees, Math.toDegrees(solution.shockAngleRad()), 0.15);
		assertEquals(ShockSolution.Attachment.ATTACHED, solution.attachment());
		assertTrue(solution.residual() < 1.0e-8);
	}

	@ParameterizedTest(name = "NACA 1135 theta max M={0}")
	@CsvSource({
			"1.5, 12.11",
			"2.0, 22.97",
			"3.0, 34.07",
			"5.0, 41.12",
			"10.0, 44.43"
	})
	void maximumTurningAngleMatchesChart2(double mach, double expectedDegrees) {
		assertEquals(expectedDegrees,
				Math.toDegrees(ObliqueShockCalculator.maximumTurningAngle(mach, 1.4)),
				0.15);
	}

	@Test
	void weakAndStrongRootsRemainDistinctAndOverturningDetaches() {
		GasState upstream = state(3);
		double turn = Math.toRadians(15);
		ShockSolution weak = ObliqueShockCalculator.solve(upstream, turn,
				ShockSolution.Branch.WEAK, AIR);
		ShockSolution strong = ObliqueShockCalculator.solve(upstream, turn,
				ShockSolution.Branch.STRONG, AIR);

		assertTrue(strong.shockAngleRad() > weak.shockAngleRad());
		assertTrue(strong.downstream().pressurePa() > weak.downstream().pressurePa());
		assertTrue(strong.downstream().mach() < 1);
		assertEquals(ShockSolution.Attachment.DETACHED,
				ObliqueShockCalculator.solve(state(2), Math.toRadians(30),
						ShockSolution.Branch.WEAK, AIR).attachment());
	}

	private static GasState state(double mach) {
		double pressurePa = 101_325;
		double temperatureK = 288.15;
		double density = pressurePa / (AIR.gasConstant() * temperatureK);
		return new GasState(mach, pressurePa, temperatureK, density,
				mach * AIR.speedOfSound(temperatureK));
	}
}

package info.openrocket.core.aerodynamics.physicsaero.gasdynamics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import info.openrocket.core.aerodynamics.physicsaero.flow.GasState;

/**
 * NACA Report 1135, Table I normal-shock values for gamma=1.4.
 *
 * <p>Adapted from AidanSYu/openrocketsupersonic,
 * {@code supersonic-aero-dev}.</p>
 */
class NormalShockNaca1135TableTest {
	private static final PerfectGasAir AIR = new PerfectGasAir();
	private static final double RELATIVE_TOLERANCE = 1.0e-3;

	@ParameterizedTest(name = "NACA 1135 normal shock M1={0}")
	@CsvSource({
			"1.1, 0.91177, 1.24500, 1.16908, 1.06494, 0.99893",
			"1.2, 0.84217, 1.51333, 1.34161, 1.12799, 0.99280",
			"1.5, 0.70109, 2.45833, 1.86207, 1.32022, 0.92979",
			"2.0, 0.57735, 4.50000, 2.66667, 1.68750, 0.72088",
			"2.5, 0.51299, 7.12500, 3.33333, 2.13750, 0.49901",
			"3.0, 0.47519, 10.3333, 3.85714, 2.67901, 0.32834",
			"4.0, 0.43496, 18.5000, 4.57143, 4.04688, 0.13876",
			"5.0, 0.41523, 29.0000, 5.00000, 5.80000, 0.06172",
			"10.0, 0.38757, 116.500, 5.71429, 20.3875, 0.00304"
	})
	void matchesTableI(double upstreamMach, double downstreamMach,
			double pressureRatio, double densityRatio, double temperatureRatio,
			double totalPressureRatio) {
		ShockSolution solution = NormalShockCalculator.solve(state(upstreamMach), AIR);

		assertRelative(downstreamMach, solution.downstream().mach());
		assertRelative(pressureRatio,
				solution.downstream().pressurePa() / solution.upstream().pressurePa());
		assertRelative(densityRatio,
				solution.downstream().densityKgM3() / solution.upstream().densityKgM3());
		assertRelative(temperatureRatio,
				solution.downstream().temperatureK() / solution.upstream().temperatureK());
		assertEquals(totalPressureRatio,
				solution.downstreamTotal().pressurePa() / solution.upstreamTotal().pressurePa(),
				Math.max(1.0e-5, totalPressureRatio * RELATIVE_TOLERANCE));
		assertTrue(solution.downstream().mach() < 1);
	}

	private static void assertRelative(double expected, double actual) {
		assertEquals(expected, actual, Math.abs(expected) * RELATIVE_TOLERANCE);
	}

	private static GasState state(double mach) {
		double pressurePa = 101_325;
		double temperatureK = 288.15;
		double density = pressurePa / (AIR.gasConstant() * temperatureK);
		return new GasState(mach, pressurePa, temperatureK, density,
				mach * AIR.speedOfSound(temperatureK));
	}
}

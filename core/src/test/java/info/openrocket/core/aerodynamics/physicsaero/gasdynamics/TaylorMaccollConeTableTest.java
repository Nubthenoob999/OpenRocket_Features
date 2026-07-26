package info.openrocket.core.aerodynamics.physicsaero.gasdynamics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.CsvSource;

import info.openrocket.core.aerodynamics.physicsaero.flow.GasState;

/**
 * Sharp-cone checks against NACA Report 1135-class Taylor-Maccoll charts and
 * the NASA Glenn Mach 2.35 ten-degree-cone CFD anchor.
 *
 * <p>Chart cases are adapted from AidanSYu/openrocketsupersonic,
 * {@code supersonic-aero-dev}.  The DTIC data is kept as a separate total-drag
 * consistency check because it includes friction and base drag and therefore
 * is not a direct wall-pressure reference.</p>
 */
class TaylorMaccollConeTableTest {
	private static final PerfectGasAir AIR = new PerfectGasAir();
	private final TaylorMaccollSolver solver = new TaylorMaccollSolver();

	@ParameterizedTest(name = "Taylor-Maccoll M={0}, cone={1} degrees")
	@CsvSource({
			"2.0, 10.0, 31.1",
			"2.0, 20.0, 38.0",
			"3.0, 10.0, 21.8",
			"3.0, 20.0, 29.7",
			"3.0, 25.0, 34.3",
			"5.0, 10.0, 15.5",
			"5.0, 20.0, 25.1",
			"5.0, 30.0, 35.9"
	})
	void shockAngleMatchesConeChart(double mach, double coneDegrees,
			double expectedShockDegrees) {
		TaylorMaccollSolution solution = solver.solve(
				state(mach), Math.toRadians(coneDegrees), AIR);

		assertTrue(solution.attached());
		assertEquals(expectedShockDegrees, Math.toDegrees(solution.shockAngleRad()), 0.30);
		assertTrue(solution.wallPressureCoefficient() > 0);
		assertTrue(solution.wallTangencyResidual() < 1.0e-7);
	}

	@ParameterizedTest
	@CsvSource({"2.35, 10.0, 26.73672, 2.1469, 1.3741"})
	void surfaceStateMatchesNasaGlennCfdAnchor(double mach, double coneDegrees,
			double shockDegrees, double wallMach, double wallPressureRatio) {
		TaylorMaccollSolution solution = solver.solve(
				state(mach), Math.toRadians(coneDegrees), AIR);
		double expectedCp = (wallPressureRatio - 1)
				/ (0.5 * 1.4 * mach * mach);

		assertEquals(shockDegrees, Math.toDegrees(solution.shockAngleRad()), 0.01);
		assertEquals(wallMach, solution.wallState().mach(), 0.011);
		assertEquals(wallPressureRatio,
				solution.wallState().pressurePa() / 101_325, 0.005 * wallPressureRatio);
		assertEquals(expectedCp, solution.wallPressureCoefficient(), 0.005 * expectedCp);
	}

	/**
	 * DTIC AD0487365 reports total drag on slightly blunt cones.  The
	 * Taylor-Maccoll pressure term should form a substantial, but not complete,
	 * fraction of that measurement.
	 */
	@Tag("benchmark")
	@ParameterizedTest(name = "DTIC cone theta={0}, M={2}")
	@CsvFileSource(resources = "/physicsaero/dtic_ad0487365_cone_drag_alpha0.csv",
			numLinesToSkip = 1)
	void pressureTermIsConsistentWithDticTotalDrag(double coneDegrees, double bluntness,
			double mach, double reynoldsLength, double wallToTotalTemperature,
			double measuredTotalCd, double laminarPredictionCd) {
		TaylorMaccollSolution solution = solver.solve(
				state(mach), Math.toRadians(coneDegrees), AIR);
		double pressureCd = solution.wallPressureCoefficient();

		assertTrue(pressureCd < measuredTotalCd,
				"pressure-only drag must remain below measured total drag");
		assertTrue(pressureCd > 0.40 * measuredTotalCd,
				"pressure drag should remain a substantial share of total drag");
	}

	private static GasState state(double mach) {
		double pressurePa = 101_325;
		double temperatureK = 288.15;
		double density = pressurePa / (AIR.gasConstant() * temperatureK);
		return new GasState(mach, pressurePa, temperatureK, density,
				mach * AIR.speedOfSound(temperatureK));
	}
}

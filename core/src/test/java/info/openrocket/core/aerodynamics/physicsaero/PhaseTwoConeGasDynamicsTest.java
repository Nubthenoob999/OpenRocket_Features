package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.physicsaero.flow.GasState;
import info.openrocket.core.aerodynamics.physicsaero.flow.SurfaceState;
import info.openrocket.core.aerodynamics.physicsaero.flow.TotalState;
import info.openrocket.core.aerodynamics.physicsaero.body.TangentConePressureModel;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.ExpansionSolution;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.IsentropicRelations;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.ModifiedNewtonianPressure;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.PerfectGasAir;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.PrandtlMeyerCalculator;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.ShockSolution;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.TaylorMaccollSolution;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.TaylorMaccollSolver;

/** Independent published-value and physical-invariant checks for the Phase II cone methods. */
class PhaseTwoConeGasDynamicsTest {
	private static final PerfectGasAir AIR = new PerfectGasAir();
	private static final double PRESSURE_PA = 101_325.0;
	private static final double TEMPERATURE_K = 288.15;

	private static GasState state(double mach) {
		double density = PRESSURE_PA / (AIR.gasConstant() * TEMPERATURE_K);
		return new GasState(mach, PRESSURE_PA, TEMPERATURE_K, density,
				mach * AIR.speedOfSound(TEMPERATURE_K));
	}

	@Test
	void taylorMaccollMatchesNasaMach235TenDegreeConeCase() {
		// NASA Glenn cone10 validation: all four grid-converged CFD solutions report
		// wall M=2.1467..2.1469 and wall p/p_inf=1.3740..1.3741. The page's separate
		// HAP row reports thermodynamically inconsistent p/T and beta values, so beta
		// below is from an independent fixed-step RK4 integration of the published ODE.
		TaylorMaccollSolution solution = new TaylorMaccollSolver().solve(
				state(2.35), Math.toRadians(10.0), AIR);

		assertTrue(solution.attached());
		assertEquals(26.736717719, Math.toDegrees(solution.shockAngleRad()), 0.01);
		assertEquals(2.1469, solution.wallState().mach(), 0.011);
		assertEquals(1.3741, solution.wallState().pressurePa() / PRESSURE_PA, 0.005 * 1.3741);
		double referenceCp = (1.3741 - 1.0) / (0.5 * 1.4 * 2.35 * 2.35);
		assertEquals(referenceCp, solution.wallPressureCoefficient(), 0.005 * referenceCp);
		assertEquals(TaylorMaccollSolution.METHOD_ID, solution.methodId());
	}

	@Test
	void attachedConeSolutionsRespectShockAndThermodynamicInvariants() {
		TaylorMaccollSolver solver = new TaylorMaccollSolver();
		double[][] cases = {{1.8, 5.0}, {2.0, 10.0}, {3.0, 15.0}, {5.0, 20.0}};
		for (double[] testCase : cases) {
			GasState freestream = state(testCase[0]);
			double coneAngle = Math.toRadians(testCase[1]);
			TaylorMaccollSolution solution = solver.solve(freestream, coneAngle, AIR);

			assertTrue(solution.attached(), "expected attached case " + testCase[0] + "/" + testCase[1]);
			assertTrue(solution.shockAngleRad() > Math.asin(1.0 / freestream.mach()));
			assertTrue(solution.shockAngleRad() > coneAngle);
			assertTrue(solution.shockAngleRad() < Math.PI / 2.0);
			assertNotNull(solution.postShockState());
			assertNotNull(solution.wallState());
			assertTrue(solution.postShockState().pressurePa() > freestream.pressurePa());
			assertTrue(solution.wallState().pressurePa() > solution.postShockState().pressurePa());
			assertTrue(solution.wallState().mach() < solution.postShockState().mach());
			assertTrue(solution.totalPressureRatio() > 0.0 && solution.totalPressureRatio() <= 1.0);
			assertTrue(solution.wallPressureCoefficient() > 0.0);
			assertTrue(solution.wallTangencyResidual() < 1.0e-7);
			assertTrue(solution.odeSteps() > 0);
		}
	}

	@Test
	void overTurningIsExplicitlyDetachedAndCarriesNoFabricatedWallState() {
		TaylorMaccollSolution solution = new TaylorMaccollSolver().solve(
				state(2.0), Math.toRadians(60.0), AIR);

		assertFalse(solution.attached());
		assertEquals(ShockSolution.Attachment.DETACHED, solution.attachment());
		assertTrue(Double.isNaN(solution.shockAngleRad()));
		assertTrue(Double.isNaN(solution.wallPressureCoefficient()));
		assertNull(solution.wallState());
	}

	@Test
	void modifiedNewtonianUsesRayleighPitotMaximumAndCorrectAngleConvention() {
		GasState machTwo = state(2.0);
		ModifiedNewtonianPressure model = new ModifiedNewtonianPressure();
		double cpMax = model.stagnationPressureCoefficient(machTwo, AIR);

		// Independent perfect-gas Rayleigh-Pitot result for gamma=1.4, M=2.
		assertEquals(1.6573002902940421, cpMax, 1.0e-12);
		assertEquals(0.0, model.pressureCoefficient(machTwo, 0.0, AIR), 0.0);
		assertEquals(cpMax,
				model.pressureCoefficient(machTwo, Math.PI / 2.0, AIR), 1.0e-12);
		assertEquals(0.25 * cpMax,
				model.pressureCoefficient(machTwo, Math.PI / 6.0, AIR), 1.0e-12);
	}

	@Test
	void tangentConeExactlyRecoversTrueConePressureWithoutInventingEntropyLoss() {
		GasState freestream = state(3.0);
		TotalState freestreamTotal = IsentropicRelations.totalState(freestream, AIR);
		SurfaceState upstream = SurfaceState.of(0.0, 0.0, freestream, freestreamTotal,
				0.0, 0.0, AIR, "FREESTREAM_TEST_STATE");
		double coneAngle = Math.toRadians(15.0);

		TaylorMaccollSolution exact = new TaylorMaccollSolver().solve(freestream, coneAngle, AIR);
		SurfaceState tangentCone = new TangentConePressureModel().evaluate(
				upstream, 1.0, Math.tan(coneAngle), coneAngle, AIR);

		assertTrue(exact.attached());
		assertEquals(exact.wallPressureCoefficient(), tangentCone.pressureCoefficient(), 1.0e-13);
		assertEquals(exact.wallState().pressurePa(), tangentCone.staticState().pressurePa(), 1.0e-7);
		assertEquals(freestreamTotal.pressurePa(), tangentCone.totalState().pressurePa(), 0.0);
		assertEquals(freestreamTotal.temperatureK(), tangentCone.totalState().temperatureK(), 0.0);
		assertEquals(TangentConePressureModel.METHOD_ID, tangentCone.methodId());
	}

	@Test
	void tenDegreeExpansionMatchesReferenceStateAndConservesTotalConditions() {
		GasState upstream = state(2.0);
		TotalState upstreamTotal = IsentropicRelations.totalState(upstream, AIR);
		ExpansionSolution solution = PrandtlMeyerCalculator.expand(upstream, Math.toRadians(10.0), AIR);

		// Independent gamma=1.4 Prandtl-Meyer inversion: nu(2)+10 deg.
		assertEquals(2.384887154593069, solution.downstream().mach(), 2.0e-9);
		assertEquals(0.5479687312769057,
				solution.downstream().pressurePa() / upstream.pressurePa(), 2.0e-9);
		assertEquals(upstreamTotal.pressurePa(), solution.conservedTotalState().pressurePa(), 1.0e-7);
		assertEquals(upstreamTotal.temperatureK(), solution.conservedTotalState().temperatureK(), 1.0e-10);
		assertTrue(solution.downstream().pressurePa() < upstream.pressurePa());
		assertTrue(solution.residual() < 1.0e-9);
	}
}

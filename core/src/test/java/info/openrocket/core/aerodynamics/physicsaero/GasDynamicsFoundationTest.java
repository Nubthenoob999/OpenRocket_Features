package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import info.openrocket.core.aerodynamics.physicsaero.flow.*;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.*;

class GasDynamicsFoundationTest {
	private static final PerfectGasAir AIR = new PerfectGasAir();
	private static GasState state(double mach) { double t = 288.15, p = 101325, rho = p / (AIR.gasConstant() * t); return new GasState(mach, p, t, rho, mach * AIR.speedOfSound(t)); }

	@Test void isentropicRoundTripsAtReferenceMachNumbers() {
		for (double mach : new double[] {0, 1, 2, 5}) {
			GasState initial = state(mach); GasState recovered = IsentropicRelations.staticState(IsentropicRelations.totalState(initial, AIR), mach, AIR);
			assertEquals(initial.pressurePa(), recovered.pressurePa(), 1e-8); assertEquals(initial.temperatureK(), recovered.temperatureK(), 1e-10);
		}
	}
	@Test void normalShockMachTwoMatchesNasaPerfectGasReference() {
		ShockSolution solution = NormalShockCalculator.solve(state(2), AIR);
		assertEquals(0.5773502692, solution.downstream().mach(), 1e-9);
		assertEquals(4.5, solution.downstream().pressurePa() / solution.upstream().pressurePa(), 1e-12);
		assertEquals(2.6666666667, solution.downstream().densityKgM3() / solution.upstream().densityKgM3(), 1e-9);
		assertTrue(solution.downstreamTotal().pressurePa() < solution.upstreamTotal().pressurePa());
	}
	@Test void obliqueShockHasWeakAndStrongRootsAndDetachment() {
		double turn = Math.toRadians(10); ShockSolution weak = ObliqueShockCalculator.solve(state(2), turn, ShockSolution.Branch.WEAK, AIR);
		ShockSolution strong = ObliqueShockCalculator.solve(state(2), turn, ShockSolution.Branch.STRONG, AIR);
		assertEquals(Math.toRadians(39.3139), weak.shockAngleRad(), 2e-5); assertTrue(strong.shockAngleRad() > weak.shockAngleRad());
		assertEquals(ShockSolution.Attachment.DETACHED, ObliqueShockCalculator.solve(state(2), Math.toRadians(30), ShockSolution.Branch.WEAK, AIR).attachment());
	}
	@Test void prandtlMeyerExpansionPreservesTotalStateAndIsMonotone() {
		assertTrue(PrandtlMeyerCalculator.angle(3, 1.4) > PrandtlMeyerCalculator.angle(2, 1.4));
		ExpansionSolution expansion = PrandtlMeyerCalculator.expand(state(2), Math.toRadians(10), AIR);
		assertEquals(IsentropicRelations.totalState(state(2), AIR).pressurePa(), expansion.conservedTotalState().pressurePa(), 1e-7);
		assertTrue(expansion.downstream().mach() > 2); assertTrue(expansion.downstream().pressurePa() > 0);
	}
	@Test void invalidInputsDoNotReturnNan() { assertThrows(GasDynamicsException.class, () -> NormalShockCalculator.solve(state(0.8), AIR)); assertThrows(GasDynamicsException.class, () -> PrandtlMeyerCalculator.angle(1, 1.4)); }
}

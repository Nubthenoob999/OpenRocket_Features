package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import info.openrocket.core.aerodynamics.physicsaero.flow.*;
import info.openrocket.core.aerodynamics.physicsaero.force.*;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.PerfectGasAir;
import info.openrocket.core.util.Coordinate;

class AxisConventionTest {
	private static final PerfectGasAir AIR = new PerfectGasAir();
	private static final AtmosphereState ATMOSPHERE = new AtmosphereState(101325, 288.15,
			101325 / (AIR.gasConstant() * 288.15), AIR.viscosity(288.15));
	@Test void alphaBetaAndCombinedVelocityVectorsShareOneConvention() {
		for (double[] angles : new double[][] {{0, 0}, {0.1, 0}, {0, -0.05}, {0.1, -0.05}}) {
			FlowCondition flow = FlowCondition.fromAngles(2, angles[0], angles[1], ATMOSPHERE, AIR, false, "axis-test");
			assertEquals(angles[0], flow.alphaRad(), 0); assertEquals(angles[1], flow.betaRad(), 0); assertEquals(1, flow.velocityUnitBody().length(), 1e-15);
		}
	}
	@Test void inconsistentAngleAndVelocityAreRejected() {
		FlowCondition valid = FlowCondition.fromAngles(2, 0, 0, ATMOSPHERE, AIR, false, "axis-test");
		assertThrows(IllegalArgumentException.class, () -> new FlowCondition(2, 0.1, 0, valid.velocityBody(), valid.velocityUnitBody(), ATMOSPHERE, AIR, false, "bad"));
	}
	@Test void openRocketBoundaryMapsDragAndAllMomentAxesExplicitly() {
		ReferenceState reference = new ReferenceState(10, 2, 4, new Coordinate());
		AerodynamicCoefficients c = OpenRocketAxisAdapter.coefficients(new Coordinate(20, 6, 8), new Coordinate(80, 40, -40), reference);
		assertEquals(new AerodynamicCoefficients(1, 0.4, 0.3, 1, 0.5, -0.5), c);
		assertEquals(c.ca(), OpenRocketAxisAdapter.toOpenRocket(c).getCDaxial()); assertEquals(c.cYaw(), OpenRocketAxisAdapter.toOpenRocket(c).getCyaw());
	}
}

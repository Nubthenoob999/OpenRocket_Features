package info.openrocket.core.aerodynamics.rom.bl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class HeadTurbulentTest {

	@Test
	public void mildAdverseGradientRemainsStable() {
		BoundaryLayerState state = new BoundaryLayerState(1.2e-3, 1.45, true, false, 0.003, 0.0, true);
		double station = 0.0;
		double stepSize = 0.005;
		double edgeVelocity = 120.0;
		double velocityGradient = -15.0;
		double kinematicViscosity = 1.5e-5;

		for (int i = 0; i < 100; i++) {
			double localVelocity = edgeVelocity + velocityGradient * station;
			state = HeadTurbulent.advance(state, station, localVelocity, velocityGradient,
					kinematicViscosity, stepSize);
			station += stepSize;

			assertTrue(state.isValid());
			assertTrue(state.isTransitioned());
			assertFalse(state.isSeparated());
			assertTrue(state.getMomentumThickness() > 0.0);
			assertTrue(state.getShapeFactor() >= 1.35);
			assertTrue(state.getShapeFactor() < 2.8);
		}

		assertTrue(state.getMomentumThickness() > 1.2e-3);
	}
}

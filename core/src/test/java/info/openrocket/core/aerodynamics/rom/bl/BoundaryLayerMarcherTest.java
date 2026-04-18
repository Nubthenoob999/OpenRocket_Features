package info.openrocket.core.aerodynamics.rom.bl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import info.openrocket.core.aerodynamics.rom.flow.EdgeState;
import info.openrocket.core.aerodynamics.rom.flow.FlowState;

import org.junit.jupiter.api.Test;

public class BoundaryLayerMarcherTest {

	private final BoundaryLayerMarcher marcher = new BoundaryLayerMarcher();

	@Test
	public void laminarFlatPlateTrendProducesThickerLayerWithDistance() {
		FlowState flowState = flowState(20.0, 0.06);
		EdgeState edgeState = edgeState(20.0, 0.06, 0.0);

		BoundaryLayerState shortPath = marcher.march(flowState, edgeState, 0.10, 1.0);
		BoundaryLayerState longPath = marcher.march(flowState, edgeState, 0.25, 1.0);

		assertTrue(shortPath.isValid());
		assertTrue(longPath.isValid());
		assertFalse(shortPath.isTransitioned());
		assertFalse(longPath.isTransitioned());
		assertFalse(longPath.isSeparated());
		assertTrue(longPath.getMomentumThickness() > shortPath.getMomentumThickness());
		assertTrue(longPath.getShapeFactor() > 2.1);
		assertTrue(longPath.getSkinFrictionCoefficient() > 0.0);
	}

	@Test
	public void transitionToggleLatchesOnceThresholdIsPassed() {
		FlowState flowState = flowState(200.0, 0.58);
		EdgeState edgeState = edgeState(200.0, 0.58, 0.0);

		BoundaryLayerState beforeTransition = marcher.march(flowState, edgeState, 0.10, 1.0);
		BoundaryLayerState atTransition = marcher.march(flowState, edgeState, 0.20, 1.0);
		BoundaryLayerState afterTransition = marcher.march(flowState, edgeState, 0.40, 1.0);

		assertTrue(beforeTransition.isValid());
		assertFalse(beforeTransition.isTransitioned());
		assertTrue(atTransition.isTransitioned());
		assertTrue(afterTransition.isTransitioned());
		assertTrue(afterTransition.getMomentumThickness() >= atTransition.getMomentumThickness());
	}

	private static FlowState flowState(double velocity, double mach) {
		double density = 1.225;
		double viscosity = 1.7894e-5;
		double dynamicPressure = 0.5 * density * velocity * velocity;
		return new FlowState(mach, velocity / (viscosity / density), dynamicPressure, 101325.0,
				288.15, density, 340.3, viscosity, velocity,
				0.0, 0.0, 0.0, 0.0, false, 0.0, 1.0);
	}

	private static EdgeState edgeState(double edgeVelocity, double edgeMach, double velocityGradient) {
		return new EdgeState(edgeVelocity, edgeMach, 0.0, 0.0, velocityGradient, true);
	}
}

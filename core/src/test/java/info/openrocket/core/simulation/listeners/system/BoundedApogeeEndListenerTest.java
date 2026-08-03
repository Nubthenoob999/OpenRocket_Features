package info.openrocket.core.simulation.listeners.system;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BoundedApogeeEndListenerTest {
	@Test
	void slowLiftoffDoesNotArmTheApogeeStop() {
		assertFalse(BoundedApogeeEndListener.shouldArm(false, 20));
		assertFalse(BoundedApogeeEndListener.shouldArm(true, 1.1));
		assertFalse(BoundedApogeeEndListener.shouldArm(true, 10.0));
		assertTrue(BoundedApogeeEndListener.shouldArm(true, 10.01));
	}
}

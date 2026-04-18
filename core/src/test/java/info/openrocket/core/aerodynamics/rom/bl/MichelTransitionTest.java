package info.openrocket.core.aerodynamics.rom.bl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class MichelTransitionTest {

	@Test
	public void requiresSufficientMomentumThicknessReynoldsNumber() {
		assertFalse(MichelTransition.shouldTransition(2.0e6, 700.0));
		assertTrue(MichelTransition.shouldTransition(2.0e6, 1200.0));
	}
}

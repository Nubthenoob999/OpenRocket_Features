package info.openrocket.core.aerodynamics.physicsaero.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FullRegimeTableBuilderParallelismTest {
	@Test
	void recommendedWorkerPoolUsesAboutHalfOfLogicalProcessors() {
		assertEquals(1, FullRegimeTableBuilder.recommendedWorkerCount(1));
		assertEquals(2, FullRegimeTableBuilder.recommendedWorkerCount(4));
		assertEquals(6, FullRegimeTableBuilder.recommendedWorkerCount(10));
		assertEquals(7, FullRegimeTableBuilder.recommendedWorkerCount(12));

		for (int available = 4; available <= 64; available++) {
			double share = (double) FullRegimeTableBuilder.recommendedWorkerCount(available)
					/ available;
			assertTrue(share >= 0.50 && share <= 0.60,
					"worker share outside target for " + available + " processors: " + share);
		}
	}
}

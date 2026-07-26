package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BarrowmanBaseWakeCorrectionTest {
	@Test
	void finnedBaseAugmentationIsGeometryAndMachBounded() {
		assertEquals(1, BarrowmanDragCalculator.finnedBaseAugmentationFactor(0, 1, 2));
		assertEquals(1, BarrowmanDragCalculator.finnedBaseAugmentationFactor(4, 1, 0.1));
		assertEquals(1.55, BarrowmanDragCalculator.finnedBaseAugmentationFactor(4, 1, 2), 1.0e-12);
		double threeFin = BarrowmanDragCalculator.finnedBaseAugmentationFactor(3, 1, 2);
		assertEquals(1, threeFin);
		assertTrue(BarrowmanDragCalculator.finnedBaseAugmentationFactor(4, 0.4, 2) < 1.55);
		assertTrue(BarrowmanDragCalculator.finnedBaseAugmentationFactor(4, 1, 4)
				< BarrowmanDragCalculator.finnedBaseAugmentationFactor(4, 1, 2));
	}

	@Test
	void boattailFactorReducesOnlyAttachedModerateAngleWake() {
		assertEquals(1, BarrowmanDragCalculator.calculateBoattailFactor(0.05, 0.05, 0.2, 2));
		double attached = BarrowmanDragCalculator.calculateBoattailFactor(0.05, 0.04, 0.2, 2);
		assertTrue(attached < 1 && attached >= 0.3);
		assertEquals(1, BarrowmanDragCalculator.calculateBoattailFactor(0.05, 0.01, 0.02, 2));
	}
}

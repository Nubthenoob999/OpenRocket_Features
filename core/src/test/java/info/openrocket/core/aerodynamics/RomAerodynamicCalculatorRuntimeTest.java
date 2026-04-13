package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RomAerodynamicCalculatorRuntimeTest {

	@Test
	void stabilizeDragCoefficientFallsBackToValidPositiveValues() {
		assertEquals(0.31, RomAerodynamicCalculator.stabilizeDragCoefficient(Double.NaN, 0.31, 0.27), 1e-12);
		assertEquals(0.27, RomAerodynamicCalculator.stabilizeDragCoefficient(Double.NaN, Double.NaN, 0.27), 1e-12);
		assertTrue(RomAerodynamicCalculator.stabilizeDragCoefficient(Double.NaN, Double.NaN, Double.NaN) > 0.0);
	}

	@Test
	void stabilizeAxialDragPreservesDirectionWhenOldCdIsUnavailable() {
		double stalled = RomAerodynamicCalculator.stabilizeAxialDrag(-0.18, 0.0, 0.42);
		assertEquals(-0.42, stalled, 1e-12);

		double scaled = RomAerodynamicCalculator.stabilizeAxialDrag(-0.18, 0.60, 0.30);
		assertEquals(-0.09, scaled, 1e-12);
	}
}

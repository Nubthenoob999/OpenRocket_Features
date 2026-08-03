package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.physicsaero.body.RasaeroSupersonicTransitionDragCorrelation;

class RasaeroSupersonicTransitionDragCorrelationTest {
	private final RasaeroSupersonicTransitionDragCorrelation correlation =
			new RasaeroSupersonicTransitionDragCorrelation();

	@Test
	void matchesRasaeroMachThreeFinCanExpansionExports() {
		assertEquals(0.01591918, expansion(3.9173, 4.15, 0.5, 4.15), 5e-8);
		assertEquals(0.01452094, expansion(8.0, 8.375, 0.75, 8.375), 5e-8);
	}

	@Test
	void matchesRasaeroMachThreeTerminalBoattailExports() {
		assertEquals(0.02289582, reducer(8.375, 6.7, 1.1, 8.375), 5e-8);
		assertEquals(0.02808633, reducer(6.0, 5.0, 1.0, 6.0), 5e-8);
	}

	private double expansion(double smallerDiameterIn, double largerDiameterIn,
			double lengthIn, double referenceDiameterIn) {
		return correlation.expansionDragCoefficient(smallerDiameterIn / 2,
				largerDiameterIn / 2, lengthIn, 3,
				Math.PI * Math.pow(referenceDiameterIn / 2, 2));
	}

	private double reducer(double foreDiameterIn, double aftDiameterIn,
			double lengthIn, double referenceDiameterIn) {
		return correlation.terminalReducerDragCoefficient(foreDiameterIn / 2,
				aftDiameterIn / 2, lengthIn, 3,
				Math.PI * Math.pow(referenceDiameterIn / 2, 2));
	}
}

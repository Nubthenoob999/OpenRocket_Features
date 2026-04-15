package info.openrocket.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class EjectionChargeCalculatorTest {

	@Test
	public void testImperialReferenceCase() {
		double diameterInches = 6.0;
		double lengthInches = 9.0;
		double pressurePsi = 10.0;

		double diameterMeters = diameterInches * 0.0254;
		double lengthMeters = lengthInches * 0.0254;
		double volumeM3 = EjectionChargeCalculator.calculateVolume(diameterMeters, lengthMeters);
		double volumeCubicIn = EjectionChargeCalculator.cubicMetersToInches(volumeM3);

		double bpGrams = EjectionChargeCalculator.calculateBPMassGramsImperial(pressurePsi, volumeCubicIn);
		assertEquals(9.57, bpGrams, 0.05);
	}

	@Test
	public void testMetricAndImperialMethodsAreEquivalent() {
		double pressurePsi = 12.5;
		double pressurePa = EjectionChargeCalculator.psiToPascals(pressurePsi);
		double volumeM3 = 0.00235;

		double metric = EjectionChargeCalculator.calculateBPMassGrams(pressurePa, volumeM3);
		double imperial = EjectionChargeCalculator.calculateBPMassGramsImperial(
				pressurePsi,
				EjectionChargeCalculator.cubicMetersToInches(volumeM3));

		assertEquals(imperial, metric, 1.0e-6);
	}
}

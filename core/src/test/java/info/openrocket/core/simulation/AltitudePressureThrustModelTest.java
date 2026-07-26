package info.openrocket.core.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AltitudePressureThrustModelTest {
	@Test
	void restoresReferenceMinusAmbientPressureAcrossExplicitExitArea() {
		double diameterM = 0.10;
		double ambientPressurePa = 25_000;
		double expected = 3 * (101_325 - ambientPressurePa)
				* Math.PI * diameterM * diameterM / 4;

		assertEquals(expected, AltitudePressureThrustModel.correction(
				101_325, ambientPressurePa, diameterM, 3), 1e-12);
		assertEquals(0, AltitudePressureThrustModel.correction(
				101_325, 101_325, diameterM, 3), 0);
		assertEquals(0, AltitudePressureThrustModel.correction(
				101_325, 110_000, diameterM, 3), 0);
	}

	@Test
	void missingOrInvalidNozzleProvenanceDisablesCorrection() {
		assertEquals(0, AltitudePressureThrustModel.correction(
				101_325, 25_000, Double.NaN, 1), 0);
		assertEquals(0, AltitudePressureThrustModel.correction(
				101_325, 25_000, 0, 1), 0);
		assertEquals(0, AltitudePressureThrustModel.correction(
				101_325, 25_000, 0.10, 0), 0);
	}
}

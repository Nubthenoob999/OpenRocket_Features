package info.openrocket.core.aerodynamics.physicsaero.gasdynamics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * NACA Report 1135, Table III Prandtl-Meyer values for gamma=1.4.
 *
 * <p>Adapted from AidanSYu/openrocketsupersonic,
 * {@code supersonic-aero-dev}.</p>
 */
class PrandtlMeyerNaca1135TableTest {
	@ParameterizedTest(name = "NACA 1135 nu({0})={1} degrees")
	@CsvSource({
			"1.5, 11.9052",
			"2.0, 26.3798",
			"2.5, 39.1236",
			"3.0, 49.7573",
			"4.0, 65.7848",
			"5.0, 76.9202",
			"10.0, 102.3121"
	})
	void angleMatchesTableIII(double mach, double expectedDegrees) {
		assertEquals(expectedDegrees,
				Math.toDegrees(PrandtlMeyerCalculator.angle(mach, 1.4)),
				Math.max(1.0e-4, expectedDegrees * 1.0e-3));
	}

	@ParameterizedTest
	@ValueSource(doubles = {1.0, 1.1, 1.5, 2, 3, 5, 8, 10, 20})
	void inverseRoundTrip(double mach) {
		double angle = mach == 1 ? 0 : PrandtlMeyerCalculator.angle(mach, 1.4);
		assertEquals(mach, PrandtlMeyerCalculator.inverse(angle, 1.4),
				Math.max(1.0e-10, mach * 1.0e-8));
	}
}

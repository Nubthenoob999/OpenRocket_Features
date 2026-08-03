package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class RocketFlightDatabaseComparisonMetricsTest {
	@Test
	void reportsPressureWeightedPreApogeeAoAWithoutLowSpeedApogeeArtifact() {
		RocketFlightDatabaseComparisonTest.AoADiagnostics diagnostics =
				RocketFlightDatabaseComparisonTest.preApogeeAoADiagnostics(
						List.of(0.0, 1.0, 2.0, 3.0),
						List.of(0.0, 100.0, 200.0, 150.0),
						List.of(0.0, Math.toRadians(10.0), Math.toRadians(20.0),
								Math.toRadians(90.0)),
						List.of(20.0, 20.0, 20.0, 0.1),
						List.of(1.0, 1.0, 1.0, 1.0));

		assertEquals(20.0, diagnostics.rawMaximumDegrees(), 1.0e-12);
		assertEquals(20.0, diagnostics.maximumAtOrAbove100PaDegrees(), 1.0e-12);
		assertEquals(Math.sqrt(150.0),
				diagnostics.dynamicPressureTimeWeightedRmsDegrees(), 1.0e-12);
	}

	@Test
	void retainsDocumentedSoundingRocketLaunchConditions() {
		RocketFlightDatabaseComparisonTest.SoundingRocketLaunchConditions blackBrant =
				RocketFlightDatabaseComparisonTest.soundingRocketLaunchConditions(26);
		assertEquals(5.0, Math.toDegrees(blackBrant.launchAngleRad()), 1.0e-12);
		assertEquals(30.0, blackBrant.launchAltitudeM(), 1.0e-12);

		for (int flightId : List.of(27, 28)) {
			RocketFlightDatabaseComparisonTest.SoundingRocketLaunchConditions nikeDeacon =
					RocketFlightDatabaseComparisonTest.soundingRocketLaunchConditions(flightId);
			assertEquals(15.0, Math.toDegrees(nikeDeacon.launchAngleRad()), 1.0e-12);
			assertEquals(0.0, nikeDeacon.launchAltitudeM(), 1.0e-12);
		}
		assertNull(RocketFlightDatabaseComparisonTest.soundingRocketLaunchConditions(1));
	}

	@Test
	void retainsCompanionCorpusDeterministicAscentIntegrationSettings() {
		RocketFlightDatabaseComparisonTest.DeterministicAscentSettings blackBrant =
				RocketFlightDatabaseComparisonTest.deterministicAscentSettings(26);
		assertEquals(0.02, blackBrant.timeStepS(), 1.0e-12);
		assertEquals(3.0, blackBrant.maximumStepAngleDeg(), 1.0e-12);
		assertEquals(900.0, blackBrant.maximumSimulationTimeS(), 1.0e-12);

		for (int flightId : List.of(27, 28)) {
			RocketFlightDatabaseComparisonTest.DeterministicAscentSettings nikeDeacon =
					RocketFlightDatabaseComparisonTest.deterministicAscentSettings(flightId);
			assertEquals(0.05, nikeDeacon.timeStepS(), 1.0e-12);
			assertEquals(5.0, nikeDeacon.maximumStepAngleDeg(), 1.0e-12);
			assertEquals(320.0, nikeDeacon.maximumSimulationTimeS(), 1.0e-12);
		}
	}

	@Test
	void usesBarometricPressureAltitudeOnlyForBarometricAltimeterFlights() {
		assertTrue(RocketFlightDatabaseComparisonTest.isBarometricAltimeter(
				" Barometric Altimeter "));
		assertFalse(RocketFlightDatabaseComparisonTest.isBarometricAltimeter("GPS"));
		assertFalse(RocketFlightDatabaseComparisonTest.isBarometricAltimeter(null));

		double altitudeM = RocketFlightDatabaseComparisonTest.barometricAltitudeAbovePadM(
				101_325.0, 89_874.76);
		assertEquals(1_000.0, altitudeM, 1.0,
				"standard-atmosphere pressure transfer should retain its unit anchor");
	}
}

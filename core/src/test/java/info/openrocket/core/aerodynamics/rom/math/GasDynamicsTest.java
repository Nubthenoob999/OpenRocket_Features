package info.openrocket.core.aerodynamics.rom.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class GasDynamicsTest {

	@Test
	public void prandtlMeyerKnownPointsAndInverseAreConsistent() {
		assertEquals(0.0, GasDynamics.prandtlMeyerAngle(1.0, 1.4), 1.0e-12);
		assertEquals(0.4604, GasDynamics.prandtlMeyerAngle(2.0, 1.4), 2.0e-4);
		assertEquals(1.1482, GasDynamics.prandtlMeyerAngle(4.0, 1.4), 3.0e-4);
		assertEquals(2.0, GasDynamics.prandtlMeyerMach(0.4604, 1.4), 1.0e-4);
	}

	@Test
	public void shockAndPressureUtilitiesMatchReferenceValues() {
		assertEquals(5.640, GasDynamics.rayleighPitotPressureRatio(2.0, 1.4), 0.01);
		assertEquals(Math.toRadians(45.34), GasDynamics.obliqueShockAngle(2.0, Math.toRadians(15.0), 1.4),
				Math.toRadians(0.15));
		assertEquals(Math.toRadians(22.97), GasDynamics.maxDeflectionAngle(2.0, 1.4), Math.toRadians(0.15));
	}

	@Test
	public void subsonicCorrectionsAndTransportPropertiesBehave() {
		assertEquals(-0.625, GasDynamics.prandtlGlauertCp(-0.5, 0.6), 1.0e-3);
		assertEquals(1.789e-5, GasDynamics.sutherlandViscosity(288.15), 1.0e-7);
		assertTrue(GasDynamics.karmanTsienCp(-0.5, 0.7) < -0.6);
	}
}

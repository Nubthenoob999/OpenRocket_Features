package info.openrocket.core.aerodynamics.rom.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class EckertReferenceTest {

	@Test
	public void adiabaticWallAndReferenceTemperatureFollowExpectedTrend() {
		assertEquals(300.0, EckertReference.adiabaticWallTemp(300.0, 0.0, 1.4, false), 1.0e-12);

		double taw = EckertReference.adiabaticWallTemp(200.0, 2.0, 1.4, true);
		assertEquals(344.0, taw, 3.0);
		assertEquals(280.8, EckertReference.referenceTemperature(200.0, 300.0, 340.0), 0.2);
		assertEquals(200.0 / 280.8, EckertReference.referenceDensity(1.0, 200.0, 280.8), 1.0e-3);
		assertTrue(EckertReference.referenceViscosity(400.0) > EckertReference.referenceViscosity(200.0));
	}
}

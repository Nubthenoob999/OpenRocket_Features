package info.openrocket.core.aerodynamics.rom.bl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class EckertReferenceTemperatureTest {

	@Test
	public void referenceTemperatureIncreasesWithMach() {
		double staticTemperature = 288.15;
		double subsonic = EckertReferenceTemperature.referenceTemperature(staticTemperature, 0.3);
		double supersonic = EckertReferenceTemperature.referenceTemperature(staticTemperature, 2.0);

		assertEquals(staticTemperature, EckertReferenceTemperature.referenceTemperature(staticTemperature, 0.0), 1.0e-12);
		assertTrue(subsonic > staticTemperature);
		assertTrue(supersonic > subsonic);
	}
}

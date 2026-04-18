package info.openrocket.core.aerodynamics.rom.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class WhiteChristophCfTest {

	@Test
	public void incompressibleLimitMatchesAverageFlatPlateOrderOfMagnitude() {
		double edgeTemperature = 288.15;
		double adiabaticWallTemperature = EckertReference.adiabaticWallTemp(edgeTemperature, 0.0, 1.4, true);
		double cf = WhiteChristophCf.whiteChristoph(1.0e6, edgeTemperature, adiabaticWallTemperature,
				adiabaticWallTemperature, 0.0, 1.4);
		assertEquals(0.00293, cf, 1.5e-4);
	}

	@Test
	public void compressibilityReducesSkinFrictionAndCorrelationsStayFinite() {
		double edgeTemperature = 288.15;
		double taw0 = EckertReference.adiabaticWallTemp(edgeTemperature, 0.0, 1.4, true);
		double taw2 = EckertReference.adiabaticWallTemp(edgeTemperature, 2.0, 1.4, true);
		double taw4 = EckertReference.adiabaticWallTemp(edgeTemperature, 4.0, 1.4, true);

		double cf0 = WhiteChristophCf.whiteChristoph(1.0e6, edgeTemperature, taw0, taw0, 0.0, 1.4);
		double cf2 = WhiteChristophCf.whiteChristoph(1.0e6, edgeTemperature, taw2, taw2, 2.0, 1.4);
		double cf4 = WhiteChristophCf.whiteChristoph(1.0e7, edgeTemperature, taw4, taw4, 4.0, 1.4);

		assertTrue(cf2 < cf0);
		assertTrue(cf4 < 0.5 * cf0);
		assertEquals(0.00361, WhiteChristophCf.ludwiegTillmann(2000.0, 1.4), 2.0e-4);
		assertTrue(WhiteChristophCf.karmanSchoenherr(5000.0) > 0.0);
	}
}

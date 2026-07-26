package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.physicsaero.measurement.StandardAtmosphereBarometricAltimeter;

class StandardAtmosphereBarometricAltimeterTest {
	private final StandardAtmosphereBarometricAltimeter model =
			new StandardAtmosphereBarometricAltimeter();

	@Test
	void reproducesStandardAtmosphereLayerAnchors() {
		assertEquals(0, model.pressureAltitudeM(101_325), 1e-9);
		assertEquals(11_000, model.pressureAltitudeM(22_632.06), 0.02);
		assertEquals(20_000, model.pressureAltitudeM(5_474.89), 0.1);
	}

	@Test
	void subtractsAbsolutePressureAltitudesAtPadAndApogee() {
		double padPressure = standardTropospherePressure(2_750 * 0.3048);
		double apogeePressure = standardTropospherePressure(6_750 * 0.3048);

		assertEquals(4_000 * 0.3048,
				model.heightAbovePadM(padPressure, apogeePressure), 0.02);
	}

	@Test
	void hotDayPressureAltitudeUnderReportsGeometricHeight() {
		double padPressure = standardTropospherePressure(2_750 * 0.3048);
		double geometricHeight = 4_000 * 0.3048;
		double launchTemperatureK = 310.9278;
		double lapse = 0.0065;
		double pressureAtApogee = padPressure * Math.pow(
				(launchTemperatureK - lapse * geometricHeight)
						/ launchTemperatureK,
				9.80665 / (287.05287 * lapse));

		double indicated = model.heightAbovePadM(
				padPressure, pressureAtApogee);
		assertTrue(indicated < geometricHeight);
		assertTrue(indicated > 0.85 * geometricHeight);
	}

	private static double standardTropospherePressure(double altitudeM) {
		return 101_325 * Math.pow(
				(288.15 - 0.0065 * altitudeM) / 288.15,
				9.80665 / (287.05287 * 0.0065));
	}
}

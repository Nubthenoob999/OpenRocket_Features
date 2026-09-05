package info.openrocket.core.util.geospatial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Wgs84Test {
	@Test
	void enuRoundTripPreservesLocalCoordinates() {
		Wgs84.GeoPoint origin = new Wgs84.GeoPoint(28.61, -80.60, 3.0);
		Wgs84.EnuPoint expected = new Wgs84.EnuPoint(12_345.0, -7_890.0, 125.0);
		Wgs84.GeoPoint geographic = Wgs84.fromEnu(origin, expected);
		Wgs84.EnuPoint actual = Wgs84.toEnu(origin, geographic);
		assertEquals(expected.eastMeters(), actual.eastMeters(), 0.001);
		assertEquals(expected.northMeters(), actual.northMeters(), 0.001);
		assertEquals(expected.upMeters(), actual.upMeters(), 0.001);
	}

	@Test
	void boundsDetectAntimeridian() {
		Wgs84.Bounds bounds = Wgs84.bounds(new Wgs84.GeoPoint(0, 179.99, 0), 5_000);
		assertTrue(bounds.crossesAntimeridian());
		assertTrue(bounds.westDeg() > bounds.eastDeg());
	}

	@Test
	void launchCoordinateIsExactOrigin() {
		Wgs84.GeoPoint origin = new Wgs84.GeoPoint(64.8, -147.7, 250);
		Wgs84.EnuPoint point = Wgs84.toEnu(origin, origin);
		assertEquals(0, point.eastMeters(), 1e-9);
		assertEquals(0, point.northMeters(), 1e-9);
		assertEquals(0, point.upMeters(), 1e-9);
	}
}

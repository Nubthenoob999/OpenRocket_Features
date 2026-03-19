package info.openrocket.core.aerodynamics.rom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.util.BaseTestCase;
import info.openrocket.core.util.TestRockets;

public class RomGeometryParametersTest extends BaseTestCase {

	private static final double EPSILON = 1e-6;

	@Test
	public void testGeometryExtractionForEstesAlpha() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		FlightConfiguration config = rocket.getSelectedConfiguration();

		RomGeometryParameters g = RomGeometryParameters.fromRocket(config);

		assertEquals(0.27, g.bodyLength, 1e-3);
		assertEquals(0.024, g.maxDiameter, 1e-3);
		assertEquals(11.25, g.finessRatio, 0.15);
		assertEquals(3, g.finCount);
		assertEquals(RomGeometryParameters.NoseShape.OGIVE, g.noseShape);

		String hash = g.geometryHash();
		assertNotNull(hash);
		assertEquals(64, hash.length());
		assertTrue(hash.matches("[0-9a-f]{64}"));

		RomGeometryParameters g2 = RomGeometryParameters.fromRocket(config);
		assertEquals(hash, g2.geometryHash());
	}
}

package info.openrocket.core.aerodynamics.rom.geometry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.util.BaseTestCase;
import info.openrocket.core.util.TestRockets;

import org.junit.jupiter.api.Test;

public class GeometryFeatureExtractorTest extends BaseTestCase {

	@Test
	public void extractsAxisymmetricBodyFinAndPatchMetricsFromRocket() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		FlightConfiguration configuration = rocket.getSelectedConfiguration();

		GeometryFeatures features = new GeometryFeatureExtractor().extract(configuration);

		assertNotNull(features);
		assertFalse(features.getGeometryHash().isBlank());
		assertTrue(features.getBodyLength() > 0.0);
		assertTrue(features.getReferenceLength() > 0.0);
		assertTrue(features.getReferenceArea() > 0.0);
		assertTrue(features.getMaxDiameter() > 0.0);
		assertFalse(features.getAxisymmetricBodies().isEmpty());
		assertFalse(features.getFins().isEmpty());
		assertFalse(features.getSurfacePatches().isEmpty());
		assertTrue(features.getTotalFinPlanformArea() > 0.0);
	}
}

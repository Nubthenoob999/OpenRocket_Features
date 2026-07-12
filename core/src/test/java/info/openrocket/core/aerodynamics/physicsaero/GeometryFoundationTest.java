package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import info.openrocket.core.aerodynamics.physicsaero.geometry.*;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.util.TestRockets;
import info.openrocket.core.util.BaseTestCase;

class GeometryFoundationTest extends BaseTestCase {
	@Test void extractsCanonicalSingleStageAndHashesDeterministically() {
		Rocket rocket = TestRockets.makeEstesAlphaIII(); GeometryExtractor extractor = new GeometryExtractor();
		AeroGeometry first = extractor.extract(rocket, 1e-6, "ADIABATIC", "settings-v1");
		AeroGeometry second = extractor.extract(rocket, 1e-6, "ADIABATIC", "settings-v1");
		assertFalse(first.components().isEmpty()); assertEquals(first.geometryHash(), second.geometryHash());
		assertNotEquals(first.geometryHash(), extractor.extract(rocket, 2e-6, "ADIABATIC", "settings-v1").geometryHash());
		assertTrue(first.references().referenceAreaM2() > 0); assertTrue(first.references().exposedBaseAreaM2() >= 0);
	}
	@Test void coneAndCylinderProfileAreasConvergeToAnalyticValues() {
		double length = 2, radius = 0.5, slope = radius / length;
		AxisymmetricProfile cone = new AxisymmetricProfile(List.of(new GeometryStation(0, 0, slope, 0), new GeometryStation(length, radius, slope, 0)), List.of(), "NONE", 0);
		assertEquals(Math.PI * radius * Math.hypot(length, radius), cone.wettedAreaM2(), 1e-12);
		AxisymmetricProfile cylinder = new AxisymmetricProfile(List.of(new GeometryStation(0, radius, 0, 0), new GeometryStation(length, radius, 0, 0)), List.of(), "NONE", 0);
		assertEquals(2 * Math.PI * radius * length, cylinder.wettedAreaM2(), 1e-12);
	}
}

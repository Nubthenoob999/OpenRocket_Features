package info.openrocket.core.aerodynamics.rom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import info.openrocket.core.rocketcomponent.FinSet;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.rocketcomponent.TrapezoidFinSet;
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
		assertTrue(g.finSpan > 0.0);
		assertTrue(g.finRootChord > 0.0);
		assertTrue(g.finTipChord >= 0.0);
		assertTrue(g.finThickness > 0.0);
		assertTrue(g.finWettedArea > 0.0);

		String hash = g.geometryHash();
		assertNotNull(hash);
		assertEquals(64, hash.length());
		assertTrue(hash.matches("[0-9a-f]{64}"));

		RomGeometryParameters g2 = RomGeometryParameters.fromRocket(config);
		assertEquals(hash, g2.geometryHash());
	}

	@Test
	public void testFinlessConfigurationProducesSafeValues() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		List<RocketComponent> finComponents = new ArrayList<>();
		for (RocketComponent component : rocket.getAllChildren()) {
			if (component instanceof FinSet) {
				finComponents.add(component);
			}
		}

		for (RocketComponent finComponent : finComponents) {
			finComponent.getParent().removeChild(finComponent);
		}

		FlightConfiguration config = rocket.getSelectedConfiguration();
		RomGeometryParameters g = RomGeometryParameters.fromRocket(config);

		assertEquals(0, g.finCount);
		assertEquals(0.0, g.finSpan, EPSILON);
		assertEquals(0.0, g.finWettedArea, EPSILON);
	}

	@Test
	public void testGeometryHashChangesWhenFinSpanChanges() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		FlightConfiguration config = rocket.getSelectedConfiguration();
		RomGeometryParameters g1 = RomGeometryParameters.fromRocket(config);

		for (RocketComponent component : rocket.getAllChildren()) {
			if (component instanceof TrapezoidFinSet) {
				TrapezoidFinSet fins = (TrapezoidFinSet) component;
				fins.setHeight(fins.getSpan() + 0.01);
				break;
			}
		}

		RomGeometryParameters g2 = RomGeometryParameters.fromRocket(config);
		assertNotEquals(g1.geometryHash(), g2.geometryHash());
	}
}

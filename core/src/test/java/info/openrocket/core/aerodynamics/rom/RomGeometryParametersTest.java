package info.openrocket.core.aerodynamics.rom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import info.openrocket.core.rocketcomponent.AxialStage;
import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.FinSet;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.NoseCone;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.rocketcomponent.Transition;
import info.openrocket.core.rocketcomponent.TrapezoidFinSet;
import info.openrocket.core.rocketcomponent.position.AxialMethod;
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

	@Test
	public void testGeometryExtractionRetainsDistinctFinSetsAndWeightedAggregate() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		BodyTube bodyTube = firstBodyTube(rocket);

		TrapezoidFinSet canards = new TrapezoidFinSet(4, 0.03, 0.015, 0.005, 0.025);
		canards.setThickness(0.0015);
		canards.setAxialMethod(AxialMethod.TOP);
		canards.setAxialOffset(0.015);
		canards.setName("Canards");
		bodyTube.addChild(canards);

		FlightConfiguration config = rocket.getSelectedConfiguration();
		RomGeometryParameters g = RomGeometryParameters.fromRocket(config);

		assertEquals(2, g.finSets.size());
		assertEquals(2, g.resolvedFinSets().size());
		assertEquals(2, g.toRomGeometryInput().finSets.size());

		RomGeometryParameters.FinGeom primary = findFinSet(g.finSets, 3, 0.05);
		RomGeometryParameters.FinGeom secondary = findFinSet(g.finSets, 4, 0.03);
		assertNotNull(primary);
		assertNotNull(secondary);
		assertEquals(7, g.finCount);

		double primaryWeight = primary.totalPlanformArea();
		double secondaryWeight = secondary.totalPlanformArea();
		double totalWeight = primaryWeight + secondaryWeight;
		double totalWettedArea = primary.wettedArea() + secondary.wettedArea();

		assertEquals(totalWettedArea, g.finWettedArea, EPSILON);
		assertEquals((primaryWeight * primary.rootChord() + secondaryWeight * secondary.rootChord()) / totalWeight,
				g.finRootChord, EPSILON);
		assertEquals((primaryWeight * primary.tipChord() + secondaryWeight * secondary.tipChord()) / totalWeight,
				g.finTipChord, EPSILON);
		assertEquals((primaryWeight * primary.span() + secondaryWeight * secondary.span()) / totalWeight,
				g.finSpan, EPSILON);
		assertEquals((primaryWeight * primary.thickness() + secondaryWeight * secondary.thickness()) / totalWeight,
				g.finThickness, EPSILON);
		assertEquals((primaryWeight * primary.sweepAngle() + secondaryWeight * secondary.sweepAngle()) / totalWeight,
				g.finSweepAngle, EPSILON);
	}

	@Test
	public void testBoattailBaseAreaUsesAftMostShrinkingTransition() {
		Rocket rocket = new Rocket();
		AxialStage stage = new AxialStage();
		rocket.addChild(stage);

		stage.addChild(new NoseCone(Transition.Shape.OGIVE, 0.12, 0.05));
		stage.addChild(new BodyTube(0.40, 0.05, 0.002));

		Transition boattail = new Transition();
		boattail.setLength(0.10);
		boattail.setForeRadius(0.05);
		boattail.setAftRadius(0.03);
		boattail.setThickness(0.002);
		stage.addChild(boattail);

		rocket.enableEvents();
		RomGeometryParameters g = RomGeometryParameters.fromRocket(rocket.getSelectedConfiguration());

		assertEquals(0.10, g.boattailLength, EPSILON);
		assertEquals(0.06, g.boattailBaseDiameter, EPSILON);
		assertEquals(Math.PI * 0.03 * 0.03, g.baseArea, EPSILON);
	}

	private static BodyTube firstBodyTube(Rocket rocket) {
		for (RocketComponent component : rocket.getAllChildren()) {
			if (component instanceof BodyTube bodyTube) {
				return bodyTube;
			}
		}
		throw new IllegalStateException("Expected a body tube in the test rocket.");
	}

	private static RomGeometryParameters.FinGeom findFinSet(List<RomGeometryParameters.FinGeom> finSets,
			int count,
			double rootChord) {
		for (RomGeometryParameters.FinGeom finSet : finSets) {
			if (finSet.count() == count && Math.abs(finSet.rootChord() - rootChord) < EPSILON) {
				return finSet;
			}
		}
		return null;
	}
}

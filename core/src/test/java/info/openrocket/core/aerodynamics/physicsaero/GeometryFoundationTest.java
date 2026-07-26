package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import info.openrocket.core.aerodynamics.physicsaero.geometry.*;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.AxialStage;
import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.ExternalComponent;
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
	@Test void equivalentFreshRocketObjectsHavePortableGeometryHashes() {
		GeometryExtractor extractor = new GeometryExtractor();
		AeroGeometry first = extractor.extract(TestRockets.makeEstesAlphaIII(),
				1e-6, "ADIABATIC", "settings-v1");
		AeroGeometry second = extractor.extract(TestRockets.makeEstesAlphaIII(),
				1e-6, "ADIABATIC", "settings-v1");
		assertEquals(first.geometryHash(), second.geometryHash());
		assertEquals(first.components().stream().map(AeroComponent::id).toList(),
				second.components().stream().map(AeroComponent::id).toList());
	}
	@Test void componentFinishRoughnessIsPreservedUnlessExplicitlyOverridden() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		rocket.getAllChildren().stream()
				.filter(ExternalComponent.class::isInstance)
				.map(ExternalComponent.class::cast)
				.forEach(component -> component.setFinish(ExternalComponent.Finish.OPTIMUM));
		GeometryExtractor extractor = new GeometryExtractor();
		AeroGeometry inherited = extractor.extractWithComponentRoughness(
				rocket, "ADIABATIC", "component-finish");
		assertTrue(inherited.components().stream().allMatch(component ->
				component.roughnessM() == ExternalComponent.Finish.OPTIMUM.getRoughnessSize()));

		AeroGeometry overridden = extractor.extract(rocket, 0, "ADIABATIC", "explicit-smooth");
		assertTrue(overridden.components().stream().allMatch(component -> component.roughnessM() == 0));
	}
	@Test void fullyTurbulentModeIsCanonicalGeometryMetadata() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		GeometryExtractor extractor = new GeometryExtractor();
		AeroGeometry natural = extractor.extractWithComponentRoughness(
				rocket, "ADIABATIC", "same-settings", false);
		AeroGeometry turbulent = extractor.extractWithComponentRoughness(
				rocket, "ADIABATIC", "same-settings", true);

		assertNotEquals(natural.geometryHash(), turbulent.geometryHash());
		assertTrue(natural.components().stream().noneMatch(component ->
				component.localReferences().containsKey("forceFullyTurbulent")));
		assertTrue(turbulent.components().stream()
				.filter(component -> component.axisymmetricProfile() != null
						|| component.finGeometry() != null)
				.allMatch(component -> component.localReferences()
						.get("forceFullyTurbulent") == 1.0));
	}
	@Test void coneAndCylinderProfileAreasConvergeToAnalyticValues() {
		double length = 2, radius = 0.5, slope = radius / length;
		AxisymmetricProfile cone = new AxisymmetricProfile(List.of(new GeometryStation(0, 0, slope, 0), new GeometryStation(length, radius, slope, 0)), List.of(), "NONE", 0);
		assertEquals(Math.PI * radius * Math.hypot(length, radius), cone.wettedAreaM2(), 1e-12);
		AxisymmetricProfile cylinder = new AxisymmetricProfile(List.of(new GeometryStation(0, radius, 0, 0), new GeometryStation(length, radius, 0, 0)), List.of(), "NONE", 0);
		assertEquals(2 * Math.PI * radius * length, cylinder.wettedAreaM2(), 1e-12);
	}
	@Test void tangentOgiveBoundaryDerivativeDoesNotCreateFalseCylinderTurn() {
		AeroGeometry geometry = new GeometryExtractor().extract(
				TestRockets.makeEstesAlphaIII(), 0, "ADIABATIC",
				"boundary-derivative");
		AeroComponent ogive = geometry.components().stream()
				.filter(component -> component.classification().equals("NOSE_OGIVE"))
				.findFirst().orElseThrow();
		List<GeometryStation> stations =
				ogive.axisymmetricProfile().stations();
		assertEquals(0, stations.get(stations.size() - 1).slope(), 1e-9,
				"tangent-ogive aft endpoint must be tangent to its cylinder");
	}
	@Test void activeFlightConfigurationExtractsOnlyActiveStagesAndChangesHash() {
		Rocket rocket = new Rocket();
		AxialStage first = new AxialStage();
		AxialStage second = new AxialStage();
		rocket.addChild(first);
		rocket.addChild(second);
		first.addChild(new BodyTube(0.4, 0.03));
		second.addChild(new BodyTube(0.6, 0.02));
		var configuration = rocket.getSelectedConfiguration();
		configuration.setAllStages();
		GeometryExtractor extractor = new GeometryExtractor();
		AeroGeometry all = extractor.extract(configuration, 1e-6, "ADIABATIC", "settings");
		configuration.setOnlyStage(1);
		AeroGeometry staged = extractor.extract(configuration, 1e-6, "ADIABATIC", "settings");
		assertEquals(2, all.components().size());
		assertEquals(1, staged.components().size());
		assertNotEquals(all.geometryHash(), staged.geometryHash());
		assertEquals("stage-1", staged.components().get(0).parentStageId());
	}
	@Test void annularLipsAndFinAnchorsHaveTypedRepeatedGeometry() {
		ProtuberanceGeometry lip = ProtuberanceGeometry.annularLip(1.2, 0.01, 0.004, 0.05);
		ProtuberanceGeometry anchors = ProtuberanceGeometry.finAnchors(4, 0.8, 0.03,
				0.006, 1e-4, 2e-5);
		assertEquals("ANNULAR_LIP", lip.type());
		assertEquals(1.2, lip.axialPositionM());
		assertTrue(lip.frontalAreaM2() > 0);
		assertEquals(4, anchors.count());
		assertEquals(4e-4, anchors.projectedAreaM2(), 1e-15);
		assertEquals(8e-5, anchors.frontalAreaM2(), 1e-15);
	}
}

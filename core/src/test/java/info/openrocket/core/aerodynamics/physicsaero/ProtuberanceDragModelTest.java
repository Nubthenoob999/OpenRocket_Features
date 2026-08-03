package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.physicsaero.flow.AtmosphereState;
import info.openrocket.core.aerodynamics.physicsaero.flow.FlowCondition;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.PerfectGasAir;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroComponent;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.ProtuberanceDragModel;
import info.openrocket.core.aerodynamics.physicsaero.geometry.ProtuberanceGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.ReferenceGeometry;
import info.openrocket.core.util.Coordinate;

class ProtuberanceDragModelTest {
	private static final AtmosphereState SEA_LEVEL =
			new AtmosphereState(101325, 288.15, 1.225, 1.7894e-5);
	private static final PerfectGasAir AIR = new PerfectGasAir();

	@Test
	void railButtonUsesProjectedAreaTransonicRiseAndBoundaryLayerExposure() {
		AeroGeometry noseMounted = geometry(railButton("rail", 0.001));
		AeroGeometry aftMounted = geometry(railButton("rail", 1.0));
		var model = new ProtuberanceDragModel();

		double subsonic = model.evaluate(noseMounted, flow(noseMounted, 0.3))
				.totalCd();
		double transonic = model.evaluate(noseMounted, flow(noseMounted, 1.0))
				.totalCd();
		double aft = model.evaluate(aftMounted, flow(aftMounted, 0.3))
				.totalCd();

		assertTrue(subsonic > 0);
		assertTrue(transonic > subsonic);
		assertTrue(aft < subsonic);
		assertEquals(0, model.evaluate(noseMounted, flow(noseMounted, 0.3))
				.internalFlowCd(), 0);
	}

	@Test
	void launchLugOwnsAnnularPressureAndInternalBoreLossSeparately() {
		AeroGeometry shortLug = geometry(launchLug("short", 0.03));
		AeroGeometry longLug = geometry(launchLug("long", 0.12));
		var model = new ProtuberanceDragModel();

		var shortResult = model.evaluate(shortLug, flow(shortLug, 0.6));
		var longResult = model.evaluate(longLug, flow(longLug, 0.6));

		assertTrue(shortResult.pressureCd() > 0);
		assertTrue(shortResult.internalFlowCd() > 0);
		assertEquals(shortResult.pressureCd(), longResult.pressureCd(), 1.0e-12);
		assertTrue(longResult.internalFlowCd() > shortResult.internalFlowCd());
	}

	@Test
	void rasaeroRailGuideUsesDocumentedPairCorrelationWithoutChangingNativeButtons() {
		double diameter = 1.5 * 0.0254;
		double totalHeight = 0.7 * 0.0254;
		AeroGeometry geometry = geometry(rasaeroRailGuide(
				"rasaero-guide", diameter, totalHeight));
		var result = new ProtuberanceDragModel().evaluate(geometry,
				flow(geometry, 2.0));
		double referenceArea = Math.PI * 0.05 * 0.05;

		assertEquals(5.625 * diameter * totalHeight / referenceArea,
				result.totalCd(), 1.0e-12);
		assertEquals(ProtuberanceDragModel.RASAERO_RAIL_GUIDE_METHOD_ID,
				result.components().get(0).methodId());
	}

	@Test
	void rasaeroRailGuideCorrelationIsContinuousAcrossTransonicJoin() {
		AeroGeometry geometry = geometry(rasaeroRailGuide(
				"rasaero-guide", 0.02, 0.01));
		var model = new ProtuberanceDragModel();
		double belowNineTenths = model.evaluate(geometry,
				flow(geometry, 0.9 - 1.0e-9)).totalCd();
		double aboveNineTenths = model.evaluate(geometry,
				flow(geometry, 0.9 + 1.0e-9)).totalCd();
		double belowOneOhFive = model.evaluate(geometry,
				flow(geometry, 1.05 - 1.0e-9)).totalCd();
		double aboveOneOhFive = model.evaluate(geometry,
				flow(geometry, 1.05 + 1.0e-9)).totalCd();

		assertEquals(belowNineTenths, aboveNineTenths, 1.0e-9);
		assertEquals(belowOneOhFive, aboveOneOhFive, 1.0e-9);
	}

	@Test
	void coefficientClosuresRemainFiniteAndContinuousAtSonicMach() {
		AeroGeometry geometry = geometry(launchLug("lug", 0.08));
		var model = new ProtuberanceDragModel();
		double below = model.evaluate(geometry, flow(geometry, 0.9999)).totalCd();
		double above = model.evaluate(geometry, flow(geometry, 1.0001)).totalCd();
		assertTrue(Double.isFinite(below));
		assertTrue(Double.isFinite(above));
		assertEquals(below, above, 5.0e-4);
	}

	private static FlowCondition flow(AeroGeometry geometry, double mach) {
		return FlowCondition.fromAngles(mach, 0, 0, SEA_LEVEL, AIR, false,
				geometry.geometryHash());
	}

	private static AeroComponent railButton(String id, double x) {
		double diameter = 0.012;
		double height = 0.010;
		return component(id, x, diameter,
				new ProtuberanceGeometry("RAIL_BUTTON", 2, x, diameter, height,
						2 * diameter * height, 2 * Math.PI * diameter * diameter / 4),
				Map.of());
	}

	private static AeroComponent rasaeroRailGuide(String id, double diameter,
			double totalHeight) {
		return component(id, 0.4, diameter,
				new ProtuberanceGeometry("RASAERO_RAIL_GUIDE", 2, 0.4,
						diameter, totalHeight, diameter * totalHeight,
						2 * Math.PI * diameter * diameter / 4),
				Map.of());
	}

	private static AeroComponent launchLug(String id, double length) {
		double outerDiameter = 0.010;
		double innerDiameter = 0.006;
		double annularArea = Math.PI / 4
				* (outerDiameter * outerDiameter - innerDiameter * innerDiameter);
		return component(id, 0.4, length,
				new ProtuberanceGeometry("LAUNCH_LUG", 2, 0.4, length,
						outerDiameter, 2 * outerDiameter * length, 2 * annularArea),
				Map.of("innerDiameterM", innerDiameter,
						"innerAreaM2", Math.PI * innerDiameter * innerDiameter / 4));
	}

	private static AeroComponent component(String id, double x, double length,
			ProtuberanceGeometry protuberance, Map<String, Double> local) {
		return new AeroComponent(id, id, "test", protuberance.type(), "stage", 0,
				new Coordinate(x, 0, 0), x, x + length, 0.05,
				0, protuberance.projectedAreaM2(), 0, 1.0e-6, "ADIABATIC",
				local, List.of(), null, null, protuberance);
	}

	private static AeroGeometry geometry(AeroComponent component) {
		ReferenceGeometry references = new ReferenceGeometry(
				Math.PI * 0.05 * 0.05, Math.PI * 0.05 * 0.05,
				2.0, 0.1, Map.of(component.id(), 0.0), new Coordinate(), 0.1);
		return new AeroGeometry(List.of(component), references,
				"protuberance-test-" + component.id());
	}
}

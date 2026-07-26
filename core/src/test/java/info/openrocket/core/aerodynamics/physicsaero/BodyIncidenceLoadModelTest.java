package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.physicsaero.body.BodyIncidenceLoadModel;
import info.openrocket.core.aerodynamics.physicsaero.flow.AtmosphereState;
import info.openrocket.core.aerodynamics.physicsaero.flow.FlowCondition;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.PerfectGasAir;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroComponent;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AxisymmetricProfile;
import info.openrocket.core.aerodynamics.physicsaero.geometry.GeometryStation;
import info.openrocket.core.aerodynamics.physicsaero.geometry.ReferenceGeometry;
import info.openrocket.core.util.Coordinate;

class BodyIncidenceLoadModelTest {
	@Test
	void signedAlphaBetaLoadsHaveOneComponentDerivedApplicationPoint() {
		AeroGeometry geometry = geometry();
		PerfectGasAir gas = new PerfectGasAir();
		AtmosphereState atmosphere = new AtmosphereState(101325, 288.15, 1.225, 1.8e-5);
		FlowCondition positive = FlowCondition.fromAngles(2, 0.02, -0.01, atmosphere, gas, false, "test");
		FlowCondition negative = FlowCondition.fromAngles(2, -0.02, 0.01, atmosphere, gas, false, "test");
		var plus = new BodyIncidenceLoadModel().evaluate(geometry, positive).get(0);
		var minus = new BodyIncidenceLoadModel().evaluate(geometry, negative).get(0);
		assertEquals(-plus.forceBodyN().z, minus.forceBodyN().z, 1e-12);
		assertEquals(-plus.forceBodyN().y, minus.forceBodyN().y, 1e-12);
		assertEquals(plus.applicationPointM(), minus.applicationPointM());
		assertTrue(plus.applicationPointM().x > 0 && plus.applicationPointM().x < 1);
		assertTrue(plus.validityFlags().contains("BODY_NONZERO_INCIDENCE_OWNED"));
	}

	private static AeroGeometry geometry() {
		double radius = 0.05;
		AxisymmetricProfile noseProfile = new AxisymmetricProfile(List.of(
				new GeometryStation(0, 0, radius, 0),
				new GeometryStation(0.5, radius, 0, 0)), List.of(), "TEST", 1e-9);
		AxisymmetricProfile tubeProfile = new AxisymmetricProfile(List.of(
				new GeometryStation(0.5, radius, 0, 0),
				new GeometryStation(1, radius, 0, 0)), List.of(), "TEST", 1e-9);
		AeroComponent nose = component("nose", noseProfile, 0, 0.5, radius);
		AeroComponent tube = component("tube", tubeProfile, 0.5, 1, radius);
		double area = Math.PI * radius * radius;
		return new AeroGeometry(List.of(nose, tube), new ReferenceGeometry(area, area, 1,
				2 * radius, Map.of("nose", nose.wettedAreaM2(), "tube", tube.wettedAreaM2()),
				new Coordinate(), 2 * radius), "body-incidence-test");
	}

	private static AeroComponent component(String id, AxisymmetricProfile profile,
			double start, double end, double radius) {
		return new AeroComponent(id, "/" + id, "test", start == 0 ? "NOSE_CONICAL" : "CYLINDER",
				"stage", 0, new Coordinate(start, 0, 0), start, end, radius,
				profile.wettedAreaM2(), 2 * radius * (end - start), Math.PI * radius * radius,
				0, "ADIABATIC", Map.of(), List.of(), profile, null, null);
	}
}

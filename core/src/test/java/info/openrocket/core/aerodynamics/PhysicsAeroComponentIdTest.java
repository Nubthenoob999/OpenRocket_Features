package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.physicsaero.geometry.GeometryExtractor;
import info.openrocket.core.rocketcomponent.AxialStage;
import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.NoseCone;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.Transition;

class PhysicsAeroComponentIdTest {
	@Test
	void zeroLengthExternalMarkerDoesNotShiftTableComponentIds() {
		Rocket rocket = new Rocket();
		AxialStage stage = new AxialStage();
		rocket.addChild(stage);
		stage.addChild(new NoseCone(Transition.Shape.CONICAL, 0.15, 0.02));
		Transition marker = new Transition();
		marker.setLength(0);
		marker.setForeRadius(0.02);
		marker.setAftRadius(0.02);
		stage.addChild(marker);
		BodyTube body = new BodyTube(0.40, 0.02, 0.001);
		stage.addChild(body);
		rocket.enableEvents();

		FlightConfiguration configuration = rocket.getSelectedConfiguration();
		configuration.setAllStages();
		var geometry = new GeometryExtractor().extract(configuration, 0,
				"ADIABATIC", "component-id-regression");

		assertEquals(2, geometry.components().size());
		assertEquals("aero-component-0001",
				PhysicsAeroAerodynamicCalculator.tableComponentId(configuration, body));
		assertNull(PhysicsAeroAerodynamicCalculator.tableComponentId(configuration, marker));
	}
}

package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;

import info.openrocket.core.models.atmosphere.AtmosphericConditions;
import info.openrocket.core.rocketcomponent.AxialStage;
import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.InstanceContext;
import info.openrocket.core.rocketcomponent.InstanceMap;
import info.openrocket.core.rocketcomponent.NoseCone;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.Transition;
import info.openrocket.core.util.BaseTestCase;
import org.junit.jupiter.api.Test;

class ThickBoundaryLayerBaseCorrectionTest extends BaseTestCase {
	private static final double INCH = 0.0254;

	@Test
	void correctionIsGatedOffSubsonicallyAndForModerateFineness() {
		assertEquals(1, multiplier(rocket(8.5, 65, 1.75), 0.9), 1.0e-12);
		assertEquals(1, multiplier(rocket(6, 60, 4), 1.1), 1.0e-12);
	}

	@Test
	void ravenLikeBodyReachesBoundedThickBoundaryLayerCorrection() {
		assertEquals(1.8, multiplier(rocket(8.5, 65, 1.75), 1.1), 1.0e-12);
	}

	@Test
	void slenderBodyPressureDragIsSupersonicAndFinenessGated() {
		Rocket slender = rocket(8.5, 65, 1.75);
		assertEquals(0, slenderPressure(slender, 1.0), 0);
		assertTrue(slenderPressure(slender, 2.0) > 0);
		assertEquals(0, slenderPressure(rocket(6, 60, 4), 2.0), 0);
	}

	private static Rocket rocket(double noseLengthIn, double bodyLengthIn, double diameterIn) {
		Rocket rocket = new Rocket();
		AxialStage stage = new AxialStage();
		rocket.addChild(stage);
		double radius = diameterIn * INCH / 2;
		stage.addChild(new NoseCone(Transition.Shape.HAACK, noseLengthIn * INCH, radius));
		stage.addChild(new BodyTube(bodyLengthIn * INCH, radius, 0.001));
		rocket.enableEvents();
		return rocket;
	}

	private static double multiplier(Rocket rocket, double mach) {
		FlightConfiguration configuration = rocket.getSelectedConfiguration();
		configuration.setAllStages();
		BodyTube body = rocket.getAllChildren().stream()
				.filter(BodyTube.class::isInstance).map(BodyTube.class::cast)
				.findFirst().orElseThrow();
		FlightConditions conditions = new FlightConditions(configuration);
		conditions.setAtmosphericConditions(new AtmosphericConditions());
		conditions.setMach(mach);
		InstanceMap instances = configuration.getActiveInstances();
		ArrayList<InstanceContext> contexts = instances.get(body);
		assertNotNull(contexts);
		return BarrowmanDragCalculator.calculateThickBLBaseMultiplier(
				body, contexts, configuration, conditions, body.getAftRadius());
	}

	private static double slenderPressure(Rocket rocket, double mach) {
		FlightConfiguration configuration = rocket.getSelectedConfiguration();
		configuration.setAllStages();
		FlightConditions conditions = new FlightConditions(configuration);
		conditions.setAtmosphericConditions(new AtmosphericConditions());
		conditions.setMach(mach);
		return BarrowmanDragCalculator.calculateSlenderBodyPressureCD(configuration, conditions);
	}
}

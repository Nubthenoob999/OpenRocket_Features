package info.openrocket.core.aerodynamics.rom.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import info.openrocket.core.aerodynamics.FlightConditions;
import info.openrocket.core.models.atmosphere.AtmosphericConditions;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.util.BaseTestCase;
import info.openrocket.core.util.TestRockets;

import org.junit.jupiter.api.Test;

public class FlowStateExtractorTest extends BaseTestCase {

	@Test
	public void extractsMachAnglesAndReynoldsStateFromFlightConditions() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		FlightConfiguration configuration = rocket.getSelectedConfiguration();
		FlightConditions conditions = new FlightConditions(configuration);
		conditions.setMach(0.82);
		conditions.setAOA(Math.toRadians(5.0));
		conditions.setTheta(Math.PI / 2.0);
		conditions.setAtmosphericConditions(new AtmosphericConditions(290.0, 95_000.0));

		FlowState flow = new FlowStateExtractor().extract(conditions, configuration.getReferenceLength(), 0.35);

		assertEquals(0.82, flow.getMach(), 1e-12);
		assertEquals(5.0, flow.getAngleOfAttackDeg(), 1e-9);
		assertEquals(5.0, flow.getSideslipDeg(), 1e-9);
		assertEquals(0.35, flow.getPlumeState(), 1e-12);
		assertTrue(flow.isPowered());
		assertTrue(flow.getVelocity() > 0.0);
		assertTrue(flow.getDynamicPressure() > 0.0);
		assertTrue(flow.getReynoldsNumber() > 0.0);
	}
}

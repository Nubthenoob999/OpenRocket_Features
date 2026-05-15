package info.openrocket.core.aerodynamics.rom.force;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import info.openrocket.core.aerodynamics.rom.flow.FlowState;
import info.openrocket.core.aerodynamics.rom.geometry.GeometryFeatureExtractor;
import info.openrocket.core.aerodynamics.rom.geometry.GeometryFeatures;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.util.BaseTestCase;
import info.openrocket.core.util.TestRockets;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class CrossflowNormalForceModelTest extends BaseTestCase {
	private static GeometryFeatures geometry;

	@BeforeAll
	static void setUpGeometry() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		FlightConfiguration configuration = rocket.getSelectedConfiguration();
		geometry = new GeometryFeatureExtractor().extract(configuration);
	}

	@Test
	public void inactiveBelowCrossflowThreshold() {
		assertEquals(0.0, evaluate(0.5, 0.0).deltaCN(), 1e-12);
		assertEquals(0.0, evaluate(0.5, 4.9).deltaCN(), 1e-12);
	}

	@Test
	public void rampsBetweenThresholdAndFullCrossflow() {
		double cn8 = evaluate(0.5, 8.0).deltaCN();
		double cn12 = evaluate(0.5, 12.0).deltaCN();

		assertTrue(cn8 > 0.0);
		assertTrue(cn12 > cn8);
	}

	@Test
	public void preservesAlphaSign() {
		double positive = evaluate(0.5, 12.0).deltaCN();
		double negative = evaluate(0.5, -12.0).deltaCN();

		assertTrue(positive > 0.0);
		assertTrue(negative < 0.0);
		assertEquals(positive, -negative, Math.abs(positive) * 1e-9);
	}

	@Test
	public void remainsFiniteAcrossMachZeroToPresonic() {
		for (double mach : new double[] {0.0, 0.5, 0.9}) {
			CrossflowNormalForceModel.Result result = evaluate(mach, 12.0);
			assertTrue(Double.isFinite(result.deltaCN()));
			assertTrue(Double.isFinite(result.deltaCm()));
			assertTrue(Double.isFinite(result.xCp()));
		}
	}

	private static CrossflowNormalForceModel.Result evaluate(double mach, double alphaDeg) {
		return CrossflowNormalForceModel.evaluate(geometry, flowState(mach, alphaDeg), geometry.getBodyLength() * 0.50);
	}

	private static FlowState flowState(double mach, double alphaDeg) {
		double speedOfSound = 340.0;
		return new FlowState(mach, 1.0e6, 100.0, 101325.0, 288.15, 1.225, speedOfSound, 1.5e-5,
				mach * speedOfSound, Math.toRadians(alphaDeg), alphaDeg, 0.0, 0.0,
				false, 0.0, geometry.getReferenceLength());
	}
}

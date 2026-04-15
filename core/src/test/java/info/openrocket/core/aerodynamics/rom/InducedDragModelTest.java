package info.openrocket.core.aerodynamics.rom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class InducedDragModelTest {

	@Test
	public void testCdInducedIsZeroAtZeroAoa() {
		RomGeometryParameters g = RomTestFixtures.standardGeometry();
		double cd = InducedDragModel.cdInduced(0.0, 0.5, g);
		assertEquals(0.0, cd, 0.0);
	}

	@Test
	public void testCdInducedScalesRoughlyWithAlphaSquared() {
		RomGeometryParameters g = RomTestFixtures.standardGeometry();
		double cdAt2deg = InducedDragModel.cdInduced(Math.toRadians(2.0), 0.5, g);
		double cdAt4deg = InducedDragModel.cdInduced(Math.toRadians(4.0), 0.5, g);
		double ratio = cdAt4deg / cdAt2deg;
		assertTrue(ratio > 3.5 && ratio < 5.0, "Expected ~4x ratio, got " + ratio);
	}

	@Test
	public void testSubsonicCompressibilityCorrectionIncreasesDrag() {
		RomGeometryParameters g = RomTestFixtures.standardGeometry();
		double alpha = Math.toRadians(5.0);
		double subsonic = InducedDragModel.cdInduced(alpha, 0.5, g);
		double supersonic = InducedDragModel.cdInduced(alpha, 1.2, g);
		assertTrue(subsonic > supersonic);
	}

	@Test
	public void testProtuberanceFactorConstant() {
		assertEquals(1.02, InducedDragModel.protuberanceFactor(), 1e-12);
	}

	@Test
	public void testCompressibilityContinuityAroundMachBreakpoints() {
		RomGeometryParameters g = RomTestFixtures.standardGeometry();
		double alpha = Math.toRadians(5.0);
		double eps = 1e-4;

		double left08 = InducedDragModel.cdInduced(alpha, 0.8 - eps, g);
		double right08 = InducedDragModel.cdInduced(alpha, 0.8 + eps, g);
		RomTestFixtures.assertRelativeError(left08, right08, 0.005);

		double left12 = InducedDragModel.cdInduced(alpha, 1.2 - eps, g);
		double right12 = InducedDragModel.cdInduced(alpha, 1.2 + eps, g);
		RomTestFixtures.assertRelativeError(left12, right12, 0.005);
	}
}
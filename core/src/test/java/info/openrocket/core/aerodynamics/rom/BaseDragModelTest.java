package info.openrocket.core.aerodynamics.rom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class BaseDragModelTest {

	private static RomGeometryParameters sampleGeometry() {
		RomGeometryParameters g = new RomGeometryParameters();
		g.maxDiameter = 0.102;
		g.referenceArea = Math.PI * Math.pow(g.maxDiameter / 2.0, 2.0);
		g.boattailLength = 0.0;
		g.boattailBaseDiameter = 0.0;
		g.motorExitDiameter = 0.03;
		g.motorExitArea = Math.PI * Math.pow(g.motorExitDiameter / 2.0, 2.0);
		return g;
	}

	@Test
	public void testTransonicMultiplierReferencePoints() {
		assertEquals(1.0, BaseDragModel.transonicMultiplier(0.6), 1e-12);
		assertEquals(1.88, BaseDragModel.transonicMultiplier(1.0), 0.1);
	}

	@Test
	public void testPlumeOnHasLowerBaseDrag() {
		RomGeometryParameters g = sampleGeometry();
		double off = BaseDragModel.cdBasePlumeOff(0.3, 0.003, g);
		double on = BaseDragModel.cdBasePlumeOn(0.3, 0.003, g);
		assertTrue(off > on);
		assertTrue(on >= 0.0);
	}

	@Test
	public void testPlumeOnFallsBackToPlumeOffWhenNoExitArea() {
		RomGeometryParameters g = sampleGeometry();
		g.motorExitArea = 0.0;
		double off = BaseDragModel.cdBasePlumeOff(0.9, 0.003, g);
		double on = BaseDragModel.cdBasePlumeOn(0.9, 0.003, g);
		assertEquals(off, on, 1e-12);
	}
}
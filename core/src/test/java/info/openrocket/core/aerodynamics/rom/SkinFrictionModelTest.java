package info.openrocket.core.aerodynamics.rom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class SkinFrictionModelTest {

	private static RomGeometryParameters sampleGeometry() {
		RomGeometryParameters g = new RomGeometryParameters();
		g.bodyLength = 1.8;
		g.maxDiameter = 0.102;
		g.referenceArea = Math.PI * Math.pow(g.maxDiameter / 2.0, 2.0);
		g.wetArea = 0.65;
		g.surfaceRoughness = 6.4e-6;
		return g;
	}

	@Test
	public void testCfIncompressibleAtHighRe() {
		double cf = SkinFrictionModel.cfIncompressible(1e7, 5e5);
		assertEquals(0.00285, cf, 0.0002);
	}

	@Test
	public void testCfIncompressibleLaminarFallback() {
		double expected = 1.328 / Math.sqrt(1e4);
		double cf = SkinFrictionModel.cfIncompressible(1e4, 5e5);
		assertEquals(expected, cf, 1e-10);
	}

	@Test
	public void testVanDriestReducesCfAtHigherMach() {
		double cfComp = SkinFrictionModel.vanDriestII(0.003, 2.0, 1.0);
		assertTrue(cfComp < 0.003);
	}

	@Test
	public void testCdFrictionReasonableRange() {
		RomGeometryParameters g = sampleGeometry();
		double cd = SkinFrictionModel.cdFriction(0.5, 1e6, g);
		assertTrue(cd > 0.01 && cd < 0.10);
	}
}
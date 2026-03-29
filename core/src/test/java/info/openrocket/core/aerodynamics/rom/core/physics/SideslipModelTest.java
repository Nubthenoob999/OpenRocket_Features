package info.openrocket.core.aerodynamics.rom.core.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.rom.core.TestFixtures;
import info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput;

public class SideslipModelTest {

	@Test
	public void testSideslipIncrementAndCrossflowBehavior() {
		RomGeometryInput g = TestFixtures.typical4Fin();
		double mach = 0.5;

		assertEquals(0.0, SideslipModel.cdBodyCrossflow(0.0, mach, g), 0.0);
		double cross45 = SideslipModel.cdBodyCrossflow(Math.PI / 4.0, mach, g);
		double cross90 = SideslipModel.cdBodyCrossflow(Math.PI / 2.0, mach, g);
		assertTrue(cross90 > cross45);

		assertEquals(0.0, SideslipModel.cdSideslipIncrement(0.0, 0.0, 0.5, g), 1e-12);
		double betaOnly = SideslipModel.cdSideslipIncrement(0.0, Math.PI / 4.0, 0.5, g);
		assertTrue(betaOnly > 0.0);

		double alphaNoBeta = SideslipModel.cdSideslipIncrement(0.087, 0.0, 0.5, g);
		double alphaWithBeta = SideslipModel.cdSideslipIncrement(0.087, Math.PI / 4.0, 0.5, g);
		assertTrue(alphaWithBeta > alphaNoBeta);
	}
}

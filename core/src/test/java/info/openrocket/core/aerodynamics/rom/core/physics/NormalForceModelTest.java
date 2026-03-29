package info.openrocket.core.aerodynamics.rom.core.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.rom.core.TestFixtures;
import info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput;

public class NormalForceModelTest {

	@Test
	public void testNormalForceModelBehavior() {
		RomGeometryInput gWithFins = TestFixtures.typical4Fin();
		RomGeometryInput gNoFins = TestFixtures.noFins();

		assertEquals(0.0, NormalForceModel.CN(0.0, 0.0, 0.5, gWithFins), 0.0);

		double cn5deg = NormalForceModel.CN(0.087, 0.0, 0.5, gWithFins);
		assertTrue(cn5deg >= 0.1 && cn5deg <= 1.5);

		double cn10deg = NormalForceModel.CN(0.174, 0.0, 0.5, gWithFins);
		assertTrue(cn10deg > cn5deg);

		assertEquals(0.0, NormalForceModel.CN(0.0, 0.087, 0.5, gWithFins), 1e-12);

		double noFinsCn = NormalForceModel.CN(0.087, 0.0, 0.5, gNoFins);
		assertTrue(noFinsCn < cn5deg);
	}
}

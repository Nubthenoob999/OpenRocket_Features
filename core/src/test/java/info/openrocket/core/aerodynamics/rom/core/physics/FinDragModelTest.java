package info.openrocket.core.aerodynamics.rom.core.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.rom.core.TestFixtures;
import info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput;

public class FinDragModelTest {

	@Test
	public void testFinDragModelExpectedBehavior() {
		RomGeometryInput g = TestFixtures.typical4Fin();
		RomGeometryInput noFins = TestFixtures.noFins();

		double cdFric = FinDragModel.cdFinFriction(0.5, 1e6, g);
		assertTrue(cdFric >= 0.005 && cdFric <= 0.05);
		assertEquals(0.0, FinDragModel.cdFinFriction(0.5, 1e6, noFins), 0.0);

		double cdWaveSup = FinDragModel.cdFinWaveSupersonic(1.5, g);
		assertTrue(cdWaveSup > 0.0);
		assertEquals(0.0, FinDragModel.cdFinWaveSupersonic(0.9, g), 0.0);
		assertEquals(0.0, FinDragModel.cdFinWaveSupersonic(1.5, noFins), 0.0);

		assertEquals(0.0, FinDragModel.cdFinInducedDrag(0.0, 0.0, 0.5, g), 0.0);
		double cdAlpha = FinDragModel.cdFinInducedDrag(0.087, 0.0, 0.5, g);
		double cdBeta = FinDragModel.cdFinInducedDrag(0.0, 0.087, 0.5, g);
		double cdCombined = FinDragModel.cdFinInducedDrag(0.087, 0.087, 0.5, g);
		assertTrue(cdAlpha > 0.0);
		assertTrue(cdBeta > 0.0);
		assertTrue(cdCombined > cdAlpha);

		double cdInterf = FinDragModel.cdFinInterference(0.05, g);
		assertEquals(0.002, cdInterf, 0.0002);
	}
}

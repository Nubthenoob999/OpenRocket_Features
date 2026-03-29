package info.openrocket.core.aerodynamics.rom.core.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.rom.core.TestFixtures;
import info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput;

public class PitchingMomentModelTest {

	@Test
	public void testPitchingMomentModelBehavior() {
		RomGeometryInput g = TestFixtures.typical4Fin();
		RomGeometryInput gLong = TestFixtures.longerBodyForMomentArm();
		RomGeometryInput gShort = TestFixtures.shorterBodyForMomentArm();

		assertEquals(0.0, PitchingMomentModel.Cm(0.0, 0.0, g), 0.0);

		double cm = PitchingMomentModel.Cm(0.5, 0.087, g);
		assertTrue(cm < 0.0);

		double cmLargeCn = PitchingMomentModel.Cm(1.0, 0.087, g);
		assertTrue(Math.abs(cmLargeCn) > Math.abs(cm));

		double cmLong = PitchingMomentModel.Cm(0.5, 0.087, gLong);
		double cmShort = PitchingMomentModel.Cm(0.5, 0.087, gShort);
		assertTrue(cmLong < cmShort);
	}
}

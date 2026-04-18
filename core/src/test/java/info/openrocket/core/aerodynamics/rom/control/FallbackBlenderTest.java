package info.openrocket.core.aerodynamics.rom.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import info.openrocket.core.aerodynamics.AerodynamicForces;
import info.openrocket.core.aerodynamics.rom.RomFallbackMode;
import info.openrocket.core.util.Coordinate;

import org.junit.jupiter.api.Test;

public class FallbackBlenderTest {

	private final FallbackBlender blender = new FallbackBlender();

	@Test
	public void blendModeInterpolatesContinuouslyBetweenLegacyAndRom() {
		AerodynamicForces legacy = forces(0.40, 1.0, -0.10, 0.90);
		AerodynamicForces rom = forces(0.60, 2.0, -0.30, 1.10);
		AerodynamicConfidence confidence = new AerodynamicConfidence(0.5, 0.0, 0.0, 0.0, 0.0, List.of());

		FallbackBlender.BlendResult result = blender.blend(legacy, rom, confidence, RomFallbackMode.BLEND);

		assertEquals(0.50, result.forces().getCD(), 1e-12);
		assertEquals(1.50, result.forces().getCN(), 1e-12);
		assertEquals(-0.20, result.forces().getCm(), 1e-12);
		assertEquals(1.00, result.forces().getCP().getX(), 1e-12);
		assertEquals(0.50, result.fallbackWeight(), 1e-12);
	}

	@Test
	public void explicitFallbackModesRespectRequestedSource() {
		AerodynamicForces legacy = forces(0.40, 1.0, -0.10, 0.90);
		AerodynamicForces rom = forces(0.60, 2.0, -0.30, 1.10);
		AerodynamicConfidence lowConfidence = new AerodynamicConfidence(0.1, 0.8, 0.0, 0.0, 0.0, List.of("transonic"));

		FallbackBlender.BlendResult legacyOnly = blender.blend(legacy, rom, lowConfidence, RomFallbackMode.BARROWMAN_ONLY);
		FallbackBlender.BlendResult romOnly = blender.blend(legacy, rom, lowConfidence, RomFallbackMode.FORCE_ROM);

		assertEquals(legacy.getCD(), legacyOnly.forces().getCD(), 1e-12);
		assertEquals(1.0, legacyOnly.fallbackWeight(), 1e-12);
		assertEquals(rom.getCD(), romOnly.forces().getCD(), 1e-12);
		assertEquals(0.0, romOnly.fallbackWeight(), 1e-12);
	}

	private static AerodynamicForces forces(double cd, double cn, double cm, double cpX) {
		AerodynamicForces forces = new AerodynamicForces();
		forces.setCD(cd);
		forces.setPressureCD(cd * 0.6);
		forces.setFrictionCD(cd * 0.3);
		forces.setBaseCD(cd * 0.1);
		forces.setCN(cn);
		forces.setCm(cm);
		forces.setCP(new Coordinate(cpX, 0.0, 0.0, Math.abs(cn)));
		return forces;
	}
}

package info.openrocket.core.aerodynamics.rom.core.eval;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.rom.core.basis.GeometryParametricRom;
import info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput;
import info.openrocket.core.aerodynamics.rom.core.sampling.GeometrySampler;

public class GeometricRomEvaluatorTest {

	@Test
	public void testBuildSmallParametricRom() {
		List<RomGeometryInput> pool = GeometrySampler.buildLhsPool(8, 99L);
		GeometryParametricRom rom = org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
				Duration.ofSeconds(120),
				() -> GeometricRomEvaluator.build(pool, List.of(pool.get(0), pool.get(1)), 4, 10.0, 1e-3));
		assertNotNull(rom);
		assertTrue(rom.getRegions().size() >= 1);
	}
}

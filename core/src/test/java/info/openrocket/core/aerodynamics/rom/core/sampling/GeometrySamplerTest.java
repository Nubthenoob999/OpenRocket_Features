package info.openrocket.core.aerodynamics.rom.core.sampling;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.rom.core.basis.GeometrySnapshot;
import info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput;

public class GeometrySamplerTest {

	@Test
	public void testLhsPoolGeneration() {
		List<RomGeometryInput> pool = GeometrySampler.buildLhsPool(20, 42L);
		assertTrue(pool.size() == 20);
		for (RomGeometryInput g : pool) {
			assertTrue(g.bodyLength > 0.0);
			assertTrue(g.maxDiameter > 0.0);
			assertTrue(g.referenceArea > 0.0);
			assertFalse(Double.isNaN(g.toFeatureVector()[0]));
		}
	}

	@Test
	public void testGreedySamplingReturnsSnapshots() {
		List<RomGeometryInput> pool = GeometrySampler.buildLhsPool(6, 7L);
		List<GeometrySnapshot> selected = GeometrySampler.greedySample(
				pool,
				List.of(pool.get(0), pool.get(1)),
				3,
				5.0,
				1e-3,
				null);
		assertTrue(selected.size() >= 2);
		assertTrue(selected.size() <= 3);
	}
}

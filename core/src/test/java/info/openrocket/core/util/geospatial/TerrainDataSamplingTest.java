package info.openrocket.core.util.geospatial;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import info.openrocket.core.util.TerrainFetcher.TerrainData;

class TerrainDataSamplingTest {
	@Test
	void bilinearSampleUsesLaunchPointAsZero() {
		double[][] elevation = {
				{ 0, 1, 2 },
				{ 10, 11, 12 },
				{ 20, 21, 22 }
		};
		TerrainData terrain = new TerrainData(3, 10, elevation, 11, 0, 22);
		assertEquals(0, terrain.relativeElevationAt(0, 0), 1e-9);
		assertEquals(5.5, terrain.relativeElevationAt(5, 5), 1e-9);
		assertEquals(-11, terrain.relativeElevationAt(-10, -10), 1e-9);
	}
}

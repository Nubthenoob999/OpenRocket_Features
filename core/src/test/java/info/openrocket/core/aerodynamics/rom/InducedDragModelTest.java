package info.openrocket.core.aerodynamics.rom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class InducedDragModelTest {

	private static RomGeometryParameters sampleGeometry() {
		RomGeometryParameters g = new RomGeometryParameters();
		g.maxDiameter = 0.10;
		g.referenceArea = Math.PI * Math.pow(g.maxDiameter / 2.0, 2.0);
		g.finCount = 3;
		g.finRootChord = 0.14;
		g.finTipChord = 0.07;
		g.finSpan = 0.08;
		g.finThickness = 0.004;
		return g;
	}

	@Test
	public void testCdInducedIsZeroAtZeroAoa() {
		RomGeometryParameters g = sampleGeometry();
		double cd = InducedDragModel.cdInduced(0.0, 0.5, g);
		assertEquals(0.0, cd, 0.0);
	}

	@Test
	public void testCdInducedIncreasesWithAoa() {
		RomGeometryParameters g = sampleGeometry();
		double low = InducedDragModel.cdInduced(Math.toRadians(2.0), 0.5, g);
		double high = InducedDragModel.cdInduced(Math.toRadians(8.0), 0.5, g);
		assertTrue(high > low);
	}

	@Test
	public void testSubsonicCompressibilityCorrectionIncreasesDrag() {
		RomGeometryParameters g = sampleGeometry();
		double alpha = Math.toRadians(5.0);
		double subsonic = InducedDragModel.cdInduced(alpha, 0.5, g);
		double supersonic = InducedDragModel.cdInduced(alpha, 1.2, g);
		assertTrue(subsonic > supersonic);
	}

	@Test
	public void testProtuberanceDefault() {
		assertEquals(1.04, InducedDragModel.protuberanceFactor(), 0.0);
	}
}
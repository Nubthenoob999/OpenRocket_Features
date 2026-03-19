package info.openrocket.core.aerodynamics.rom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class WaveDragModelTest {

	private static RomGeometryParameters sampleGeometry() {
		RomGeometryParameters g = new RomGeometryParameters();
		g.noseLength = 0.30;
		g.maxDiameter = 0.10;
		g.referenceArea = Math.PI * Math.pow(g.maxDiameter / 2.0, 2.0);
		g.finCount = 3;
		g.finRootChord = 0.15;
		g.finTipChord = 0.08;
		g.finSpan = 0.09;
		g.finThickness = 0.004;
		g.noseShape = RomGeometryParameters.NoseShape.OGIVE;
		return g;
	}

	@Test
	public void testCriticalMachOffsetFromDragDivergence() {
		RomGeometryParameters g = sampleGeometry();
		double mdd = WaveDragModel.dragDivergenceMach(g);
		double mcr = WaveDragModel.criticalMach(g);
		assertEquals(mdd - 0.10, mcr, 1e-12);
	}

	@Test
	public void testNoseShapeAffectsSupersonicWaveDrag() {
		RomGeometryParameters g = sampleGeometry();
		g.noseShape = RomGeometryParameters.NoseShape.VON_KARMAN;
		double vonKarman = WaveDragModel.cdNoseWaveSupersonic(2.0, g);
		g.noseShape = RomGeometryParameters.NoseShape.CONICAL;
		double conical = WaveDragModel.cdNoseWaveSupersonic(2.0, g);
		assertTrue(vonKarman < conical);
	}

	@Test
	public void testFinWaveDragIsZeroAtSubsonicAndPositiveSupersonic() {
		RomGeometryParameters g = sampleGeometry();
		assertEquals(0.0, WaveDragModel.cdFinWaveSupersonic(0.95, g), 0.0);
		assertTrue(WaveDragModel.cdFinWaveSupersonic(2.0, g) > 0.0);
	}
}
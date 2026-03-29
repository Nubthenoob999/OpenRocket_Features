package info.openrocket.core.aerodynamics.rom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class WaveDragModelTest {

	@Test
	public void testCriticalMachOffsetFromDragDivergence() {
		RomGeometryParameters g = RomTestFixtures.standardGeometry();
		double mdd = WaveDragModel.dragDivergenceMach(g);
		double mcr = WaveDragModel.criticalMach(g);
		assertEquals(mdd - 0.10, mcr, 1e-12);
	}

	@Test
	public void testDragDivergenceMachRangeAcrossNoseLengthRatios() {
		RomGeometryParameters g = RomTestFixtures.standardGeometry();
		double[] lnOverD = new double[] { 0.5, 1.0, 2.0, 4.0, 6.0 };
		for (double ratio : lnOverD) {
			g.noseLength = ratio * g.maxDiameter;
			double mdd = WaveDragModel.dragDivergenceMach(g);
			assertTrue(mdd >= 0.68 && mdd <= 1.0,
					"M_DD out of range for lN/d=" + ratio);
		}
	}

	@Test
	public void testNoseShapeOrderingAtSupersonicSpeed() {
		RomGeometryParameters g = RomTestFixtures.standardGeometry();
		g.noseShape = RomGeometryParameters.NoseShape.VON_KARMAN;
		double vonKarman = WaveDragModel.cdNoseWaveSupersonic(2.0, g);
		g.noseShape = RomGeometryParameters.NoseShape.OGIVE;
		double ogive = WaveDragModel.cdNoseWaveSupersonic(2.0, g);
		g.noseShape = RomGeometryParameters.NoseShape.CONICAL;
		double conical = WaveDragModel.cdNoseWaveSupersonic(2.0, g);
		assertTrue(vonKarman < ogive);
		assertTrue(ogive < conical);
	}

	@Test
	public void testFinWaveDragIsZeroAtSubsonicAndPositiveSupersonic() {
		RomGeometryParameters g = RomTestFixtures.standardGeometry();
		assertEquals(0.0, WaveDragModel.cdFinWaveSupersonic(0.95, g), 0.0);
		assertTrue(WaveDragModel.cdFinWaveSupersonic(2.0, g) > 0.0);
	}

	@Test
	public void testWaveDragDecaysAtHighMach() {
		RomGeometryParameters g = RomTestFixtures.standardGeometry();
		double noseAt2 = WaveDragModel.cdNoseWaveSupersonic(2.0, g);
		double noseAt4 = WaveDragModel.cdNoseWaveSupersonic(4.0, g);
		assertTrue(noseAt4 < noseAt2);

		double finAt2 = WaveDragModel.cdFinWaveSupersonic(2.0, g);
		double finAt4 = WaveDragModel.cdFinWaveSupersonic(4.0, g);
		assertTrue(finAt4 < finAt2);
	}
}
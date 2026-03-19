package info.openrocket.core.aerodynamics.rom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class TransonicBlendingModelTest {

	private static RomGeometryParameters sampleGeometry() {
		RomGeometryParameters g = new RomGeometryParameters();
		g.noseShape = RomGeometryParameters.NoseShape.OGIVE;
		g.finessRatio = 12.0;
		return g;
	}

	@Test
	public void testWeightsAreProperlyBoundedAndSummed() {
		double mLow = 0.0;
		assertTrue(TransonicBlendingModel.sigmaSubsonic(mLow) > 0.99);
		assertTrue(TransonicBlendingModel.sigmaSupersonic(mLow) < 0.01);

		for (int i = 0; i <= 50; i++) {
			double mach = 3.0 * i / 50.0;
			double sum = TransonicBlendingModel.sigmaSubsonic(mach)
					+ TransonicBlendingModel.sigmaTransonic(mach)
					+ TransonicBlendingModel.sigmaSupersonic(mach);
			assertEquals(1.0, sum, 1e-12);
		}
	}

	@Test
	public void testBlendMonotoneAcrossTransonicRise() {
		double prev = Double.NEGATIVE_INFINITY;
		for (int i = 0; i <= 50; i++) {
			double mach = 0.5 + 0.5 * i / 50.0;
			double cd = TransonicBlendingModel.blend(mach, 0.3, 0.55, 0.42);
			assertTrue(cd >= prev - 1e-12);
			prev = cd;
		}
	}

	@Test
	public void testTransonicPeakDependsOnNoseShape() {
		RomGeometryParameters g = sampleGeometry();
		double ogive = TransonicBlendingModel.transonicPeakCd(0.30, g);
		g.noseShape = RomGeometryParameters.NoseShape.CONICAL;
		double conical = TransonicBlendingModel.transonicPeakCd(0.30, g);
		assertTrue(conical > ogive);
	}
}
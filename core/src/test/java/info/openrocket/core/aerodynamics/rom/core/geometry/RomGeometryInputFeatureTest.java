package info.openrocket.core.aerodynamics.rom.core.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.rom.core.TestFixtures;

public class RomGeometryInputFeatureTest {

	@Test
	public void testFeatureVectorLengthAndBounds() {
		RomGeometryInput g = TestFixtures.typical4Fin();
		double[] v = g.toFeatureVector();
		assertEquals(10, v.length);
		for (int i = 0; i < v.length; i++) {
			assertTrue(Double.isFinite(v[i]));
			assertTrue(v[i] >= 0.0);
			if (i != 3) {
				assertTrue(v[i] <= 1.0 + 1e-12);
			}
		}
	}

	@Test
	public void testFeatureDistanceIdentityAndNoseShapeDifference() {
		RomGeometryInput g1 = TestFixtures.typical4Fin();
		RomGeometryInput g2 = TestFixtures.typical4Fin();
		assertEquals(0.0, g1.featureDistance(g2), 1e-12);

		RomGeometryInput g3 = new RomGeometryInput(
				g1.bodyLength,
				g1.maxDiameter,
				g1.baseArea,
				g1.wetArea,
				g1.noseLength,
				RomGeometryInput.NoseShape.CONICAL,
				g1.finenessRatio,
				g1.referenceArea,
				g1.boattailLength,
				g1.boattailBaseDiameter,
				g1.finCount,
				g1.finRootChord,
				g1.finTipChord,
				g1.finSpan,
				g1.finThickness,
				g1.finSweepAngle,
				g1.finWettedArea,
				g1.finAxialPosition,
				g1.motorExitArea,
				g1.surfaceRoughness);

		double dist = g1.featureDistance(g3);
		assertTrue(dist > 0.0);
		assertEquals(0.6, dist, 1e-12);
	}
}

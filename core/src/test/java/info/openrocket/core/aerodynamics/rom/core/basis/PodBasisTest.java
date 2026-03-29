package info.openrocket.core.aerodynamics.rom.core.basis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.rom.core.TestFixtures;
import info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput;
import info.openrocket.core.aerodynamics.rom.core.surface.AeroSurface4D;

public class PodBasisTest {

	@Test
	public void testIdenticalSnapshotsRankOne() {
		GeometrySnapshot s1 = new GeometrySnapshot(TestFixtures.typical4Fin(), syntheticSurface(1.0, "a"));
		GeometrySnapshot s2 = new GeometrySnapshot(TestFixtures.typical4Fin(), syntheticSurface(1.0, "b"));
		GeometrySnapshot s3 = new GeometrySnapshot(TestFixtures.typical4Fin(), syntheticSurface(1.0, "c"));

		PodBasis basis = new PodBasis(List.of(s1, s2, s3), 1e-8, true);
		assertEquals(1, basis.rank);
		assertTrue(basis.reconstructionError(s1.cdOffFlat) < 1e-10);
	}

	@Test
	public void testSmoothVariationLowRank() {
		GeometrySnapshot s1 = new GeometrySnapshot(withBodyScale(TestFixtures.typical4Fin(), 0.8), syntheticSurface(0.8, "s1"));
		GeometrySnapshot s2 = new GeometrySnapshot(withBodyScale(TestFixtures.typical4Fin(), 1.0), syntheticSurface(1.0, "s2"));
		GeometrySnapshot s3 = new GeometrySnapshot(withBodyScale(TestFixtures.typical4Fin(), 1.2), syntheticSurface(1.2, "s3"));
		GeometrySnapshot s4 = new GeometrySnapshot(withBodyScale(TestFixtures.typical4Fin(), 1.4), syntheticSurface(1.4, "s4"));
		GeometrySnapshot s5 = new GeometrySnapshot(withBodyScale(TestFixtures.typical4Fin(), 1.6), syntheticSurface(1.6, "s5"));

		PodBasis basis = new PodBasis(List.of(s1, s2, s3, s4, s5), 1e-3, true);
		assertTrue(basis.rank <= 3);
		assertTrue(basis.relativeEnergy >= 0.999);
	}

	private static RomGeometryInput withBodyScale(RomGeometryInput g, double scale) {
		return new RomGeometryInput(
				g.bodyLength * scale,
				g.maxDiameter,
				g.baseArea,
				g.wetArea * scale,
				g.noseLength,
				g.noseShape,
				(g.bodyLength * scale) / g.maxDiameter,
				g.referenceArea,
				g.boattailLength,
				g.boattailBaseDiameter,
				g.finCount,
				g.finRootChord,
				g.finTipChord,
				g.finSpan,
				g.finThickness,
				g.finSweepAngle,
				g.finWettedArea,
				g.finAxialPosition,
				g.motorExitArea,
				g.surfaceRoughness);
	}

	private static AeroSurface4D syntheticSurface(double scale, String hash) {
		double[] mach = new double[] { 0.5, 1.0, 2.0 };
		double[] logRe = new double[] { 5.0 };
		double[] alpha = new double[] { 0.0 };
		double[] beta = new double[] { 0.0 };

		double[][][][] cdOff = new double[mach.length][1][1][1];
		double[][][][] cdOn = new double[mach.length][1][1][1];
		double[][][][] cdBody = new double[mach.length][1][1][1];
		double[][][][] cn = new double[mach.length][1][1][1];
		double[][][][] cm = new double[mach.length][1][1][1];

		for (int im = 0; im < mach.length; im++) {
			double base = (0.2 + 0.05 * im) * scale;
			cdOff[im][0][0][0] = base;
			cdOn[im][0][0][0] = base * 0.95;
			cdBody[im][0][0][0] = base * 0.9;
			cn[im][0][0][0] = 0.01 * im;
			cm[im][0][0][0] = -0.005 * im;
		}
		return new AeroSurface4D(mach, logRe, alpha, beta, cdOff, cdOn, cdBody, cn, cm, hash, 4, 0.0);
	}
}

package info.openrocket.core.aerodynamics.rom.core.basis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.rom.core.TestFixtures;
import info.openrocket.core.aerodynamics.rom.core.surface.AeroSurface4D;

public class GeometryParametricRomTest {

	@Test
	public void testReconstructSurfaceProducesFinitePositiveCd() {
		GeometrySnapshot s1 = new GeometrySnapshot(TestFixtures.typical4Fin(), syntheticSurface(1.0, "r1"));
		GeometrySnapshot s2 = new GeometrySnapshot(TestFixtures.typical3Fin(), syntheticSurface(1.2, "r2"));
		GeometrySnapshot s3 = new GeometrySnapshot(TestFixtures.noFins(), syntheticSurface(0.9, "r3"));

		PodBasis off = new PodBasis(List.of(s1, s2, s3), 1e-5, true);
		PodBasis on = new PodBasis(List.of(s1, s2, s3), 1e-5, false);
		double[] centroid = s1.featureVector.clone();
		LocalPodRegion region = new LocalPodRegion(centroid, 0.5, off, on, "test");
		GeometryParametricRom rom = GeometryParametricRom.withAutoMagicPoints(
				List.of(region),
				s1.machAxis,
				s1.logReAxis,
				s1.alphaAxis,
				s1.betaAxis);

		AeroSurface4D reconstructed = rom.reconstructSurface(TestFixtures.typical4Fin(), "phase2-test");
		assertEquals(s1.machAxis.length, reconstructed.machAxis.length);
		for (int im = 0; im < reconstructed.machAxis.length; im++) {
			double cd = reconstructed.cdPlumeOff[im][0][0][0];
			assertTrue(Double.isFinite(cd));
			assertTrue(cd >= 0.001);
		}
	}

	private static AeroSurface4D syntheticSurface(double scale, String hash) {
		double[] mach = new double[] { 0.4, 0.8, 1.2, 2.0 };
		double[] logRe = new double[] { 5.0 };
		double[] alpha = new double[] { 0.0 };
		double[] beta = new double[] { 0.0 };
		double[][][][] cdOff = new double[mach.length][1][1][1];
		double[][][][] cdOn = new double[mach.length][1][1][1];
		double[][][][] cdBody = new double[mach.length][1][1][1];
		double[][][][] cn = new double[mach.length][1][1][1];
		double[][][][] cm = new double[mach.length][1][1][1];
		for (int im = 0; im < mach.length; im++) {
			double val = scale * (0.2 + 0.08 * Math.exp(-Math.abs(mach[im] - 1.0)));
			cdOff[im][0][0][0] = val;
			cdOn[im][0][0][0] = 0.94 * val;
			cdBody[im][0][0][0] = 0.90 * val;
		}
		return new AeroSurface4D(mach, logRe, alpha, beta, cdOff, cdOn, cdBody, cn, cm, hash, 4, 0.0);
	}
}

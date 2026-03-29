package info.openrocket.core.aerodynamics.rom.core.sampling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.rom.core.TestFixtures;
import info.openrocket.core.aerodynamics.rom.core.basis.GeometrySnapshot;
import info.openrocket.core.aerodynamics.rom.core.basis.PodBasis;
import info.openrocket.core.aerodynamics.rom.core.surface.AeroSurface4D;

public class MagicPointSelectorTest {

	@Test
	public void testMagicPointsAreDistinct() {
		GeometrySnapshot s1 = new GeometrySnapshot(TestFixtures.typical4Fin(), syntheticSurface(1.0, "m1"));
		GeometrySnapshot s2 = new GeometrySnapshot(TestFixtures.typical4Fin(), syntheticSurface(1.2, "m2"));
		GeometrySnapshot s3 = new GeometrySnapshot(TestFixtures.typical4Fin(), syntheticSurface(0.8, "m3"));

		PodBasis basis = new PodBasis(List.of(s1, s2, s3), 1e-6, true);
		int[] points = MagicPointSelector.selectMagicPoints(basis, 2);
		Set<Integer> unique = new HashSet<>();
		for (int p : points) {
			assertTrue(p >= 0 && p < basis.snapshotLength);
			unique.add(p);
		}
		assertEquals(points.length, unique.size());
	}

	private static AeroSurface4D syntheticSurface(double scale, String hash) {
		double[] mach = new double[] { 0.3, 0.7, 1.1, 2.0 };
		double[] logRe = new double[] { 5.0 };
		double[] alpha = new double[] { 0.0 };
		double[] beta = new double[] { 0.0 };
		double[][][][] cdOff = new double[mach.length][1][1][1];
		double[][][][] cdOn = new double[mach.length][1][1][1];
		double[][][][] cdBody = new double[mach.length][1][1][1];
		double[][][][] cn = new double[mach.length][1][1][1];
		double[][][][] cm = new double[mach.length][1][1][1];
		for (int im = 0; im < mach.length; im++) {
			double val = scale * (0.15 + 0.02 * im * im);
			cdOff[im][0][0][0] = val;
			cdOn[im][0][0][0] = 0.95 * val;
			cdBody[im][0][0][0] = 0.9 * val;
		}
		return new AeroSurface4D(mach, logRe, alpha, beta, cdOff, cdOn, cdBody, cn, cm, hash, 4, 0.0);
	}
}

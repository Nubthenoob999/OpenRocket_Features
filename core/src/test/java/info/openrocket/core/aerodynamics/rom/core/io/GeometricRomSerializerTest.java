package info.openrocket.core.aerodynamics.rom.core.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.rom.core.TestFixtures;
import info.openrocket.core.aerodynamics.rom.core.basis.GeometryParametricRom;
import info.openrocket.core.aerodynamics.rom.core.basis.GeometrySnapshot;
import info.openrocket.core.aerodynamics.rom.core.basis.LocalPodRegion;
import info.openrocket.core.aerodynamics.rom.core.basis.PodBasis;
import info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput;
import info.openrocket.core.aerodynamics.rom.core.surface.AeroSurface4D;

public class GeometricRomSerializerTest {

	@Test
	public void testRoundTrip() throws IOException {
		GeometrySnapshot s1 = new GeometrySnapshot(TestFixtures.typical4Fin(), syntheticSurface(1.0, "io1"));
		GeometrySnapshot s2 = new GeometrySnapshot(TestFixtures.typical3Fin(), syntheticSurface(1.1, "io2"));
		PodBasis off = new PodBasis(List.of(s1, s2), 1e-6, true);
		PodBasis on = new PodBasis(List.of(s1, s2), 1e-6, false);
		LocalPodRegion region = new LocalPodRegion(s1.featureVector.clone(), 0.5, off, on, "io");
		GeometryParametricRom rom = GeometryParametricRom.withAutoMagicPoints(
				List.of(region),
				s1.machAxis,
				s1.logReAxis,
				s1.alphaAxis,
				s1.betaAxis);

		byte[] blob = GeometricRomSerializer.serializeToBase64Gzip(rom, 2, 0.2);
		GeometryParametricRom restored = GeometricRomSerializer.deserializeFromBase64Gzip(blob);

		assertEquals(rom.machAxis.length, restored.machAxis.length);
		assertEquals(rom.getRegions().size(), restored.getRegions().size());
		assertTrue(blob.length > 0);
	}

	@Test
	public void testRoundTripReconstructsWithinTolerance() throws IOException {
		GeometrySnapshot s1 = new GeometrySnapshot(TestFixtures.typical4Fin(), syntheticSurface(1.0, "io1"));
		GeometrySnapshot s2 = new GeometrySnapshot(TestFixtures.typical3Fin(), syntheticSurface(1.1, "io2"));
		PodBasis off = new PodBasis(List.of(s1, s2), 1e-6, true);
		PodBasis on = new PodBasis(List.of(s1, s2), 1e-6, false);
		LocalPodRegion region = new LocalPodRegion(s1.featureVector.clone(), 0.5, off, on, "io");
		GeometryParametricRom rom = GeometryParametricRom.withAutoMagicPoints(
				List.of(region),
				s1.machAxis,
				s1.logReAxis,
				s1.alphaAxis,
				s1.betaAxis);

		byte[] blob = GeometricRomSerializer.serializeToBase64Gzip(rom, 2, 0.2);
		GeometryParametricRom restored = GeometricRomSerializer.deserializeFromBase64Gzip(blob);

		RomGeometryInput query = TestFixtures.typical4Fin();
		AeroSurface4D before = rom.reconstructSurface(query, "before");
		AeroSurface4D after = restored.reconstructSurface(query, "after");

		double offMaxAbsDiff = maxAbsDiff(before.cdPlumeOff, after.cdPlumeOff);
		double onMaxAbsDiff = maxAbsDiff(before.cdPlumeOn, after.cdPlumeOn);

		assertTrue(offMaxAbsDiff <= 1e-12, "Plume-off reconstructed surface drifted: " + offMaxAbsDiff);
		assertTrue(onMaxAbsDiff <= 1e-12, "Plume-on reconstructed surface drifted: " + onMaxAbsDiff);
	}

	@Test
	public void testRoundTripPreservesVersionedCentroidLength() throws IOException {
		GeometrySnapshot s1 = new GeometrySnapshot(TestFixtures.typical4Fin(), syntheticSurface(1.0, "io1"));
		GeometrySnapshot s2 = new GeometrySnapshot(TestFixtures.typical3Fin(), syntheticSurface(1.1, "io2"));
		PodBasis off = new PodBasis(List.of(s1, s2), 1e-6, true);
		PodBasis on = new PodBasis(List.of(s1, s2), 1e-6, false);
		double[] centroid = new double[12];
		System.arraycopy(s1.featureVector, 0, centroid, 0, s1.featureVector.length);
		centroid[10] = 0.25;
		centroid[11] = 0.75;
		LocalPodRegion region = new LocalPodRegion(centroid, 0.5, off, on, "io");
		GeometryParametricRom rom = GeometryParametricRom.withAutoMagicPoints(
				List.of(region),
				s1.machAxis,
				s1.logReAxis,
				s1.alphaAxis,
				s1.betaAxis);

		byte[] blob = GeometricRomSerializer.serializeToBase64Gzip(rom, 2, 0.2);
		GeometryParametricRom restored = GeometricRomSerializer.deserializeFromBase64Gzip(blob);

		assertEquals(12, restored.getRegions().get(0).centroidFeatureVector.length);
	}

	private static double maxAbsDiff(double[][][][] a, double[][][][] b) {
		double max = 0.0;
		for (int im = 0; im < a.length; im++) {
			for (int ir = 0; ir < a[im].length; ir++) {
				for (int ia = 0; ia < a[im][ir].length; ia++) {
					for (int ib = 0; ib < a[im][ir][ia].length; ib++) {
						double d = Math.abs(a[im][ir][ia][ib] - b[im][ir][ia][ib]);
						max = Math.max(max, d);
					}
				}
			}
		}
		return max;
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
			double val = scale * (0.18 + 0.04 * im);
			cdOff[im][0][0][0] = val;
			cdOn[im][0][0][0] = 0.95 * val;
			cdBody[im][0][0][0] = 0.90 * val;
		}
		return new AeroSurface4D(mach, logRe, alpha, beta, cdOff, cdOn, cdBody, cn, cm, hash, 4, 0.0);
	}
}

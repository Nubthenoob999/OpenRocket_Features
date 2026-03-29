package info.openrocket.core.aerodynamics.rom.core.surface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import info.openrocket.core.aerodynamics.rom.DragSurfaceInterpolator;
import info.openrocket.core.aerodynamics.rom.adapter.SurfaceAdapter;
import info.openrocket.core.aerodynamics.rom.core.TestFixtures;
import info.openrocket.core.aerodynamics.rom.core.eval.AeroGridEvaluator4D;
import info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AeroSurface4DInterpolatorTest {

	private AeroSurface4D surface;
	private AeroSurface4DInterpolator interpolator;

	@BeforeAll
	public void setupSurface() {
		RomGeometryInput g = TestFixtures.typical4Fin();
		surface = AeroGridEvaluator4D.evaluate(g, null);
		interpolator = new AeroSurface4DInterpolator(surface);
	}

	@Test
	public void testQueryAtGridPointsMatchesExactly() {
		double maxRel = 0.0;
		for (int im = 0; im < surface.machAxis.length; im++) {
			for (int ir = 0; ir < surface.logReAxis.length; ir++) {
				for (int ia = 0; ia < surface.alphaAxis.length; ia++) {
					for (int ib = 0; ib < surface.betaAxis.length; ib++) {
						double mach = surface.machAxis[im];
						double reL = Math.pow(10.0, surface.logReAxis[ir]);
						double alpha = surface.alphaAxis[ia];
						double beta = surface.betaAxis[ib];
						double expected = surface.cdPlumeOff[im][ir][ia][ib];
						double actual = interpolator.queryCdPlumeOff(mach, reL, alpha, beta);
						double rel = relErr(actual, expected);
						if (rel > maxRel) {
							maxRel = rel;
						}
					}
				}
			}
		}
		assertTrue(maxRel < 0.005);
	}

	@Test
	public void testQueryAtMidpointsHasLowError() {
		for (int im = 0; im < surface.machAxis.length - 1; im++) {
			for (int ir = 0; ir < surface.logReAxis.length - 1; ir++) {
				for (int ia = 0; ia < surface.alphaAxis.length - 1; ia++) {
					for (int ib = 0; ib < surface.betaAxis.length - 1; ib++) {
						double mach = 0.5 * (surface.machAxis[im] + surface.machAxis[im + 1]);
						double logRe = 0.5 * (surface.logReAxis[ir] + surface.logReAxis[ir + 1]);
						double alpha = 0.5 * (surface.alphaAxis[ia] + surface.alphaAxis[ia + 1]);
						double beta = 0.5 * (surface.betaAxis[ib] + surface.betaAxis[ib + 1]);
						double reL = Math.pow(10.0, logRe);
						double interp = interpolator.queryCdPlumeOff(mach, reL, alpha, beta);

						// Compare against the average of cell corners as a reference baseline.
						double maxCorner = Double.NEGATIVE_INFINITY;
						for (int dm = 0; dm <= 1; dm++) {
							for (int dr = 0; dr <= 1; dr++) {
								for (int da = 0; da <= 1; da++) {
									for (int db = 0; db <= 1; db++) {
										double corner = surface.cdPlumeOff[im + dm][ir + dr][ia + da][ib + db];
										maxCorner = Math.max(maxCorner, corner);
									}
								}
							}
						}
						assertTrue(Double.isFinite(interp));
						assertTrue(interp >= 0.001);
						assertTrue(interp <= Math.max(8.0, 2.0 * maxCorner));
					}
				}
			}
		}
	}

	@Test
	public void testBetaZeroParityAndClampingAndIdentities() {
		DragSurfaceInterpolator legacy = new DragSurfaceInterpolator(SurfaceAdapter.toBetaZeroDragSurface(surface));

		double mach = 0.5;
		double reL = 1e6;
		double alpha = 4.0;
		double betaMax = surface.betaAxis[surface.betaAxis.length - 1];

		double cd4dBeta0 = interpolator.queryCdPlumeOff(mach, reL, alpha, 0.0);
		double cd3d = legacy.queryCdPlumeOff(mach, reL, alpha);
		assertTrue(relErr(cd4dBeta0, cd3d) < 0.02);

		double cdClamped = interpolator.queryCdPlumeOff(mach, reL, alpha, betaMax + 5.0);
		double cdAtMax = interpolator.queryCdPlumeOff(mach, reL, alpha, betaMax);
		assertEquals(cdAtMax, cdClamped, 1e-12);

		double cdBeta45 = interpolator.queryCdPlumeOff(mach, reL, 0.0, 45.0);
		double cdBeta0 = interpolator.queryCdPlumeOff(mach, reL, 0.0, 0.0);
		assertTrue(cdBeta45 > cdBeta0);

		AeroSurface4DInterpolator.QueryResult qr = interpolator.query(mach, reL, alpha, 15.0);
		assertEquals(qr.cdPlumeOff - qr.cdBody, qr.dCdFin, 1e-12);
		assertTrue(qr.dCdFin >= -0.005);
	}

	private static double relErr(double a, double b) {
		return Math.abs(a - b) / Math.max(1e-12, Math.abs(b));
	}
}

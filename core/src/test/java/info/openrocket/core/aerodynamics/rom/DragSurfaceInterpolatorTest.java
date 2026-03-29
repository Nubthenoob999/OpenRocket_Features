package info.openrocket.core.aerodynamics.rom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class DragSurfaceInterpolatorTest {

    @Test
    public void testExactKnotRecovery() {
        DragSurface s = DragGridEvaluator.evaluate(RomTestFixtures.standardGeometry(), null);
        DragSurfaceInterpolator interp = new DragSurfaceInterpolator(s);

        for (int im = 0; im < s.machAxis.length; im++) {
            double mach = s.machAxis[im];
            for (int ir = 0; ir < s.logReAxis.length; ir++) {
                double re = Math.pow(10.0, s.logReAxis[ir]);
                for (int ia = 0; ia < s.alphaAxis.length; ia++) {
                    double alpha = s.alphaAxis[ia];
                    double expected = s.cdPlumeOff[im][ir][ia];
                    double actual = interp.queryCdPlumeOff(mach, re, alpha);
                    RomTestFixtures.assertRelativeError(actual, expected, 1e-8,
                            "Knot recovery failed at M=" + mach + " Re=" + re + " alpha=" + alpha);
                }
            }
        }
    }

    @Test
    public void testOutOfBoundsClampingAndNegativeAlphaSymmetry() {
        DragSurfaceInterpolator interp = new DragSurfaceInterpolator(
                DragGridEvaluator.evaluate(RomTestFixtures.standardGeometry(), null));

        assertTrue(interp.queryCdPlumeOff(0.0001, 1e6, 0.0) > 0.0);
        assertTrue(interp.queryCdPlumeOff(99.0, 1e6, 0.0) > 0.0);

        double cdPos = interp.queryCdPlumeOff(0.5, 1e6, 5.0);
        double cdNeg = interp.queryCdPlumeOff(0.5, 1e6, -5.0);
        assertEquals(cdPos, cdNeg, 1e-12);
    }

    @Test
    public void testFiniteAndPositiveAcrossDomain() {
        DragSurfaceInterpolator interp = new DragSurfaceInterpolator(
                DragGridEvaluator.evaluate(RomTestFixtures.standardGeometry(), null));
        double[][] testPoints = new double[][] {
                { 0.5, 1e6, 0.0 },
                { 1.0, 1e5, 5.0 },
                { 2.5, 1e8, 15.0 },
                { 0.01, 1e4, 0.0 },
                { 4.0, 1e8, 15.0 }
        };
        for (double[] p : testPoints) {
            double cd = interp.queryCdPlumeOff(p[0], p[1], p[2]);
            assertTrue(Double.isFinite(cd), "Cd must be finite at M=" + p[0]);
            assertTrue(cd >= 0.001, "Cd must respect lower bound at M=" + p[0]);
        }
    }

    @Test
    public void testMidPointSmoothnessNoLargeDerivativeJumps() {
        DragSurfaceInterpolator interp = new DragSurfaceInterpolator(
                DragGridEvaluator.evaluate(RomTestFixtures.standardGeometry(), null));

        int n = 500;
        double re = 1e6;
        double[] cd = new double[n];
        for (int i = 0; i < n; i++) {
            double mach = 0.01 + i * (4.0 - 0.01) / (n - 1);
            cd[i] = interp.queryCdPlumeOff(mach, re, 0.0);
        }

        double[] diff = new double[n - 1];
        double avgAbsDiff = 0.0;
        for (int i = 0; i < n - 1; i++) {
            diff[i] = cd[i + 1] - cd[i];
            avgAbsDiff += Math.abs(diff[i]);
        }
        avgAbsDiff /= (n - 1);

        for (int i = 1; i < n - 1; i++) {
            double jump = Math.abs(diff[i] - diff[i - 1]);
            assertTrue(jump < 10.0 * avgAbsDiff,
                    "Excessive slope change detected at step " + i);
        }
    }
}

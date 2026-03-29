package info.openrocket.core.aerodynamics.rom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class DragGridEvaluatorTest {

    @Test
    public void testAxisBuilders() {
        double[] mach = DragGridEvaluator.buildMachAxis();
        assertEquals(DragGridEvaluator.N_MACH, mach.length);
        assertEquals(0.01, mach[0], 1e-10);
        assertEquals(4.0, mach[mach.length - 1], 1e-6);
        for (int i = 1; i < mach.length; i++) {
            assertTrue(mach[i] > mach[i - 1], "Mach axis is not strictly increasing at " + i);
        }

        double[] logRe = DragGridEvaluator.buildLogReAxis();
        assertEquals(DragGridEvaluator.N_RE, logRe.length);
        assertEquals(Math.log10(1e4), logRe[0], 1e-10);
        assertEquals(Math.log10(1e8), logRe[logRe.length - 1], 1e-10);

        double[] alpha = DragGridEvaluator.buildAlphaAxis();
        assertEquals(DragGridEvaluator.N_ALPHA, alpha.length);
        assertEquals(0.0, alpha[0], 1e-10);
        assertEquals(15.0, alpha[alpha.length - 1], 1e-6);
    }

    @Test
    public void testPointLevelPhysicsChecks() {
        RomGeometryParameters g = RomTestFixtures.standardGeometry();
        double[][] points = new double[][] {
                { 0.1, 1e5, 0.0 },
                { 0.5, 1e6, 0.0 },
                { 0.9, 1e6, 0.0 },
                { 1.1, 1e6, 0.0 },
                { 2.0, 1e7, 0.0 },
                { 3.5, 1e7, 0.0 },
                { 0.5, 1e6, Math.toRadians(10.0) }
        };
        for (double[] p : points) {
            double cd = DragGridEvaluator.computeCdPlumeOff(p[0], p[1], p[2], g);
            assertTrue(cd > 0.0, "Cd must be positive at M=" + p[0] + " Re=" + p[1]);
        }

        double re = 1e6;
        double cdSub = DragGridEvaluator.computeCdPlumeOff(0.5, re, 0.0, g);
        double cdTrans = DragGridEvaluator.computeCdPlumeOff(1.0, re, 0.0, g);
        double cdSup = DragGridEvaluator.computeCdPlumeOff(3.0, re, 0.0, g);
        assertTrue(cdTrans > cdSub, "Expected transonic rise over subsonic value.");
        assertTrue(cdSup < cdTrans, "Expected supersonic decay after transonic peak.");

        for (double mach : new double[] { 0.5, 1.0, 2.0 }) {
            double cd0 = DragGridEvaluator.computeCdPlumeOff(mach, re, 0.0, g);
            double cd10 = DragGridEvaluator.computeCdPlumeOff(mach, re, Math.toRadians(10.0), g);
            assertTrue(cd10 > cd0, "Expected positive AoA increment at M=" + mach);
        }

        for (double mach : new double[] { 0.3, 0.8, 1.2, 2.5 }) {
            double off = DragGridEvaluator.computeCdPlumeOff(mach, re, 0.0, g);
            double on = DragGridEvaluator.computeCdPlumeOn(mach, re, 0.0, g);
            assertTrue(on <= off + 1e-12, "Plume-on Cd must be <= plume-off at M=" + mach);
        }
    }

    @Test
    public void testGridValidationGates() {
        RomGeometryParameters g = RomTestFixtures.standardGeometry();
        DragSurface s1 = DragGridEvaluator.evaluate(g, null);
        DragSurface s2 = DragGridEvaluator.evaluate(g, null);

        assertEquals(DragGridEvaluator.N_MACH, s1.machAxis.length);
        assertEquals(DragGridEvaluator.N_RE, s1.logReAxis.length);
        assertEquals(DragGridEvaluator.N_ALPHA, s1.alphaAxis.length);
        assertEquals(DragGridEvaluator.N_MACH, s1.cdPlumeOff.length);
        assertEquals(DragGridEvaluator.N_RE, s1.cdPlumeOff[0].length);
        assertEquals(DragGridEvaluator.N_ALPHA, s1.cdPlumeOff[0][0].length);

        for (int im = 0; im < s1.machAxis.length; im++) {
            for (int ir = 0; ir < s1.logReAxis.length; ir++) {
                for (int ia = 0; ia < s1.alphaAxis.length; ia++) {
                    assertTrue(s1.cdPlumeOff[im][ir][ia] > 0.0, "cdPlumeOff must be positive at im=" + im);
                    assertTrue(s1.cdPlumeOn[im][ir][ia] > 0.0, "cdPlumeOn must be positive at im=" + im);
                    assertTrue(s1.cdPlumeOn[im][ir][ia] <= s1.cdPlumeOff[im][ir][ia] + 1e-12,
                            "Plume-on must not exceed plume-off at im=" + im + " ir=" + ir + " ia=" + ia);
                    if (ia > 0) {
                        assertTrue(s1.cdPlumeOff[im][ir][ia] >= s1.cdPlumeOff[im][ir][ia - 1] - 1e-9,
                                "Cd must be non-decreasing with alpha at im=" + im + " ir=" + ir);
                    }
                }
            }
        }

        for (int ir = 0; ir < s1.logReAxis.length; ir++) {
            double[] cd = new double[s1.machAxis.length];
            for (int im = 0; im < s1.machAxis.length; im++) {
                cd[im] = s1.cdPlumeOff[im][ir][0];
            }

            int idx08 = 0;
            int idx10 = 0;
            int idx30 = s1.machAxis.length - 1;
            for (int im = 0; im < s1.machAxis.length; im++) {
                if (Math.abs(s1.machAxis[im] - 0.8) < Math.abs(s1.machAxis[idx08] - 0.8)) {
                    idx08 = im;
                }
                if (Math.abs(s1.machAxis[im] - 1.0) < Math.abs(s1.machAxis[idx10] - 1.0)) {
                    idx10 = im;
                }
                if (Math.abs(s1.machAxis[im] - 3.0) < Math.abs(s1.machAxis[idx30] - 3.0)) {
                    idx30 = im;
                }
            }

            double cd08 = cd[idx08];
            double cd10 = cd[idx10];
            double cd30 = cd[idx30];
            if (cd10 > cd08 && cd10 > cd30) {
                int peakIdx = RomTestFixtures.argmax(cd);
                double peakMach = s1.machAxis[peakIdx];
                assertTrue(peakMach >= 0.85 && peakMach <= 1.30,
                        "Peak Cd should occur in transonic band when a rise exists, got M=" + peakMach);
            }
        }

        assertFalse(s1.geometryHash.isEmpty());
        assertEquals(s1.geometryHash, s2.geometryHash);
    }
}

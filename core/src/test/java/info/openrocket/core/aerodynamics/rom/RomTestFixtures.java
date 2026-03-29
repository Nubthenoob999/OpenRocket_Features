package info.openrocket.core.aerodynamics.rom;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class RomTestFixtures {

    private RomTestFixtures() {
    }

    static RomGeometryParameters standardGeometry() {
        RomGeometryParameters g = new RomGeometryParameters();
        g.bodyLength = 2.0;
        g.maxDiameter = 0.076;
        g.referenceArea = Math.PI * Math.pow(0.038, 2.0);
        g.baseArea = g.referenceArea;
        g.wetArea = Math.PI * g.maxDiameter * g.bodyLength;
        g.noseLength = 0.30;
        g.noseShape = RomGeometryParameters.NoseShape.OGIVE;
        g.finessRatio = g.bodyLength / g.maxDiameter;
        g.finCount = 4;
        g.finRootChord = 0.15;
        g.finTipChord = 0.06;
        g.finSpan = 0.09;
        g.finThickness = 0.003;
        g.finSweepAngle = Math.toRadians(45.0);
        g.finWettedArea = 4.0 * 0.5 * (g.finRootChord + g.finTipChord) * g.finSpan * 2.0;
        g.boattailLength = 0.08;
        g.boattailBaseDiameter = 0.054;
        g.motorExitDiameter = 0.038;
        g.motorExitArea = Math.PI * Math.pow(g.motorExitDiameter / 2.0, 2.0);
        g.surfaceRoughness = 6.4e-6;
        return g;
    }

    static void assertRelativeError(double actual, double expected, double tolerance) {
        assertRelativeError(actual, expected, tolerance, "");
    }

    static void assertRelativeError(double actual, double expected, double tolerance, String message) {
        double relErr = Math.abs(actual - expected) / Math.max(Math.abs(expected), 1e-30);
        assertTrue(relErr <= tolerance,
                message + " Relative error " + relErr + " exceeds tolerance " + tolerance
                        + " (actual=" + actual + " expected=" + expected + ")");
    }

    static int argmax(double[] arr) {
        int idx = 0;
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] > arr[idx]) {
                idx = i;
            }
        }
        return idx;
    }

    static double[] removeIndex(double[] arr, int k) {
        double[] out = new double[arr.length - 1];
        int j = 0;
        for (int i = 0; i < arr.length; i++) {
            if (i != k) {
                out[j++] = arr[i];
            }
        }
        return out;
    }
}

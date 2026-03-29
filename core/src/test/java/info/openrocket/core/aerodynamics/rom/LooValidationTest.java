package info.openrocket.core.aerodynamics.rom;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class LooValidationTest {

    @Test
    public void testLooRmseAndMaxErrorGates() {
        RomGeometryParameters g = RomTestFixtures.standardGeometry();
        double[] machFull = DragGridEvaluator.buildMachAxis();
        double[] logReFull = DragGridEvaluator.buildLogReAxis();
        double[] alphaFull = DragGridEvaluator.buildAlphaAxis();

        int nM = machFull.length;
        int nR = logReFull.length;
        int nA = alphaFull.length;

        double sumSq = 0.0;
        int count = 0;

        double maxErrPercent = 0.0;
        double worstMach = 0.0;
        double worstRe = 0.0;
        double worstAlpha = 0.0;

        for (int k = 1; k < nM - 1; k++) {
            double[] machReduced = RomTestFixtures.removeIndex(machFull, k);

            for (int ir = 0; ir < nR; ir++) {
                double re = Math.pow(10.0, logReFull[ir]);
                for (int ia = 0; ia < nA; ia++) {
                    double alphaRad = Math.toRadians(alphaFull[ia]);

                    double[] cdReduced = new double[machReduced.length];
                    for (int j = 0; j < machReduced.length; j++) {
                        cdReduced[j] = DragGridEvaluator.computeCdPlumeOff(machReduced[j], re, alphaRad, g);
                    }

                    double cdTrue = DragGridEvaluator.computeCdPlumeOff(machFull[k], re, alphaRad, g);
                    double cdPred = new PchipInterpolator1D(machReduced, cdReduced).evaluate(machFull[k]);

                    double relErr = Math.abs(cdPred - cdTrue) / Math.max(cdTrue, 1e-12);
                    sumSq += relErr * relErr;
                    count++;

                    double relErrPercent = relErr * 100.0;
                    if (relErrPercent > maxErrPercent) {
                        maxErrPercent = relErrPercent;
                        worstMach = machFull[k];
                        worstRe = re;
                        worstAlpha = alphaFull[ia];
                    }
                }
            }
        }

        double rmsePercent = Math.sqrt(sumSq / Math.max(1, count)) * 100.0;

        assertTrue(rmsePercent < 2.0,
                "LOO RMSE = " + rmsePercent + "% exceeds 2% gate");
        assertTrue(maxErrPercent < 5.0,
                "Max LOO error = " + maxErrPercent + "% exceeds 5% gate at M="
                        + worstMach + " Re=" + worstRe + " alpha=" + worstAlpha);
    }
}

package info.openrocket.core.aerodynamics.rom;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Integration-style validation tests for ROM drag surfaces.
 *
 * These tests mirror the paper-style acceptance checks using existing Java ROM
 * implementations as the oracle and lookup interpolation as the surrogate.
 */
public class RomValidationPipelineTest {

	private static final class Sample {
		final double mach;
		final double re;
		final double alphaDeg;
		final String regime;

		Sample(double mach, double re, double alphaDeg, String regime) {
			this.mach = mach;
			this.re = re;
			this.alphaDeg = alphaDeg;
			this.regime = regime;
		}
	}

	private static RomGeometryParameters sampleGeometry() {
		RomGeometryParameters g = new RomGeometryParameters();
		g.bodyLength = 1.5;
		g.maxDiameter = 0.1;
		g.referenceArea = Math.PI * Math.pow(g.maxDiameter / 2.0, 2.0);
		g.baseArea = g.referenceArea;
		g.wetArea = 0.58;
		g.noseLength = 0.3;
		g.noseShape = RomGeometryParameters.NoseShape.OGIVE;
		g.finessRatio = g.bodyLength / g.maxDiameter;
		g.finCount = 4;
		g.finRootChord = 0.15;
		g.finTipChord = 0.07;
		g.finSpan = 0.05;
		g.finThickness = 0.003;
		g.finSweepAngle = Math.toRadians(22.0);
		g.finWettedArea = 2.0 * (0.5 * (g.finRootChord + g.finTipChord) * g.finSpan);
		g.surfaceRoughness = 6.4e-6;
		g.boattailLength = 0.0;
		g.boattailBaseDiameter = 0.0;
		g.motorExitDiameter = 0.038;
		g.motorExitArea = Math.PI * Math.pow(g.motorExitDiameter / 2.0, 2.0);
		return g;
	}

	private static String regimeForMach(double mach) {
		if (mach < 0.8) {
			return "subsonic";
		}
		if (mach <= 1.2) {
			return "transonic";
		}
		return "supersonic";
	}

	private static List<Sample> holdoutSamples() {
		List<Sample> samples = new ArrayList<>();
		double[] machValues = new double[] {
				0.18, 0.35, 0.60, 0.78,
				0.82, 0.90, 1.00, 1.10, 1.18,
				1.30, 1.60, 2.00, 3.00
		};
		double[] reValues = new double[] { 2.3e5, 9.1e5, 4.2e6, 2.0e7 };
		double[] alphaValues = new double[] { 0.0, 3.5, 7.0 };

		for (double mach : machValues) {
			for (double re : reValues) {
				for (double alpha : alphaValues) {
					samples.add(new Sample(mach, re, alpha, regimeForMach(mach)));
				}
			}
		}
		return samples;
	}

	@Test
	public void testRegimeStratifiedHoldoutAccuracy() {
		RomGeometryParameters g = sampleGeometry();
		DragSurface surface = DragGridEvaluator.evaluate(g, null);
		DragSurfaceInterpolator rom = new DragSurfaceInterpolator(surface);

		double sumPctSub = 0.0;
		double sumPctTrans = 0.0;
		double sumPctSup = 0.0;
		int nSub = 0;
		int nTrans = 0;
		int nSup = 0;
		double maxRelErr = 0.0;

		for (Sample s : holdoutSamples()) {
			double truth = DragGridEvaluator.computeCdPlumeOff(s.mach, s.re, Math.toRadians(s.alphaDeg), g);
			double pred = rom.queryCdPlumeOff(s.mach, s.re, s.alphaDeg);
			double pct = Math.abs(pred - truth) / Math.max(1e-9, truth);
			maxRelErr = Math.max(maxRelErr, pct);

			switch (s.regime) {
				case "subsonic":
					sumPctSub += pct;
					nSub++;
					break;
				case "transonic":
					sumPctTrans += pct;
					nTrans++;
					break;
				default:
					sumPctSup += pct;
					nSup++;
					break;
			}
		}

		double mapeSub = sumPctSub / Math.max(1, nSub);
		double mapeTrans = sumPctTrans / Math.max(1, nTrans);
		double mapeSup = sumPctSup / Math.max(1, nSup);

		assertTrue(mapeSub < 0.02, "Subsonic MAPE should remain below 2%.");
		assertTrue(mapeTrans < 0.03, "Transonic MAPE should remain below 3%.");
		assertTrue(mapeSup < 0.02, "Supersonic MAPE should remain below 2%.");
		assertTrue(maxRelErr < 0.05, "Global relative L_inf error should remain below 5%.");
	}

	@Test
	public void testGradientConsistencyInTransonicBand() {
		RomGeometryParameters g = sampleGeometry();
		DragSurface surface = DragGridEvaluator.evaluate(g, null);
		DragSurfaceInterpolator rom = new DragSurfaceInterpolator(surface);

		double re = 1.2e6;
		double alphaDeg = 2.0;
		double dM = 0.015;
		double maxRelativeErr = 0.0;
		double maxAbsoluteErr = 0.0;

		for (double mach = 0.82; mach <= 1.28; mach += 0.04) {
			double romPlus = rom.queryCdPlumeOff(mach + dM, re, alphaDeg);
			double romMinus = rom.queryCdPlumeOff(mach - dM, re, alphaDeg);
			double gradRom = (romPlus - romMinus) / (2.0 * dM);

			double oraclePlus = DragGridEvaluator.computeCdPlumeOff(mach + dM, re, Math.toRadians(alphaDeg), g);
			double oracleMinus = DragGridEvaluator.computeCdPlumeOff(mach - dM, re, Math.toRadians(alphaDeg), g);
			double gradOracle = (oraclePlus - oracleMinus) / (2.0 * dM);

			double absErr = Math.abs(gradRom - gradOracle);
			double relErr = absErr / Math.max(1e-8, Math.abs(gradOracle));
			maxAbsoluteErr = Math.max(maxAbsoluteErr, absErr);
			if (Math.abs(gradOracle) > 0.05) {
				maxRelativeErr = Math.max(maxRelativeErr, relErr);
			}
		}

		assertTrue(maxRelativeErr < 0.30,
				"Relative transonic gradient mismatch should stay below 30% where slope is significant.");
		assertTrue(maxAbsoluteErr < 0.10,
				"Absolute transonic gradient mismatch should stay below 0.10 Cd/Mach.");
	}

	@Test
	public void testPhysicsInvariantSweep() {
		RomGeometryParameters g = sampleGeometry();
		DragSurface surface = DragGridEvaluator.evaluate(g, null);
		DragSurfaceInterpolator rom = new DragSurfaceInterpolator(surface);

		double[] reValues = new double[] { 3e5, 1e6, 8e6 };
		double[] alphaValues = new double[] { 0.0, 5.0, 10.0 };

		for (double re : reValues) {
			for (double alphaDeg : alphaValues) {
				for (double mach = 0.05; mach <= 4.0; mach += 0.1) {
					double cd = rom.queryCdPlumeOff(mach, re, alphaDeg);
					assertTrue(cd > 0.0, "Cd must stay positive over the validation domain.");
				}
			}
		}

		double cdStartRise = rom.queryCdPlumeOff(0.80, 1e6, 0.0);
		double cdNearPeak = rom.queryCdPlumeOff(1.05, 1e6, 0.0);
		assertTrue(cdNearPeak > cdStartRise,
				"Cd should increase overall from Mach 0.8 to 1.05 in transonic rise.");

		double prevSup = rom.queryCdPlumeOff(1.55, 1e6, 0.0);
		for (double mach = 1.60; mach <= 3.8; mach += 0.05) {
			double cd = rom.queryCdPlumeOff(mach, 1e6, 0.0);
			assertTrue(cd <= prevSup + 2e-3, "Cd should show smooth supersonic decay after the peak.");
			prevSup = cd;
		}

		double cdAlpha0 = rom.queryCdPlumeOff(0.6, 1e6, 0.0);
		double cdAlpha5 = rom.queryCdPlumeOff(0.6, 1e6, 5.0);
		double cdAlpha10 = rom.queryCdPlumeOff(0.6, 1e6, 10.0);
		assertTrue(cdAlpha5 > cdAlpha0);
		assertTrue(cdAlpha10 > cdAlpha5);
	}
}

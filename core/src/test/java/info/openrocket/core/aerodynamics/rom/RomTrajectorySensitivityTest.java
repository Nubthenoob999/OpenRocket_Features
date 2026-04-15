package info.openrocket.core.aerodynamics.rom;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests for trajectory-level sensitivity relationships used to validate ROM drag quality.
 */
public class RomTrajectorySensitivityTest {

	private static final double G = 9.80665;

	private static double dragLossFraction(double v0Ms, double mKg, double cd, double refAreaM2, double rho) {
		double beta = rho * cd * refAreaM2 * v0Ms * v0Ms / (2.0 * mKg * G);
		return 1.0 - (Math.log(1.0 + beta) / beta);
	}

	private static double coastDeltaH(double v0Ms, double mKg, double cd, double refAreaM2, double rho) {
		double numerator = mKg;
		double denominator = rho * cd * refAreaM2;
		double inside = 1.0 + (rho * cd * refAreaM2 * v0Ms * v0Ms) / (2.0 * mKg * G);
		return (numerator / denominator) * Math.log(inside);
	}

	private static double barrowmanLikeCd(double mach, double reL, double finenessRatio, double baseAreaFraction) {
		double cf = 0.455 / Math.pow(Math.log10(Math.max(reL, 1e5)), 2.58);
		double cfComp;
		if (mach < 1.0) {
			cfComp = cf / (1.0 + 0.2044 * mach * mach);
		} else {
			double term = 1.0 + 0.15 * mach * mach;
			cfComp = cf / Math.pow(term, 0.58);
		}

		double wettedOverRef = 4.0 * Math.max(8.0, finenessRatio);
		double cdFriction = cfComp * wettedOverRef;
		double cdBase = (mach < 1.0)
				? (0.12 + 0.13 * mach * mach) * baseAreaFraction
				: (0.25 / Math.max(1.0, mach)) * baseAreaFraction;

		if (mach >= 0.8 && mach <= 1.2) {
			double t = (mach - 0.8) / 0.4;
			double cd08 = cf / (1.0 + 0.2044 * 0.8 * 0.8) * wettedOverRef + (0.12 + 0.13 * 0.8 * 0.8) * baseAreaFraction;
			double cd12 = cf / Math.pow(1.0 + 0.15 * 1.2 * 1.2, 0.58) * wettedOverRef + (0.25 / 1.2) * baseAreaFraction;
			double linear = (1.0 - t) * cd08 + t * cd12;
			double peakEnvelope = 1.0 + 0.25 * (1.0 - Math.min(1.0, Math.abs(mach - 1.0) / 0.2));
			return linear * peakEnvelope;
		}

		return cdFriction + cdBase;
	}

	private static RomGeometryParameters sampleGeometry() {
		RomGeometryParameters g = new RomGeometryParameters();
		g.bodyLength = 1.5;
		g.maxDiameter = 0.1;
		g.referenceArea = Math.PI * Math.pow(g.maxDiameter / 2.0, 2.0);
		g.baseArea = g.referenceArea;
		g.wetArea = 0.58;
		g.bodyWetArea = g.wetArea;
		g.noseLength = 0.3;
		g.noseShape = RomGeometryParameters.NoseShape.OGIVE;
		g.finessRatio = g.bodyLength / g.maxDiameter;
		g.finCount = 4;
		g.finRootChord = 0.15;
		g.finTipChord = 0.07;
		g.finSpan = 0.05;
		g.finThickness = 0.003;
		g.finSweepAngle = Math.toRadians(22.0);
		g.finWettedArea = g.finCount * 2.0 * (0.5 * (g.finRootChord + g.finTipChord) * g.finSpan);
		g.surfaceRoughness = 6.4e-6;
		g.boattailLength = 0.0;
		g.boattailBaseDiameter = 0.0;
		g.motorExitDiameter = 0.0;
		g.motorExitArea = 0.0;
		return g;
	}

	@Test
	public void testApogeeSensitivityRelationship() {
		double v0 = 165.0;
		double mass = 22.0;
		double cd = 0.45;
		double area = Math.PI * Math.pow(0.1 / 2.0, 2.0);
		double rho = 1.225;

		double fDrag = dragLossFraction(v0, mass, cd, area, rho);
		assertTrue(fDrag > 0.0 && fDrag < 1.0, "Drag-loss fraction must remain physically bounded.");

		double delta = 0.05;
		double h0 = coastDeltaH(v0, mass, cd, area, rho);
		double h1 = coastDeltaH(v0, mass, cd * (1.0 + delta), area, rho);

		double actualRelative = (h1 - h0) / h0;
		double predictedRelative = -delta * fDrag;
		double relError = Math.abs(actualRelative - predictedRelative) / Math.max(1e-9, Math.abs(predictedRelative));

		assertTrue(relError < 0.05, "Linear sensitivity approximation should hold to within 5%.");
	}

	@Test
	public void testBarrowmanLikeBaselineShowsTransonicMismatch() {
		RomGeometryParameters g = sampleGeometry();
		double re = 1e6;

		double sumErr = 0.0;
		double sumAbsErr = 0.0;
		int count = 0;
		for (double mach = 0.85; mach <= 1.15; mach += 0.05) {
			double oracle = DragGridEvaluator.computeCdPlumeOff(mach, re, 0.0, g);
			double baseline = barrowmanLikeCd(mach, re, g.finessRatio, g.baseArea / g.referenceArea);
			double fracErr = (baseline - oracle) / Math.max(1e-9, oracle);
			sumErr += fracErr;
			sumAbsErr += Math.abs(fracErr);
			count++;
		}

		double meanErr = sumErr / Math.max(1, count);
		double meanAbsErr = sumAbsErr / Math.max(1, count);
		assertTrue(meanAbsErr > 0.10,
				"Barrowman-like baseline should show >10% mean absolute transonic mismatch versus ROM oracle.");
		assertTrue(Math.abs(meanErr) > 0.03,
				"Barrowman-like baseline should show a non-trivial signed bias in transonic regime.");
	}
}

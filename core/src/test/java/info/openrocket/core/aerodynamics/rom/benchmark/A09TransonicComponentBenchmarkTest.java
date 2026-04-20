package info.openrocket.core.aerodynamics.rom.benchmark;

import static info.openrocket.core.aerodynamics.rom.benchmark.BenchmarkAssertions.assertCloseTo;
import static info.openrocket.core.aerodynamics.rom.benchmark.BenchmarkAssertions.assertInRange;
import static info.openrocket.core.aerodynamics.rom.benchmark.BenchmarkAssertions.assertMonotonicallyIncreasing;

import info.openrocket.core.aerodynamics.rom.RomGeometryParameters;
import info.openrocket.core.aerodynamics.rom.RomGeometryParameters.NoseShape;
import info.openrocket.core.aerodynamics.rom.core.physics.TransonicBlendingModel;
import info.openrocket.core.aerodynamics.rom.WaveDragModel;
import info.openrocket.core.aerodynamics.rom.integration.ConfidenceScorer;
import info.openrocket.core.aerodynamics.rom.integration.ConfidenceScorer.ConfidenceLevel;
import info.openrocket.core.util.BaseTestCase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A09 transonic component benchmark anchored to the Bachalo-Johnson
 * axisymmetric transonic bump reference condition.
 *
 * <p>The A09 corpus does not provide a direct rocket-force table. Instead it
 * defines a validated transonic flow regime with a shock-induced separation
 * system at M = 0.875 and Re/m = 13.6e6. This class therefore locks the ROM's
 * component behavior in the same regime: transonic blending, wave-drag onset,
 * and confidence degradation under shock/separation conditions.
 */
@DisplayName("A09 – Transonic component benchmark")
public class A09TransonicComponentBenchmarkTest extends BaseTestCase {

	private static final String DATASET = "A09";
	private static final String SRC = "Bachalo-Johnson axisymmetric bump";
	private static final double A09_MACH = 0.875;
	private static final double A09_RE_PER_M = 13.6e6;

	@Test
	void a09ReferenceConditionIsInsideTransonicBand() {
		double ws = TransonicBlendingModel.sigmaSubsonic(A09_MACH);
		double wt = TransonicBlendingModel.sigmaTransonic(A09_MACH);
		double wp = TransonicBlendingModel.sigmaSupersonic(A09_MACH);

		assertInRange(BenchmarkAssertions.caseLabel(DATASET, SRC, "A09 Mach in transonic band"),
				A09_MACH, 0.85, 1.15);
		assertInRange(BenchmarkAssertions.caseLabel(DATASET, SRC, "weight normalization"),
				ws + wt + wp, 0.999999, 1.000001);
	}

	@Test
	void transonicWeightDominatesAtA09Mach() {
		double ws = TransonicBlendingModel.sigmaSubsonic(A09_MACH);
		double wt = TransonicBlendingModel.sigmaTransonic(A09_MACH);
		double wp = TransonicBlendingModel.sigmaSupersonic(A09_MACH);

		org.junit.jupiter.api.Assertions.assertTrue(
				wt > ws && wt > wp,
				BenchmarkAssertions.caseLabel(DATASET, SRC, "M=0.875 transonic dominance")
						+ " – ws=" + ws + " wt=" + wt + " wp=" + wp);
	}

	@Test
	void transonicWeightPeaksNearUnitMach() {
		double[] transonicWeights = {
				TransonicBlendingModel.sigmaTransonic(0.70),
				TransonicBlendingModel.sigmaTransonic(0.80),
				TransonicBlendingModel.sigmaTransonic(0.90),
				TransonicBlendingModel.sigmaTransonic(1.00)
		};
		assertMonotonicallyIncreasing(
				BenchmarkAssertions.caseLabel(DATASET, SRC, "sigma_transonic rise toward M=1"),
				transonicWeights);

		double peak = TransonicBlendingModel.sigmaTransonic(1.00);
		double afterPeak = TransonicBlendingModel.sigmaTransonic(1.20);
		org.junit.jupiter.api.Assertions.assertTrue(
				peak > afterPeak,
				BenchmarkAssertions.caseLabel(DATASET, SRC, "sigma_transonic decays after peak")
						+ " – peak=" + peak + " after=" + afterPeak);
	}

	@Test
	void blendedDragStaysInsideRegimeEnvelope() {
		double cdSub = 0.24;
		double cdTrans = 0.46;
		double cdSup = 0.33;
		double blended = TransonicBlendingModel.blend(A09_MACH, cdSub, cdTrans, cdSup);

		assertInRange(BenchmarkAssertions.caseLabel(DATASET, SRC, "blended drag envelope"),
				blended, Math.min(cdSub, Math.min(cdTrans, cdSup)), Math.max(cdSub, Math.max(cdTrans, cdSup)));
		org.junit.jupiter.api.Assertions.assertTrue(
				blended > cdSub,
				BenchmarkAssertions.caseLabel(DATASET, SRC, "transonic drag rise retained")
						+ " – blended=" + blended + " sub=" + cdSub);
	}

	@Test
	void confidenceDefaultsToMediumInA09RegimeWithoutCalibrationWhenSeparationIsClean() {
		ConfidenceScorer scorer = new ConfidenceScorer();
		ConfidenceLevel level = scorer.score(A09_MACH, Math.toRadians(3.0), Math.toRadians(10.0), false,
				new double[]{0.02, 0.03, 0.01, 0.02});
		org.junit.jupiter.api.Assertions.assertEquals(
				ConfidenceLevel.MEDIUM,
				level,
				BenchmarkAssertions.caseLabel(DATASET, SRC, "A09 confidence without calibration"));
	}

	@Test
	void confidenceDropsToLowWhenTransonicSeparationFractionRises() {
		ConfidenceScorer scorer = new ConfidenceScorer();
		ConfidenceLevel level = scorer.score(A09_MACH, Math.toRadians(4.0), Math.toRadians(10.0), false,
				new double[]{0.20, 0.16, 0.12, 0.18});
		org.junit.jupiter.api.Assertions.assertEquals(
				ConfidenceLevel.LOW,
				level,
				BenchmarkAssertions.caseLabel(DATASET, SRC, "A09 shock-induced separation penalty"));
	}

	@Test
	void confidenceBecomesHighWithCalibrationOverlayInA09Regime() {
		ConfidenceScorer scorer = new ConfidenceScorer();
		ConfidenceLevel level = scorer.score(A09_MACH, Math.toRadians(3.0), Math.toRadians(10.0), true,
				new double[]{0.01, 0.02, 0.03, 0.02});
		org.junit.jupiter.api.Assertions.assertEquals(
				ConfidenceLevel.HIGH,
				level,
				BenchmarkAssertions.caseLabel(DATASET, SRC, "A09 calibrated confidence"));
	}

	@Test
	void confidenceWarningsReportTransonicAndSeparationContext() {
		ConfidenceScorer scorer = new ConfidenceScorer();
		String[] warnings = scorer.generateWarnings(A09_MACH, Math.toRadians(10.0), Math.toRadians(8.0),
				false, new double[]{0.15, 0.11, 0.14, 0.12});

		org.junit.jupiter.api.Assertions.assertTrue(
				warnings.length >= 2,
				BenchmarkAssertions.caseLabel(DATASET, SRC, "warning count") + " – got " + warnings.length);
	}

	@Test
	void criticalMachPrecedesDragDivergenceForSlenderOgiveBody() {
		RomGeometryParameters g = slenderOgiveBenchmarkGeometry();
		double mCrit = WaveDragModel.criticalMach(g);
		double mdd = WaveDragModel.dragDivergenceMach(g);

		org.junit.jupiter.api.Assertions.assertTrue(
				mCrit < mdd,
				BenchmarkAssertions.caseLabel(DATASET, SRC, "Mcrit < Mdd")
						+ " – Mcrit=" + mCrit + " Mdd=" + mdd);
		assertInRange(BenchmarkAssertions.caseLabel(DATASET, SRC, "A09 near onset band"),
				A09_MACH, mCrit - 0.15, mdd + 0.20);
	}

	@Test
	void noseWaveDragTurnsOnAcrossSonicTransition() {
		RomGeometryParameters g = slenderOgiveBenchmarkGeometry();
		double cdNearSub = WaveDragModel.cdNoseWaveSupersonic(0.95, g);
		double cdSup = WaveDragModel.cdNoseWaveSupersonic(1.20, g);

		org.junit.jupiter.api.Assertions.assertTrue(
				cdSup >= cdNearSub,
				BenchmarkAssertions.caseLabel(DATASET, SRC, "nose wave drag onset")
						+ " – sub=" + cdNearSub + " sup=" + cdSup);
	}

	@Test
	void a09ReferenceReynoldsNumberIsHighEnoughForShockBoundaryLayerInteraction() {
		assertCloseTo(BenchmarkAssertions.caseLabel(DATASET, SRC, "A09 Reynolds per meter"),
				13.6e6, A09_RE_PER_M, 1e-12, 1e-9);
		assertInRange(BenchmarkAssertions.caseLabel(DATASET, SRC, "A09 Reynolds order"),
				Math.log10(A09_RE_PER_M), 7.0, 8.0);
	}

	private static RomGeometryParameters slenderOgiveBenchmarkGeometry() {
		RomGeometryParameters g = new RomGeometryParameters();
		g.bodyLength = 2.0;
		g.maxDiameter = 0.15;
		g.referenceArea = Math.PI * g.maxDiameter * g.maxDiameter / 4.0;
		g.baseArea = g.referenceArea;
		g.wetArea = 0.95;
		g.noseLength = 0.35;
		g.noseShape = NoseShape.OGIVE;
		g.finessRatio = g.bodyLength / g.maxDiameter;
		g.boattailLength = 0.0;
		g.boattailBaseDiameter = g.maxDiameter;
		g.finCount = 0;
		g.finRootChord = 0.0;
		g.finTipChord = 0.0;
		g.finSpan = 0.0;
		g.finThickness = 0.0;
		g.finSweepAngle = 0.0;
		g.finWettedArea = 0.0;
		g.motorExitDiameter = 0.0;
		g.motorExitArea = 0.0;
		g.surfaceRoughness = 6.4e-6;
		return g;
	}
}
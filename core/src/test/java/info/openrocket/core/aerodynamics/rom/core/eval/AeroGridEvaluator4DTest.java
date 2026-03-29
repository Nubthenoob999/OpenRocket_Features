package info.openrocket.core.aerodynamics.rom.core.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.rom.DragGridEvaluator;
import info.openrocket.core.aerodynamics.rom.DragSurface;
import info.openrocket.core.aerodynamics.rom.DragSurfaceInterpolator;
import info.openrocket.core.aerodynamics.rom.RomGeometryParameters;
import info.openrocket.core.aerodynamics.rom.core.TestFixtures;
import info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput;

public class AeroGridEvaluator4DTest {

	@Test
	public void testComputePointExpectedRangesAndTrends() {
		RomGeometryInput g = TestFixtures.typical4Fin();

		AeroGridEvaluator4D.PointResult sub = AeroGridEvaluator4D.computePoint(0.3, 1e6, 0.0, 0.0, g);
		AeroGridEvaluator4D.PointResult trans = AeroGridEvaluator4D.computePoint(0.9, 1e6, 0.0, 0.0, g);
		AeroGridEvaluator4D.PointResult sup = AeroGridEvaluator4D.computePoint(2.0, 1e6, 0.0, 0.0, g);
		AeroGridEvaluator4D.PointResult beta = AeroGridEvaluator4D.computePoint(0.3, 1e6, 0.0, Math.PI / 4.0, g);
		AeroGridEvaluator4D.PointResult alpha = AeroGridEvaluator4D.computePoint(0.3, 1e6, 0.087, 0.0, g);

		assertTrue(sub.cdPlumeOff >= 0.15 && sub.cdPlumeOff <= 0.70);
		assertTrue(sup.cdPlumeOff < trans.cdPlumeOff);
		assertTrue(sub.cdPlumeOn < sub.cdPlumeOff);
		assertTrue(beta.cdPlumeOff > sub.cdPlumeOff);

		assertEquals(0.0, sub.CN, 1e-10);
		assertTrue(alpha.CN > 0.0);
		assertTrue(alpha.Cm < 0.0);
	}

	@Test
	public void testBetaZeroParityWithLegacy3DAtOnePoint() {
		RomGeometryInput g4d = TestFixtures.typical4Fin();
		RomGeometryParameters g3d = toLegacyGeometry(g4d);

		AeroGridEvaluator4D.PointResult p = AeroGridEvaluator4D.computePoint(0.3, 1e6, 0.0, 0.0, g4d);
		DragSurface s3d = DragGridEvaluator.evaluate(g3d, null);
		DragSurfaceInterpolator i3d = new DragSurfaceInterpolator(s3d);
		double cd3d = i3d.queryCdPlumeOff(0.3, 1e6, 0.0);

		double rel = Math.abs(p.cdPlumeOff - cd3d) / Math.max(1e-12, Math.abs(cd3d));
		assertTrue(rel < 0.01);
	}

	@Test
	public void testEvaluatePerformanceAndBetaAxisBehavior() {
		RomGeometryInput g4 = TestFixtures.typical4Fin();
		RomGeometryInput g3 = TestFixtures.typical3Fin();
		RomGeometryInput g0 = TestFixtures.noFins();

		assertTimeoutPreemptively(Duration.ofSeconds(20), () -> AeroGridEvaluator4D.evaluate(g4, null));

		double[] alpha = AeroGridEvaluator4D.buildAlphaAxis();
		assertEquals(AeroGridEvaluator4D.N_ALPHA, alpha.length);
		assertEquals(0.0, alpha[0], 0.0);
		assertEquals(15.0, alpha[alpha.length - 1], 0.0);

		double[] beta4 = AeroGridEvaluator4D.buildBetaAxis(g4);
		assertEquals(9, beta4.length);
		assertEquals(45.0, beta4[beta4.length - 1], 0.0);

		double[] beta3 = AeroGridEvaluator4D.buildBetaAxis(g3);
		assertEquals(9, beta3.length);
		assertEquals(60.0, beta3[beta3.length - 1], 0.0);

		double[] beta0 = AeroGridEvaluator4D.buildBetaAxis(g0);
		assertEquals(1, beta0.length);
		assertEquals(0.0, beta0[0], 0.0);
	}

	private static RomGeometryParameters toLegacyGeometry(RomGeometryInput g) {
		RomGeometryParameters p = new RomGeometryParameters();
		p.bodyLength = g.bodyLength;
		p.maxDiameter = g.maxDiameter;
		p.baseArea = g.baseArea;
		p.wetArea = g.wetArea;
		p.noseLength = g.noseLength;
		p.noseShape = RomGeometryParameters.NoseShape.OGIVE;
		p.finessRatio = g.finenessRatio;
		p.referenceArea = g.referenceArea;
		p.boattailLength = g.boattailLength;
		p.boattailBaseDiameter = g.boattailBaseDiameter;
		p.finCount = g.finCount;
		p.finRootChord = g.finRootChord;
		p.finTipChord = g.finTipChord;
		p.finSpan = g.finSpan;
		p.finThickness = g.finThickness;
		p.finSweepAngle = g.finSweepAngle;
		p.finWettedArea = g.finWettedArea;
		p.motorExitArea = g.motorExitArea;
		p.surfaceRoughness = g.surfaceRoughness;
		return p;
	}
}

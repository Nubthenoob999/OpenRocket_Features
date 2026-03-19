package info.openrocket.core.aerodynamics.rom;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class DragGridEvaluatorTest {

	private static RomGeometryParameters sampleGeometry() {
		RomGeometryParameters g = new RomGeometryParameters();
		g.bodyLength = 1.8;
		g.maxDiameter = 0.102;
		g.referenceArea = Math.PI * Math.pow(g.maxDiameter / 2.0, 2.0);
		g.baseArea = g.referenceArea;
		g.wetArea = 0.65;
		g.noseLength = 0.35;
		g.noseShape = RomGeometryParameters.NoseShape.OGIVE;
		g.finessRatio = g.bodyLength / g.maxDiameter;
		g.finCount = 3;
		g.finRootChord = 0.16;
		g.finTipChord = 0.08;
		g.finSpan = 0.09;
		g.finThickness = 0.004;
		g.finSweepAngle = Math.toRadians(25.0);
		g.finWettedArea = 2.0 * (0.5 * (g.finRootChord + g.finTipChord) * g.finSpan);
		g.surfaceRoughness = 6.4e-6;
		g.boattailLength = 0.0;
		g.boattailBaseDiameter = 0.0;
		g.motorExitDiameter = 0.038;
		g.motorExitArea = Math.PI * Math.pow(g.motorExitDiameter / 2.0, 2.0);
		return g;
	}

	@Test
	public void testPointValuesAndRelativeBehavior() {
		RomGeometryParameters g = sampleGeometry();
		double cdSub = DragGridEvaluator.computeCdPlumeOff(0.3, 1e6, 0.0, g);
		double cdTrans = DragGridEvaluator.computeCdPlumeOff(0.9, 1e6, 0.0, g);
		double cdSup = DragGridEvaluator.computeCdPlumeOff(2.0, 1e6, 0.0, g);
		double cdOn = DragGridEvaluator.computeCdPlumeOn(0.3, 1e6, 0.0, g);

		assertTrue(cdSub > 0.15 && cdSub < 0.70);
		assertTrue(cdSup < cdTrans);
		assertTrue(cdOn < cdSub);
	}

	@Test
	public void testEvaluatePerformanceAndShape() {
		RomGeometryParameters g = sampleGeometry();
		long start = System.nanoTime();
		DragSurface surface = DragGridEvaluator.evaluate(g, null);
		long elapsedMs = (System.nanoTime() - start) / 1_000_000;

		assertTrue(elapsedMs < 2000);
		assertTrue(surface.machAxis.length == DragGridEvaluator.N_MACH);
		assertTrue(surface.logReAxis.length == DragGridEvaluator.N_RE);
		assertTrue(surface.alphaAxis.length == DragGridEvaluator.N_ALPHA);
	}
}

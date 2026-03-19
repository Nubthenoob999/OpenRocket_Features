package info.openrocket.core.aerodynamics.rom;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class DragSurfaceInterpolatorTest {

	private static RomGeometryParameters sampleGeometry() {
		RomGeometryParameters g = new RomGeometryParameters();
		g.bodyLength = 1.4;
		g.maxDiameter = 0.075;
		g.referenceArea = Math.PI * Math.pow(g.maxDiameter / 2.0, 2.0);
		g.baseArea = g.referenceArea;
		g.wetArea = 0.42;
		g.noseLength = 0.28;
		g.noseShape = RomGeometryParameters.NoseShape.VON_KARMAN;
		g.finessRatio = g.bodyLength / g.maxDiameter;
		g.finCount = 4;
		g.finRootChord = 0.12;
		g.finTipChord = 0.05;
		g.finSpan = 0.07;
		g.finThickness = 0.003;
		g.finSweepAngle = Math.toRadians(20.0);
		g.finWettedArea = 2.0 * (0.5 * (g.finRootChord + g.finTipChord) * g.finSpan);
		g.surfaceRoughness = 6.4e-6;
		g.motorExitDiameter = 0.029;
		g.motorExitArea = Math.PI * Math.pow(g.motorExitDiameter / 2.0, 2.0);
		return g;
	}

	@Test
	public void testGridPointAndMidpointAccuracy() {
		RomGeometryParameters g = sampleGeometry();
		DragSurface surface = DragGridEvaluator.evaluate(g, null);
		DragSurfaceInterpolator interpolator = new DragSurfaceInterpolator(surface);

		double maxGridErr = 0.0;
		for (int im = 0; im < surface.machAxis.length; im++) {
			double mach = surface.machAxis[im];
			for (int ir = 0; ir < surface.logReAxis.length; ir++) {
				double re = Math.pow(10.0, surface.logReAxis[ir]);
				for (int ia = 0; ia < surface.alphaAxis.length; ia++) {
					double alpha = surface.alphaAxis[ia];
					double expected = surface.cdPlumeOff[im][ir][ia];
					double actual = interpolator.queryCdPlumeOff(mach, re, alpha);
					double rel = Math.abs(actual - expected) / Math.max(1e-9, expected);
					maxGridErr = Math.max(maxGridErr, rel);
				}
			}
		}
		assertTrue(maxGridErr < 0.005);

		double maxMidErr = 0.0;
		for (int im = 0; im < surface.machAxis.length - 1; im++) {
			double mach = 0.5 * (surface.machAxis[im] + surface.machAxis[im + 1]);
			for (int ir = 0; ir < surface.logReAxis.length - 1; ir++) {
				double logRe = 0.5 * (surface.logReAxis[ir] + surface.logReAxis[ir + 1]);
				double re = Math.pow(10.0, logRe);
				for (int ia = 0; ia < surface.alphaAxis.length - 1; ia++) {
					double alpha = 0.5 * (surface.alphaAxis[ia] + surface.alphaAxis[ia + 1]);
					double expected = DragGridEvaluator.computeCdPlumeOff(mach, re, Math.toRadians(alpha), g);
					double actual = interpolator.queryCdPlumeOff(mach, re, alpha);
					double rel = Math.abs(actual - expected) / Math.max(1e-9, expected);
					maxMidErr = Math.max(maxMidErr, rel);
				}
			}
		}
		assertTrue(maxMidErr < 0.02);
	}

	@Test
	public void testOutOfBoundsClampsGracefully() {
		RomGeometryParameters g = sampleGeometry();
		DragSurface surface = DragGridEvaluator.evaluate(g, null);
		DragSurfaceInterpolator interpolator = new DragSurfaceInterpolator(surface);

		assertDoesNotThrow(() -> interpolator.queryCdPlumeOff(-0.5, 1e3, -15.0));
		assertDoesNotThrow(() -> interpolator.queryCdPlumeOn(8.0, 1e10, 80.0));
	}
}

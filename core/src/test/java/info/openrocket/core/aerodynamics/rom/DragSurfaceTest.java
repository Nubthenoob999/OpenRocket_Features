package info.openrocket.core.aerodynamics.rom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class DragSurfaceTest {

	@Test
	public void testConstructorAssignsFieldsAndMetadata() {
		double[] machAxis = new double[] { 0.01, 0.5, 1.0 };
		double[] logReAxis = new double[] { 4.0, 6.0 };
		double[] alphaAxis = new double[] { 0.0, 10.0 };

		double[][][] off = new double[machAxis.length][logReAxis.length][alphaAxis.length];
		double[][][] on = new double[machAxis.length][logReAxis.length][alphaAxis.length];

		long before = System.currentTimeMillis();
		DragSurface surface = new DragSurface(machAxis, logReAxis, alphaAxis, off, on,
				"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", 1.7);
		long after = System.currentTimeMillis();

		assertSame(machAxis, surface.machAxis);
		assertSame(logReAxis, surface.logReAxis);
		assertSame(alphaAxis, surface.alphaAxis);
		assertSame(off, surface.cdPlumeOff);
		assertSame(on, surface.cdPlumeOn);
		assertEquals(1.7, surface.looRmsePercent, 0.0);
		assertEquals(64, surface.geometryHash.length());
		assertTrue(surface.buildTimestampMs >= before);
		assertTrue(surface.buildTimestampMs <= after);
	}
}
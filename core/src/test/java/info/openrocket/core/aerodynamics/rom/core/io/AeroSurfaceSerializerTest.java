package info.openrocket.core.aerodynamics.rom.core.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.rom.core.TestFixtures;
import info.openrocket.core.aerodynamics.rom.core.eval.AeroGridEvaluator4D;
import info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput;
import info.openrocket.core.aerodynamics.rom.core.surface.AeroSurface4D;

public class AeroSurfaceSerializerTest {

	@Test
	public void testRoundTripPreservesAxesAndSamples() throws IOException {
		AeroSurface4D original = AeroGridEvaluator4D.evaluate(TestFixtures.typical4Fin(), null);
		byte[] payload = AeroSurfaceSerializer.serialize(original);
		AeroSurface4D restored = AeroSurfaceSerializer.deserialize(payload, original.geometryHash, original.buildTimestampMs,
				original.finCount);

		assertArrayEquals(original.machAxis, restored.machAxis, 1e-12);
		assertArrayEquals(original.logReAxis, restored.logReAxis, 1e-12);
		assertArrayEquals(original.alphaAxis, restored.alphaAxis, 1e-12);
		assertArrayEquals(original.betaAxis, restored.betaAxis, 1e-12);

		assertEquals(original.cdPlumeOff[0][0][0][0], restored.cdPlumeOff[0][0][0][0], 1e-12);
		assertEquals(original.cdBody[3][5][2][1], restored.cdBody[3][5][2][1], 1e-12);
		assertEquals(original.CN[10][8][4][2], restored.CN[10][8][4][2], 1e-12);
	}

	@Test
	public void testRoundTripOneBetaPointSurfaceNoFins() {
		RomGeometryInput noFins = TestFixtures.noFins();
		AeroSurface4D original = AeroGridEvaluator4D.evaluate(noFins, null);
		assertEquals(1, original.betaAxis.length);

		assertDoesNotThrow(() -> {
			byte[] payload = AeroSurfaceSerializer.serialize(original);
			AeroSurface4D restored = AeroSurfaceSerializer.deserialize(payload, original.geometryHash,
					original.buildTimestampMs, original.finCount);
			assertEquals(1, restored.betaAxis.length);
		});
	}
}

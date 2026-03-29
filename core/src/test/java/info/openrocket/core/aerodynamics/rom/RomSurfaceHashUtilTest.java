package info.openrocket.core.aerodynamics.rom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RomSurfaceHashUtilTest {

	@Test
	void tagsAndRecoversModeWithoutChangingRawHash() {
		String raw = "abc123deadbeef";

		String tagged3D = RomSurfaceHashUtil.tag(raw, RomSurfaceHashUtil.MODE_3D);
		String tagged4D = RomSurfaceHashUtil.tag(raw, RomSurfaceHashUtil.MODE_4D);

		assertEquals(raw, RomSurfaceHashUtil.rawHash(tagged3D));
		assertEquals(raw, RomSurfaceHashUtil.rawHash(tagged4D));
		assertEquals(RomSurfaceHashUtil.MODE_3D, RomSurfaceHashUtil.modeTag(tagged3D));
		assertEquals(RomSurfaceHashUtil.MODE_4D, RomSurfaceHashUtil.modeTag(tagged4D));
		assertTrue(RomSurfaceHashUtil.matchesGeometry(tagged3D, raw));
		assertTrue(RomSurfaceHashUtil.matchesGeometry(tagged4D, raw));
		assertFalse(RomSurfaceHashUtil.is4D(tagged3D));
		assertTrue(RomSurfaceHashUtil.is4D(tagged4D));
	}
}

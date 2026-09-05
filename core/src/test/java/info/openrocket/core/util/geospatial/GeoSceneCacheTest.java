package info.openrocket.core.util.geospatial;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeoSceneCacheTest {
	@TempDir Path temporaryDirectory;

	@Test
	void storesFreshnessAndValidators() throws Exception {
		GeoSceneCache cache = new GeoSceneCache(temporaryDirectory, 2 * 1024 * 1024);
		byte[] body = { 1, 2, 3, 4 };
		cache.write("request", body, System.currentTimeMillis() + 60_000, "etag-1", "yesterday");
		GeoSceneCache.Entry entry = cache.read("request").orElseThrow();
		assertArrayEquals(body, entry.body());
		assertTrue(entry.fresh());
		assertTrue(entry.etag().equals("etag-1"));
		cache.clear();
		assertFalse(cache.read("request").isPresent());
	}
}

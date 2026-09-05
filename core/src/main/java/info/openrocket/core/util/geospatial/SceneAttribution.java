package info.openrocket.core.util.geospatial;

import java.net.URI;
import java.time.Instant;

/** Attribution and provenance for an active geospatial layer. */
public record SceneAttribution(String provider, String license, URI source, Instant retrievedAt) { }

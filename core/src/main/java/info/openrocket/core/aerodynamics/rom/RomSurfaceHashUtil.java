package info.openrocket.core.aerodynamics.rom;

import java.util.Locale;

/**
 * Helper for tagging ROM geometry hashes with the build mode used to generate them.
 */
public final class RomSurfaceHashUtil {

	public static final String MODE_3D = "3D";
	public static final String MODE_4D = "4D";

	private RomSurfaceHashUtil() {
	}

	public static String tag(String geometryHash, String modeTag) {
		String raw = rawHash(geometryHash);
		String normalizedMode = normalizeMode(modeTag);
		if (raw == null || raw.isBlank() || normalizedMode.isEmpty()) {
			return raw;
		}
		return raw + "|" + normalizedMode;
	}

	public static String rawHash(String taggedHash) {
		if (taggedHash == null) {
			return null;
		}
		int split = taggedHash.lastIndexOf('|');
		if (split < 0) {
			return taggedHash;
		}
		String suffix = taggedHash.substring(split + 1);
		return isKnownModeTag(suffix) ? taggedHash.substring(0, split) : taggedHash;
	}

	public static String modeTag(String taggedHash) {
		if (taggedHash == null) {
			return "";
		}
		int split = taggedHash.lastIndexOf('|');
		if (split < 0) {
			return "";
		}
		String suffix = taggedHash.substring(split + 1);
		return isKnownModeTag(suffix) ? normalizeMode(suffix) : "";
	}

	public static boolean is4D(String taggedHash) {
		return MODE_4D.equals(modeTag(taggedHash));
	}

	public static boolean matchesGeometry(String taggedHash, String rawGeometryHash) {
		return rawGeometryHash != null && rawGeometryHash.equals(rawHash(taggedHash));
	}

	private static boolean isKnownModeTag(String value) {
		return MODE_3D.equalsIgnoreCase(value) || MODE_4D.equalsIgnoreCase(value);
	}

	private static String normalizeMode(String modeTag) {
		if (modeTag == null) {
			return "";
		}
		String trimmed = modeTag.trim();
		if (trimmed.isEmpty()) {
			return "";
		}
		if (MODE_3D.equalsIgnoreCase(trimmed)) {
			return MODE_3D;
		}
		if (MODE_4D.equalsIgnoreCase(trimmed)) {
			return MODE_4D;
		}
		return trimmed.toUpperCase(Locale.ROOT);
	}
}

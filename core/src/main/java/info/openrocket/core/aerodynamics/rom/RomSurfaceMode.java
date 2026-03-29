package info.openrocket.core.aerodynamics.rom;

import java.util.Locale;

/**
 * Identifies which ROM surface dimensionality is selected for a simulation.
 */
public enum RomSurfaceMode {
	THREE_D("3d"),
	FOUR_D("4d");

	private final String storageValue;

	RomSurfaceMode(String storageValue) {
		this.storageValue = storageValue;
	}

	public String toStorageValue() {
		return storageValue;
	}

	public String tagGeometryHash(String baseGeometryHash) {
		return RomSurfaceHashUtil.tag(baseGeometryHash,
				this == FOUR_D ? RomSurfaceHashUtil.MODE_4D : RomSurfaceHashUtil.MODE_3D);
	}

	public static RomSurfaceMode fromStorageValue(String value) {
		if (value == null) {
			return THREE_D;
		}
		String normalized = value.trim().toLowerCase(Locale.ROOT);
		if ("4d".equals(normalized) || "four_d".equals(normalized) || "four-d".equals(normalized)) {
			return FOUR_D;
		}
		return THREE_D;
	}
}

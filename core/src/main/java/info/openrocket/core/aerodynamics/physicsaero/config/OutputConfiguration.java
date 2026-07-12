package info.openrocket.core.aerodynamics.physicsaero.config;

import java.nio.file.Path;

public record OutputConfiguration(Path tableFile, Path manifestFile, boolean writeInspectionCsv) {
	public OutputConfiguration { if (tableFile == null || manifestFile == null) throw new IllegalArgumentException("output paths required"); }
}

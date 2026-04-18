package info.openrocket.core.tuning;

import java.nio.file.Files;
import java.nio.file.Path;

public final class PhaseThreeTuningPaths {
	private static final Path DEFAULT_CONFIG_RELATIVE = Path.of(
			"core", "src", "test", "java", "info", "openrocket", "core", "tuning", "Phase3_tuning.json");
	private static final Path DEFAULT_TUNING_DIR_RELATIVE = DEFAULT_CONFIG_RELATIVE.getParent();
	private static final Path DEFAULT_REPORTS_RELATIVE = Path.of("build", "reports", "phase-three");

	private PhaseThreeTuningPaths() {
	}

	public static Path findDefaultConfig() {
		return findFromWorkingTree(DEFAULT_CONFIG_RELATIVE);
	}

	public static Path findDefaultTuningDirectory() {
		Path config = findDefaultConfig();
		if (config != null) {
			return config.getParent();
		}
		return findFromWorkingTree(DEFAULT_TUNING_DIR_RELATIVE);
	}

	public static Path defaultReportsDirectory() {
		return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize().resolve(DEFAULT_REPORTS_RELATIVE).normalize();
	}

	private static Path findFromWorkingTree(Path relativePath) {
		Path start = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
		for (Path cursor = start; cursor != null; cursor = cursor.getParent()) {
			Path candidate = cursor.resolve(relativePath).normalize();
			if (Files.exists(candidate)) {
				return candidate;
			}
		}
		return null;
	}
}

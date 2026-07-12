package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class LegacyDependencyBarrierTest {
	@Test void physicsAeroProductionCodeDoesNotImportLegacyRom() throws IOException {
		Path root = Path.of(System.getProperty("user.dir"));
		Path source = Files.exists(root.resolve("core/src/main/java")) ? root.resolve("core/src/main/java") : root.resolve("src/main/java");
		Path physics = source.resolve("info/openrocket/core/aerodynamics/physicsaero");
		try (var files = Files.walk(physics)) {
			List<Path> offenders = files.filter(p -> p.toString().endsWith(".java")).filter(p -> {
				try { return Files.readString(p).contains("import info.openrocket.core.aerodynamics.rom"); } catch (IOException e) { throw new IllegalStateException(e); }
			}).toList();
			assertTrue(offenders.isEmpty(), "new physics code imports legacy ROM: " + offenders);
		}
	}
}

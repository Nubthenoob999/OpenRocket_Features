package info.openrocket.core.tuning;

import info.openrocket.core.util.BaseTestCase;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TelemetryTruthSelectorTest extends BaseTestCase {
	@Test
	public void prefersBarometricTruthAndIgnoresGeneratedCsvFiles() throws Exception {
		Path dir = Files.createTempDirectory("truth-selector");
		Path reportCsv = dir.resolve("phase-three-analysis.csv");
		Path dragCsv = dir.resolve("drag-table.csv");
		Path goodFluctus = dir.resolve("fluctus-good.csv");
		Path noisyFluctus = dir.resolve("fluctus-noisy.csv");
		Path airbrakes = dir.resolve("ab.csv");

		Files.writeString(reportCsv, "dataset,fullScore\nexample,99\n", StandardCharsets.UTF_8);
		Files.writeString(dragCsv, "velocity,drag\n1,2\n", StandardCharsets.UTF_8);
		Files.writeString(goodFluctus,
				"sep=;\n"
						+ "time (ms);dedrck-alti (m);dedrck-v-speed (m/s);vert-accel (m/s2);amb-temp (deg c)\n"
						+ "0;0;0;0;20\n"
						+ "50;5;1;2;20\n"
						+ "100;20;2;3;21\n"
						+ "150;25;3;4;21\n",
				StandardCharsets.UTF_8);
		Files.writeString(noisyFluctus,
				"sep=;\n"
						+ "time (ms);dedrck-alti (m);dedrck-v-speed (m/s);vert-accel (m/s2);amb-temp (deg c)\n"
						+ "0;0;0;0;20\n"
						+ "50;2147483647;1;2;20\n"
						+ "100;20;2;3;21\n"
						+ "150;2147483647;3;4;21\n",
				StandardCharsets.UTF_8);
		Files.copy(resolveFixture("Jackpot_Launch_2", "ab_jackpot_launch_2.csv"), airbrakes);

		TelemetryTruthSelector.TruthSelection selection = TelemetryTruthSelector.select(dir);

		assertEquals(goodFluctus.toAbsolutePath().normalize(), selection.path().toAbsolutePath().normalize());
		assertEquals(TelemetrySchema.FLUCTUS_SEMICOLON, selection.series().getSchema());
		assertTrue(selection.series().getParserDiagnostics().getSentinelRate() < 0.30);
	}

	private static Path resolveFixture(String folder, String file) {
		Path path = Path.of("src", "test", "java", "info", "openrocket", "core", "tuning", folder, file);
		if (!Files.exists(path)) {
			path = Path.of("core", "src", "test", "java", "info", "openrocket", "core", "tuning", folder, file);
		}
		return path;
	}
}

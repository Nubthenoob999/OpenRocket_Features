package info.openrocket.core.tuning;

import info.openrocket.core.util.BaseTestCase;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TelemetryParsersDiagnosticsTest extends BaseTestCase {
	@Test
	public void easyMiniTracksRangeDropsForOutliers() throws Exception {
		Path dir = Files.createTempDirectory("easymini-diagnostics");
		Path file = dir.resolve("easymini.csv");
		String row = "1,2,3,0.1,5,6,99999,8,9,1000000,1000000,25";
		Files.writeString(file, "#version\n" + row + "\n", StandardCharsets.UTF_8);

		TelemetrySeries series = TelemetryParsers.parse(file);
		TelemetryParserDiagnostics d = series.getParserDiagnostics();

		assertTrue(d.getRowsRead() >= 1);
		assertTrue(d.getRangeDrops() >= 1, "Expected out-of-range values to be tracked");
	}

	@Test
	public void tabDelimitedTracksUnitCorrections() throws Exception {
		Path dir = Files.createTempDirectory("tab-diagnostics");
		Path file = dir.resolve("altimeter.txt");
		String content = "0\t100\t68F\t0\n1\t150\t70F\t0\n";
		Files.writeString(file, content, StandardCharsets.UTF_8);

		TelemetrySeries series = TelemetryParsers.parse(file);
		TelemetryParserDiagnostics d = series.getParserDiagnostics();

		assertTrue(d.getRowsAccepted() >= 2);
		assertTrue(d.getUnitCorrections() >= 2, "Expected feet/F conversions to count as unit corrections");
	}

	@Test
	public void tabDelimitedAcceptsQuotedRows() throws Exception {
		Path dir = Files.createTempDirectory("tab-quoted");
		Path file = dir.resolve("perfectflite.txt");
		String content = "\"0\t100\t68F\t9.3\"\n\"1\t150\t70F\t9.1\"\n";
		Files.writeString(file, content, StandardCharsets.UTF_8);

		TelemetrySeries series = TelemetryParsers.parse(file);
		TelemetryParserDiagnostics d = series.getParserDiagnostics();

		assertTrue(series.size() >= 2, "Quoted tab-delimited rows should be parsed");
		assertTrue(d.getRowsAccepted() >= 2, "Quoted rows should no longer be discarded");
	}

	@Test
	public void fluctusTracksSentinelRate() throws Exception {
		Path dir = Files.createTempDirectory("fluctus-sentinel-rate");
		Path file = dir.resolve("fluctus.csv");
		String content = "sep=;\n"
				+ "time (ms);dedrck-alti (m);dedrck-v-speed (m/s);vert-accel (m/s2);amb-temp (deg c)\n"
				+ "0;0;0;0;20\n"
				+ "50;2147483647;1;2;20\n"
				+ "100;20;2;3;21\n"
				+ "150;2147483647;3;4;21\n";
		Files.writeString(file, content, StandardCharsets.UTF_8);

		TelemetrySeries series = TelemetryParsers.parse(file);
		TelemetryParserDiagnostics d = series.getParserDiagnostics();

		assertTrue(d.getSentinelDrops() >= 2);
		assertTrue(d.getSentinelRate() > 0.30, "Sentinel rate should exceed quality-gate threshold");
	}
}

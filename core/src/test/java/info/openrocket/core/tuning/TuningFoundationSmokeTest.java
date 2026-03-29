package info.openrocket.core.tuning;

import info.openrocket.core.util.BaseTestCase;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TuningFoundationSmokeTest extends BaseTestCase {
	private static final Path JACKPOT_AB = Path.of("src", "test", "java", "info", "openrocket", "core", "tuning",
			"Jackpot_Launch_2", "ab_jackpot_launch_2.csv");
	private static final Path JACKPOT_FLUCTUS = Path.of("src", "test", "java", "info", "openrocket", "core", "tuning",
			"Jackpot_Launch_2", "fluctus_launch_2.csv");
	private static final Path PELENCATOR_STRATO = Path.of("src", "test", "java", "info", "openrocket", "core", "tuning",
			"Pelencator_launch_2", "Stratologger_Data_02_22_25.csv");

	@Test
	public void detectsAbSchema() throws IOException {
		assertEquals(TelemetrySchema.AB_EXTENDED, TelemetryParsers.detectSchema(JACKPOT_AB));
	}

	@Test
	public void detectsFluctusSchema() throws IOException {
		assertEquals(TelemetrySchema.FLUCTUS_SEMICOLON, TelemetryParsers.detectSchema(JACKPOT_FLUCTUS));
	}

	@Test
	public void detectsTabDelimitedSchema() throws IOException {
		assertEquals(TelemetrySchema.TAB_DELIMITED_ALTIMETER, TelemetryParsers.detectSchema(PELENCATOR_STRATO));
	}

	@Test
	public void parsesAbTelemetryWithCoreChannels() throws IOException {
		TelemetrySeries series = TelemetryParsers.parse(JACKPOT_AB);
		assertTrue(series.size() > 100, "Expected AB telemetry to produce many parsed rows");
		assertTrue(series.hasAltitude(), "AB parser should expose altitude");
		assertTrue(series.hasVelocityZ(), "AB parser should expose vertical velocity");
		assertTrue(series.hasPressure(), "AB parser should expose pressure");
	}

	@Test
	public void parsesFluctusTelemetryWithCoreChannels() throws IOException {
		TelemetrySeries series = TelemetryParsers.parse(JACKPOT_FLUCTUS);
		assertTrue(series.size() > 100, "Expected Fluctus telemetry to produce many parsed rows");
		assertTrue(series.hasAltitude(), "Fluctus parser should expose altitude");
		assertTrue(series.hasVelocityZ(), "Fluctus parser should expose vertical velocity");
		assertTrue(series.hasTemperature(), "Fluctus parser should expose temperature");
	}

	@Test
	public void findsTZeroBeforeFirstAltitudeChange() throws IOException {
		TelemetrySeries series = TelemetryParsers.parse(PELENCATOR_STRATO);
		int t0Index = series.indexBeforeFirstAltitudeChange();
		assertEquals(2, t0Index, "Expected t=0 marker to be the sample before first altitude change");
	}
}

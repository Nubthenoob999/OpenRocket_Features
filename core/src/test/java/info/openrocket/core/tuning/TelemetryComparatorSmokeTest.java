package info.openrocket.core.tuning;

import info.openrocket.core.util.BaseTestCase;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TelemetryComparatorSmokeTest extends BaseTestCase {
	private static final Path JACKPOT_AB = Path.of("src", "test", "java", "info", "openrocket", "core", "tuning",
			"Jackpot_Launch_2", "ab_jackpot_launch_2.csv");
	private static final Path JACKPOT_FLUCTUS = Path.of("src", "test", "java", "info", "openrocket", "core", "tuning",
			"Jackpot_Launch_2", "fluctus_launch_2.csv");

	@Test
	public void comparesAbAgainstItself() throws IOException {
		TuningTestInfrastructure.requireFixtures(JACKPOT_AB);
		TelemetrySeries reference = TelemetryParsers.parse(JACKPOT_AB);
		TelemetrySeries candidate = TelemetryParsers.parse(JACKPOT_AB);

		TelemetryComparisonResult result = TelemetryComparator.compare(
				reference,
				candidate,
				20.0,
				InterpolationMode.CUBIC_HERMITE
		);

		assertTrue(result.hasChannel("altitude"), "Expected altitude comparison metrics");
		assertTrue(result.hasChannel("velocityZ"), "Expected velocity comparison metrics");
		assertTrue(result.hasChannel("density"), "Expected density comparison metrics when pressure and temperature are present");
		assertTrue(result.getChannelMetrics().get("altitude").getSampleCount() > 100,
				"Expected sufficient overlap samples for altitude");
		assertTrue(result.getChannelMetrics().get("altitude").getMapePercent() < 0.001,
				"Self-comparison should have near-zero MAPE");
	}

	@Test
	public void producesFiniteErrorMetrics() throws IOException {
		TuningTestInfrastructure.requireFixtures(JACKPOT_FLUCTUS);
		TelemetrySeries reference = TelemetryParsers.parse(JACKPOT_FLUCTUS);
		TelemetrySeries candidate = TelemetryParsers.parse(JACKPOT_FLUCTUS);

		TelemetryComparisonResult result = TelemetryComparator.compare(
				reference,
				candidate,
				20.0,
				InterpolationMode.LINEAR
		);

		ChannelMetrics altitude = result.getChannelMetrics().get("altitude");
		assertTrue(Double.isFinite(altitude.getMapePercent()));
		assertTrue(Double.isFinite(altitude.getSmapePercent()));
		assertTrue(Double.isFinite(altitude.getNrmsePercent()));
	}
}

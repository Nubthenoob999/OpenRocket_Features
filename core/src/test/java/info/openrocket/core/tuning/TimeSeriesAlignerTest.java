package info.openrocket.core.tuning;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TimeSeriesAlignerTest {
	@Test
	public void buildsUniformTimelineAt20Hz() {
		List<Double> timeline = TimeSeriesAligner.buildUniformTimeline(0.0, 0.2, 20.0);
		assertEquals(5, timeline.size());
		assertEquals(0.0, timeline.get(0), 1e-12);
		assertEquals(0.05, timeline.get(1), 1e-12);
		assertEquals(0.2, timeline.get(4), 1e-12);
	}

	@Test
	public void resamplesLinearValues() {
		List<Double> sourceTime = List.of(0.0, 1.0);
		List<Double> sourceValues = List.of(0.0, 10.0);
		List<Double> targetTime = List.of(0.0, 0.25, 0.5, 0.75, 1.0);

		List<Double> values = TimeSeriesAligner.resample(sourceTime, sourceValues, targetTime, InterpolationMode.LINEAR);
		assertNotNull(values);
		assertEquals(5, values.size());
		assertEquals(2.5, values.get(1), 1e-9);
		assertEquals(5.0, values.get(2), 1e-9);
		assertEquals(7.5, values.get(3), 1e-9);
	}

	@Test
	public void resamplesCubicHermiteForSmoothCurve() {
		List<Double> sourceTime = List.of(0.0, 1.0, 2.0);
		List<Double> sourceValues = List.of(0.0, 1.0, 0.0);
		List<Double> targetTime = List.of(0.5, 1.0, 1.5);

		List<Double> values = TimeSeriesAligner.resample(sourceTime, sourceValues, targetTime, InterpolationMode.CUBIC_HERMITE);
		assertNotNull(values);
		assertEquals(3, values.size());
		assertEquals(1.0, values.get(1), 1e-9);
	}

	@Test
	public void estimatesCrossCorrelationLagFromAltitude() {
		TelemetrySeries reference = syntheticSeries(0.0, 0.0);
		TelemetrySeries delayedCandidate = syntheticSeries(0.0, 0.35);

		AlignmentResult alignment = TimeSeriesAligner.estimateAlignment(reference, delayedCandidate, 20.0);

		assertEquals("altitude", alignment.getChannel());
		assertTrue(Double.isFinite(alignment.getQuality()));
		assertEquals(-0.35, alignment.getLagSec(), 0.10);
		assertTrue(alignment.getMatchedSamples() >= 20);
	}

	private static TelemetrySeries syntheticSeries(double timeOffsetSec, double signalDelaySec) {
		TelemetrySeries series = new TelemetrySeries(TelemetrySchema.OPENROCKET_SIMULATION);
		TelemetryParserDiagnostics.Mutable diagnostics =
				new TelemetryParserDiagnostics.Mutable(TelemetrySchema.OPENROCKET_SIMULATION.name());
		for (int i = 0; i < 120; i++) {
			double t = i * 0.05 + timeOffsetSec;
			double relative = t - timeOffsetSec - signalDelaySec;
			double altitude = 20.0 * Math.sin(relative) + relative * 8.0;
			double velocity = 20.0 * Math.cos(relative) + 8.0;
			double pressure = 101325.0 - altitude * 12.0;
			series.addPoint(t, altitude, velocity, null, null, -20.0 * Math.sin(relative), pressure, 20.0);
			diagnostics.incRowsRead();
			diagnostics.incRowsAccepted();
		}
		series.setParserDiagnostics(diagnostics.freeze());
		return series;
	}
}

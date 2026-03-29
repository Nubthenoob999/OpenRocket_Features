package info.openrocket.core.tuning;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
}

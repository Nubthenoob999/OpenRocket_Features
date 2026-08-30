package info.openrocket.core.simulation.montecarlo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

public class MetricStatisticsTest {
	@Test
	public void testDescriptiveStatisticsAndNearestRankQuantiles() {
		MetricStatistics statistics = MetricStatistics.from(List.of(5.0, 1.0, 4.0, 2.0, 3.0));

		assertEquals(5, statistics.getSampleCount());
		assertEquals(1.0, statistics.getMinimum());
		assertEquals(5.0, statistics.getMaximum());
		assertEquals(3.0, statistics.getMean());
		assertEquals(3.0, statistics.getMedian());
		assertEquals(Math.sqrt(2.5), statistics.getStandardDeviation(), 1.0e-12);
		assertEquals(1.0, statistics.getQuantile(0.05));
		assertEquals(5.0, statistics.getQuantile(0.95));
	}

	@Test
	public void testNonFiniteValuesAreIgnored() {
		MetricStatistics statistics = MetricStatistics.from(
				List.of(Double.NaN, 2.0, Double.POSITIVE_INFINITY, 4.0));

		assertEquals(2, statistics.getSampleCount());
		assertEquals(3.0, statistics.getMean());
		assertThrows(IllegalArgumentException.class,
				() -> MetricStatistics.from(List.of(Double.NaN)));
	}

	@Test
	public void testEvenSampleMedianAveragesTheMiddleValues() {
		MetricStatistics statistics = MetricStatistics.from(List.of(1.0, 2.0, 4.0, 8.0));

		assertEquals(3.0, statistics.getMedian());
		assertEquals(2.0, statistics.getQuantile(0.5));
	}

	@Test
	public void testMonteCarloErrorAndMeanConfidenceInterval() {
		MetricStatistics statistics = MetricStatistics.from(List.of(1.0, 2.0, 3.0, 4.0, 5.0));

		assertEquals(Math.sqrt(2.5 / 5.0), statistics.getStandardError(), 1.0e-12);
		MetricStatistics.ConfidenceInterval interval = statistics.getMeanConfidenceInterval95();
		assertEquals(3.0, (interval.lowerBound() + interval.upperBound()) / 2.0, 1.0e-12);
		assertEquals(1.959963984540054 * statistics.getStandardError(),
				interval.upperBound() - statistics.getMean(), 1.0e-12);
	}

	@Test
	public void testConstantAndSingleValueSamplesHaveZeroMonteCarloError() {
		MetricStatistics constant = MetricStatistics.from(List.of(7.0, 7.0, 7.0));
		MetricStatistics singleton = MetricStatistics.from(List.of(9.0));

		assertEquals(0.0, constant.getStandardError());
		assertEquals(new MetricStatistics.ConfidenceInterval(7.0, 7.0),
				constant.getMeanConfidenceInterval95());
		assertEquals(0.0, singleton.getStandardError());
		assertEquals(new MetricStatistics.ConfidenceInterval(9.0, 9.0),
				singleton.getMeanConfidenceInterval95());
	}

	@Test
	public void testEmptyDataAndInvalidQuantilesFailFast() {
		assertThrows(IllegalArgumentException.class, () -> MetricStatistics.from(List.of()));

		MetricStatistics statistics = MetricStatistics.from(List.of(1.0, 2.0));
		assertThrows(IllegalArgumentException.class, () -> statistics.getQuantile(0));
		assertThrows(IllegalArgumentException.class, () -> statistics.getQuantile(1.01));
		assertThrows(IllegalArgumentException.class,
				() -> statistics.getQuantile(Double.NaN));
	}
}

package info.openrocket.core.tuning;

import info.openrocket.core.util.BaseTestCase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PhaseTwoScoreCalculatorCoverageTest extends BaseTestCase {
	@Test
	public void emitsInsufficientDataWhenAllChannelsBelowCoverageThreshold() {
		TelemetryComparisonResult comparison = new TelemetryComparisonResult();
		comparison.setTimelineSampleCount(100);
		comparison.put("velocityZ", new ChannelMetrics(20, 0.20, 10.0, 10.0, 10.0));
		comparison.put("accelZ", new ChannelMetrics(25, 0.25, 10.0, 10.0, 10.0));

		PhaseTwoScoreResult score = PhaseTwoScoreCalculator.score(comparison, PhaseTwoScoringConfig.defaults());

		assertEquals(0.0, score.getScore(), 1e-9);
		assertEquals(ScoreSeverity.CRITICAL, score.getSeverity());
		assertEquals("INSUFFICIENT_DATA", score.getFailureReason());
		assertEquals("insufficient-window-coverage", score.getInsufficientDataReason());
		assertEquals(25, score.getMatchedSampleCount());
		assertEquals(100, score.getTimelineSampleCount());
	}

	@Test
	public void preservesComparatorReasonWhenNoMatchedSamples() {
		TelemetryComparisonResult comparison = new TelemetryComparisonResult();
		comparison.setTimelineSampleCount(50);
		comparison.setInsufficientDataReason("no-matched-channel-samples");

		PhaseTwoScoreResult score = PhaseTwoScoreCalculator.score(comparison, PhaseTwoScoringConfig.defaults());

		assertEquals("INSUFFICIENT_DATA", score.getFailureReason());
		assertNotNull(score.getInsufficientDataReason());
		assertEquals("no-matched-channel-samples", score.getInsufficientDataReason());
	}
}

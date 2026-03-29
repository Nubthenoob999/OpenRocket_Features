package info.openrocket.core.tuning;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PhaseTwoScoreCalculatorTest {
	@Test
	public void usesVelocityAndAccelWeightedDefaults() {
		TelemetryComparisonResult comparison = new TelemetryComparisonResult();
		comparison.put("velocityZ", new ChannelMetrics(500, 1.0, 40.0, 40.0, 40.0));
		comparison.put("accelZ", new ChannelMetrics(500, 1.0, 20.0, 20.0, 20.0));
		comparison.put("temperature", new ChannelMetrics(500, 1.0, 0.0, 0.0, 0.0));

		PhaseTwoScoreResult score = PhaseTwoScoreCalculator.score(comparison, PhaseTwoScoringConfig.defaults());

		assertTrue(score.getChannelScores().containsKey("velocityZ"));
		assertTrue(score.getChannelScores().containsKey("accelZ"));
		assertTrue(score.getScore() < 90.0, "Velocity and accel errors should reduce total score with weighted defaults");
		assertEquals(ScoreSeverity.WARNING, score.getSeverity());
	}

	@Test
	public void marksCriticalBelowThreshold() {
		TelemetryComparisonResult comparison = new TelemetryComparisonResult();
		comparison.put("velocityZ", new ChannelMetrics(300, 1.0, 95.0, 90.0, 90.0));
		comparison.put("accelZ", new ChannelMetrics(300, 1.0, 92.0, 92.0, 92.0));

		PhaseTwoScoreResult score = PhaseTwoScoreCalculator.score(comparison, PhaseTwoScoringConfig.defaults());

		assertTrue(score.getScore() < 65.0, "Expected severe errors to produce critical score");
		assertEquals(ScoreSeverity.CRITICAL, score.getSeverity());
	}

	@Test
	public void generatesEquationFlagsForLowChannelScores() {
		TelemetryComparisonResult comparison = new TelemetryComparisonResult();
		comparison.put("velocityZ", new ChannelMetrics(300, 1.0, 75.0, 75.0, 75.0));
		comparison.put("accelZ", new ChannelMetrics(300, 1.0, 45.0, 45.0, 45.0));
		comparison.put("density", new ChannelMetrics(300, 1.0, 70.0, 70.0, 70.0));
		comparison.put("temperature", new ChannelMetrics(300, 1.0, 5.0, 5.0, 5.0));

		PhaseTwoScoringConfig config = PhaseTwoScoringConfig.defaults();
		PhaseTwoScoreResult score = PhaseTwoScoreCalculator.score(comparison, config);
		List<TuningFlag> flags = EquationTuningRuleEngine.buildFlags(score, config);

		assertFalse(flags.isEmpty());
		assertEquals("velocityZ", flags.get(0).getChannel());
		assertEquals("rom.drag.force-balance", flags.get(0).getEquationGroup());
		assertTrue(flags.stream().anyMatch(f -> f.getChannel().equals("accelZ")),
				"Expected acceleration channel to generate a tuning flag");
		assertTrue(flags.stream().anyMatch(f -> f.getChannel().equals("density")
				&& f.getEquationGroup().equals("rom.atmosphere.density")),
				"Expected density channel to map to atmosphere density guidance");
	}
}
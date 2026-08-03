package info.openrocket.core.tuning;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PhaseThreeAnalysisSupportTest {
	@Test
	public void aggregateFullScorePenalizesMissingPhaseCoverage() {
		PhaseTwoScoringConfig scoringConfig = PhaseTwoScoringConfig.defaults();
		PhaseTwoScoreResult fullScoreRaw = new PhaseTwoScoreResult(
				96.0,
				ScoreSeverity.OK,
				Map.of("velocityZ", 96.0),
				1.0,
				300,
				300,
				null,
				null);
		Map<FlightPhaseWindow, PhaseTwoScoreResult> windows = new EnumMap<>(FlightPhaseWindow.class);
		windows.put(FlightPhaseWindow.DESCENT, new PhaseTwoScoreResult(
				95.0,
				ScoreSeverity.OK,
				Map.of("velocityZ", 95.0),
				1.0,
				120,
				120,
				null,
				null));

		PhaseTwoScoreResult aggregated = PhaseThreeAnalysisSupport.aggregateFullScore(
				fullScoreRaw,
				windows,
				scoringConfig,
				buildSeries(TelemetrySchema.AB_EXTENDED),
				buildSeries(TelemetrySchema.FLUCTUS_SEMICOLON));

		assertEquals("no-phase-coverage", aggregated.getInsufficientDataReason());
		assertEquals(ScoreSeverity.WARNING, aggregated.getSeverity());
		assertTrue(aggregated.getScore() < scoringConfig.getWarningBelowScore());
	}

	@Test
	public void aggregateFullScoreUsesAbInterleavedCoverageGuidance() {
		PhaseTwoScoreResult fullScoreRaw = new PhaseTwoScoreResult(
				90.0,
				ScoreSeverity.OK,
				Map.of("velocityZ", 90.0),
				1.0,
				100,
				100,
				null,
				null);
		Map<FlightPhaseWindow, PhaseTwoScoreResult> windows = new EnumMap<>(FlightPhaseWindow.class);
		windows.put(FlightPhaseWindow.BOOST, new PhaseTwoScoreResult(
				88.0,
				ScoreSeverity.WARNING,
				Map.of("velocityZ", 88.0),
				1.0,
				40,
				40,
				null,
				null));

		PhaseTwoScoreResult aggregated = PhaseThreeAnalysisSupport.aggregateFullScore(
				fullScoreRaw,
				windows,
				PhaseTwoScoringConfig.defaults(),
				buildSeries(TelemetrySchema.AB_IMU_INTERLEAVED),
				buildSeries(TelemetrySchema.FLUCTUS_SEMICOLON));

		assertTrue(aggregated.getInsufficientDataReason().contains("AB_IMU_INTERLEAVED"));
	}

	private static TelemetrySeries buildSeries(TelemetrySchema schema) {
		TelemetrySeries series = new TelemetrySeries(schema);
		series.addPoint(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 101325.0, 20.0);
		series.addPoint(1.0, 5.0, 10.0, 0.0, 0.0, 10.0, 101290.0, 19.5);
		series.addPoint(2.0, 2.0, -5.0, 0.0, 0.0, -8.0, 101250.0, 19.0);
		return series;
	}
}

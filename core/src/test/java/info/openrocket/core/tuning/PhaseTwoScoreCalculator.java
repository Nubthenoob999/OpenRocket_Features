package info.openrocket.core.tuning;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PhaseTwoScoreCalculator {
	private static final double CHANNEL_MAPE_WEIGHT = 0.45;
	private static final double CHANNEL_SMAPE_WEIGHT = 0.35;
	private static final double CHANNEL_NRMSE_WEIGHT = 0.20;
	private static final double MIN_CHANNEL_COVERAGE = 0.40;

	private PhaseTwoScoreCalculator() {
	}

	public static PhaseTwoScoreResult score(TelemetryComparisonResult comparison,
												PhaseTwoScoringConfig config) {
		Map<String, ChannelMetrics> metrics = comparison.getChannelMetrics();
		Map<String, Double> channelScores = new LinkedHashMap<>();

		double weightedScoreSum = 0.0;
		double totalWeight = 0.0;
		int evaluatedChannels = 0;
		int lowCoverageChannels = 0;

		double coverageRatio = comparison.getCoverageRatio();
		int matchedSampleCount = comparison.getMatchedSampleCount();
		int timelineSampleCount = comparison.getTimelineSampleCount();

		for (Map.Entry<String, Double> channelWeight : config.getChannelWeights().entrySet()) {
			String channel = channelWeight.getKey();
			Double weight = channelWeight.getValue();
			ChannelMetrics channelMetrics = metrics.get(channel);
			if (channelMetrics == null || channelMetrics.getSampleCount() == 0) {
				continue;
			}
			evaluatedChannels++;
			if (!Double.isFinite(channelMetrics.getCoverageRatio())
					|| channelMetrics.getCoverageRatio() < MIN_CHANNEL_COVERAGE) {
				lowCoverageChannels++;
				continue;
			}

			double channelScore = channelScore(channelMetrics);
			channelScores.put(channel, channelScore);
			weightedScoreSum += channelScore * weight;
			totalWeight += weight;
		}

		double score = totalWeight <= 0.0 ? 0.0 : weightedScoreSum / totalWeight;
		String insufficientDataReason = null;
		if (totalWeight <= 0.0) {
			if (comparison.getInsufficientDataReason() != null && !comparison.getInsufficientDataReason().isBlank()) {
				insufficientDataReason = comparison.getInsufficientDataReason();
			} else if (evaluatedChannels > 0 && lowCoverageChannels == evaluatedChannels) {
				insufficientDataReason = "insufficient-window-coverage";
			} else {
				insufficientDataReason = "no-scored-channels";
			}
		}

		String failureReason = null;
		if (insufficientDataReason != null) {
			failureReason = "INSUFFICIENT_DATA";
		}

		ScoreSeverity severity = classify(score, config);
		return new PhaseTwoScoreResult(
				score,
				severity,
				channelScores,
				coverageRatio,
				matchedSampleCount,
				timelineSampleCount,
				failureReason,
				insufficientDataReason);
	}

	private static double channelScore(ChannelMetrics m) {
		double aggregateError = CHANNEL_MAPE_WEIGHT * m.getMapePercent()
				+ CHANNEL_SMAPE_WEIGHT * m.getSmapePercent()
				+ CHANNEL_NRMSE_WEIGHT * m.getNrmsePercent();

		// Convert percent error into score where 0% => 100 and >=100% approaches 0.
		double score = 100.0 - clamp(aggregateError, 0.0, 100.0);
		return clamp(score, 0.0, 100.0);
	}

	private static ScoreSeverity classify(double score, PhaseTwoScoringConfig config) {
		if (score < config.getCriticalBelowScore()) {
			return ScoreSeverity.CRITICAL;
		}
		if (score < config.getWarningBelowScore()) {
			return ScoreSeverity.WARNING;
		}
		return ScoreSeverity.OK;
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}
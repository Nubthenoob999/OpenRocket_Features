package info.openrocket.core.tuning;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PhaseTwoScoreResult {
	private final double score;
	private final ScoreSeverity severity;
	private final Map<String, Double> channelScores;
	private final double coverageRatio;
	private final int matchedSampleCount;
	private final int timelineSampleCount;
	private final String failureReason;
	private final String insufficientDataReason;

	public PhaseTwoScoreResult(double score, ScoreSeverity severity, Map<String, Double> channelScores) {
		this(score, severity, channelScores, Double.NaN, 0, 0, null, null);
	}

	public PhaseTwoScoreResult(double score,
								ScoreSeverity severity,
								Map<String, Double> channelScores,
								double coverageRatio,
								int matchedSampleCount,
								int timelineSampleCount,
								String failureReason,
								String insufficientDataReason) {
		this.score = score;
		this.severity = severity;
		this.channelScores = Collections.unmodifiableMap(new LinkedHashMap<>(channelScores));
		this.coverageRatio = coverageRatio;
		this.matchedSampleCount = matchedSampleCount;
		this.timelineSampleCount = timelineSampleCount;
		this.failureReason = failureReason;
		this.insufficientDataReason = insufficientDataReason;
	}

	public double getScore() {
		return score;
	}

	public ScoreSeverity getSeverity() {
		return severity;
	}

	public Map<String, Double> getChannelScores() {
		return channelScores;
	}

	public double getCoverageRatio() {
		return coverageRatio;
	}

	public int getMatchedSampleCount() {
		return matchedSampleCount;
	}

	public int getTimelineSampleCount() {
		return timelineSampleCount;
	}

	public String getFailureReason() {
		return failureReason;
	}

	public String getInsufficientDataReason() {
		return insufficientDataReason;
	}
}

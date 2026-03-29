package info.openrocket.core.tuning;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class TelemetryComparisonResult {
	private final Map<String, ChannelMetrics> channelMetrics = new LinkedHashMap<>();
	private int timelineSampleCount;
	private int matchedSampleCount;
	private double coverageRatio;
	private String insufficientDataReason;

	public void put(String channel, ChannelMetrics metrics) {
		channelMetrics.put(channel, metrics);
		if (metrics != null && metrics.getSampleCount() > matchedSampleCount) {
			matchedSampleCount = metrics.getSampleCount();
			if (timelineSampleCount > 0) {
				coverageRatio = (double) matchedSampleCount / (double) timelineSampleCount;
			}
		}
	}

	public Map<String, ChannelMetrics> getChannelMetrics() {
		return Collections.unmodifiableMap(channelMetrics);
	}

	public boolean hasChannel(String channel) {
		return channelMetrics.containsKey(channel);
	}

	public int getTimelineSampleCount() {
		return timelineSampleCount;
	}

	public void setTimelineSampleCount(int timelineSampleCount) {
		this.timelineSampleCount = Math.max(0, timelineSampleCount);
		if (this.timelineSampleCount > 0 && matchedSampleCount > 0) {
			coverageRatio = (double) matchedSampleCount / (double) this.timelineSampleCount;
		}
	}

	public int getMatchedSampleCount() {
		return matchedSampleCount;
	}

	public double getCoverageRatio() {
		return coverageRatio;
	}

	public String getInsufficientDataReason() {
		return insufficientDataReason;
	}

	public void setInsufficientDataReason(String insufficientDataReason) {
		this.insufficientDataReason = insufficientDataReason;
	}
}

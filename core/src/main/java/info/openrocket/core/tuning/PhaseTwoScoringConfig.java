package info.openrocket.core.tuning;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PhaseTwoScoringConfig {
	private final double warningBelowScore;
	private final double criticalBelowScore;
	private final Map<String, Double> channelWeights;

	public PhaseTwoScoringConfig(double warningBelowScore,
							 double criticalBelowScore,
							 Map<String, Double> channelWeights) {
		if (Double.isNaN(warningBelowScore) || Double.isNaN(criticalBelowScore)) {
			throw new IllegalArgumentException("Thresholds must be finite");
		}
		if (criticalBelowScore >= warningBelowScore) {
			throw new IllegalArgumentException("Critical threshold must be below warning threshold");
		}
		if (channelWeights == null || channelWeights.isEmpty()) {
			throw new IllegalArgumentException("Channel weights must not be empty");
		}

		Map<String, Double> copy = new LinkedHashMap<>();
		for (Map.Entry<String, Double> entry : channelWeights.entrySet()) {
			if (entry.getKey() == null || entry.getKey().isBlank()) {
				throw new IllegalArgumentException("Channel name must not be blank");
			}
			Double weight = entry.getValue();
			if (weight == null || !Double.isFinite(weight) || weight <= 0.0) {
				throw new IllegalArgumentException("Channel weight must be finite and > 0 for " + entry.getKey());
			}
			copy.put(entry.getKey(), weight);
		}

		this.warningBelowScore = warningBelowScore;
		this.criticalBelowScore = criticalBelowScore;
		this.channelWeights = Collections.unmodifiableMap(copy);
	}

	public static PhaseTwoScoringConfig defaults() {
		Map<String, Double> weights = new LinkedHashMap<>();
		weights.put("velocityZ", 2.0);
		weights.put("accelZ", 2.0);
		weights.put("altitude", 1.5);
		weights.put("pressure", 1.0);
		weights.put("density", 1.0);
		weights.put("temperature", 0.5);
		weights.put("accelX", 0.5);
		weights.put("accelY", 0.5);
		return new PhaseTwoScoringConfig(80.0, 65.0, weights);
	}

	public double getWarningBelowScore() {
		return warningBelowScore;
	}

	public double getCriticalBelowScore() {
		return criticalBelowScore;
	}

	public Map<String, Double> getChannelWeights() {
		return channelWeights;
	}
}

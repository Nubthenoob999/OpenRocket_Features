package info.openrocket.core.tuning;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class EquationTuningRuleEngine {
	private static final String DEFAULT_GROUP = "rom.general.residual";

	private EquationTuningRuleEngine() {
	}

	public static List<TuningFlag> buildFlags(PhaseTwoScoreResult score,
											 PhaseTwoScoringConfig config) {
		Map<String, String> channelToEquationGroup = buildDefaultChannelGroupMap();
		List<TuningFlag> flags = new ArrayList<>();

		for (Map.Entry<String, Double> entry : score.getChannelScores().entrySet()) {
			String channel = entry.getKey();
			double channelScore = entry.getValue();
			ScoreSeverity severity = classify(channelScore, config);
			if (severity == ScoreSeverity.OK) {
				continue;
			}

			String equationGroup = channelToEquationGroup.getOrDefault(channel, DEFAULT_GROUP);
			flags.add(new TuningFlag(channel, equationGroup, severity, channelScore));
		}

		flags.sort(Comparator
				.comparing((TuningFlag f) -> f.getSeverity() == ScoreSeverity.CRITICAL ? 0 : 1)
				.thenComparing(TuningFlag::getChannelScore));
		return flags;
	}

	private static ScoreSeverity classify(double channelScore, PhaseTwoScoringConfig config) {
		if (channelScore < config.getCriticalBelowScore()) {
			return ScoreSeverity.CRITICAL;
		}
		if (channelScore < config.getWarningBelowScore()) {
			return ScoreSeverity.WARNING;
		}
		return ScoreSeverity.OK;
	}

	private static Map<String, String> buildDefaultChannelGroupMap() {
		Map<String, String> map = new HashMap<>();
		map.put("velocityZ", "rom.drag.force-balance");
		map.put("accelZ", "rom.drag.force-balance");
		map.put("altitude", "rom.integrator.vertical-kinematics");
		map.put("pressure", "rom.atmosphere.static-pressure");
		map.put("density", "rom.atmosphere.density");
		map.put("temperature", "rom.atmosphere.temperature");
		map.put("accelX", "rom.sideslip.lateral-coupling");
		map.put("accelY", "rom.sideslip.lateral-coupling");
		return map;
	}
}
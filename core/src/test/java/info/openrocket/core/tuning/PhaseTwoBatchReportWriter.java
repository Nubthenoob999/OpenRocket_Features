package info.openrocket.core.tuning;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

public final class PhaseTwoBatchReportWriter {
	private PhaseTwoBatchReportWriter() {
	}

	public static void write(PhaseTwoBatchResult result, Path outputDir) throws IOException {
		Files.createDirectories(outputDir);
		writeSummaryCsv(result, outputDir.resolve("phase-two-summary.csv"));
		writeQuantitiesCsv(result, outputDir.resolve("phase-two-quantities.csv"));
		writeImprovementsCsv(result, outputDir.resolve("phase-two-improvements.csv"));
		writeJUnitXml(result, outputDir.resolve("phase-two-junit.xml"));
		writeConsoleLog(result, outputDir.resolve("phase-two-console.log"));
	}

	private static void writeSummaryCsv(PhaseTwoBatchResult result, Path path) throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append("dataset,airbrakeEnabled,pluginStatus,pluginExitCode,fullScore,fullSeverity,boostScore,coastScore,descentScore,referenceCdProxy,candidateCdProxy,")
				.append("referenceApogeeM,candidateApogeeM,apogeeErrorM,apogeeErrorPercent,flags,")
				.append("fullCoverage,boostCoverage,coastCoverage,descentCoverage,")
				.append("fullMatchedSamples,boostMatchedSamples,coastMatchedSamples,descentMatchedSamples,")
				.append("fullTimelineSamples,boostTimelineSamples,coastTimelineSamples,descentTimelineSamples,")
				.append("failureReason,insufficientDataReason,topContributors,romImprovementHints");
		sb.append(System.lineSeparator());

		for (PhaseTwoDatasetResult dataset : result.getDatasets()) {
			PhaseTwoScoreResult full = dataset.getWindowScores().get(FlightPhaseWindow.FULL);
			PhaseTwoScoreResult boost = dataset.getWindowScores().get(FlightPhaseWindow.BOOST);
			PhaseTwoScoreResult coast = dataset.getWindowScores().get(FlightPhaseWindow.COAST);
			PhaseTwoScoreResult descent = dataset.getWindowScores().get(FlightPhaseWindow.DESCENT);

			StringJoiner flags = new StringJoiner("|");
			for (TuningFlag flag : dataset.getTuningFlags()) {
				flags.add(flag.getChannel() + ":" + flag.getEquationGroup() + ":" + flag.getSeverity());
			}

			String failureReason = buildFailureMessage(dataset);
			String insufficientDataReason = full == null ? "" : safe(full.getInsufficientDataReason());
			String topContributors = topContributors(full, 3);
			String improvementHints = buildImprovementHints(dataset, full, 3);
			double referenceApogeeM = dataset.getReferenceQuantities().getApogeeAltitudeMeters();
			double candidateApogeeM = dataset.getCandidateQuantities().getApogeeAltitudeMeters();
			double apogeeErrorM = apogeeErrorMeters(referenceApogeeM, candidateApogeeM);
			double apogeeErrorPercent = apogeeErrorPercent(referenceApogeeM, candidateApogeeM);

			sb.append(csv(dataset.getDatasetName())).append(',')
					.append(dataset.isAirbrakeEnabled()).append(',')
					.append(dataset.getPluginResult().getStatus()).append(',')
					.append(dataset.getPluginResult().getExitCode()).append(',')
					.append(format(full == null ? Double.NaN : full.getScore())).append(',')
					.append(full == null ? "NA" : full.getSeverity()).append(',')
					.append(format(boost == null ? Double.NaN : boost.getScore())).append(',')
					.append(format(coast == null ? Double.NaN : coast.getScore())).append(',')
					.append(format(descent == null ? Double.NaN : descent.getScore())).append(',')
					.append(format(dataset.getReferenceQuantities().getCdProxyMean())).append(',')
					.append(format(dataset.getCandidateQuantities().getCdProxyMean())).append(',')
					.append(format(referenceApogeeM)).append(',')
					.append(format(candidateApogeeM)).append(',')
					.append(format(apogeeErrorM)).append(',')
					.append(format(apogeeErrorPercent)).append(',')
					.append(csv(flags.toString())).append(',')
					.append(format(coverage(full))).append(',')
					.append(format(coverage(boost))).append(',')
					.append(format(coverage(coast))).append(',')
					.append(format(coverage(descent))).append(',')
					.append(samplesMatched(full)).append(',')
					.append(samplesMatched(boost)).append(',')
					.append(samplesMatched(coast)).append(',')
					.append(samplesMatched(descent)).append(',')
					.append(samplesTimeline(full)).append(',')
					.append(samplesTimeline(boost)).append(',')
					.append(samplesTimeline(coast)).append(',')
					.append(samplesTimeline(descent)).append(',')
					.append(csv(failureReason)).append(',')
					.append(csv(insufficientDataReason)).append(',')
					.append(csv(topContributors)).append(',')
					.append(csv(improvementHints))
					.append(System.lineSeparator());
		}

		Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
	}

	private static void writeImprovementsCsv(PhaseTwoBatchResult result, Path path) throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append("dataset,window,rowType,channel,channelScore,severity,equationGroup,recommendation");
		sb.append(System.lineSeparator());

		PhaseTwoScoringConfig scoringConfig = PhaseTwoScoringConfig.defaults();
		for (PhaseTwoDatasetResult dataset : result.getDatasets()) {
			for (FlightPhaseWindow window : FlightPhaseWindow.values()) {
				PhaseTwoScoreResult score = dataset.getWindowScores().get(window);
				if (score == null || score.getChannelScores().isEmpty()) {
					continue;
				}

				List<Map.Entry<String, Double>> channels = new ArrayList<>(score.getChannelScores().entrySet());
				channels.sort(Comparator.comparingDouble(Map.Entry::getValue));
				for (Map.Entry<String, Double> channel : channels) {
					String equationGroup = equationGroupForChannel(channel.getKey());
					ScoreSeverity severity = classifyChannelScore(channel.getValue(), scoringConfig);
					sb.append(csv(dataset.getDatasetName())).append(',')
							.append(window).append(',')
							.append("channel-score").append(',')
							.append(channel.getKey()).append(',')
							.append(format(channel.getValue())).append(',')
							.append(severity).append(',')
							.append(csv(equationGroup)).append(',')
							.append(csv(recommendationForEquationGroup(equationGroup)))
							.append(System.lineSeparator());
				}
			}

			for (TuningFlag flag : dataset.getTuningFlags()) {
				String equationGroup = safe(flag.getEquationGroup());
				if (equationGroup.isBlank()) {
					equationGroup = equationGroupForChannel(flag.getChannel());
				}
				sb.append(csv(dataset.getDatasetName())).append(',')
						.append(FlightPhaseWindow.FULL).append(',')
						.append("tuning-flag").append(',')
						.append(safe(flag.getChannel())).append(',')
						.append(format(flag.getChannelScore())).append(',')
						.append(flag.getSeverity()).append(',')
						.append(csv(equationGroup)).append(',')
						.append(csv(recommendationForEquationGroup(equationGroup)))
						.append(System.lineSeparator());
			}
		}

		Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
	}

	private static void writeQuantitiesCsv(PhaseTwoBatchResult result, Path path) throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append("dataset,source,velocityMean,velocityAbsPeak,accelMean,accelAbsPeak,cdProxyMean,apogeeAltitudeM,apogeeTimeSec,")
				.append("parserSchema,parserSchemaVersion,rowsRead,rowsAccepted,droppedSamples,unitCorrections,outlierDrops,rangeDrops,sentinelDrops");
		sb.append(System.lineSeparator());
		for (PhaseTwoDatasetResult dataset : result.getDatasets()) {
			writeQuantitiesRow(sb,
					dataset.getDatasetName(),
					"reference",
					dataset.getReferenceQuantities(),
					dataset.getReferenceParserDiagnostics());
			writeQuantitiesRow(sb,
					dataset.getDatasetName(),
					"candidate",
					dataset.getCandidateQuantities(),
					dataset.getCandidateParserDiagnostics());
		}
		Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
	}

	private static void writeQuantitiesRow(StringBuilder sb,
									  String datasetName,
									  String source,
									  DerivedTelemetryQuantities.Quantities q,
									  TelemetryParserDiagnostics diagnostics) {
		sb.append(csv(datasetName)).append(',')
				.append(source).append(',')
				.append(format(q.getVelocityMean())).append(',')
				.append(format(q.getVelocityAbsPeak())).append(',')
				.append(format(q.getAccelMean())).append(',')
				.append(format(q.getAccelAbsPeak())).append(',')
				.append(format(q.getCdProxyMean())).append(',')
				.append(format(q.getApogeeAltitudeMeters())).append(',')
				.append(format(q.getApogeeTimeSec())).append(',')
				.append(csv(diagnostics == null ? "" : diagnostics.getSchema())).append(',')
				.append(csv(diagnostics == null ? "" : diagnostics.getSchemaVersion())).append(',')
				.append(diagnostics == null ? 0 : diagnostics.getRowsRead()).append(',')
				.append(diagnostics == null ? 0 : diagnostics.getRowsAccepted()).append(',')
				.append(diagnostics == null ? 0 : diagnostics.getDroppedSamples()).append(',')
				.append(diagnostics == null ? 0 : diagnostics.getUnitCorrections()).append(',')
				.append(diagnostics == null ? 0 : diagnostics.getOutlierDrops()).append(',')
				.append(diagnostics == null ? 0 : diagnostics.getRangeDrops()).append(',')
				.append(diagnostics == null ? 0 : diagnostics.getSentinelDrops())
				.append(System.lineSeparator());
	}

	private static void writeJUnitXml(PhaseTwoBatchResult result, Path path) throws IOException {
		int tests = result.getDatasets().size();
		int failures = 0;
		for (PhaseTwoDatasetResult dataset : result.getDatasets()) {
			if (isDatasetFailure(dataset)) {
				failures++;
			}
		}

		StringBuilder xml = new StringBuilder();
		xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>").append(System.lineSeparator());
		xml.append("<testsuite name=\"phase-two-rom-validation\" tests=\"").append(tests)
				.append("\" failures=\"").append(failures).append("\">").append(System.lineSeparator());

		for (PhaseTwoDatasetResult dataset : result.getDatasets()) {
			PhaseTwoScoreResult full = dataset.getWindowScores().get(FlightPhaseWindow.FULL);
			xml.append("  <testcase classname=\"PhaseTwoBatch\" name=\"")
					.append(escapeXml(dataset.getDatasetName())).append("\">").append(System.lineSeparator());
			if (isDatasetFailure(dataset)) {
				xml.append("    <failure message=\"")
						.append(escapeXml(buildFailureMessage(dataset)))
						.append("\">")
						.append(escapeXml(buildFailureDetail(dataset, full)))
						.append("</failure>").append(System.lineSeparator());
			}
			xml.append("  </testcase>").append(System.lineSeparator());
		}

		xml.append("</testsuite>").append(System.lineSeparator());
		Files.writeString(path, xml.toString(), StandardCharsets.UTF_8);
	}

	private static boolean isDatasetFailure(PhaseTwoDatasetResult dataset) {
		PhaseTwoScoreResult full = dataset.getWindowScores().get(FlightPhaseWindow.FULL);
		if (full == null || full.getSeverity() == ScoreSeverity.CRITICAL) {
			return true;
		}
		AbPluginExecutionResult plugin = dataset.getPluginResult();
		if (plugin == null) {
			return true;
		}
		return plugin.getStatus() == AbPluginExecutionResult.Status.FAILED
				|| plugin.getStatus() == AbPluginExecutionResult.Status.TIMED_OUT;
	}

	private static String buildFailureMessage(PhaseTwoDatasetResult dataset) {
		if (!isDatasetFailure(dataset)) {
			return "";
		}

		AbPluginExecutionResult plugin = dataset.getPluginResult();
		PhaseTwoScoreResult full = dataset.getWindowScores().get(FlightPhaseWindow.FULL);

		StringBuilder message = new StringBuilder();
		if (plugin == null) {
			message.append("plugin-missing");
		} else if (plugin.getStatus() != AbPluginExecutionResult.Status.SUCCEEDED) {
			message.append("plugin-")
					.append(plugin.getStatus())
					.append("-exit-")
					.append(plugin.getExitCode());
		}

		if (full == null) {
			if (message.length() > 0) {
				message.append("; ");
			}
			message.append("missing-full-score");
		} else if (full.getFailureReason() != null && !full.getFailureReason().isBlank()) {
			if (message.length() > 0) {
				message.append("; ");
			}
			message.append(full.getFailureReason());
		} else if (full.getSeverity() == ScoreSeverity.CRITICAL) {
			if (message.length() > 0) {
				message.append("; ");
			}
			message.append("critical-full-score-")
					.append(format(full.getScore()));
		}

		if (message.length() == 0) {
			return "";
		}
		return message.toString();
	}

	private static String buildFailureDetail(PhaseTwoDatasetResult dataset, PhaseTwoScoreResult full) {
		StringBuilder detail = new StringBuilder();
		detail.append("dataset=").append(dataset.getDatasetName());

		AbPluginExecutionResult plugin = dataset.getPluginResult();
		if (plugin != null) {
			detail.append(", pluginStatus=").append(plugin.getStatus())
					.append(", pluginExitCode=").append(plugin.getExitCode());
			String pluginMsg = plugin.getMessage();
			if (pluginMsg != null && !pluginMsg.isBlank()) {
				detail.append(", pluginMessage=").append(pluginMsg);
			}
		}

		if (full != null) {
			double referenceApogeeM = dataset.getReferenceQuantities().getApogeeAltitudeMeters();
			double candidateApogeeM = dataset.getCandidateQuantities().getApogeeAltitudeMeters();
			double apogeeErrorM = apogeeErrorMeters(referenceApogeeM, candidateApogeeM);
			double apogeeErrorPercent = apogeeErrorPercent(referenceApogeeM, candidateApogeeM);

			detail.append(", fullScore=").append(format(full.getScore()))
					.append(", fullSeverity=").append(full.getSeverity())
					.append(", fullCoverage=").append(format(coverage(full)))
					.append(", fullMatchedSamples=").append(samplesMatched(full))
					.append(", fullTimelineSamples=").append(samplesTimeline(full))
					.append(", referenceApogeeM=").append(format(referenceApogeeM))
					.append(", candidateApogeeM=").append(format(candidateApogeeM))
					.append(", apogeeErrorM=").append(format(apogeeErrorM))
					.append(", apogeeErrorPercent=").append(format(apogeeErrorPercent));
			if (full.getInsufficientDataReason() != null && !full.getInsufficientDataReason().isBlank()) {
				detail.append(", insufficientDataReason=").append(full.getInsufficientDataReason());
			}
			String contributors = topContributors(full, 3);
			if (!contributors.isBlank()) {
				detail.append(", topContributors=").append(contributors);
			}
			String improvementHints = buildImprovementHints(dataset, full, 3);
			if (!improvementHints.isBlank()) {
				detail.append(", romImprovementHints=").append(improvementHints);
			}
		}

		detail.append(", referenceParserDrops=").append(dataset.getReferenceParserDiagnostics().getDroppedSamples())
				.append(", candidateParserDrops=").append(dataset.getCandidateParserDiagnostics().getDroppedSamples());

		return detail.toString();
	}

	private static void writeConsoleLog(PhaseTwoBatchResult result, Path path) throws IOException {
		StringBuilder log = new StringBuilder();
		for (PhaseTwoDatasetResult dataset : result.getDatasets()) {
			PhaseTwoScoreResult full = dataset.getWindowScores().get(FlightPhaseWindow.FULL);
			PhaseTwoScoreResult boost = dataset.getWindowScores().get(FlightPhaseWindow.BOOST);
			PhaseTwoScoreResult coast = dataset.getWindowScores().get(FlightPhaseWindow.COAST);
			PhaseTwoScoreResult descent = dataset.getWindowScores().get(FlightPhaseWindow.DESCENT);
			String failureReason = buildFailureMessage(dataset);
			String topContributors = topContributors(full, 3);
			String improvementHints = buildImprovementHints(dataset, full, 3);
			log.append("dataset=").append(dataset.getDatasetName())
					.append(",airbrake=").append(dataset.isAirbrakeEnabled())
					.append(",plugin=").append(dataset.getPluginResult().getStatus())
					.append(",score=").append(format(full == null ? Double.NaN : full.getScore()))
					.append(",severity=").append(full == null ? "NA" : full.getSeverity())
					.append(",boostScore=").append(format(boost == null ? Double.NaN : boost.getScore()))
					.append(",coastScore=").append(format(coast == null ? Double.NaN : coast.getScore()))
					.append(",descentScore=").append(format(descent == null ? Double.NaN : descent.getScore()))
					.append(",coverage=").append(format(coverage(full)))
					.append(",topContributors=").append(topContributors)
					.append(",romImprovementHints=").append(improvementHints)
					.append(",failureReason=").append(failureReason)
					.append(",insufficientDataReason=").append(full == null ? "" : safe(full.getInsufficientDataReason()))
					.append(System.lineSeparator());
		}
		Files.writeString(path, log.toString(), StandardCharsets.UTF_8);
	}

	private static String format(double value) {
		if (!Double.isFinite(value)) {
			return "NA";
		}
		return String.format(Locale.US, "%.3f", value);
	}

	private static String csv(String value) {
		if (value == null) {
			return "";
		}
		String escaped = value.replace("\"", "\"\"");
		return "\"" + escaped + "\"";
	}

	private static String escapeXml(String text) {
		if (text == null) {
			return "";
		}
		return text
				.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\"", "&quot;")
				.replace("'", "&apos;");
	}

	private static String safe(String value) {
		return value == null ? "" : value;
	}

	private static double coverage(PhaseTwoScoreResult score) {
		return score == null ? Double.NaN : score.getCoverageRatio();
	}

	private static int samplesMatched(PhaseTwoScoreResult score) {
		return score == null ? 0 : score.getMatchedSampleCount();
	}

	private static int samplesTimeline(PhaseTwoScoreResult score) {
		return score == null ? 0 : score.getTimelineSampleCount();
	}

	private static String topContributors(PhaseTwoScoreResult score, int count) {
		if (score == null || score.getChannelScores().isEmpty()) {
			return "";
		}
		List<Map.Entry<String, Double>> sorted = score.getChannelScores().entrySet().stream()
				.sorted(Comparator.comparingDouble(Map.Entry::getValue))
				.limit(Math.max(1, count))
				.toList();
		StringJoiner joiner = new StringJoiner("|");
		for (Map.Entry<String, Double> entry : sorted) {
			joiner.add(entry.getKey() + ":" + format(entry.getValue()));
		}
		return joiner.toString();
	}

	private static String buildImprovementHints(PhaseTwoDatasetResult dataset, PhaseTwoScoreResult score, int count) {
		if (score == null || score.getChannelScores().isEmpty()) {
			return "";
		}

		List<Map.Entry<String, Double>> sorted = score.getChannelScores().entrySet().stream()
				.sorted(Comparator.comparingDouble(Map.Entry::getValue))
				.limit(Math.max(1, count))
				.toList();

		StringJoiner joiner = new StringJoiner("|");
		for (Map.Entry<String, Double> entry : sorted) {
			String equationGroup = equationGroupForChannel(entry.getKey());
			String recommendation = recommendationForEquationGroup(equationGroup);
			joiner.add(entry.getKey() + "->" + recommendation);
		}

		for (TuningFlag flag : dataset.getTuningFlags()) {
			if (flag.getSeverity() == ScoreSeverity.OK) {
				continue;
			}
			String recommendation = recommendationForEquationGroup(flag.getEquationGroup());
			String hint = safe(flag.getChannel()) + "->" + recommendation;
			if (!joiner.toString().contains(hint)) {
				joiner.add(hint);
			}
		}

		return joiner.toString();
	}

	private static ScoreSeverity classifyChannelScore(double channelScore, PhaseTwoScoringConfig config) {
		if (!Double.isFinite(channelScore)) {
			return ScoreSeverity.WARNING;
		}
		if (channelScore < config.getCriticalBelowScore()) {
			return ScoreSeverity.CRITICAL;
		}
		if (channelScore < config.getWarningBelowScore()) {
			return ScoreSeverity.WARNING;
		}
		return ScoreSeverity.OK;
	}

	private static String equationGroupForChannel(String channel) {
		if ("velocityZ".equals(channel) || "accelZ".equals(channel)) {
			return "rom.drag.force-balance";
		}
		if ("altitude".equals(channel)) {
			return "rom.integrator.vertical-kinematics";
		}
		if ("pressure".equals(channel)) {
			return "rom.atmosphere.static-pressure";
		}
		if ("density".equals(channel)) {
			return "rom.atmosphere.density";
		}
		if ("temperature".equals(channel)) {
			return "rom.atmosphere.temperature";
		}
		if ("accelX".equals(channel) || "accelY".equals(channel)) {
			return "rom.sideslip.lateral-coupling";
		}
		return "rom.general.residual";
	}

	private static String recommendationForEquationGroup(String equationGroup) {
		if (equationGroup == null || equationGroup.isBlank()) {
			return "inspect residual channel behavior and sensor alignment";
		}
		switch (equationGroup) {
			case "rom.drag.force-balance":
				return "retune drag-force terms (Cd, reference area, and dynamic-pressure coupling)";
			case "rom.integrator.vertical-kinematics":
				return "check vertical integrator stability and acceleration-to-altitude propagation";
			case "rom.atmosphere.static-pressure":
				return "revisit static-pressure model and pressure sensor bias corrections";
			case "rom.atmosphere.temperature":
				return "refine temperature lapse-rate and ground-offset calibration";
			case "rom.atmosphere.density":
				return "align density with pressure-temperature consistency and atmospheric constants";
			case "rom.sideslip.lateral-coupling":
				return "inspect lateral coupling/sideslip terms and inertial frame alignment";
			case "data-quality.cross-sensor-consistency":
				return "verify cross-sensor calibration and timeline synchronization";
			case "DATA_QUALITY:HIGH_SENTINEL_RATE":
				return "clean parser sentinels and improve telemetry quality filters";
			case "PHYSICS:CD_PROXY_OUT_OF_RANGE":
				return "review drag proxy assumptions and acceleration sign conventions";
			case "TEMPERATURE_PROFILE_MISMATCH":
				return "reconcile modeled temperature profile with measured atmospheric trend";
			default:
				return "inspect residual channel behavior and sensor alignment";
		}
	}

	private static double apogeeErrorMeters(double referenceApogeeM, double candidateApogeeM) {
		if (!Double.isFinite(referenceApogeeM) || !Double.isFinite(candidateApogeeM)) {
			return Double.NaN;
		}
		return Math.abs(candidateApogeeM - referenceApogeeM);
	}

	private static double apogeeErrorPercent(double referenceApogeeM, double candidateApogeeM) {
		double absErrorM = apogeeErrorMeters(referenceApogeeM, candidateApogeeM);
		if (!Double.isFinite(absErrorM) || !Double.isFinite(referenceApogeeM) || referenceApogeeM <= 0.0) {
			return Double.NaN;
		}
		return 100.0 * absErrorM / referenceApogeeM;
	}
}
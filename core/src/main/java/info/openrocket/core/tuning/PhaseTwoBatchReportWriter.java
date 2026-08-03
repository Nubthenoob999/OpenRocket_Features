package info.openrocket.core.tuning;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

public final class PhaseTwoBatchReportWriter {
	private static final String RESIDUALS_CSV = "phase-three-drag-residuals.csv";
	private static final double ASCENT_BOOST_GATE_SCORE = 90.0;
	private static final double ASCENT_COAST_GATE_SCORE = 90.0;
	private static final double ASCENT_APOGEE_TIME_GATE_SEC = 0.1;
	private static final double ASCENT_APOGEE_PERCENT_GATE = 1.0;

	private PhaseTwoBatchReportWriter() {
	}

	public static void write(PhaseTwoBatchResult result, Path outputDir) throws IOException {
		Files.createDirectories(outputDir);
		writeSummaryCsv(result, outputDir.resolve("phase-two-summary.csv"));
		writeQuantitiesCsv(result, outputDir.resolve("phase-two-quantities.csv"));
		writeImprovementsCsv(result, outputDir.resolve("phase-two-improvements.csv"));
		writePhaseThreeAnalysisCsv(result, outputDir.resolve("phase-three-analysis.csv"));
		writeResidualsCsv(result, outputDir.resolve(RESIDUALS_CSV));
		writeJUnitXml(result, outputDir.resolve("phase-two-junit.xml"));
		writeConsoleLog(result, outputDir.resolve("phase-two-console.log"));
	}

	private static void writeSummaryCsv(PhaseTwoBatchResult result, Path path) throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append("dataset,airbrakeEnabled,airbrakesStatus,airbrakesExitCode,fullScore,fullSeverity,boostScore,coastScore,descentScore,referenceCdProxy,candidateCdProxy,")
				.append("referenceApogeeM,candidateApogeeM,apogeeErrorM,apogeeErrorPercent,")
				.append("datasetClass,candidateSource,orkProvenance,physicsAeroMode,tableSource,candidateMaxMach,")
				.append("referenceAlignedApogeeTimeSec,candidateAlignedApogeeTimeSec,alignedApogeeTimeDeltaSec,alignedApogeeTimeErrorSec,")
				.append("ascentScore,ascentLaneStatus,ascentGateFailures,reliabilityLaneStatus,reliabilityFailureReason,flags,")
				.append("truthSource,alignmentChannel,alignmentLagSec,alignmentQuality,")
				.append("candidateAccelBiasEstimateMps2,candidatePreLaunchSamples,candidateLaunchDetectedTimeSec,candidateBurnoutDetectedTimeSec,candidateApogeeDetectedTimeSec,candidateDeploymentDetectedTimeSec,integratorDiagnostics,")
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
			double boostScoreValue = boost == null ? Double.NaN : boost.getScore();
			double coastScoreValue = coast == null ? Double.NaN : coast.getScore();
			double ascentScore = computeAscentScore(boostScoreValue, coastScoreValue);

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
			LaneEvaluation ascentLane = evaluateAscentLane(dataset, boostScoreValue, coastScoreValue, apogeeErrorPercent);
			LaneEvaluation reliabilityLane = evaluateReliabilityLane(dataset);
			VerticalIntegratorDiagnostics integratorDiagnostics = (VerticalIntegratorDiagnostics) datasetProperty(dataset, "getIntegratorDiagnostics", "integratorDiagnostics");

			sb.append(csv(dataset.getDatasetName())).append(',')
					.append(dataset.isAirbrakeEnabled()).append(',')
					.append(dataset.getPluginResult().getStatus()).append(',')
					.append(dataset.getPluginResult().getExitCode()).append(',')
					.append(format(full == null ? Double.NaN : full.getScore())).append(',')
					.append(full == null ? "NA" : full.getSeverity()).append(',')
					.append(format(boostScoreValue)).append(',')
					.append(format(coastScoreValue)).append(',')
					.append(format(descent == null ? Double.NaN : descent.getScore())).append(',')
					.append(format(dataset.getReferenceQuantities().getCdProxyMean())).append(',')
					.append(format(dataset.getCandidateQuantities().getCdProxyMean())).append(',')
					.append(format(referenceApogeeM)).append(',')
					.append(format(candidateApogeeM)).append(',')
					.append(format(apogeeErrorM)).append(',')
					.append(format(apogeeErrorPercent)).append(',')
					.append(csv(dataset.getDatasetClass())).append(',')
					.append(csv(dataset.getCandidateSource())).append(',')
					.append(csv(dataset.getOrkProvenance())).append(',')
					.append(csv(dataset.getPhysicsAeroMode())).append(',')
					.append(csv(dataset.getTableSource())).append(',')
					.append(format(dataset.getCandidateMaxMach())).append(',')
					.append(format(dataset.getReferenceAlignedApogeeTimeSec())).append(',')
					.append(format(dataset.getCandidateAlignedApogeeTimeSec())).append(',')
					.append(format(dataset.getAlignedApogeeTimeDeltaSec())).append(',')
					.append(format(dataset.getAlignedApogeeTimeErrorSec())).append(',')
					.append(format(ascentScore)).append(',')
					.append(ascentLane.status()).append(',')
					.append(csv(ascentLane.reasons())).append(',')
					.append(reliabilityLane.status()).append(',')
					.append(csv(reliabilityLane.reasons())).append(',')
					.append(csv(flags.toString())).append(',')
					.append(csv(stringValue(datasetProperty(dataset, "getTruthSource", "truthSource")))).append(',')
					.append(csv(stringValue(datasetProperty(dataset, "getAlignmentChannel", "alignmentChannel")))).append(',')
					.append(formatValue(datasetProperty(dataset, "getAlignmentLagSec", "alignmentLagSec"))).append(',')
					.append(csv(stringValue(datasetProperty(dataset, "getAlignmentQuality", "alignmentQuality")))).append(',')
					.append(format(integratorDiagnostics == null ? Double.NaN : integratorDiagnostics.getAccelBiasEstimateMetersPerSec2())).append(',')
					.append(integratorDiagnostics == null ? 0 : integratorDiagnostics.getPreLaunchSampleCount()).append(',')
					.append(format(integratorDiagnostics == null ? Double.NaN : integratorDiagnostics.getLaunchDetectedTimeSec())).append(',')
					.append(format(integratorDiagnostics == null ? Double.NaN : integratorDiagnostics.getBurnoutDetectedTimeSec())).append(',')
					.append(format(integratorDiagnostics == null ? Double.NaN : integratorDiagnostics.getApogeeDetectedTimeSec())).append(',')
					.append(format(integratorDiagnostics == null ? Double.NaN : integratorDiagnostics.getDeploymentDetectedTimeSec())).append(',')
					.append(csv(renderObject(integratorDiagnostics))).append(',')
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
		sb.append("dataset,source,velocityMean,velocityAbsPeak,accelMean,accelAbsPeak,cdProxyMean,launchTimeSec,burnoutTimeSec,apogeeAltitudeM,apogeeTimeSec,deploymentTimeSec,alignedApogeeTimeSec,maxMach,")
				.append("parserSchema,parserSchemaVersion,rowsRead,rowsAccepted,droppedSamples,unitCorrections,outlierDrops,rangeDrops,sentinelDrops");
		sb.append(System.lineSeparator());
		for (PhaseTwoDatasetResult dataset : result.getDatasets()) {
			writeQuantitiesRow(sb,
					dataset.getDatasetName(),
					"reference",
					dataset.getReferenceQuantities(),
					dataset.getReferenceParserDiagnostics(),
					dataset.getReferenceAlignedApogeeTimeSec(),
					Double.NaN);
			writeQuantitiesRow(sb,
					dataset.getDatasetName(),
					"candidate",
					dataset.getCandidateQuantities(),
					dataset.getCandidateParserDiagnostics(),
					dataset.getCandidateAlignedApogeeTimeSec(),
					dataset.getCandidateMaxMach());
		}
		Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
	}

	private static void writeQuantitiesRow(StringBuilder sb,
									  String datasetName,
									  String source,
									  DerivedTelemetryQuantities.Quantities q,
									  TelemetryParserDiagnostics diagnostics,
									  double alignedApogeeTimeSec,
									  double maxMach) {
		sb.append(csv(datasetName)).append(',')
				.append(source).append(',')
				.append(format(q.getVelocityMean())).append(',')
				.append(format(q.getVelocityAbsPeak())).append(',')
				.append(format(q.getAccelMean())).append(',')
				.append(format(q.getAccelAbsPeak())).append(',')
				.append(format(q.getCdProxyMean())).append(',')
				.append(format(q.getLaunchTimeSec())).append(',')
				.append(format(q.getBurnoutTimeSec())).append(',')
				.append(format(q.getApogeeAltitudeMeters())).append(',')
				.append(format(q.getApogeeTimeSec())).append(',')
				.append(format(q.getDeploymentTimeSec())).append(',')
				.append(format(alignedApogeeTimeSec)).append(',')
				.append(format(maxMach)).append(',')
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

	private static void writePhaseThreeAnalysisCsv(PhaseTwoBatchResult result, Path path) throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append("dataset,datasetClass,candidateSource,orkProvenance,physicsAeroMode,tableSource,candidateMaxMach,")
				.append("referenceAlignedApogeeTimeSec,candidateAlignedApogeeTimeSec,alignedApogeeTimeDeltaSec,alignedApogeeTimeErrorSec,")
				.append("fullScore,fullSeverity,apogeeErrorPercent,ascentScore,ascentLaneStatus,ascentGateFailures,reliabilityLaneStatus,reliabilityFailureReason,")
				.append("primaryWeakness,primaryWeaknessScore,primaryStrength,primaryStrengthScore,recommendedFocus,")
				.append("truthSource,alignmentChannel,alignmentLagSec,alignmentQuality,")
				.append("candidateAccelBiasEstimateMps2,candidateLaunchDetectedTimeSec,candidateBurnoutDetectedTimeSec,candidateDeploymentDetectedTimeSec,")
				.append("weakestResidualPhase,weakestResidualChannel,weakestResidualNrmse,")
				.append("primaryDragResidualPhase,primaryDragDeltaPercent,integratorDiagnostics");
		sb.append(System.lineSeparator());

		for (PhaseTwoDatasetResult dataset : result.getDatasets()) {
			PhaseTwoScoreResult full = dataset.getWindowScores().get(FlightPhaseWindow.FULL);
			PhaseTwoScoreResult boost = dataset.getWindowScores().get(FlightPhaseWindow.BOOST);
			PhaseTwoScoreResult coast = dataset.getWindowScores().get(FlightPhaseWindow.COAST);
			double boostScoreValue = boost == null ? Double.NaN : boost.getScore();
			double coastScoreValue = coast == null ? Double.NaN : coast.getScore();
			double ascentScore = computeAscentScore(boostScoreValue, coastScoreValue);
			double referenceApogeeM = dataset.getReferenceQuantities().getApogeeAltitudeMeters();
			double candidateApogeeM = dataset.getCandidateQuantities().getApogeeAltitudeMeters();
			double apogeeErrorPercent = apogeeErrorPercent(referenceApogeeM, candidateApogeeM);
			LaneEvaluation ascentLane = evaluateAscentLane(dataset, boostScoreValue, coastScoreValue, apogeeErrorPercent);
			LaneEvaluation reliabilityLane = evaluateReliabilityLane(dataset);
			Map.Entry<String, Double> weakness = weakestChannel(full);
			Map.Entry<String, Double> strength = strongestChannel(full);
			String recommendedFocus = buildImprovementHints(dataset, full, 1);
			VerticalIntegratorDiagnostics integratorDiagnostics = (VerticalIntegratorDiagnostics) datasetProperty(dataset, "getIntegratorDiagnostics", "integratorDiagnostics");
			ResidualHeadline weakestResidual = weakestResidual(dataset.getPhaseResiduals());
			DragHeadline strongestDragResidual = strongestDragResidual(dataset.getPhaseResiduals());

			sb.append(csv(dataset.getDatasetName())).append(',')
					.append(csv(dataset.getDatasetClass())).append(',')
					.append(csv(dataset.getCandidateSource())).append(',')
					.append(csv(dataset.getOrkProvenance())).append(',')
					.append(csv(dataset.getPhysicsAeroMode())).append(',')
					.append(csv(dataset.getTableSource())).append(',')
					.append(format(dataset.getCandidateMaxMach())).append(',')
					.append(format(dataset.getReferenceAlignedApogeeTimeSec())).append(',')
					.append(format(dataset.getCandidateAlignedApogeeTimeSec())).append(',')
					.append(format(dataset.getAlignedApogeeTimeDeltaSec())).append(',')
					.append(format(dataset.getAlignedApogeeTimeErrorSec())).append(',')
					.append(format(full == null ? Double.NaN : full.getScore())).append(',')
					.append(full == null ? "NA" : full.getSeverity()).append(',')
					.append(format(apogeeErrorPercent)).append(',')
					.append(format(ascentScore)).append(',')
					.append(ascentLane.status()).append(',')
					.append(csv(ascentLane.reasons())).append(',')
					.append(reliabilityLane.status()).append(',')
					.append(csv(reliabilityLane.reasons())).append(',')
					.append(csv(weakness == null ? "" : weakness.getKey())).append(',')
					.append(format(weakness == null ? Double.NaN : weakness.getValue())).append(',')
					.append(csv(strength == null ? "" : strength.getKey())).append(',')
					.append(format(strength == null ? Double.NaN : strength.getValue())).append(',')
					.append(csv(recommendedFocus)).append(',')
					.append(csv(stringValue(datasetProperty(dataset, "getTruthSource", "truthSource")))).append(',')
					.append(csv(stringValue(datasetProperty(dataset, "getAlignmentChannel", "alignmentChannel")))).append(',')
					.append(formatValue(datasetProperty(dataset, "getAlignmentLagSec", "alignmentLagSec"))).append(',')
					.append(csv(stringValue(datasetProperty(dataset, "getAlignmentQuality", "alignmentQuality")))).append(',')
					.append(format(integratorDiagnostics == null ? Double.NaN : integratorDiagnostics.getAccelBiasEstimateMetersPerSec2())).append(',')
					.append(format(integratorDiagnostics == null ? Double.NaN : integratorDiagnostics.getLaunchDetectedTimeSec())).append(',')
					.append(format(integratorDiagnostics == null ? Double.NaN : integratorDiagnostics.getBurnoutDetectedTimeSec())).append(',')
					.append(format(integratorDiagnostics == null ? Double.NaN : integratorDiagnostics.getDeploymentDetectedTimeSec())).append(',')
					.append(csv(weakestResidual.phase())).append(',')
					.append(csv(weakestResidual.channel())).append(',')
					.append(format(weakestResidual.nrmse())).append(',')
					.append(csv(strongestDragResidual.phase())).append(',')
					.append(format(strongestDragResidual.deltaPercent())).append(',')
					.append(csv(renderObject(integratorDiagnostics)))
					.append(System.lineSeparator());
		}

		Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
	}

	private static void writeResidualsCsv(PhaseTwoBatchResult result, Path path) throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append("dataset,phase,residualType,channel,truthSource,alignmentChannel,alignmentLagSec,alignmentQuality,")
				.append("signedMeanError,mae,rmse,nrmse,truthMean,candidateMean,delta,percentDelta,")
				.append("integratorDiagnostics,residualSummary");
		sb.append(System.lineSeparator());

		for (PhaseTwoDatasetResult dataset : result.getDatasets()) {
			Object integratorDiagnostics = datasetProperty(dataset, "getIntegratorDiagnostics", "integratorDiagnostics");
			for (Map.Entry<FlightPhaseWindow, PhaseResidualMetrics> entry : dataset.getPhaseResiduals().entrySet()) {
				String phase = entry.getKey().name();
				PhaseResidualMetrics metrics = entry.getValue();
				if (metrics == null) {
					continue;
				}
				for (Map.Entry<String, ResidualMetrics> channelEntry : metrics.getChannelResiduals().entrySet()) {
					ResidualMetrics residual = channelEntry.getValue();
					if (residual == null || residual.getSampleCount() <= 0) {
						continue;
					}
					sb.append(csv(dataset.getDatasetName())).append(',')
							.append(csv(phase)).append(',')
							.append(csv("motion")).append(',')
							.append(csv(channelEntry.getKey())).append(',')
							.append(csv(dataset.getTruthSource())).append(',')
							.append(csv(dataset.getAlignmentChannel())).append(',')
							.append(format(dataset.getAlignmentLagSec())).append(',')
							.append(format(dataset.getAlignmentQuality())).append(',')
							.append(format(residual.getSignedMeanError())).append(',')
							.append(format(residual.getMae())).append(',')
							.append(format(residual.getRmse())).append(',')
							.append(format(residual.getNrmsePercent())).append(',')
							.append("NA,NA,NA,NA,")
							.append(csv(renderObject(integratorDiagnostics))).append(',')
							.append(csv(buildMotionResidualSummary(phase, channelEntry.getKey(), residual)))
							.append(System.lineSeparator());
				}

				DragResidualMetrics drag = metrics.getDragResidual();
				if (drag != null && drag.getSampleCount() > 0) {
					sb.append(csv(dataset.getDatasetName())).append(',')
							.append(csv(phase)).append(',')
							.append(csv("dragProxy")).append(',')
							.append(csv("cdProxy")).append(',')
							.append(csv(dataset.getTruthSource())).append(',')
							.append(csv(dataset.getAlignmentChannel())).append(',')
							.append(format(dataset.getAlignmentLagSec())).append(',')
							.append(format(dataset.getAlignmentQuality())).append(',')
							.append("NA,NA,NA,NA,")
							.append(format(drag.getTruthMean())).append(',')
							.append(format(drag.getCandidateMean())).append(',')
							.append(format(drag.getDelta())).append(',')
							.append(format(drag.getDeltaPercent())).append(',')
							.append(csv(renderObject(integratorDiagnostics))).append(',')
							.append(csv(buildDragResidualSummary(phase, drag)))
							.append(System.lineSeparator());
				}
			}
		}

		Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
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
		AbPluginExecutionResult airbrakes = dataset.getPluginResult();
		if (airbrakes == null) {
			return true;
		}
		return airbrakes.getStatus() == AbPluginExecutionResult.Status.FAILED
				|| airbrakes.getStatus() == AbPluginExecutionResult.Status.TIMED_OUT;
	}

	private static String buildFailureMessage(PhaseTwoDatasetResult dataset) {
		if (!isDatasetFailure(dataset)) {
			return "";
		}

		AbPluginExecutionResult airbrakes = dataset.getPluginResult();
		PhaseTwoScoreResult full = dataset.getWindowScores().get(FlightPhaseWindow.FULL);

		StringBuilder message = new StringBuilder();
		if (airbrakes == null) {
			message.append("airbrakes-missing");
		} else if (airbrakes.getStatus() == AbPluginExecutionResult.Status.FAILED
				|| airbrakes.getStatus() == AbPluginExecutionResult.Status.TIMED_OUT) {
			message.append("airbrakes-")
					.append(airbrakes.getStatus())
					.append("-exit-")
					.append(airbrakes.getExitCode());
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

		AbPluginExecutionResult airbrakes = dataset.getPluginResult();
		if (airbrakes != null) {
			detail.append(", airbrakesStatus=").append(airbrakes.getStatus())
					.append(", airbrakesExitCode=").append(airbrakes.getExitCode());
			String airbrakesMessage = airbrakes.getMessage();
			if (airbrakesMessage != null && !airbrakesMessage.isBlank()) {
				detail.append(", airbrakesMessage=").append(airbrakesMessage);
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
					.append(", apogeeErrorPercent=").append(format(apogeeErrorPercent))
					.append(", datasetClass=").append(dataset.getDatasetClass())
					.append(", physicsAeroMode=").append(dataset.getPhysicsAeroMode())
					.append(", tableSource=").append(dataset.getTableSource())
					.append(", candidateMaxMach=").append(format(dataset.getCandidateMaxMach()))
					.append(", alignedApogeeTimeDeltaSec=").append(format(dataset.getAlignedApogeeTimeDeltaSec()))
					.append(", alignedApogeeTimeErrorSec=").append(format(dataset.getAlignedApogeeTimeErrorSec()));
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
			double boostScoreValue = boost == null ? Double.NaN : boost.getScore();
			double coastScoreValue = coast == null ? Double.NaN : coast.getScore();
			double referenceApogeeM = dataset.getReferenceQuantities().getApogeeAltitudeMeters();
			double candidateApogeeM = dataset.getCandidateQuantities().getApogeeAltitudeMeters();
			double apogeePercentError = apogeeErrorPercent(referenceApogeeM, candidateApogeeM);
			double ascentScore = computeAscentScore(boostScoreValue, coastScoreValue);
			LaneEvaluation ascentLane = evaluateAscentLane(dataset, boostScoreValue, coastScoreValue, apogeePercentError);
			LaneEvaluation reliabilityLane = evaluateReliabilityLane(dataset);
			String failureReason = buildFailureMessage(dataset);
			String topContributors = topContributors(full, 3);
			String improvementHints = buildImprovementHints(dataset, full, 3);
			log.append("dataset=").append(dataset.getDatasetName())
					.append(",datasetClass=").append(dataset.getDatasetClass())
					.append(",candidateSource=").append(dataset.getCandidateSource())
					.append(",airbrake=").append(dataset.isAirbrakeEnabled())
					.append(",airbrakes=").append(dataset.getPluginResult().getStatus())
					.append(",score=").append(format(full == null ? Double.NaN : full.getScore()))
					.append(",severity=").append(full == null ? "NA" : full.getSeverity())
					.append(",physicsAeroMode=").append(dataset.getPhysicsAeroMode())
					.append(",tableSource=").append(dataset.getTableSource())
					.append(",candidateMaxMach=").append(format(dataset.getCandidateMaxMach()))
					.append(",alignedApogeeTimeDeltaSec=").append(format(dataset.getAlignedApogeeTimeDeltaSec()))
					.append(",boostScore=").append(format(boostScoreValue))
					.append(",coastScore=").append(format(coastScoreValue))
					.append(",descentScore=").append(format(descent == null ? Double.NaN : descent.getScore()))
					.append(",ascentScore=").append(format(ascentScore))
					.append(",ascentLaneStatus=").append(ascentLane.status())
					.append(",ascentGateFailures=").append(ascentLane.reasons())
					.append(",reliabilityLaneStatus=").append(reliabilityLane.status())
					.append(",reliabilityFailureReason=").append(reliabilityLane.reasons())
					.append(",coverage=").append(format(coverage(full)))
					.append(",topContributors=").append(topContributors)
					.append(",romImprovementHints=").append(improvementHints)
					.append(",failureReason=").append(failureReason)
					.append(",insufficientDataReason=").append(full == null ? "" : safe(full.getInsufficientDataReason()))
					.append(System.lineSeparator());
		}
		Files.writeString(path, log.toString(), StandardCharsets.UTF_8);
	}

	private static ResidualHeadline weakestResidual(Map<FlightPhaseWindow, PhaseResidualMetrics> phaseResiduals) {
		String bestPhase = "";
		String bestChannel = "";
		double bestNrmse = Double.NaN;
		for (Map.Entry<FlightPhaseWindow, PhaseResidualMetrics> phaseEntry : phaseResiduals.entrySet()) {
			PhaseResidualMetrics metrics = phaseEntry.getValue();
			if (metrics == null) {
				continue;
			}
			for (Map.Entry<String, ResidualMetrics> channelEntry : metrics.getChannelResiduals().entrySet()) {
				ResidualMetrics residual = channelEntry.getValue();
				if (residual == null || !Double.isFinite(residual.getNrmsePercent())) {
					continue;
				}
				if (!Double.isFinite(bestNrmse) || residual.getNrmsePercent() > bestNrmse) {
					bestPhase = phaseEntry.getKey().name();
					bestChannel = channelEntry.getKey();
					bestNrmse = residual.getNrmsePercent();
				}
			}
		}
		return new ResidualHeadline(bestPhase, bestChannel, bestNrmse);
	}

	private static DragHeadline strongestDragResidual(Map<FlightPhaseWindow, PhaseResidualMetrics> phaseResiduals) {
		String bestPhase = "";
		double bestDeltaPercent = Double.NaN;
		double bestMagnitude = Double.NaN;
		for (Map.Entry<FlightPhaseWindow, PhaseResidualMetrics> phaseEntry : phaseResiduals.entrySet()) {
			PhaseResidualMetrics metrics = phaseEntry.getValue();
			if (metrics == null || metrics.getDragResidual() == null) {
				continue;
			}
			DragResidualMetrics drag = metrics.getDragResidual();
			double magnitude = Double.isFinite(drag.getDeltaPercent())
					? Math.abs(drag.getDeltaPercent())
					: Math.abs(drag.getDelta());
			if (!Double.isFinite(magnitude)) {
				continue;
			}
			if (!Double.isFinite(bestMagnitude) || magnitude > bestMagnitude) {
				bestPhase = phaseEntry.getKey().name();
				bestDeltaPercent = drag.getDeltaPercent();
				bestMagnitude = magnitude;
			}
		}
		return new DragHeadline(bestPhase, bestDeltaPercent);
	}

	private static String buildMotionResidualSummary(String phase, String channel, ResidualMetrics residual) {
		return "phase=" + phase
				+ "|channel=" + channel
				+ "|type=motion"
				+ "|signedMeanError=" + format(residual.getSignedMeanError())
				+ "|mae=" + format(residual.getMae())
				+ "|rmse=" + format(residual.getRmse())
				+ "|nrmse=" + format(residual.getNrmsePercent());
	}

	private static String buildDragResidualSummary(String phase, DragResidualMetrics drag) {
		return "phase=" + phase
				+ "|channel=cdProxy"
				+ "|type=dragProxy"
				+ "|truthMean=" + format(drag.getTruthMean())
				+ "|candidateMean=" + format(drag.getCandidateMean())
				+ "|delta=" + format(drag.getDelta())
				+ "|percentDelta=" + format(drag.getDeltaPercent());
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

	private static Object firstNonBlank(Object... values) {
		for (Object value : values) {
			if (value == null) {
				continue;
			}
			if (value instanceof String string) {
				if (!string.isBlank()) {
					return string;
				}
				continue;
			}
			return value;
		}
		return null;
	}

	private static String stringValue(Object value) {
		if (value == null) {
			return "";
		}
		if (value instanceof String string) {
			return string;
		}
		if (value instanceof Number number) {
			return format(number.doubleValue());
		}
		if (value instanceof Boolean bool) {
			return bool.toString();
		}
		if (value instanceof Enum<?> enumeration) {
			return enumeration.name();
		}
		return value.toString();
	}

	private static String formatValue(Object value) {
		if (value == null) {
			return "";
		}
		if (value instanceof Number number) {
			return format(number.doubleValue());
		}
		if (value instanceof String string) {
			if (string.isBlank()) {
				return "";
			}
			try {
				return format(Double.parseDouble(string.trim()));
			} catch (NumberFormatException ignored) {
				return string;
			}
		}
		if (value instanceof Boolean bool) {
			return bool.toString();
		}
		return value.toString();
	}

	private static Object datasetProperty(Object target, String getterName, String fieldName) {
		if (target == null) {
			return null;
		}
		Object viaMethod = invokeNoArg(target, getterName);
		if (viaMethod != null) {
			return viaMethod;
		}
		viaMethod = invokeNoArg(target, fieldName);
		if (viaMethod != null) {
			return viaMethod;
		}
		if (fieldName != null) {
			try {
				Field field = target.getClass().getDeclaredField(fieldName);
				field.setAccessible(true);
				return field.get(target);
			} catch (ReflectiveOperationException ignored) {
				// Best-effort reflection only.
			}
		}
		return null;
	}

	private static Object invokeNoArg(Object target, String name) {
		if (target == null || name == null || name.isBlank()) {
			return null;
		}
		try {
			Method method = target.getClass().getMethod(name);
			if (method.getParameterCount() == 0) {
				return method.invoke(target);
			}
		} catch (NoSuchMethodException ignored) {
			// Try the next lookup strategy.
		} catch (IllegalAccessException | InvocationTargetException ignored) {
			// Best-effort reflection only.
		}
		return null;
	}

	private static List<Map<String, Object>> collectResidualRows(Object source, String phaseHint) {
		List<Map<String, Object>> rows = new ArrayList<>();
		collectResidualRows(source, phaseHint, rows);
		return rows;
	}

	private static void collectResidualRows(Object source, String phaseHint, List<Map<String, Object>> rows) {
		if (source == null) {
			return;
		}
		if (source instanceof Map<?, ?> map) {
			if (looksLikeResidualLeaf(map)) {
				rows.add(extractResidualRow(map, phaseHint));
				return;
			}
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				String nextPhase = phaseHint;
				if (entry.getKey() != null && nextPhase == null) {
					nextPhase = entry.getKey().toString();
				}
				collectResidualRows(entry.getValue(), nextPhase, rows);
			}
			return;
		}
		if (source instanceof Iterable<?> iterable) {
			for (Object item : iterable) {
				collectResidualRows(item, phaseHint, rows);
			}
			return;
		}
		if (source.getClass().isArray()) {
			int length = Array.getLength(source);
			for (int i = 0; i < length; i++) {
				collectResidualRows(Array.get(source, i), phaseHint, rows);
			}
			return;
		}

		Map<String, Object> row = extractResidualRow(source, phaseHint);
		if (!row.isEmpty()) {
			rows.add(row);
		}
	}

	private static boolean looksLikeResidualLeaf(Map<?, ?> map) {
		for (Object key : map.keySet()) {
			if (key == null) {
				continue;
			}
			String name = key.toString();
			if (name.equals("phase")
					|| name.equals("window")
					|| name.equals("phaseName")
					|| name.equals("label")
					|| name.equals("residualType")
					|| name.equals("rowType")
					|| name.equals("type")
					|| name.equals("channel")
					|| name.equals("metric")
					|| name.equals("quantity")
					|| name.equals("signedMeanError")
					|| name.equals("meanError")
					|| name.equals("bias")
					|| name.equals("mae")
					|| name.equals("meanAbsoluteError")
					|| name.equals("rmse")
					|| name.equals("rootMeanSquareError")
					|| name.equals("nrmse")
					|| name.equals("normalizedRmse")
					|| name.equals("truthMean")
					|| name.equals("referenceMean")
					|| name.equals("candidateMean")
					|| name.equals("simulationMean")
					|| name.equals("delta")
					|| name.equals("difference")
					|| name.equals("percentDelta")
					|| name.equals("percentDifference")
					|| name.equals("integratorDriftBefore")
					|| name.equals("pressureAnchorCorrectionMagnitude")
					|| name.equals("apogeeTimingErrorBefore")
					|| name.equals("apogeeTimingErrorAfter")
					|| name.equals("altitudeResidualBefore")
					|| name.equals("altitudeResidualAfter")) {
				return true;
			}
		}
		return false;
	}

	private static Map<String, Object> extractResidualRow(Object source, String phaseHint) {
		Map<String, Object> row = new LinkedHashMap<>();
		if (phaseHint != null && !phaseHint.isBlank()) {
			row.put("phase", phaseHint);
		}
		if (source instanceof Map<?, ?> map) {
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				if (entry.getKey() != null) {
					row.put(entry.getKey().toString(), entry.getValue());
				}
			}
			return row;
		}

		row.put("phase", firstNonBlank(row.get("phase"), datasetProperty(source, "getPhase", "phase")));
		row.put("window", datasetProperty(source, "getWindow", "window"));
		row.put("phaseName", datasetProperty(source, "getPhaseName", "phaseName"));
		row.put("label", datasetProperty(source, "getLabel", "label"));
		row.put("residualType", firstNonBlank(
				datasetProperty(source, "getResidualType", "residualType"),
				datasetProperty(source, "getRowType", "rowType"),
				datasetProperty(source, "getType", "type"),
				datasetProperty(source, "getName", "name")));
		row.put("channel", firstNonBlank(
				datasetProperty(source, "getChannel", "channel"),
				datasetProperty(source, "getMetric", "metric"),
				datasetProperty(source, "getQuantity", "quantity")));
		row.put("truthSource", datasetProperty(source, "getTruthSource", "truthSource"));
		row.put("alignmentChannel", datasetProperty(source, "getAlignmentChannel", "alignmentChannel"));
		row.put("alignmentLagSec", datasetProperty(source, "getAlignmentLagSec", "alignmentLagSec"));
		row.put("alignmentQuality", datasetProperty(source, "getAlignmentQuality", "alignmentQuality"));
		row.put("signedMeanError", firstNonBlank(
				datasetProperty(source, "getSignedMeanError", "signedMeanError"),
				datasetProperty(source, "getMeanError", "meanError"),
				datasetProperty(source, "getBias", "bias")));
		row.put("mae", firstNonBlank(
				datasetProperty(source, "getMae", "mae"),
				datasetProperty(source, "getMeanAbsoluteError", "meanAbsoluteError")));
		row.put("rmse", firstNonBlank(
				datasetProperty(source, "getRmse", "rmse"),
				datasetProperty(source, "getRootMeanSquareError", "rootMeanSquareError")));
		row.put("nrmse", firstNonBlank(
				datasetProperty(source, "getNrmse", "nrmse"),
				datasetProperty(source, "getNormalizedRmse", "normalizedRmse")));
		row.put("truthMean", firstNonBlank(
				datasetProperty(source, "getTruthMean", "truthMean"),
				datasetProperty(source, "getReferenceMean", "referenceMean")));
		row.put("candidateMean", firstNonBlank(
				datasetProperty(source, "getCandidateMean", "candidateMean"),
				datasetProperty(source, "getSimulationMean", "simulationMean")));
		row.put("delta", firstNonBlank(
				datasetProperty(source, "getDelta", "delta"),
				datasetProperty(source, "getDifference", "difference")));
		row.put("percentDelta", firstNonBlank(
				datasetProperty(source, "getPercentDelta", "percentDelta"),
				datasetProperty(source, "getPercentDifference", "percentDifference")));
		row.put("integratorDriftBefore", datasetProperty(source, "getIntegratorDriftBefore", "integratorDriftBefore"));
		row.put("pressureAnchorCorrectionMagnitude", datasetProperty(source, "getPressureAnchorCorrectionMagnitude", "pressureAnchorCorrectionMagnitude"));
		row.put("apogeeTimingErrorBefore", datasetProperty(source, "getApogeeTimingErrorBefore", "apogeeTimingErrorBefore"));
		row.put("apogeeTimingErrorAfter", datasetProperty(source, "getApogeeTimingErrorAfter", "apogeeTimingErrorAfter"));
		row.put("altitudeResidualBefore", datasetProperty(source, "getAltitudeResidualBefore", "altitudeResidualBefore"));
		row.put("altitudeResidualAfter", datasetProperty(source, "getAltitudeResidualAfter", "altitudeResidualAfter"));
		return row;
	}

	private static String renderResidualSummary(Map<String, Object> row, Object source) {
		String phase = stringValue(firstNonBlank(row.get("phase"), row.get("window"), row.get("phaseName")));
		String channel = stringValue(firstNonBlank(row.get("channel"), row.get("metric"), row.get("quantity")));
		String residualType = stringValue(firstNonBlank(row.get("residualType"), row.get("rowType"), row.get("type"), row.get("name")));
		String stats = "signedMeanError=" + formatValue(firstNonBlank(row.get("signedMeanError"), row.get("meanError"), row.get("bias")))
				+ ",mae=" + formatValue(firstNonBlank(row.get("mae"), row.get("meanAbsoluteError")))
				+ ",rmse=" + formatValue(firstNonBlank(row.get("rmse"), row.get("rootMeanSquareError")))
				+ ",nrmse=" + formatValue(firstNonBlank(row.get("nrmse"), row.get("normalizedRmse")));
		String diagnostics = renderObject(source);
		StringBuilder summary = new StringBuilder();
		if (!phase.isBlank()) {
			summary.append("phase=").append(phase);
		}
		if (!channel.isBlank()) {
			if (summary.length() > 0) {
				summary.append('|');
			}
			summary.append("channel=").append(channel);
		}
		if (!residualType.isBlank()) {
			if (summary.length() > 0) {
				summary.append('|');
			}
			summary.append("type=").append(residualType);
		}
		if (!stats.isBlank()) {
			if (summary.length() > 0) {
				summary.append('|');
			}
			summary.append(stats);
		}
		if (!diagnostics.isBlank()) {
			if (summary.length() > 0) {
				summary.append('|');
			}
			summary.append("details=").append(diagnostics);
		}
		return summary.toString();
	}

	private static String renderObject(Object value) {
		if (value == null) {
			return "";
		}
		if (value instanceof String string) {
			return string;
		}
		if (value instanceof Number number) {
			return format(number.doubleValue());
		}
		if (value instanceof Boolean bool) {
			return bool.toString();
		}
		if (value instanceof Enum<?> enumeration) {
			return enumeration.name();
		}
		if (value instanceof Map<?, ?> map) {
			StringJoiner joiner = new StringJoiner("|");
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				if (entry.getKey() == null) {
					continue;
				}
				joiner.add(entry.getKey().toString() + "=" + renderObject(entry.getValue()));
			}
			return joiner.toString();
		}
		if (value instanceof Iterable<?> iterable) {
			StringJoiner joiner = new StringJoiner("|");
			for (Object item : iterable) {
				joiner.add(renderObject(item));
			}
			return joiner.toString();
		}
		if (value.getClass().isArray()) {
			StringJoiner joiner = new StringJoiner("|");
			int length = Array.getLength(value);
			for (int i = 0; i < length; i++) {
				joiner.add(renderObject(Array.get(value, i)));
			}
			return joiner.toString();
		}

		StringJoiner joiner = new StringJoiner("|");
		for (String methodName : List.of("getPhase", "getWindow", "getChannel", "getMetric", "getQuantity",
				"getResidualType", "getRowType", "getType", "getName", "getSignedMeanError", "getMeanError",
				"getBias", "getMae", "getMeanAbsoluteError", "getRmse", "getRootMeanSquareError", "getNrmse",
				"getNormalizedRmse", "getTruthMean", "getReferenceMean", "getCandidateMean", "getSimulationMean",
				"getDelta", "getDifference", "getPercentDelta", "getPercentDifference", "getIntegratorDriftBefore",
				"getPressureAnchorCorrectionMagnitude", "getApogeeTimingErrorBefore", "getApogeeTimingErrorAfter",
				"getAltitudeResidualBefore", "getAltitudeResidualAfter", "getRawDriftMeanAbsMeters",
				"getPressureAnchorCorrectionMeanAbsMeters", "getApogeeTimeErrorBeforeSec", "getApogeeTimeErrorAfterSec",
				"getAltitudeRmseBeforeMeters", "getAltitudeRmseAfterMeters")) {
			Object property = invokeNoArg(value, methodName);
			if (property != null) {
				joiner.add(simpleName(methodName) + "=" + stringValue(property));
			}
		}
		if (joiner.length() > 0) {
			return joiner.toString();
		}
		return value.toString();
	}

	private static String simpleName(String getterName) {
		if (getterName == null || getterName.length() <= 3) {
			return safe(getterName);
		}
		String name = getterName.substring(3);
		if (name.isEmpty()) {
			return safe(getterName);
		}
		return Character.toLowerCase(name.charAt(0)) + name.substring(1);
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

	private static Map.Entry<String, Double> weakestChannel(PhaseTwoScoreResult score) {
		if (score == null || score.getChannelScores().isEmpty()) {
			return null;
		}
		return score.getChannelScores().entrySet().stream()
				.min(Comparator.comparingDouble(Map.Entry::getValue))
				.orElse(null);
	}

	private static Map.Entry<String, Double> strongestChannel(PhaseTwoScoreResult score) {
		if (score == null || score.getChannelScores().isEmpty()) {
			return null;
		}
		return score.getChannelScores().entrySet().stream()
				.max(Comparator.comparingDouble(Map.Entry::getValue))
				.orElse(null);
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

	private static double computeAscentScore(double boostScore, double coastScore) {
		double weightedSum = 0.0;
		double totalWeight = 0.0;
		if (Double.isFinite(boostScore)) {
			weightedSum += boostScore;
			totalWeight += 1.0;
		}
		if (Double.isFinite(coastScore)) {
			weightedSum += coastScore;
			totalWeight += 1.0;
		}
		if (totalWeight <= 0.0) {
			return Double.NaN;
		}
		return weightedSum / totalWeight;
	}

	private static LaneEvaluation evaluateAscentLane(PhaseTwoDatasetResult dataset,
																  double boostScore,
																  double coastScore,
																  double apogeePercentError) {
		if ("BROKEN".equalsIgnoreCase(safe(dataset.getDatasetClass()))) {
			return new LaneEvaluation("EXCLUDED", "datasetClass=BROKEN");
		}

		List<String> gateFailures = new ArrayList<>();
		if (!Double.isFinite(boostScore)) {
			gateFailures.add("boostScore=NA");
		} else if (boostScore < ASCENT_BOOST_GATE_SCORE) {
			gateFailures.add("boostScore<" + format(ASCENT_BOOST_GATE_SCORE) + "(" + format(boostScore) + ")");
		}
		if (!Double.isFinite(coastScore)) {
			gateFailures.add("coastScore=NA");
		} else if (coastScore < ASCENT_COAST_GATE_SCORE) {
			gateFailures.add("coastScore<" + format(ASCENT_COAST_GATE_SCORE) + "(" + format(coastScore) + ")");
		}

		double apogeeTimeErrorSec = apogeeTimeErrorSec(dataset);
		if (!Double.isFinite(apogeeTimeErrorSec)) {
			gateFailures.add("alignedApogeeTimeErrorSec=NA");
		} else if (apogeeTimeErrorSec > ASCENT_APOGEE_TIME_GATE_SEC) {
			gateFailures.add("alignedApogeeTimeErrorSec>" + format(ASCENT_APOGEE_TIME_GATE_SEC)
					+ "(" + format(apogeeTimeErrorSec) + ")");
		}

		if (!Double.isFinite(apogeePercentError)) {
			gateFailures.add("apogeeErrorPercent=NA");
		} else if (apogeePercentError > ASCENT_APOGEE_PERCENT_GATE) {
			gateFailures.add("apogeeErrorPercent>" + format(ASCENT_APOGEE_PERCENT_GATE)
					+ "(" + format(apogeePercentError) + ")");
		}

		return laneEvaluation(gateFailures);
	}

	private static LaneEvaluation evaluateReliabilityLane(PhaseTwoDatasetResult dataset) {
		List<String> failures = new ArrayList<>();
		if ("BROKEN".equalsIgnoreCase(safe(dataset.getDatasetClass()))) {
			failures.add("datasetClass=BROKEN");
		}

		AbPluginExecutionResult plugin = dataset.getPluginResult();
		if (plugin == null) {
			failures.add("plugin=MISSING");
		} else if (plugin.getStatus() == AbPluginExecutionResult.Status.FAILED
				|| plugin.getStatus() == AbPluginExecutionResult.Status.TIMED_OUT) {
			failures.add("plugin=" + plugin.getStatus());
		}

		String candidateSource = safe(dataset.getCandidateSource()).toUpperCase(Locale.ROOT);
		if (candidateSource.contains("FALLBACK")) {
			failures.add("candidateSource=" + candidateSource);
		}

		PhaseTwoScoreResult full = dataset.getWindowScores().get(FlightPhaseWindow.FULL);
		if (full == null) {
			failures.add("fullScore=NA");
		} else {
			if (full.getSeverity() == ScoreSeverity.CRITICAL) {
				failures.add("fullSeverity=CRITICAL");
			}
			if (full.getInsufficientDataReason() != null && !full.getInsufficientDataReason().isBlank()) {
				failures.add("insufficientData=" + compactReason(full.getInsufficientDataReason()));
			}
		}

		return laneEvaluation(failures);
	}

	private static LaneEvaluation laneEvaluation(List<String> failures) {
		if (failures.isEmpty()) {
			return new LaneEvaluation("PASS", "");
		}
		StringJoiner joiner = new StringJoiner("|");
		for (String failure : failures) {
			if (failure == null || failure.isBlank()) {
				continue;
			}
			joiner.add(compactReason(failure));
		}
		String reason = joiner.toString();
		if (reason.isBlank()) {
			return new LaneEvaluation("PASS", "");
		}
		return new LaneEvaluation("FAIL", reason);
	}

	private static String compactReason(String value) {
		if (value == null) {
			return "";
		}
		return value.replace(',', ';')
				.replace('\n', ' ')
				.replace('\r', ' ')
				.trim();
	}

	private static double apogeeTimeErrorSec(PhaseTwoDatasetResult dataset) {
		double explicitError = dataset.getAlignedApogeeTimeErrorSec();
		if (Double.isFinite(explicitError)) {
			return Math.abs(explicitError);
		}
		double delta = dataset.getAlignedApogeeTimeDeltaSec();
		if (Double.isFinite(delta)) {
			return Math.abs(delta);
		}
		return Double.NaN;
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

	private record ResidualHeadline(String phase, String channel, double nrmse) {
	}

	private record DragHeadline(String phase, double deltaPercent) {
	}

	private record LaneEvaluation(String status, String reasons) {
	}
}

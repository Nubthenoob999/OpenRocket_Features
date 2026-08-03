package info.openrocket.core.tuning;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PhaseTwoBatchRunner {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final double CROSS_SENSOR_CD_PROXY_RATIO_MAX = 3.0;
	private static final double CROSS_SENSOR_ALIGNMENT_LAG_SPREAD_MAX_SEC = 0.35;
	private static final double APOGEE_TIME_ERROR_FUSION_BLEND = 0.60;
	private static final Pattern LAUNCH_KEY_PATTERN = Pattern.compile("(jackpot_launch_\\d+|government_work_launch_\\d+|govenmnet_work_launch_\\d+)", Pattern.CASE_INSENSITIVE);

	private PhaseTwoBatchRunner() {
	}

	public static PhaseTwoBatchResult runFromConfig(Path configPath, Path outputDir) throws Exception {
		configPath = configPath.toAbsolutePath().normalize();
		outputDir = outputDir.toAbsolutePath().normalize();
		Path configDir = configPath.getParent() == null ? Path.of(".") : configPath.getParent();
		PhaseTwoRunConfig config = loadConfig(configPath);
		DeduplicationResult deduplication = deduplicateDatasets(config.getDatasets(), configDir);
		Files.createDirectories(outputDir);

		List<PhaseTwoDatasetResult> results = new ArrayList<>();
		for (PhaseTwoDatasetConfig dataset : deduplication.getDatasets()) {
			try {
				results.add(runDataset(config, dataset, configDir, outputDir));
			} catch (Throwable ex) {
				results.add(failedDatasetResult(dataset, ex));
			}
		}
		results = applyCrossSensorCdProxyWarnings(results);

		PhaseTwoBatchResult batch = new PhaseTwoBatchResult(results);
		PhaseTwoBatchReportWriter.write(batch, outputDir);
		writeRunMetadata(outputDir.resolve("phase-two-run-metadata.log"), configPath, config, deduplication);
		return batch;
	}

	private static DeduplicationResult deduplicateDatasets(List<PhaseTwoDatasetConfig> datasets, Path configDir) {
		List<PhaseTwoDatasetConfig> unique = new ArrayList<>();
		List<String> warnings = new ArrayList<>();
		Set<String> seenNames = new HashSet<>();
		Set<String> seenSignatures = new HashSet<>();
		int skippedCount = 0;
		int orkDatasetCount = 0;

		for (PhaseTwoDatasetConfig dataset : datasets) {
			String datasetName = dataset.getName() == null ? "" : dataset.getName().trim();
			String datasetNameKey = datasetName.toLowerCase();
			if (!datasetNameKey.isBlank() && !seenNames.add(datasetNameKey)) {
				skippedCount++;
				warnings.add("duplicate-dataset-name-skipped=" + datasetName);
				continue;
			}

			String signature = datasetSignature(configDir, dataset);
			if (!seenSignatures.add(signature)) {
				skippedCount++;
				warnings.add("duplicate-dataset-signature-skipped=" + safeDatasetName(dataset) + " (" + signature + ")");
				continue;
			}

			if (dataset.getOrkPath() != null && !dataset.getOrkPath().isBlank()) {
				orkDatasetCount++;
			}
			unique.add(dataset);
		}

		if (orkDatasetCount == 0) {
			warnings.add("openrocket-comparison-missing=no datasets use orkPath; add OpenRocket-vs-log datasets to validate pressure/temperature/density");
		}

		return new DeduplicationResult(unique, warnings, skippedCount, orkDatasetCount);
	}

	private static String datasetSignature(Path configDir, PhaseTwoDatasetConfig dataset) {
		String truthOrReference = dataset.getTruthCsv() != null && !dataset.getTruthCsv().isBlank()
				? dataset.getTruthCsv()
				: dataset.getReferenceCsv();
		String referencePath = canonicalPath(configDir, truthOrReference, "reference-missing");
		String candidatePath;
		if (dataset.getCandidateCsv() != null && !dataset.getCandidateCsv().isBlank()) {
			candidatePath = "candidateCsv:" + canonicalPath(configDir, dataset.getCandidateCsv(), "candidate-csv-missing");
		} else if (dataset.getOrkPath() != null && !dataset.getOrkPath().isBlank()) {
			candidatePath = "orkPath:" + canonicalPath(configDir, dataset.getOrkPath(), "ork-missing");
		} else {
			candidatePath = "candidate-missing";
		}
		return referencePath + "|" + candidatePath + "|airbrake=" + dataset.isAirbrakeEnabled();
	}

	private static String canonicalPath(Path configDir, String rawValue, String defaultValue) {
		if (rawValue == null || rawValue.isBlank()) {
			return defaultValue;
		}
		return resolvePath(configDir, rawValue)
				.toAbsolutePath()
				.normalize()
				.toString()
				.toLowerCase();
	}

	private static String safeDatasetName(PhaseTwoDatasetConfig dataset) {
		if (dataset == null || dataset.getName() == null || dataset.getName().isBlank()) {
			return "unnamed-dataset";
		}
		return dataset.getName();
	}

	private static void writeRunMetadata(Path metadataPath,
										 Path configPath,
										 PhaseTwoRunConfig config,
										 DeduplicationResult deduplication) throws IOException {
		StringBuilder metadata = new StringBuilder();
		metadata.append("timestamp=").append(Instant.now()).append(System.lineSeparator());
		metadata.append("config=").append(configPath).append(System.lineSeparator());
		metadata.append("sampleRateHz=").append(config.getSampleRateHz()).append(System.lineSeparator());
		metadata.append("telemetryInterpolationMode=").append(config.getTelemetryInterpolationMode()).append(System.lineSeparator());
		metadata.append("interpolationMode=").append(config.getInterpolationMode()).append(System.lineSeparator());
		metadata.append("datasetsConfigured=").append(config.getDatasets().size()).append(System.lineSeparator());
		metadata.append("datasetsExecuted=").append(deduplication.getDatasets().size()).append(System.lineSeparator());
		metadata.append("datasetsSkippedAsRedundant=").append(deduplication.getSkippedCount()).append(System.lineSeparator());
		metadata.append("openRocketDatasets=").append(deduplication.getOrkDatasetCount()).append(System.lineSeparator());

		for (int i = 0; i < deduplication.getWarnings().size(); i++) {
			metadata.append("warning.")
					.append(i + 1)
					.append('=')
					.append(deduplication.getWarnings().get(i))
					.append(System.lineSeparator());
		}

		Files.writeString(metadataPath, metadata.toString(), StandardCharsets.UTF_8);
	}

	private static PhaseTwoDatasetResult failedDatasetResult(PhaseTwoDatasetConfig dataset, Throwable ex) {
		Map<FlightPhaseWindow, PhaseTwoScoreResult> emptyScores = new EnumMap<>(FlightPhaseWindow.class);
		DerivedTelemetryQuantities.Quantities nanQuantities = new DerivedTelemetryQuantities.Quantities(
				Double.NaN,
				Double.NaN,
				Double.NaN,
				Double.NaN,
				Double.NaN);
		AbPluginExecutionResult pluginResult = new AbPluginExecutionResult(
				AbPluginExecutionResult.Status.FAILED,
				-1,
				ex.getClass().getSimpleName() + ": " + ex.getMessage());
		return new PhaseTwoDatasetResult(
				dataset.getName(),
				dataset.isAirbrakeEnabled(),
				pluginResult,
				emptyScores,
				List.of(),
				nanQuantities,
				nanQuantities,
				TelemetryParserDiagnostics.EMPTY,
				TelemetryParserDiagnostics.EMPTY,
				"BROKEN",
				"",
				"",
				"",
				"",
				"",
				Double.NaN,
				"",
				Double.NaN,
				Double.NaN,
				Double.NaN,
				Double.NaN,
				Double.NaN,
				Double.NaN,
				Map.of(),
				VerticalIntegratorDiagnostics.EMPTY);
	}

	private static PhaseTwoDatasetResult runDataset(PhaseTwoRunConfig config,
													 PhaseTwoDatasetConfig dataset,
													 Path configDir,
													 Path outputDir) throws Exception {
		List<TuningFlag> flags = new ArrayList<>();
		TruthLoadResult truthLoad = loadTruth(configDir, dataset);
		CandidateLoadResult candidateLoad = loadCandidate(configDir, dataset);
		AbPluginExecutionResult pluginResult = candidateLoad.getPluginResult();
		if (candidateLoad.getFallbackReason() != null) {
			flags.add(new TuningFlag("candidate-source", "DATA_QUALITY:ORK_FALLBACK_TO_CSV", ScoreSeverity.WARNING, 0.0));
		}

		TelemetrySeries reference = truthLoad.getSeries();
		TelemetrySeries candidateRaw = candidateLoad.getSeries();
		DerivedTelemetryQuantities.Quantities referenceQ = DerivedTelemetryQuantities.summarize(reference);
		DerivedTelemetryQuantities.Quantities rawCandidateQ = DerivedTelemetryQuantities.summarize(candidateRaw);
		String datasetClass = classifyDataset(configDir, dataset, candidateLoad, reference, pluginResult);
		String pluginFailureReason = pluginPipelineFailureReason(dataset, pluginResult);
		if (pluginFailureReason != null) {
			flags.add(new TuningFlag("candidate-source", "DATA_QUALITY:PLUGIN_PIPELINE_UNHEALTHY", ScoreSeverity.CRITICAL, 0.0));
			Map<FlightPhaseWindow, PhaseTwoScoreResult> excludedScores = dataQualityExcludedScores(pluginFailureReason);
			return new PhaseTwoDatasetResult(
					dataset.getName(),
					dataset.isAirbrakeEnabled(),
					pluginResult,
					excludedScores,
					flags,
					referenceQ,
					rawCandidateQ,
					reference.getParserDiagnostics(),
					candidateRaw.getParserDiagnostics(),
					datasetClass,
					truthLoad.getTruthSource(),
					candidateLoad.getCandidateSource(),
					candidateLoad.getOrkProvenance(),
					candidateLoad.getPhysicsAeroMode(),
					candidateLoad.getTableSource(),
					candidateLoad.getMaxMach(),
					"",
					Double.NaN,
					Double.NaN,
					Double.NaN,
					Double.NaN,
					Double.NaN,
					Double.NaN,
					Map.of(),
					candidateLoad.getIntegratorDiagnostics());
		}
		String tableRuntimeFailureReason = tableRuntimeFailureReason(dataset, candidateLoad);
		if (tableRuntimeFailureReason != null) {
			flags.add(new TuningFlag("physicsAeroMode", "DATA_QUALITY:TABLE_RUNTIME_INACTIVE", ScoreSeverity.CRITICAL, 0.0));
			Map<FlightPhaseWindow, PhaseTwoScoreResult> excludedScores = dataQualityExcludedScores(tableRuntimeFailureReason);
			return new PhaseTwoDatasetResult(
					dataset.getName(),
					dataset.isAirbrakeEnabled(),
					pluginResult,
					excludedScores,
					flags,
					referenceQ,
					rawCandidateQ,
					reference.getParserDiagnostics(),
					candidateRaw.getParserDiagnostics(),
					datasetClass,
					truthLoad.getTruthSource(),
					candidateLoad.getCandidateSource(),
					candidateLoad.getOrkProvenance(),
					candidateLoad.getPhysicsAeroMode(),
					candidateLoad.getTableSource(),
					candidateLoad.getMaxMach(),
					"",
					Double.NaN,
					Double.NaN,
					Double.NaN,
					Double.NaN,
					Double.NaN,
					Double.NaN,
					Map.of(),
					candidateLoad.getIntegratorDiagnostics());
		}
		if (isAutoDisabledPlugin(pluginResult)) {
			flags.add(new TuningFlag("candidate-source", "DATA_QUALITY:PLUGIN_AUTO_DISABLED", ScoreSeverity.WARNING, 0.0));
		}
		String qualityFailureReason = PhaseThreeAnalysisSupport.evaluateDataQuality(reference, candidateRaw, referenceQ, rawCandidateQ, flags);

		TelemetrySeries candidate = qualityFailureReason == null
				? PhaseThreeAnalysisSupport.applyTemperatureModel(reference, candidateRaw, flags)
				: candidateRaw;
		DerivedTelemetryQuantities.Quantities candidateQ = DerivedTelemetryQuantities.summarize(candidate);
		AlignmentResult alignment = TimeSeriesAligner.estimateAlignment(reference, candidate, config.getSampleRateHz());
		double referenceAlignedApogeeTimeSec = PhaseThreeAnalysisSupport.alignedApogeeTime(reference, 0.0);
		double candidateAlignedApogeeTimeSec = PhaseThreeAnalysisSupport.alignedApogeeTime(candidate, alignment.getLagSec());
		double alignedApogeeTimeDeltaSec = PhaseThreeAnalysisSupport.alignedApogeeTimeDelta(referenceAlignedApogeeTimeSec, candidateAlignedApogeeTimeSec);
		VerticalIntegratorDiagnostics integratorDiagnostics = PhaseThreeAnalysisSupport.enrichIntegratorDiagnostics(
				candidateLoad.getIntegratorDiagnostics(),
				reference,
				candidateLoad.getRawSeries(),
				candidate,
				config.getSampleRateHz(),
				config.getInterpolationMode());
		double alignedApogeeTimeErrorSec = fusedApogeeTimeErrorSec(alignedApogeeTimeDeltaSec, integratorDiagnostics);

		if (qualityFailureReason != null) {
			Map<FlightPhaseWindow, PhaseTwoScoreResult> excludedScores = dataQualityExcludedScores(qualityFailureReason);
			return new PhaseTwoDatasetResult(
					dataset.getName(),
					dataset.isAirbrakeEnabled(),
					pluginResult,
					excludedScores,
					flags,
					referenceQ,
					candidateQ,
					reference.getParserDiagnostics(),
					candidate.getParserDiagnostics(),
					datasetClass,
					truthLoad.getTruthSource(),
					candidateLoad.getCandidateSource(),
					candidateLoad.getOrkProvenance(),
					candidateLoad.getPhysicsAeroMode(),
					candidateLoad.getTableSource(),
					candidateLoad.getMaxMach(),
					alignment.getChannel(),
					alignment.getLagSec(),
					alignment.getQuality(),
					referenceAlignedApogeeTimeSec,
					candidateAlignedApogeeTimeSec,
					alignedApogeeTimeDeltaSec,
					alignedApogeeTimeErrorSec,
					Map.of(),
					integratorDiagnostics);
		}

		InterpolationMode mode = config.getInterpolationMode();
		Map<FlightPhaseWindow, PhaseTwoScoreResult> scores = new EnumMap<>(FlightPhaseWindow.class);
		PhaseTwoScoringConfig scoringConfig = PhaseTwoScoringConfig.defaults();
		Map<FlightPhaseWindow, double[]> windows = PhaseThreeAnalysisSupport.estimateWindows(reference);

		TelemetryComparisonResult full = TelemetryComparator.compare(reference, candidate, config.getSampleRateHz(), mode, alignment);
		PhaseTwoScoreResult fullScoreRaw = PhaseTwoScoreCalculator.score(full, scoringConfig);
		for (Map.Entry<FlightPhaseWindow, double[]> entry : windows.entrySet()) {
			TelemetryComparisonResult perWindow = TelemetryComparator.compare(
					reference,
					candidate,
					config.getSampleRateHz(),
					mode,
					alignment,
					entry.getValue()[0],
					entry.getValue()[1]);
			scores.put(entry.getKey(), PhaseTwoScoreCalculator.score(perWindow, scoringConfig));
		}
		PhaseTwoScoreResult fullScore = PhaseThreeAnalysisSupport.aggregateFullScore(fullScoreRaw, scores, scoringConfig, reference, candidate);
		scores.put(FlightPhaseWindow.FULL, fullScore);

		Map<FlightPhaseWindow, PhaseResidualMetrics> residuals = TelemetryResidualAnalyzer.analyzeByPhase(
				reference,
				candidate,
				config.getSampleRateHz(),
				mode,
				alignment,
				windows);

		flags.addAll(EquationTuningRuleEngine.buildFlags(fullScore, scoringConfig));
		PhaseThreeAnalysisSupport.appendApogeeFlag(referenceQ, candidateQ, flags);
		return new PhaseTwoDatasetResult(
				dataset.getName(),
				dataset.isAirbrakeEnabled(),
				pluginResult,
				scores,
				flags,
				referenceQ,
				candidateQ,
				reference.getParserDiagnostics(),
				candidate.getParserDiagnostics(),
				datasetClass,
				truthLoad.getTruthSource(),
				candidateLoad.getCandidateSource(),
				candidateLoad.getOrkProvenance(),
				candidateLoad.getPhysicsAeroMode(),
				candidateLoad.getTableSource(),
				candidateLoad.getMaxMach(),
				alignment.getChannel(),
				alignment.getLagSec(),
				alignment.getQuality(),
				referenceAlignedApogeeTimeSec,
				candidateAlignedApogeeTimeSec,
				alignedApogeeTimeDeltaSec,
				alignedApogeeTimeErrorSec,
				residuals,
				integratorDiagnostics);
	}

	private static Map<FlightPhaseWindow, PhaseTwoScoreResult> dataQualityExcludedScores(String reason) {
		Map<FlightPhaseWindow, PhaseTwoScoreResult> scores = new EnumMap<>(FlightPhaseWindow.class);
		scores.put(FlightPhaseWindow.FULL, new PhaseTwoScoreResult(
				Double.NaN,
				ScoreSeverity.WARNING,
				Map.of(),
				Double.NaN,
				0,
				0,
				"DATA_QUALITY_EXCLUDED",
				reason));
		return scores;
	}

	private static List<PhaseTwoDatasetResult> applyCrossSensorCdProxyWarnings(List<PhaseTwoDatasetResult> datasets) {
		Map<String, List<Double>> cdProxyByLaunch = new HashMap<>();
		Map<String, List<Double>> lagByLaunch = new HashMap<>();
		for (PhaseTwoDatasetResult dataset : datasets) {
			String launchKey = launchKey(dataset.getDatasetName());
			if (launchKey == null) {
				continue;
			}
			cdProxyByLaunch.computeIfAbsent(launchKey, key -> new ArrayList<>());
			lagByLaunch.computeIfAbsent(launchKey, key -> new ArrayList<>());
			collectFinite(cdProxyByLaunch.get(launchKey), dataset.getReferenceQuantities().getCdProxyMean());
			collectFiniteAny(lagByLaunch.get(launchKey), dataset.getAlignmentLagSec());
		}

		Map<String, Double> ratioByLaunch = new HashMap<>();
		for (Map.Entry<String, List<Double>> entry : cdProxyByLaunch.entrySet()) {
			double ratio = robustCdProxySpreadRatio(entry.getValue());
			if (ratio > CROSS_SENSOR_CD_PROXY_RATIO_MAX) {
				ratioByLaunch.put(entry.getKey(), ratio);
			}
		}

		Map<String, Double> lagSpreadByLaunch = new HashMap<>();
		for (Map.Entry<String, List<Double>> entry : lagByLaunch.entrySet()) {
			double spread = finiteSpread(entry.getValue());
			if (spread > CROSS_SENSOR_ALIGNMENT_LAG_SPREAD_MAX_SEC) {
				lagSpreadByLaunch.put(entry.getKey(), spread);
			}
		}

		if (ratioByLaunch.isEmpty() && lagSpreadByLaunch.isEmpty()) {
			return datasets;
		}

		List<PhaseTwoDatasetResult> updated = new ArrayList<>(datasets.size());
		for (PhaseTwoDatasetResult dataset : datasets) {
			String launchKey = launchKey(dataset.getDatasetName());
			Double ratio = launchKey == null ? null : ratioByLaunch.get(launchKey);
			Double lagSpread = launchKey == null ? null : lagSpreadByLaunch.get(launchKey);
			if (ratio == null && lagSpread == null) {
				updated.add(dataset);
				continue;
			}

			List<TuningFlag> flags = new ArrayList<>(dataset.getTuningFlags());
			if (ratio != null) {
				flags.add(new TuningFlag("cdProxy", "data-quality.cross-sensor-consistency", ScoreSeverity.WARNING, ratio));
			}
			if (lagSpread != null) {
				flags.add(new TuningFlag("alignmentLagSec", "data-quality.cross-sensor-consistency", ScoreSeverity.WARNING, lagSpread));
			}
			updated.add(new PhaseTwoDatasetResult(
					dataset.getDatasetName(),
					dataset.isAirbrakeEnabled(),
					dataset.getPluginResult(),
					dataset.getWindowScores(),
					flags,
					dataset.getReferenceQuantities(),
					dataset.getCandidateQuantities(),
					dataset.getReferenceParserDiagnostics(),
					dataset.getCandidateParserDiagnostics(),
					dataset.getDatasetClass(),
					dataset.getTruthSource(),
					dataset.getCandidateSource(),
					dataset.getOrkProvenance(),
					dataset.getPhysicsAeroMode(),
					dataset.getTableSource(),
					dataset.getCandidateMaxMach(),
					dataset.getAlignmentChannel(),
					dataset.getAlignmentLagSec(),
					dataset.getAlignmentQuality(),
					dataset.getReferenceAlignedApogeeTimeSec(),
					dataset.getCandidateAlignedApogeeTimeSec(),
					dataset.getAlignedApogeeTimeDeltaSec(),
					dataset.getAlignedApogeeTimeErrorSec(),
					dataset.getPhaseResiduals(),
					dataset.getIntegratorDiagnostics()));
		}
		return updated;
	}

	private static String launchKey(String datasetName) {
		Matcher matcher = LAUNCH_KEY_PATTERN.matcher(datasetName);
		if (!matcher.find()) {
			return null;
		}
		return matcher.group(1).toLowerCase();
	}

	private static void collectFinite(List<Double> target, double value) {
		if (Double.isFinite(value) && value > 0.0) {
			target.add(value);
		}
	}

	private static void collectFiniteAny(List<Double> target, double value) {
		if (Double.isFinite(value)) {
			target.add(value);
		}
	}

	private static double robustCdProxySpreadRatio(List<Double> values) {
		if (values == null || values.size() < 2) {
			return Double.NaN;
		}
		List<Double> sorted = values.stream().sorted(Comparator.naturalOrder()).toList();
		double lower;
		double upper;
		if (sorted.size() >= 4) {
			int quartile = sorted.size() / 4;
			lower = sorted.get(quartile);
			upper = sorted.get(sorted.size() - 1 - quartile);
		} else {
			lower = sorted.get(0);
			upper = sorted.get(sorted.size() - 1);
		}
		if (lower <= 0.0) {
			return Double.NaN;
		}
		return upper / lower;
	}

	private static double finiteSpread(List<Double> values) {
		if (values == null || values.size() < 2) {
			return Double.NaN;
		}
		double min = Double.POSITIVE_INFINITY;
		double max = Double.NEGATIVE_INFINITY;
		for (Double value : values) {
			if (value == null || !Double.isFinite(value)) {
				continue;
			}
			if (value < min) {
				min = value;
			}
			if (value > max) {
				max = value;
			}
		}
		if (!Double.isFinite(min) || !Double.isFinite(max)) {
			return Double.NaN;
		}
		return max - min;
	}

	private static TruthLoadResult loadTruth(Path configDir, PhaseTwoDatasetConfig dataset) throws Exception {
		Path truthPath;
		TelemetrySeries truthSeries;
		if (dataset.getTruthCsv() != null && !dataset.getTruthCsv().isBlank()) {
			truthPath = resolvePath(configDir, dataset.getTruthCsv());
			truthSeries = TelemetryParsers.parse(truthPath);
		} else if (dataset.getOrkPath() != null && !dataset.getOrkPath().isBlank()) {
			Path datasetDir = inferDatasetDirectory(configDir, dataset);
			TelemetryTruthSelector.TruthSelection truth = TelemetryTruthSelector.select(datasetDir);
			truthPath = truth.path();
			truthSeries = truth.series();
		} else {
			truthPath = resolvePath(configDir, dataset.getReferenceCsv());
			truthSeries = TelemetryParsers.parse(truthPath);
		}
		return new TruthLoadResult(truthPath, truthSeries);
	}

	private static Path inferDatasetDirectory(Path configDir, PhaseTwoDatasetConfig dataset) {
		for (String value : datasetPathCandidates(dataset)) {
			if (value == null || value.isBlank()) {
				continue;
			}
			Path path = resolvePath(configDir, value);
			Path parent = path.getParent();
			if (parent != null && Files.isDirectory(parent)) {
				return parent;
			}
		}
		return configDir.toAbsolutePath().normalize();
	}

	private static List<String> datasetPathCandidates(PhaseTwoDatasetConfig dataset) {
		List<String> values = new ArrayList<>(4);
		values.add(dataset.getTruthCsv());
		values.add(dataset.getReferenceCsv());
		values.add(dataset.getOrkPath());
		values.add(dataset.getCandidateCsv());
		return values;
	}

	private static CandidateLoadResult loadCandidate(Path configDir, PhaseTwoDatasetConfig dataset) throws Exception {
		boolean hasOrk = dataset.getOrkPath() != null && !dataset.getOrkPath().isBlank();
		boolean hasCandidateCsv = dataset.getCandidateCsv() != null && !dataset.getCandidateCsv().isBlank();
		Path resolvedOrkPath = hasOrk ? resolvePath(configDir, dataset.getOrkPath()) : null;

		if (hasOrk) {
			AtomicReference<AbPluginExecutionResult> airbrakesResultRef = new AtomicReference<>(
					new AbPluginExecutionResult(AbPluginExecutionResult.Status.SKIPPED, 0,
							"Native airbrakes not configured"));
			try {
				HeadlessOrkSimulationRunner.OrkSimulationResult result =
						HeadlessOrkSimulationRunner.runFirstSimulationDetailed(
								resolvedOrkPath.toFile(),
								simulation -> {
									try {
										airbrakesResultRef.set(NativeAirbrakesConfigurer.configure(
												dataset,
												configDir,
												simulation.getOptions()));
									} catch (Exception ex) {
										simulation.getOptions().setAirbrakesEnabled(false);
										airbrakesResultRef.set(new AbPluginExecutionResult(
												AbPluginExecutionResult.Status.SKIPPED,
												0,
												"Native airbrakes auto-disabled: " + ex.getClass().getSimpleName()
														+ ": " + safeExceptionMessage(ex)));
									}
								});
				return new CandidateLoadResult(
						result.getSeries(),
						result.getRawSeries(),
						result.getIntegratorDiagnostics(),
						airbrakesResultRef.get(),
						null,
						"ORK_SIMULATION",
						resolvedOrkPath.toAbsolutePath().normalize().toString(),
						result.getPhysicsAeroMode(),
						result.getTableSource(),
						result.getMaxMach());
			} catch (Exception ex) {
				if (!hasCandidateCsv) {
					throw ex;
				}
				TelemetrySeries fallback = TelemetryParsers.parse(resolvePath(configDir, dataset.getCandidateCsv()));
				AbPluginExecutionResult airbrakesResult = fallbackPluginResult(airbrakesResultRef.get(), ex);
				return new CandidateLoadResult(
						fallback,
						fallback,
						VerticalIntegratorDiagnostics.EMPTY,
						airbrakesResult,
						"ORK simulation failed and candidateCsv fallback was used: " + ex.getClass().getSimpleName()
								+ ": " + safeExceptionMessage(ex),
						"ORK_FALLBACK_TO_CSV",
						resolvedOrkPath == null ? "" : resolvedOrkPath.toAbsolutePath().normalize().toString(),
						"",
						"",
						Double.NaN);
			}
		}

		if (hasCandidateCsv) {
			TelemetrySeries candidate = TelemetryParsers.parse(resolvePath(configDir, dataset.getCandidateCsv()));
			return new CandidateLoadResult(
					candidate,
					candidate,
					VerticalIntegratorDiagnostics.EMPTY,
					new AbPluginExecutionResult(AbPluginExecutionResult.Status.SKIPPED, 0,
							"Candidate CSV comparison only"),
					null,
					"CANDIDATE_CSV",
					"",
					"",
					"",
					Double.NaN);
		}

		throw new IllegalArgumentException("Dataset must provide candidateCsv or orkPath: " + dataset.getName());
	}

	private static AbPluginExecutionResult fallbackPluginResult(AbPluginExecutionResult current, Exception ex) {
		if (current != null
				&& current.getStatus() == AbPluginExecutionResult.Status.SKIPPED
				&& current.getMessage() != null
				&& current.getMessage().startsWith("Native airbrakes auto-disabled")) {
			return current;
		}
		if (current != null
				&& (current.getStatus() == AbPluginExecutionResult.Status.FAILED
				|| current.getStatus() == AbPluginExecutionResult.Status.TIMED_OUT)) {
			return current;
		}
		return new AbPluginExecutionResult(
				AbPluginExecutionResult.Status.FAILED,
				-1,
				"Native airbrakes run failed: " + ex.getClass().getSimpleName() + ": " + safeExceptionMessage(ex));
	}

	private static String pluginPipelineFailureReason(PhaseTwoDatasetConfig dataset,
												  AbPluginExecutionResult pluginResult) {
		boolean hasOrk = dataset.getOrkPath() != null && !dataset.getOrkPath().isBlank();
		if (!hasOrk) {
			return null;
		}
		if (pluginResult == null) {
			return "plugin-pipeline-unhealthy: plugin result missing";
		}
		if (pluginResult.getStatus() == AbPluginExecutionResult.Status.FAILED
				|| pluginResult.getStatus() == AbPluginExecutionResult.Status.TIMED_OUT) {
			String details = safe(pluginResult.getMessage());
			if (details.isBlank()) {
				return "plugin-pipeline-unhealthy: status=" + pluginResult.getStatus();
			}
			return "plugin-pipeline-unhealthy: status=" + pluginResult.getStatus() + "; " + compactReason(details);
		}
		return null;
	}

	private static String tableRuntimeFailureReason(PhaseTwoDatasetConfig dataset,
												 CandidateLoadResult candidateLoad) {
		boolean hasOrk = dataset.getOrkPath() != null && !dataset.getOrkPath().isBlank();
		if (!hasOrk || candidateLoad == null || !"ORK_SIMULATION".equals(candidateLoad.getCandidateSource())) {
			return null;
		}
		String mode = safe(candidateLoad.getPhysicsAeroMode());
		String surface = safe(candidateLoad.getTableSource());
		if (mode.startsWith("PHYSICS_AERO_") && surface.equals("TABLE_CACHE_IDENTITY")) {
			return null;
		}
		return "table-runtime-inactive: physicsAeroMode=" + mode + "; tableSource=" + surface;
	}

	private static boolean isAutoDisabledPlugin(AbPluginExecutionResult pluginResult) {
		if (pluginResult == null || pluginResult.getStatus() != AbPluginExecutionResult.Status.SKIPPED) {
			return false;
		}
		String message = pluginResult.getMessage();
		return message != null && message.startsWith("Native airbrakes auto-disabled");
	}

	private static double fusedApogeeTimeErrorSec(double alignedApogeeTimeDeltaSec,
												VerticalIntegratorDiagnostics diagnostics) {
		double alignedError = Double.isFinite(alignedApogeeTimeDeltaSec) ? Math.abs(alignedApogeeTimeDeltaSec) : Double.NaN;
		double reconstructedError = diagnostics == null
				? Double.NaN
				: absoluteFinite(diagnostics.getApogeeTimeErrorAfterSec());
		if (Double.isFinite(alignedError) && Double.isFinite(reconstructedError)) {
			double blended = APOGEE_TIME_ERROR_FUSION_BLEND * alignedError
					+ (1.0 - APOGEE_TIME_ERROR_FUSION_BLEND) * reconstructedError;
			return Math.min(alignedError, blended);
		}
		if (Double.isFinite(alignedError)) {
			return alignedError;
		}
		return reconstructedError;
	}

	private static double absoluteFinite(double value) {
		return Double.isFinite(value) ? Math.abs(value) : Double.NaN;
	}

	private static String safeExceptionMessage(Exception ex) {
		if (ex == null) {
			return "unknown";
		}
		String message = ex.getMessage();
		if (message == null || message.isBlank()) {
			return "no detail";
		}
		return compactReason(message);
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

	private static String safe(String value) {
		return value == null ? "" : value;
	}

	private static String classifyDataset(Path configDir,
										  PhaseTwoDatasetConfig dataset,
										  CandidateLoadResult candidateLoad,
										  TelemetrySeries reference,
										  AbPluginExecutionResult pluginResult) {
		boolean hasOrk = dataset.getOrkPath() != null && !dataset.getOrkPath().isBlank();
		if (hasOrk) {
			if (candidateLoad.getFallbackReason() != null) {
				return "BROKEN";
			}
			if (pluginResult == null
					|| pluginResult.getStatus() == AbPluginExecutionResult.Status.FAILED
					|| pluginResult.getStatus() == AbPluginExecutionResult.Status.TIMED_OUT) {
				return "BROKEN";
			}
			if (!"ORK_SIMULATION".equals(candidateLoad.getCandidateSource())) {
				return "BROKEN";
			}
			if (reference.getSchema() == TelemetrySchema.AB_IMU_INTERLEAVED) {
				return "BROKEN";
			}
			return "TABLE_USABLE";
		}

		if (sameTelemetryFile(configDir, dataset.getReferenceCsv(), dataset.getCandidateCsv())) {
			return "PARSER_ONLY";
		}
		return "CROSS_SENSOR";
	}

	private static boolean sameTelemetryFile(Path configDir, String referenceCsv, String candidateCsv) {
		if (referenceCsv == null || referenceCsv.isBlank() || candidateCsv == null || candidateCsv.isBlank()) {
			return false;
		}
		Path referencePath = resolvePath(configDir, referenceCsv).toAbsolutePath().normalize();
		Path candidatePath = resolvePath(configDir, candidateCsv).toAbsolutePath().normalize();
		return referencePath.equals(candidatePath);
	}

	private static PhaseTwoRunConfig loadConfig(Path configPath) throws IOException {
		String json = Files.readString(configPath, StandardCharsets.UTF_8);
		JsonObject root = GSON.fromJson(json, JsonObject.class);
		if (root == null) {
			throw new IllegalArgumentException("Invalid config JSON: " + configPath);
		}

		if (root.has("datasets") && root.get("datasets").isJsonArray()) {
			PhaseTwoRunConfig single = GSON.fromJson(root, PhaseTwoRunConfig.class);
			if (single == null || single.getDatasets() == null || single.getDatasets().isEmpty()) {
				throw new IllegalArgumentException("No datasets defined in config: " + configPath);
			}
			return single;
		}

		List<PhaseTwoDatasetConfig> mergedDatasets = new ArrayList<>();
		JsonObject firstConfigObject = null;
		for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
			if (!entry.getValue().isJsonObject()) {
				continue;
			}
			JsonObject child = entry.getValue().getAsJsonObject();
			PhaseTwoRunConfig childConfig = GSON.fromJson(child, PhaseTwoRunConfig.class);
			if (childConfig == null || childConfig.getDatasets() == null || childConfig.getDatasets().isEmpty()) {
				continue;
			}
			if (firstConfigObject == null) {
				firstConfigObject = child;
			}
			mergedDatasets.addAll(childConfig.getDatasets());
		}

		if (mergedDatasets.isEmpty()) {
			throw new IllegalArgumentException("No datasets defined in config: " + configPath);
		}

		JsonObject normalized = new JsonObject();
		if (firstConfigObject != null) {
			copyIfPresent(firstConfigObject, normalized, "sampleRateHz");
			copyIfPresent(firstConfigObject, normalized, "telemetryInterpolationMode");
			copyIfPresent(firstConfigObject, normalized, "interpolationMode");
		}
		normalized.add("datasets", GSON.toJsonTree(mergedDatasets));

		PhaseTwoRunConfig config = GSON.fromJson(normalized, PhaseTwoRunConfig.class);
		if (config == null || config.getDatasets() == null || config.getDatasets().isEmpty()) {
			throw new IllegalArgumentException("No datasets defined in config: " + configPath);
		}
		return config;
	}

	private static void copyIfPresent(JsonObject source, JsonObject target, String key) {
		if (source.has(key) && !source.get(key).isJsonNull()) {
			target.add(key, source.get(key));
		}
	}

	private static Path resolvePath(Path baseDir, String value) {
		String normalized = normalizeWindowsDrivePath(value);
		Path path = Path.of(normalized);
		if (path.isAbsolute()) {
			return path;
		}

		Path anchor = baseDir == null ? Path.of(".") : baseDir.toAbsolutePath().normalize();
		Path cursor = anchor;
		while (cursor != null) {
			Path candidate = cursor.resolve(path).normalize();
			if (Files.exists(candidate)) {
				return candidate;
			}
			cursor = cursor.getParent();
		}

		return anchor.resolve(path).normalize();
	}

	private static String normalizeWindowsDrivePath(String value) {
		if (value == null) {
			return "";
		}
		if (value.matches("^[A-Za-z]:[^\\\\/].*")) {
			return value.substring(0, 2) + "/" + value.substring(2);
		}
		return value;
	}

	public static void main(String[] args) throws Exception {
		if (args.length < 2) {
			throw new IllegalArgumentException("Usage: PhaseTwoBatchRunner <config.json> <outputDir>");
		}
		Path config = Path.of(args[0]);
		Path output = Path.of(args[1]);
		runFromConfig(config, output);
	}

	private static final class DeduplicationResult {
		private final List<PhaseTwoDatasetConfig> datasets;
		private final List<String> warnings;
		private final int skippedCount;
		private final int orkDatasetCount;

		private DeduplicationResult(List<PhaseTwoDatasetConfig> datasets,
									  List<String> warnings,
									  int skippedCount,
									  int orkDatasetCount) {
			this.datasets = datasets;
			this.warnings = warnings;
			this.skippedCount = skippedCount;
			this.orkDatasetCount = orkDatasetCount;
		}

		private List<PhaseTwoDatasetConfig> getDatasets() {
			return datasets;
		}

		private List<String> getWarnings() {
			return warnings;
		}

		private int getSkippedCount() {
			return skippedCount;
		}

		private int getOrkDatasetCount() {
			return orkDatasetCount;
		}
	}

	private static final class TruthLoadResult {
		private final Path truthPath;
		private final TelemetrySeries series;

		private TruthLoadResult(Path truthPath, TelemetrySeries series) {
			this.truthPath = truthPath;
			this.series = series;
		}

		private TelemetrySeries getSeries() {
			return series;
		}

		private String getTruthSource() {
			return truthPath == null ? "" : truthPath.toAbsolutePath().normalize().toString();
		}
	}

	private static final class CandidateLoadResult {
		private final TelemetrySeries series;
		private final TelemetrySeries rawSeries;
		private final VerticalIntegratorDiagnostics integratorDiagnostics;
		private final AbPluginExecutionResult pluginResult;
		private final String fallbackReason;
		private final String candidateSource;
		private final String orkProvenance;
		private final String physicsAeroMode;
		private final String tableSource;
		private final double maxMach;

		private CandidateLoadResult(TelemetrySeries series,
									TelemetrySeries rawSeries,
									VerticalIntegratorDiagnostics integratorDiagnostics,
									AbPluginExecutionResult pluginResult,
									String fallbackReason,
									String candidateSource,
									String orkProvenance,
									String physicsAeroMode,
									String tableSource,
									double maxMach) {
			this.series = series;
			this.rawSeries = rawSeries == null ? series : rawSeries;
			this.integratorDiagnostics = integratorDiagnostics == null ? VerticalIntegratorDiagnostics.EMPTY : integratorDiagnostics;
			this.pluginResult = pluginResult == null
					? new AbPluginExecutionResult(AbPluginExecutionResult.Status.SKIPPED, 0, "Native airbrakes not configured")
					: pluginResult;
			this.fallbackReason = fallbackReason;
			this.candidateSource = candidateSource == null ? "" : candidateSource;
			this.orkProvenance = orkProvenance == null ? "" : orkProvenance;
			this.physicsAeroMode = physicsAeroMode == null ? "" : physicsAeroMode;
			this.tableSource = tableSource == null ? "" : tableSource;
			this.maxMach = maxMach;
		}

		private TelemetrySeries getSeries() {
			return series;
		}

		private TelemetrySeries getRawSeries() {
			return rawSeries;
		}

		private VerticalIntegratorDiagnostics getIntegratorDiagnostics() {
			return integratorDiagnostics;
		}

		private AbPluginExecutionResult getPluginResult() {
			return pluginResult;
		}

		private String getFallbackReason() {
			return fallbackReason;
		}

		private String getCandidateSource() {
			return candidateSource;
		}

		private String getOrkProvenance() {
			return orkProvenance;
		}

		private String getPhysicsAeroMode() {
			return physicsAeroMode;
		}

		private String getTableSource() {
			return tableSource;
		}

		private double getMaxMach() {
			return maxMach;
		}
	}
}

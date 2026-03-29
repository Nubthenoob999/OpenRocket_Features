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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PhaseTwoBatchRunner {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final double ISA_LAPSE_RATE_C_PER_M = 0.0065;
	private static final double MIN_LAPSE_FIT_ALT_M = 200.0;
	private static final double TEMPERATURE_APOGEE_MISMATCH_C = 10.0;
	private static final double SENTINEL_RATE_THRESHOLD = 0.30;
	private static final double CD_PROXY_MIN = 0.01;
	private static final double CD_PROXY_MAX = 2.5;
	private static final double CROSS_SENSOR_CD_PROXY_RATIO_MAX = 3.0;
	private static final double APOGEE_WARNING_ERROR_METERS = 60.0;
	private static final double APOGEE_CRITICAL_ERROR_METERS = 120.0;
	private static final double APOGEE_WARNING_ERROR_PERCENT = 5.0;
	private static final double APOGEE_CRITICAL_ERROR_PERCENT = 10.0;
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
		String referencePath = canonicalPath(configDir, dataset.getReferenceCsv(), "reference-missing");
		String candidatePath;
		if (dataset.getCandidateCsv() != null && !dataset.getCandidateCsv().isBlank()) {
			candidatePath = "candidateCsv:" + canonicalPath(configDir, dataset.getCandidateCsv(), "candidate-csv-missing");
		} else if (dataset.getOrkPath() != null && !dataset.getOrkPath().isBlank()) {
			candidatePath = "orkPath:" + canonicalPath(configDir, dataset.getOrkPath(), "ork-missing");
		} else {
			candidatePath = "candidate-missing";
		}
		return referencePath
				+ "|"
				+ candidatePath
				+ "|airbrake="
				+ dataset.isAirbrakeEnabled();
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
				TelemetryParserDiagnostics.EMPTY);
	}

	private static PhaseTwoDatasetResult runDataset(PhaseTwoRunConfig config,
												PhaseTwoDatasetConfig dataset,
												Path configDir,
												Path outputDir) throws Exception {
		AbPluginExecutionResult pluginResult = AirBrakesJarExecutor.execute(
				dataset,
				config.getPluginJarPath(),
				configDir,
				outputDir,
				config.getPluginTimeoutSeconds());

		List<TuningFlag> flags = new ArrayList<>();
		TelemetrySeries reference = TelemetryParsers.parse(resolvePath(configDir, dataset.getReferenceCsv()));
		CandidateLoadResult candidateLoad = loadCandidate(configDir, dataset);
		TelemetrySeries candidate = candidateLoad.getSeries();
		if (candidateLoad.getFallbackReason() != null) {
			flags.add(new TuningFlag("candidate-source", "DATA_QUALITY:ORK_FALLBACK_TO_CSV", ScoreSeverity.WARNING, 0.0));
		}
		DerivedTelemetryQuantities.Quantities referenceQ = DerivedTelemetryQuantities.summarize(reference);
		DerivedTelemetryQuantities.Quantities candidateQ = DerivedTelemetryQuantities.summarize(candidate);
		String qualityFailureReason = evaluateDataQuality(reference, candidate, referenceQ, candidateQ, flags);
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
					candidate.getParserDiagnostics());
		}

		candidate = applyTemperatureModel(reference, candidate, flags);

		InterpolationMode mode = config.getInterpolationMode();
		Map<FlightPhaseWindow, PhaseTwoScoreResult> scores = new EnumMap<>(FlightPhaseWindow.class);
		PhaseTwoScoringConfig scoringConfig = PhaseTwoScoringConfig.defaults();

		TelemetryComparisonResult full = TelemetryComparator.compare(reference, candidate, config.getSampleRateHz(), mode);
		PhaseTwoScoreResult fullScoreRaw = PhaseTwoScoreCalculator.score(full, scoringConfig);

		Map<FlightPhaseWindow, double[]> windows = estimateWindows(reference);
		for (Map.Entry<FlightPhaseWindow, double[]> entry : windows.entrySet()) {
			TelemetryComparisonResult perWindow = TelemetryComparator.compare(
					reference,
					candidate,
					config.getSampleRateHz(),
					mode,
					entry.getValue()[0],
					entry.getValue()[1]);
			scores.put(entry.getKey(), PhaseTwoScoreCalculator.score(perWindow, scoringConfig));
		}
		PhaseTwoScoreResult fullScore = aggregateFullScore(dataset, fullScoreRaw, scores, scoringConfig, reference, candidate);
		scores.put(FlightPhaseWindow.FULL, fullScore);

		flags.addAll(EquationTuningRuleEngine.buildFlags(fullScore, scoringConfig));
		appendApogeeFlag(referenceQ, candidateQ, flags);
		return new PhaseTwoDatasetResult(
				dataset.getName(),
				dataset.isAirbrakeEnabled(),
				pluginResult,
				scores,
				flags,
				referenceQ,
				candidateQ,
				reference.getParserDiagnostics(),
				candidate.getParserDiagnostics());
	}

	private static void appendApogeeFlag(DerivedTelemetryQuantities.Quantities referenceQ,
									 DerivedTelemetryQuantities.Quantities candidateQ,
									 List<TuningFlag> flags) {
		double referenceApogeeMeters = referenceQ.getApogeeAltitudeMeters();
		double candidateApogeeMeters = candidateQ.getApogeeAltitudeMeters();
		if (!Double.isFinite(referenceApogeeMeters) || !Double.isFinite(candidateApogeeMeters)
				|| referenceApogeeMeters <= 0.0) {
			return;
		}

		double absoluteErrorMeters = Math.abs(candidateApogeeMeters - referenceApogeeMeters);
		double absoluteErrorPercent = 100.0 * absoluteErrorMeters / referenceApogeeMeters;

		ScoreSeverity severity = ScoreSeverity.OK;
		if (absoluteErrorMeters >= APOGEE_CRITICAL_ERROR_METERS
				|| absoluteErrorPercent >= APOGEE_CRITICAL_ERROR_PERCENT) {
			severity = ScoreSeverity.CRITICAL;
		} else if (absoluteErrorMeters >= APOGEE_WARNING_ERROR_METERS
				|| absoluteErrorPercent >= APOGEE_WARNING_ERROR_PERCENT) {
			severity = ScoreSeverity.WARNING;
		}

		if (severity == ScoreSeverity.OK) {
			return;
		}

		double score = Math.max(0.0, 100.0 - absoluteErrorPercent);
		flags.add(new TuningFlag("apogee", "rom.integrator.vertical-kinematics", severity, score));
	}

	private static String evaluateDataQuality(TelemetrySeries reference,
									 TelemetrySeries candidate,
									 DerivedTelemetryQuantities.Quantities referenceQ,
									 DerivedTelemetryQuantities.Quantities candidateQ,
									 List<TuningFlag> flags) {
		List<String> reasons = new ArrayList<>();

		appendSentinelRateFlag("reference", reference.getParserDiagnostics(), flags, reasons);
		appendSentinelRateFlag("candidate", candidate.getParserDiagnostics(), flags, reasons);
		appendCdProxyRangeFlag("reference", referenceQ.getCdProxyMean(), flags, reasons);
		appendCdProxyRangeFlag("candidate", candidateQ.getCdProxyMean(), flags, reasons);

		if (reasons.isEmpty()) {
			return null;
		}
		return "candidate-data-quality: " + String.join("; ", reasons);
	}

	private static void appendSentinelRateFlag(String source,
									 TelemetryParserDiagnostics diagnostics,
									 List<TuningFlag> flags,
									 List<String> reasons) {
		double rate = diagnostics.getSentinelRate();
		if (rate <= SENTINEL_RATE_THRESHOLD) {
			return;
		}
		String detail = source + "-high-sentinel-rate=" + String.format("%.1f%%", rate * 100.0);
		reasons.add(detail);
		flags.add(new TuningFlag("data-quality", "DATA_QUALITY:HIGH_SENTINEL_RATE", ScoreSeverity.WARNING, 100.0 * rate));
	}

	private static void appendCdProxyRangeFlag(String source,
									double cdProxy,
									List<TuningFlag> flags,
									List<String> reasons) {
		if (!Double.isFinite(cdProxy) || (cdProxy >= CD_PROXY_MIN && cdProxy <= CD_PROXY_MAX)) {
			return;
		}
		String detail = source + "-cdProxy=" + String.format("%.3f", cdProxy);
		reasons.add(detail);
		flags.add(new TuningFlag("cdProxy", "PHYSICS:CD_PROXY_OUT_OF_RANGE", ScoreSeverity.WARNING, cdProxy));
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

	private static TelemetrySeries applyTemperatureModel(TelemetrySeries reference,
											 TelemetrySeries candidate,
											 List<TuningFlag> flags) {
		if (!reference.hasTemperature() || !candidate.hasTemperature()) {
			return candidate;
		}

		double referenceGroundTemp = firstFinite(reference.getTemperatureC());
		double candidateGroundTemp = firstFinite(candidate.getTemperatureC());
		if (!Double.isFinite(referenceGroundTemp) || !Double.isFinite(candidateGroundTemp)) {
			return candidate;
		}

		double[] lapseFit = fitTemperatureLapse(reference);
		List<Double> modeledTemps = new ArrayList<>(candidate.getTemperatureC());
		for (int i = 0; i < candidate.size(); i++) {
			Double altitude = candidate.getAltitudeMetersAgl().get(i);
			if (altitude == null || !Double.isFinite(altitude)) {
				continue;
			}
			double modeled;
			if (lapseFit != null) {
				modeled = lapseFit[0] + lapseFit[1] * altitude;
			} else {
				modeled = referenceGroundTemp - ISA_LAPSE_RATE_C_PER_M * Math.max(0.0, altitude);
			}
			modeledTemps.set(i, modeled);
		}
		TelemetrySeries corrected = copyWithTemperatures(candidate, modeledTemps);

		double apogeeReferenceTemp = apogeeTemperature(reference);
		double apogeeModeledTemp = apogeeTemperature(corrected);
		if (Double.isFinite(apogeeReferenceTemp) && Double.isFinite(apogeeModeledTemp)) {
			double delta = Math.abs(apogeeModeledTemp - apogeeReferenceTemp);
			if (delta > TEMPERATURE_APOGEE_MISMATCH_C) {
				flags.add(new TuningFlag("temperature", "TEMPERATURE_PROFILE_MISMATCH", ScoreSeverity.WARNING, delta));
				double offset = referenceGroundTemp - candidateGroundTemp;
				for (int i = 0; i < modeledTemps.size(); i++) {
					Double t = modeledTemps.get(i);
					if (t == null || !Double.isFinite(t)) {
						continue;
					}
					modeledTemps.set(i, t + offset);
				}
				corrected = copyWithTemperatures(candidate, modeledTemps);
			}
		}

		return corrected;
	}

	private static PhaseTwoScoreResult aggregateFullScore(PhaseTwoDatasetConfig dataset,
												 PhaseTwoScoreResult fullScoreRaw,
												 Map<FlightPhaseWindow, PhaseTwoScoreResult> windows,
												 PhaseTwoScoringConfig scoringConfig,
												 TelemetrySeries reference,
												 TelemetrySeries candidate) {
		double scoreSum = 0.0;
		double totalWeight = 0.0;
		int matched = 0;
		int timeline = 0;
		int missingPhases = 0;

		for (FlightPhaseWindow phase : List.of(FlightPhaseWindow.BOOST, FlightPhaseWindow.COAST, FlightPhaseWindow.DESCENT)) {
			PhaseTwoScoreResult phaseScore = windows.get(phase);
			if (phaseScore == null || phaseScore.getTimelineSampleCount() <= 0) {
				missingPhases++;
				continue;
			}
			double phaseWeight = 1.0;
			if (Double.isFinite(phaseScore.getScore())) {
				scoreSum += phaseScore.getScore() * phaseWeight;
				totalWeight += phaseWeight;
			}
			matched += phaseScore.getMatchedSampleCount();
			timeline += phaseScore.getTimelineSampleCount();
		}

		double aggregatedScore = totalWeight > 0.0 ? scoreSum / totalWeight : fullScoreRaw.getScore();
		String insufficientDataReason = fullScoreRaw.getInsufficientDataReason();
		if (missingPhases > 0 && (reference.getSchema() == TelemetrySchema.AB_IMU_INTERLEAVED
				|| candidate.getSchema() == TelemetrySchema.AB_IMU_INTERLEAVED)) {
			insufficientDataReason = "AB_IMU_INTERLEAVED source truncated at burnout; coast and descent phases unavailable - use AB_EXTENDED schema or substitute with EasyMini/Fluctus as reference for coast/descent analysis";
		}

		String failureReason = fullScoreRaw.getFailureReason();
		if (totalWeight <= 0.0) {
			failureReason = "INSUFFICIENT_DATA";
			if (insufficientDataReason == null || insufficientDataReason.isBlank()) {
				insufficientDataReason = "no-phase-coverage";
			}
		}

		return new PhaseTwoScoreResult(
				aggregatedScore,
				classifyScore(aggregatedScore, scoringConfig),
				fullScoreRaw.getChannelScores(),
				timeline <= 0 ? fullScoreRaw.getCoverageRatio() : (double) matched / (double) timeline,
				matched,
				timeline,
				failureReason,
				insufficientDataReason);
	}

	private static ScoreSeverity classifyScore(double score, PhaseTwoScoringConfig config) {
		if (!Double.isFinite(score)) {
			return ScoreSeverity.WARNING;
		}
		if (score < config.getCriticalBelowScore()) {
			return ScoreSeverity.CRITICAL;
		}
		if (score < config.getWarningBelowScore()) {
			return ScoreSeverity.WARNING;
		}
		return ScoreSeverity.OK;
	}

	private static TelemetrySeries copyWithTemperatures(TelemetrySeries original, List<Double> temperatures) {
		TelemetrySeries copy = new TelemetrySeries(original.getSchema());
		for (int i = 0; i < original.size(); i++) {
			copy.addPoint(
					original.getTimeSec().get(i),
					original.getAltitudeMetersAgl().get(i),
					original.getVelocityZMetersPerSec().get(i),
					original.getAccelerationXMetersPerSec2().get(i),
					original.getAccelerationYMetersPerSec2().get(i),
					original.getAccelerationZMetersPerSec2().get(i),
					original.getPressurePa().get(i),
					i < temperatures.size() ? temperatures.get(i) : original.getTemperatureC().get(i));
		}
		copy.setParserDiagnostics(original.getParserDiagnostics());
		return copy;
	}

	private static double[] fitTemperatureLapse(TelemetrySeries series) {
		double sumH = 0.0;
		double sumT = 0.0;
		double sumHH = 0.0;
		double sumHT = 0.0;
		int n = 0;
		for (int i = 0; i < series.size(); i++) {
			Double h = series.getAltitudeMetersAgl().get(i);
			Double t = series.getTemperatureC().get(i);
			if (h == null || t == null || !Double.isFinite(h) || !Double.isFinite(t) || h <= MIN_LAPSE_FIT_ALT_M) {
				continue;
			}
			sumH += h;
			sumT += t;
			sumHH += h * h;
			sumHT += h * t;
			n++;
		}
		if (n < 2) {
			return null;
		}
		double denom = n * sumHH - sumH * sumH;
		if (Math.abs(denom) < 1e-9) {
			return null;
		}
		double slope = (n * sumHT - sumH * sumT) / denom;
		double intercept = (sumT - slope * sumH) / n;
		return new double[]{intercept, slope};
	}

	private static double apogeeTemperature(TelemetrySeries series) {
		int bestIndex = -1;
		double maxAlt = Double.NEGATIVE_INFINITY;
		for (int i = 0; i < series.size(); i++) {
			Double altitude = series.getAltitudeMetersAgl().get(i);
			if (altitude == null || !Double.isFinite(altitude)) {
				continue;
			}
			if (altitude > maxAlt) {
				maxAlt = altitude;
				bestIndex = i;
			}
		}
		if (bestIndex < 0 || bestIndex >= series.getTemperatureC().size()) {
			return Double.NaN;
		}
		Double value = series.getTemperatureC().get(bestIndex);
		return value == null ? Double.NaN : value;
	}

	private static double firstFinite(List<Double> values) {
		for (Double value : values) {
			if (value != null && Double.isFinite(value)) {
				return value;
			}
		}
		return Double.NaN;
	}

	private static List<PhaseTwoDatasetResult> applyCrossSensorCdProxyWarnings(List<PhaseTwoDatasetResult> datasets) {
		Map<String, List<Double>> byLaunch = new HashMap<>();
		for (PhaseTwoDatasetResult dataset : datasets) {
			String launchKey = launchKey(dataset.getDatasetName());
			if (launchKey == null) {
				continue;
			}
			byLaunch.computeIfAbsent(launchKey, k -> new ArrayList<>());
			collectFinite(byLaunch.get(launchKey), dataset.getReferenceQuantities().getCdProxyMean());
			collectFinite(byLaunch.get(launchKey), dataset.getCandidateQuantities().getCdProxyMean());
		}

		Map<String, Double> ratioByLaunch = new HashMap<>();
		for (Map.Entry<String, List<Double>> entry : byLaunch.entrySet()) {
			List<Double> values = entry.getValue().stream().sorted(Comparator.naturalOrder()).toList();
			if (values.size() < 2) {
				continue;
			}
			double min = values.get(0);
			double max = values.get(values.size() - 1);
			if (min <= 0.0) {
				continue;
			}
			double ratio = max / min;
			if (ratio > CROSS_SENSOR_CD_PROXY_RATIO_MAX) {
				ratioByLaunch.put(entry.getKey(), ratio);
			}
		}

		if (ratioByLaunch.isEmpty()) {
			return datasets;
		}

		List<PhaseTwoDatasetResult> updated = new ArrayList<>(datasets.size());
		for (PhaseTwoDatasetResult dataset : datasets) {
			String launchKey = launchKey(dataset.getDatasetName());
			Double ratio = launchKey == null ? null : ratioByLaunch.get(launchKey);
			if (ratio == null) {
				updated.add(dataset);
				continue;
			}

			List<TuningFlag> flags = new ArrayList<>(dataset.getTuningFlags());
			flags.add(new TuningFlag("cdProxy", "data-quality.cross-sensor-consistency", ScoreSeverity.WARNING, ratio));
			updated.add(new PhaseTwoDatasetResult(
					dataset.getDatasetName(),
					dataset.isAirbrakeEnabled(),
					dataset.getPluginResult(),
					dataset.getWindowScores(),
					flags,
					dataset.getReferenceQuantities(),
					dataset.getCandidateQuantities(),
					dataset.getReferenceParserDiagnostics(),
					dataset.getCandidateParserDiagnostics()));
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

	private static CandidateLoadResult loadCandidate(Path configDir, PhaseTwoDatasetConfig dataset) throws Exception {
		boolean hasOrk = dataset.getOrkPath() != null && !dataset.getOrkPath().isBlank();
		boolean hasCandidateCsv = dataset.getCandidateCsv() != null && !dataset.getCandidateCsv().isBlank();

		if (hasOrk) {
			try {
				TelemetrySeries series = HeadlessOrkSimulationRunner.runFirstSimulation(resolvePath(configDir, dataset.getOrkPath()).toFile());
				return new CandidateLoadResult(series, null);
			} catch (Exception ex) {
				if (!hasCandidateCsv) {
					throw ex;
				}
				TelemetrySeries fallback = TelemetryParsers.parse(resolvePath(configDir, dataset.getCandidateCsv()));
				return new CandidateLoadResult(fallback,
						"ORK simulation failed and candidateCsv fallback was used: " + ex.getClass().getSimpleName());
			}
		}

		if (hasCandidateCsv) {
			return new CandidateLoadResult(TelemetryParsers.parse(resolvePath(configDir, dataset.getCandidateCsv())), null);
		}

		throw new IllegalArgumentException("Dataset must provide candidateCsv or orkPath: " + dataset.getName());
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

		// Aggregate format: { "groupA": { ...runConfig... }, "groupB": { ...runConfig... } }
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
			copyIfPresent(firstConfigObject, normalized, "interpolationMode");
			copyIfPresent(firstConfigObject, normalized, "pluginJarPath");
			copyIfPresent(firstConfigObject, normalized, "pluginTimeoutSeconds");
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

	private static Map<FlightPhaseWindow, double[]> estimateWindows(TelemetrySeries reference) {
		Map<FlightPhaseWindow, double[]> windows = new EnumMap<>(FlightPhaseWindow.class);
		List<Double> shiftedTime = shiftToT0(reference);
		List<Double> altitude = reference.getAltitudeMetersAgl();
		List<Double> velocity = reference.getVelocityZMetersPerSec();

		double apogeeTime = apogeeTime(shiftedTime, altitude);
		double boostEnd = boostEndTime(shiftedTime, velocity, apogeeTime);

		if (Double.isFinite(boostEnd) && boostEnd > 0.0) {
			windows.put(FlightPhaseWindow.BOOST, new double[]{0.0, boostEnd});
		}
		if (Double.isFinite(apogeeTime) && apogeeTime > boostEnd) {
			windows.put(FlightPhaseWindow.COAST, new double[]{Math.max(0.0, boostEnd), apogeeTime});
			windows.put(FlightPhaseWindow.DESCENT, new double[]{apogeeTime, Double.POSITIVE_INFINITY});
		}
		return windows;
	}

	private static List<Double> shiftToT0(TelemetrySeries series) {
		List<Double> shifted = new ArrayList<>(series.getTimeSec().size());
		int t0Index = series.estimateLaunchAnchorIndex();
		double t0 = (t0Index >= 0 && t0Index < series.getTimeSec().size() && series.getTimeSec().get(t0Index) != null)
				? series.getTimeSec().get(t0Index)
				: 0.0;
		for (Double t : series.getTimeSec()) {
			shifted.add(t == null ? null : t - t0);
		}
		return shifted;
	}

	private static double apogeeTime(List<Double> time, List<Double> altitude) {
		double maxAlt = Double.NEGATIVE_INFINITY;
		double maxTime = Double.NaN;
		for (int i = 0; i < time.size() && i < altitude.size(); i++) {
			Double t = time.get(i);
			Double a = altitude.get(i);
			if (t == null || a == null) {
				continue;
			}
			if (a > maxAlt) {
				maxAlt = a;
				maxTime = t;
			}
		}
		return maxTime;
	}

	private static double boostEndTime(List<Double> time, List<Double> velocityZ, double apogeeTime) {
		double maxV = Double.NEGATIVE_INFINITY;
		double maxVTime = Double.NaN;
		for (int i = 0; i < time.size() && i < velocityZ.size(); i++) {
			Double t = time.get(i);
			Double v = velocityZ.get(i);
			if (t == null || v == null || t < 0.0) {
				continue;
			}
			if (Double.isFinite(apogeeTime) && t > apogeeTime) {
				continue;
			}
			if (v > maxV) {
				maxV = v;
				maxVTime = t;
			}
		}
		return maxVTime;
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
		if (value.matches("^[A-Za-z]:[^\\/].*")) {
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

	private static final class CandidateLoadResult {
		private final TelemetrySeries series;
		private final String fallbackReason;

		private CandidateLoadResult(TelemetrySeries series, String fallbackReason) {
			this.series = series;
			this.fallbackReason = fallbackReason;
		}

		private TelemetrySeries getSeries() {
			return series;
		}

		private String getFallbackReason() {
			return fallbackReason;
		}
	}
}
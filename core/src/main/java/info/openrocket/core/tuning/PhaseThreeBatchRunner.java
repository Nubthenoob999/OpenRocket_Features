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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class PhaseThreeBatchRunner {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private PhaseThreeBatchRunner() {
	}

	public static PhaseTwoBatchResult runFromConfig(Path configPath, Path outputDir) throws Exception {
		Path normalizedConfigPath = configPath.toAbsolutePath().normalize();
		Path normalizedOutputDir = outputDir.toAbsolutePath().normalize();
		Path configDir = normalizedConfigPath.getParent() == null ? Path.of(".") : normalizedConfigPath.getParent();
		PhaseThreeBatchRunConfig config = loadConfig(normalizedConfigPath);
		Files.createDirectories(normalizedOutputDir);

		List<PhaseTwoDatasetResult> results = new ArrayList<>();
		for (PhaseThreeBatchDatasetConfig dataset : config.getDatasets()) {
			try {
				results.add(runDataset(config, dataset, configDir));
			} catch (Throwable ex) {
				results.add(failedDatasetResult(dataset, ex));
			}
		}

		PhaseTwoBatchResult batch = new PhaseTwoBatchResult(results);
		PhaseTwoBatchReportWriter.write(batch, normalizedOutputDir);
		writeRunMetadata(normalizedOutputDir.resolve("phase-two-run-metadata.log"), normalizedConfigPath, config);
		return batch;
	}

	private static PhaseTwoDatasetResult runDataset(PhaseThreeBatchRunConfig config,
													 PhaseThreeBatchDatasetConfig dataset,
													 Path configDir) throws Exception {
		List<TuningFlag> flags = new ArrayList<>();
		TruthLoadResult truthLoad = loadTruth(configDir, dataset);
		CandidateLoadResult candidateLoad = loadCandidate(configDir, dataset);
		TelemetrySeries reference = truthLoad.series();
		TelemetrySeries candidateRaw = candidateLoad.series();
		DerivedTelemetryQuantities.Quantities referenceQ = DerivedTelemetryQuantities.summarize(reference);
		DerivedTelemetryQuantities.Quantities rawCandidateQ = DerivedTelemetryQuantities.summarize(candidateRaw);
		String datasetClass = classifyDataset(configDir, dataset, candidateLoad, reference);

		String qualityFailureReason = PhaseThreeAnalysisSupport.evaluateDataQuality(
				reference, candidateRaw, referenceQ, rawCandidateQ, flags);
		TelemetrySeries candidate = qualityFailureReason == null
				? PhaseThreeAnalysisSupport.applyTemperatureModel(reference, candidateRaw, flags)
				: candidateRaw;
		DerivedTelemetryQuantities.Quantities candidateQ = DerivedTelemetryQuantities.summarize(candidate);
		AlignmentResult alignment = TimeSeriesAligner.estimateAlignment(reference, candidate, config.getSampleRateHz());
		double referenceAlignedApogeeTimeSec = PhaseThreeAnalysisSupport.alignedApogeeTime(reference, 0.0);
		double candidateAlignedApogeeTimeSec = PhaseThreeAnalysisSupport.alignedApogeeTime(candidate, alignment.getLagSec());
		double alignedApogeeTimeDeltaSec = PhaseThreeAnalysisSupport.alignedApogeeTimeDelta(
				referenceAlignedApogeeTimeSec, candidateAlignedApogeeTimeSec);
		VerticalIntegratorDiagnostics integratorDiagnostics = PhaseThreeAnalysisSupport.enrichIntegratorDiagnostics(
				candidateLoad.integratorDiagnostics(),
				reference,
				candidateLoad.rawSeries(),
				candidate,
				config.getSampleRateHz(),
				config.getInterpolationMode());
		double alignedApogeeTimeErrorSec = Double.isFinite(alignedApogeeTimeDeltaSec)
				? Math.abs(alignedApogeeTimeDeltaSec)
				: Double.NaN;

		if (qualityFailureReason != null) {
			return new PhaseTwoDatasetResult(
					safe(dataset.getName()),
					dataset.isAirbrakeEnabled(),
					candidateLoad.pluginResult(),
					dataQualityExcludedScores(qualityFailureReason),
					flags,
					referenceQ,
					candidateQ,
					reference.getParserDiagnostics(),
					candidate.getParserDiagnostics(),
					datasetClass,
					truthLoad.truthSource(),
					candidateLoad.candidateSource(),
					candidateLoad.orkProvenance(),
					candidateLoad.romMode(),
					candidateLoad.romSurfaceSource(),
					candidateLoad.maxMach(),
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
		PhaseTwoScoreResult fullScore = PhaseThreeAnalysisSupport.aggregateFullScore(
				fullScoreRaw, scores, scoringConfig, reference, candidate);
		scores.put(FlightPhaseWindow.FULL, fullScore);

		Map<FlightPhaseWindow, PhaseResidualMetrics> residuals = TelemetryResidualAnalyzer.analyzeByPhase(
				reference, candidate, config.getSampleRateHz(), mode, alignment, windows);
		flags.addAll(EquationTuningRuleEngine.buildFlags(fullScore, scoringConfig));
		PhaseThreeAnalysisSupport.appendApogeeFlag(referenceQ, candidateQ, flags);

		return new PhaseTwoDatasetResult(
				safe(dataset.getName()),
				dataset.isAirbrakeEnabled(),
				candidateLoad.pluginResult(),
				scores,
				flags,
				referenceQ,
				candidateQ,
				reference.getParserDiagnostics(),
				candidate.getParserDiagnostics(),
				datasetClass,
				truthLoad.truthSource(),
				candidateLoad.candidateSource(),
				candidateLoad.orkProvenance(),
				candidateLoad.romMode(),
				candidateLoad.romSurfaceSource(),
				candidateLoad.maxMach(),
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

	private static TruthLoadResult loadTruth(Path configDir, PhaseThreeBatchDatasetConfig dataset) throws Exception {
		Path truthPath;
		TelemetrySeries truthSeries;
		if (dataset.getTruthCsv() != null && !dataset.getTruthCsv().isBlank()) {
			truthPath = PhaseThreeNativeAirbrakesConfigurer.resolvePath(configDir, dataset.getTruthCsv());
			truthSeries = TelemetryParsers.parse(truthPath);
		} else if (dataset.getOrkPath() != null && !dataset.getOrkPath().isBlank()) {
			TelemetryTruthSelector.TruthSelection truth = TelemetryTruthSelector.select(inferDatasetDirectory(configDir, dataset));
			truthPath = truth.path();
			truthSeries = truth.series();
		} else {
			truthPath = PhaseThreeNativeAirbrakesConfigurer.resolvePath(configDir, dataset.getReferenceCsv());
			truthSeries = TelemetryParsers.parse(truthPath);
		}
		return new TruthLoadResult(truthPath, truthSeries);
	}

	private static CandidateLoadResult loadCandidate(Path configDir, PhaseThreeBatchDatasetConfig dataset) throws Exception {
		boolean hasOrk = dataset.getOrkPath() != null && !dataset.getOrkPath().isBlank();
		boolean hasCandidateCsv = dataset.getCandidateCsv() != null && !dataset.getCandidateCsv().isBlank();
		Path resolvedOrkPath = hasOrk ? PhaseThreeNativeAirbrakesConfigurer.resolvePath(configDir, dataset.getOrkPath()) : null;

		if (hasOrk) {
			AtomicReference<AbPluginExecutionResult> pluginResultRef = new AtomicReference<>(
					new AbPluginExecutionResult(AbPluginExecutionResult.Status.SKIPPED, 0,
							"Native airbrakes not configured"));
			try {
				HeadlessOrkSimulationRunner.OrkSimulationResult result =
						HeadlessOrkSimulationRunner.runFirstSimulationDetailed(
								resolvedOrkPath.toFile(),
								simulation -> {
									try {
										pluginResultRef.set(PhaseThreeNativeAirbrakesConfigurer.configure(
												dataset, configDir, simulation.getOptions()));
									} catch (IOException ex) {
										throw new IllegalStateException("Failed to configure native airbrakes", ex);
									}
								});
				return new CandidateLoadResult(
						result.getSeries(),
						result.getRawSeries(),
						result.getIntegratorDiagnostics(),
						pluginResultRef.get(),
						null,
						"ORK_SIMULATION",
						resolvedOrkPath.toAbsolutePath().normalize().toString(),
						result.getRomMode(),
						result.getRomSurfaceSource(),
						result.getMaxMach());
			} catch (Exception ex) {
				if (!hasCandidateCsv) {
					throw ex;
				}
				TelemetrySeries fallback = TelemetryParsers.parse(
						PhaseThreeNativeAirbrakesConfigurer.resolvePath(configDir, dataset.getCandidateCsv()));
				return new CandidateLoadResult(
						fallback,
						fallback,
						VerticalIntegratorDiagnostics.EMPTY,
						new AbPluginExecutionResult(AbPluginExecutionResult.Status.SKIPPED, 0,
								"ORK simulation fallback to candidateCsv: " + ex.getClass().getSimpleName()),
						"ORK simulation failed and candidateCsv fallback was used",
						"ORK_FALLBACK_TO_CSV",
						resolvedOrkPath == null ? "" : resolvedOrkPath.toAbsolutePath().normalize().toString(),
						"",
						"",
						Double.NaN);
			}
		}

		if (hasCandidateCsv) {
			TelemetrySeries candidate = TelemetryParsers.parse(
					PhaseThreeNativeAirbrakesConfigurer.resolvePath(configDir, dataset.getCandidateCsv()));
			return new CandidateLoadResult(
					candidate,
					candidate,
					VerticalIntegratorDiagnostics.EMPTY,
					new AbPluginExecutionResult(AbPluginExecutionResult.Status.SKIPPED, 0, "Candidate CSV comparison only"),
					null,
					"CANDIDATE_CSV",
					"",
					"",
					"",
					Double.NaN);
		}

		throw new IllegalArgumentException("Dataset must provide candidateCsv or orkPath: " + dataset.getName());
	}

	private static PhaseThreeBatchRunConfig loadConfig(Path configPath) throws IOException {
		String json = Files.readString(configPath, StandardCharsets.UTF_8);
		JsonObject root = GSON.fromJson(json, JsonObject.class);
		if (root == null) {
			throw new IllegalArgumentException("Invalid config JSON: " + configPath);
		}
		if (root.has("datasets") && root.get("datasets").isJsonArray()) {
			PhaseThreeBatchRunConfig single = GSON.fromJson(root, PhaseThreeBatchRunConfig.class);
			if (single == null || single.getDatasets() == null || single.getDatasets().isEmpty()) {
				throw new IllegalArgumentException("No datasets defined in config: " + configPath);
			}
			return single;
		}

		List<PhaseThreeBatchDatasetConfig> mergedDatasets = new ArrayList<>();
		JsonObject firstConfigObject = null;
		for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
			if (!entry.getValue().isJsonObject()) {
				continue;
			}
			JsonObject child = entry.getValue().getAsJsonObject();
			PhaseThreeBatchRunConfig childConfig = GSON.fromJson(child, PhaseThreeBatchRunConfig.class);
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

		PhaseThreeBatchRunConfig config = GSON.fromJson(normalized, PhaseThreeBatchRunConfig.class);
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

	private static String classifyDataset(Path configDir,
										  PhaseThreeBatchDatasetConfig dataset,
										  CandidateLoadResult candidateLoad,
										  TelemetrySeries reference) {
		boolean hasOrk = dataset.getOrkPath() != null && !dataset.getOrkPath().isBlank();
		if (hasOrk) {
			if (candidateLoad.fallbackReason() != null) {
				return "BROKEN";
			}
			if (!"ORK_SIMULATION".equals(candidateLoad.candidateSource())) {
				return "BROKEN";
			}
			if (reference.getSchema() == TelemetrySchema.AB_IMU_INTERLEAVED) {
				return "BROKEN";
			}
			return "ROM_USABLE";
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
		Path referencePath = PhaseThreeNativeAirbrakesConfigurer.resolvePath(configDir, referenceCsv).toAbsolutePath().normalize();
		Path candidatePath = PhaseThreeNativeAirbrakesConfigurer.resolvePath(configDir, candidateCsv).toAbsolutePath().normalize();
		return referencePath.equals(candidatePath);
	}

	private static Path inferDatasetDirectory(Path configDir, PhaseThreeBatchDatasetConfig dataset) {
		List<String> values = new ArrayList<>(4);
		values.add(dataset.getTruthCsv());
		values.add(dataset.getReferenceCsv());
		values.add(dataset.getOrkPath());
		values.add(dataset.getCandidateCsv());
		for (String value : values) {
			if (value == null || value.isBlank()) {
				continue;
			}
			Path parent = PhaseThreeNativeAirbrakesConfigurer.resolvePath(configDir, value).getParent();
			if (parent != null && Files.isDirectory(parent)) {
				return parent;
			}
		}
		return configDir.toAbsolutePath().normalize();
	}

	private static PhaseTwoDatasetResult failedDatasetResult(PhaseThreeBatchDatasetConfig dataset, Throwable ex) {
		Map<FlightPhaseWindow, PhaseTwoScoreResult> emptyScores = new EnumMap<>(FlightPhaseWindow.class);
		DerivedTelemetryQuantities.Quantities nanQuantities = new DerivedTelemetryQuantities.Quantities(
				Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
		AbPluginExecutionResult pluginResult = new AbPluginExecutionResult(
				AbPluginExecutionResult.Status.FAILED, -1, ex.getClass().getSimpleName() + ": " + ex.getMessage());
		return new PhaseTwoDatasetResult(
				safe(dataset.getName()),
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

	private static void writeRunMetadata(Path metadataPath,
										 Path configPath,
										 PhaseThreeBatchRunConfig config) throws IOException {
		StringBuilder metadata = new StringBuilder();
		metadata.append("timestamp=").append(Instant.now()).append(System.lineSeparator());
		metadata.append("config=").append(configPath).append(System.lineSeparator());
		metadata.append("sampleRateHz=").append(config.getSampleRateHz()).append(System.lineSeparator());
		metadata.append("interpolationMode=").append(config.getInterpolationMode()).append(System.lineSeparator());
		metadata.append("datasetsExecuted=").append(config.getDatasets().size()).append(System.lineSeparator());
		Files.writeString(metadataPath, metadata.toString(), StandardCharsets.UTF_8);
	}

	private static String safe(String value) {
		return value == null ? "" : value;
	}

	private record TruthLoadResult(Path truthPath, TelemetrySeries series) {
		private String truthSource() {
			return truthPath == null ? "" : truthPath.toAbsolutePath().normalize().toString();
		}
	}

	private record CandidateLoadResult(TelemetrySeries series,
									 TelemetrySeries rawSeries,
									 VerticalIntegratorDiagnostics integratorDiagnostics,
									 AbPluginExecutionResult pluginResult,
									 String fallbackReason,
									 String candidateSource,
									 String orkProvenance,
									 String romMode,
									 String romSurfaceSource,
									 double maxMach) {
	}
}

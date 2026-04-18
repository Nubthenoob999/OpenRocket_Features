package info.openrocket.core.tuning;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.simulation.exception.SimulationException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class PhaseThreeSingleSimulationRunner {
	private PhaseThreeSingleSimulationRunner() {
	}

	public static PhaseTwoDatasetResult compareCurrentSimulation(OpenRocketDocument document,
																 Simulation simulation,
																 Path referenceCsv) throws IOException, SimulationException {
		return compareCurrentSimulation(document, simulation, referenceCsv, 20.0, InterpolationMode.CUBIC_HERMITE);
	}

	public static PhaseTwoDatasetResult compareCurrentSimulation(OpenRocketDocument document,
																 Simulation simulation,
																 Path referenceCsv,
																 double sampleRateHz,
																 InterpolationMode interpolationMode) throws IOException, SimulationException {
		Path normalizedReference = referenceCsv.toAbsolutePath().normalize();
		TelemetrySeries reference = TelemetryParsers.parse(normalizedReference);
		HeadlessOrkSimulationRunner.OrkSimulationResult candidateTelemetry =
				HeadlessOrkSimulationRunner.runSimulationDetailed(detachedSimulationCopy(document, simulation));

		List<TuningFlag> flags = new ArrayList<>();
		TelemetrySeries candidateRaw = candidateTelemetry.getSeries();
		DerivedTelemetryQuantities.Quantities referenceQ = DerivedTelemetryQuantities.summarize(reference);
		DerivedTelemetryQuantities.Quantities rawCandidateQ = DerivedTelemetryQuantities.summarize(candidateRaw);
		String datasetClass = classifyDataset(reference);
		String qualityFailureReason = PhaseThreeAnalysisSupport.evaluateDataQuality(reference, candidateRaw, referenceQ, rawCandidateQ, flags);

		TelemetrySeries candidate = qualityFailureReason == null
				? PhaseThreeAnalysisSupport.applyTemperatureModel(reference, candidateRaw, flags)
				: candidateRaw;
		DerivedTelemetryQuantities.Quantities candidateQ = DerivedTelemetryQuantities.summarize(candidate);
		AlignmentResult alignment = TimeSeriesAligner.estimateAlignment(reference, candidate, sampleRateHz);
		double referenceAlignedApogeeTimeSec = PhaseThreeAnalysisSupport.alignedApogeeTime(reference, 0.0);
		double candidateAlignedApogeeTimeSec = PhaseThreeAnalysisSupport.alignedApogeeTime(candidate, alignment.getLagSec());
		double alignedApogeeTimeDeltaSec = PhaseThreeAnalysisSupport.alignedApogeeTimeDelta(referenceAlignedApogeeTimeSec, candidateAlignedApogeeTimeSec);
		double alignedApogeeTimeErrorSec = Double.isFinite(alignedApogeeTimeDeltaSec) ? Math.abs(alignedApogeeTimeDeltaSec) : Double.NaN;
		VerticalIntegratorDiagnostics integratorDiagnostics = PhaseThreeAnalysisSupport.enrichIntegratorDiagnostics(
				candidateTelemetry.getIntegratorDiagnostics(),
				reference,
				candidateTelemetry.getRawSeries(),
				candidate,
				sampleRateHz,
				interpolationMode);

		Map<FlightPhaseWindow, PhaseTwoScoreResult> scores = new EnumMap<>(FlightPhaseWindow.class);
		Map<FlightPhaseWindow, PhaseResidualMetrics> residuals = Map.of();
		PhaseTwoScoringConfig scoringConfig = PhaseTwoScoringConfig.defaults();

		if (qualityFailureReason != null) {
			scores.put(FlightPhaseWindow.FULL, new PhaseTwoScoreResult(
					Double.NaN,
					ScoreSeverity.WARNING,
					Map.of(),
					Double.NaN,
					0,
					0,
					"DATA_QUALITY_EXCLUDED",
					qualityFailureReason));
			datasetClass = "BROKEN";
		} else {
			TelemetryComparisonResult full = TelemetryComparator.compare(reference, candidate, sampleRateHz, interpolationMode, alignment);
			PhaseTwoScoreResult fullScoreRaw = PhaseTwoScoreCalculator.score(full, scoringConfig);
			Map<FlightPhaseWindow, double[]> windows = PhaseThreeAnalysisSupport.estimateWindows(reference);
			for (Map.Entry<FlightPhaseWindow, double[]> entry : windows.entrySet()) {
				TelemetryComparisonResult perWindow = TelemetryComparator.compare(
						reference,
						candidate,
						sampleRateHz,
						interpolationMode,
						alignment,
						entry.getValue()[0],
						entry.getValue()[1]);
				scores.put(entry.getKey(), PhaseTwoScoreCalculator.score(perWindow, scoringConfig));
			}
			PhaseTwoScoreResult fullScore = PhaseThreeAnalysisSupport.aggregateFullScore(fullScoreRaw, scores, scoringConfig, reference, candidate);
			scores.put(FlightPhaseWindow.FULL, fullScore);
			residuals = TelemetryResidualAnalyzer.analyzeByPhase(reference, candidate, sampleRateHz, interpolationMode, alignment, windows);
			flags.addAll(EquationTuningRuleEngine.buildFlags(fullScore, scoringConfig));
			PhaseThreeAnalysisSupport.appendApogeeFlag(referenceQ, candidateQ, flags);
		}

		return new PhaseTwoDatasetResult(
				simulation.getName(),
				false,
				new AbPluginExecutionResult(AbPluginExecutionResult.Status.SKIPPED, 0, "Current simulation comparison"),
				scores,
				flags,
				referenceQ,
				candidateQ,
				reference.getParserDiagnostics(),
				candidate.getParserDiagnostics(),
				datasetClass,
				normalizedReference.toString(),
				"CURRENT_SIMULATION_RERUN",
				provenance(document),
				candidateTelemetry.getRomMode(),
				candidateTelemetry.getRomSurfaceSource(),
				candidateTelemetry.getMaxMach(),
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

	public static PhaseTwoBatchResult compareAndWrite(OpenRocketDocument document,
													 Simulation simulation,
													 Path referenceCsv,
													 Path outputDir) throws IOException, SimulationException {
		PhaseTwoDatasetResult dataset = compareCurrentSimulation(document, simulation, referenceCsv);
		PhaseTwoBatchResult batch = new PhaseTwoBatchResult(List.of(dataset));
		PhaseTwoBatchReportWriter.write(batch, outputDir);
		return batch;
	}

	private static Simulation detachedSimulationCopy(OpenRocketDocument document, Simulation simulation) {
		if (document != null) {
			int index = document.getSimulations().indexOf(simulation);
			if (index >= 0) {
				OpenRocketDocument copy = document.copy();
				if (index < copy.getSimulations().size()) {
					return copy.getSimulations().get(index);
				}
			}
		}
		return simulation.copy();
	}

	private static String provenance(OpenRocketDocument document) {
		if (document == null || document.getFile() == null) {
			return "Current simulation";
		}
		return document.getFile().getAbsolutePath();
	}

	private static String classifyDataset(TelemetrySeries reference) {
		return reference.getSchema() == TelemetrySchema.AB_IMU_INTERLEAVED ? "BROKEN" : "ROM_USABLE";
	}
}

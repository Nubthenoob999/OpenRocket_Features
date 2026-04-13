package info.openrocket.core.tuning;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class PhaseThreeAnalysisSupport {
	private static final double ISA_LAPSE_RATE_C_PER_M = 0.0065;
	private static final double MIN_LAPSE_FIT_ALT_M = 200.0;
	private static final double TEMPERATURE_APOGEE_MISMATCH_C = 10.0;
	private static final double SENTINEL_RATE_THRESHOLD = 0.30;
	private static final double CD_PROXY_MIN = 0.01;
	private static final double CD_PROXY_MAX = 2.5;
	private static final double APOGEE_WARNING_ERROR_METERS = 60.0;
	private static final double APOGEE_CRITICAL_ERROR_METERS = 120.0;
	private static final double APOGEE_WARNING_ERROR_PERCENT = 5.0;
	private static final double APOGEE_CRITICAL_ERROR_PERCENT = 10.0;
	private static final double MISSING_PHASE_SCORE_PENALTY = 12.0;
	private static final int MISSING_PHASE_WARNING_CAP_COUNT = 2;

	private PhaseThreeAnalysisSupport() {
	}

	public static String evaluateDataQuality(TelemetrySeries reference,
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

	public static TelemetrySeries applyTemperatureModel(TelemetrySeries reference,
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
			double modeled = lapseFit != null
					? lapseFit[0] + lapseFit[1] * altitude
					: referenceGroundTemp - ISA_LAPSE_RATE_C_PER_M * Math.max(0.0, altitude);
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
					Double value = modeledTemps.get(i);
					if (value != null && Double.isFinite(value)) {
						modeledTemps.set(i, value + offset);
					}
				}
				corrected = copyWithTemperatures(candidate, modeledTemps);
			}
		}

		return corrected;
	}

	public static Map<FlightPhaseWindow, double[]> estimateWindows(TelemetrySeries reference) {
		Map<FlightPhaseWindow, double[]> windows = new EnumMap<>(FlightPhaseWindow.class);
		TelemetrySeries.EventMarkers markers = reference.estimateEventMarkers();
		double launchTime = finiteOrZero(markers.launchTimeSec());
		double burnoutTime = alignedEventTime(reference, launchTime, markers.burnoutTimeSec(), 0.0);
		double apogeeTime = alignedEventTime(reference, launchTime, markers.apogeeTimeSec(), 0.0);
		if (!Double.isFinite(apogeeTime)) {
			List<Double> shiftedTime = shiftToAlignedT0(reference, 0.0);
			apogeeTime = apogeeTime(shiftedTime, reference.getAltitudeMetersAgl());
		}
		if (Double.isFinite(burnoutTime) && burnoutTime > 0.0) {
			windows.put(FlightPhaseWindow.BOOST, new double[]{0.0, burnoutTime});
		}
		if (Double.isFinite(apogeeTime) && apogeeTime > burnoutTime) {
			windows.put(FlightPhaseWindow.COAST, new double[]{Math.max(0.0, burnoutTime), apogeeTime});
			windows.put(FlightPhaseWindow.DESCENT, new double[]{apogeeTime, Double.POSITIVE_INFINITY});
		}
		return windows;
	}

	public static double alignedApogeeTime(TelemetrySeries series) {
		return alignedApogeeTime(series, 0.0);
	}

	public static double alignedApogeeTime(TelemetrySeries series, double lagSec) {
		TelemetrySeries.EventMarkers markers = series.estimateEventMarkers();
		double launchTime = finiteOrZero(markers.launchTimeSec());
		double aligned = alignedEventTime(series, launchTime, markers.apogeeTimeSec(), lagSec);
		if (Double.isFinite(aligned)) {
			return aligned;
		}
		return apogeeTime(shiftToAlignedT0(series, lagSec), series.getAltitudeMetersAgl());
	}

	public static double alignedApogeeTimeDelta(double referenceAlignedApogeeTimeSec,
												 double candidateAlignedApogeeTimeSec) {
		if (!Double.isFinite(referenceAlignedApogeeTimeSec) || !Double.isFinite(candidateAlignedApogeeTimeSec)) {
			return Double.NaN;
		}
		return candidateAlignedApogeeTimeSec - referenceAlignedApogeeTimeSec;
	}

	public static PhaseTwoScoreResult aggregateFullScore(PhaseTwoScoreResult fullScoreRaw,
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
			if (Double.isFinite(phaseScore.getScore())) {
				scoreSum += phaseScore.getScore();
				totalWeight += 1.0;
			}
			matched += phaseScore.getMatchedSampleCount();
			timeline += phaseScore.getTimelineSampleCount();
		}

		double aggregatedScore = totalWeight > 0.0 ? scoreSum / totalWeight : fullScoreRaw.getScore();
		if (missingPhases > 0 && Double.isFinite(aggregatedScore)) {
			aggregatedScore = Math.max(0.0, aggregatedScore - MISSING_PHASE_SCORE_PENALTY * missingPhases);
		}
		String insufficientDataReason = fullScoreRaw.getInsufficientDataReason();
		if (missingPhases > 0 && (reference.getSchema() == TelemetrySchema.AB_IMU_INTERLEAVED
				|| candidate.getSchema() == TelemetrySchema.AB_IMU_INTERLEAVED)) {
			insufficientDataReason = "AB_IMU_INTERLEAVED source truncated at burnout; coast and descent phases unavailable - use AB_EXTENDED schema or substitute with EasyMini/Fluctus as reference for coast/descent analysis";
		} else if (missingPhases > 0 && (insufficientDataReason == null || insufficientDataReason.isBlank())) {
			insufficientDataReason = "no-phase-coverage";
		}
		if (missingPhases >= MISSING_PHASE_WARNING_CAP_COUNT
				&& Double.isFinite(aggregatedScore)) {
			aggregatedScore = Math.min(aggregatedScore, scoringConfig.getWarningBelowScore() - 1.0e-3);
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

	public static void appendApogeeFlag(DerivedTelemetryQuantities.Quantities referenceQ,
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

		if (severity != ScoreSeverity.OK) {
			flags.add(new TuningFlag(
					"apogee",
					"rom.integrator.vertical-kinematics",
					severity,
					Math.max(0.0, 100.0 - absoluteErrorPercent)));
		}
	}

	public static VerticalIntegratorDiagnostics enrichIntegratorDiagnostics(VerticalIntegratorDiagnostics base,
																			 TelemetrySeries reference,
																			 TelemetrySeries rawCandidate,
																			 TelemetrySeries correctedCandidate,
																			 double sampleRateHz,
																			 InterpolationMode interpolationMode) {
		VerticalIntegratorDiagnostics seed = base == null ? VerticalIntegratorDiagnostics.EMPTY : base;
		if (reference == null || rawCandidate == null || correctedCandidate == null || sampleRateHz <= 0.0) {
			return seed;
		}

		AlignmentResult rawAlignment = TimeSeriesAligner.estimateAlignment(reference, rawCandidate, sampleRateHz);
		AlignmentResult correctedAlignment = TimeSeriesAligner.estimateAlignment(reference, correctedCandidate, sampleRateHz);

		double referenceApogeeTime = alignedApogeeTime(reference, 0.0);
		double rawApogeeError = absolute(alignedApogeeTimeDelta(referenceApogeeTime, alignedApogeeTime(rawCandidate, rawAlignment.getLagSec())));
		double correctedApogeeError = absolute(alignedApogeeTimeDelta(referenceApogeeTime, alignedApogeeTime(correctedCandidate, correctedAlignment.getLagSec())));
		double rawAltitudeRmse = altitudeRmse(reference, rawCandidate, sampleRateHz, interpolationMode, rawAlignment);
		double correctedAltitudeRmse = altitudeRmse(reference, correctedCandidate, sampleRateHz, interpolationMode, correctedAlignment);

		return new VerticalIntegratorDiagnostics(
				seed.getRawDriftMeanAbsMeters(),
				seed.getPressureAnchorCorrectionMeanAbsMeters(),
				rawApogeeError,
				correctedApogeeError,
				rawAltitudeRmse,
				correctedAltitudeRmse,
				seed.getAccelBiasEstimateMetersPerSec2(),
				seed.getPreLaunchSampleCount(),
				seed.getLaunchDetectedTimeSec(),
				seed.getBurnoutDetectedTimeSec(),
				seed.getApogeeDetectedTimeSec(),
				seed.getDeploymentDetectedTimeSec());
	}

	public static ScoreSeverity classifyScore(double score, PhaseTwoScoringConfig config) {
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

	private static double altitudeRmse(TelemetrySeries reference,
										 TelemetrySeries candidate,
										 double sampleRateHz,
										 InterpolationMode interpolationMode,
										 AlignmentResult alignment) {
		PhaseResidualMetrics metrics = TelemetryResidualAnalyzer.analyze(
				reference,
				candidate,
				sampleRateHz,
				interpolationMode,
				alignment == null ? AlignmentResult.NONE : alignment,
				0.0,
				Double.POSITIVE_INFINITY);
		ResidualMetrics altitude = metrics.getChannelResiduals().get("altitude");
		return altitude == null ? Double.NaN : altitude.getRmse();
	}

	private static void appendSentinelRateFlag(String source,
												 TelemetryParserDiagnostics diagnostics,
												 List<TuningFlag> flags,
												 List<String> reasons) {
		double rate = diagnostics.getSentinelRate();
		if (rate <= SENTINEL_RATE_THRESHOLD) {
			return;
		}
		reasons.add(source + "-high-sentinel-rate=" + String.format(java.util.Locale.US, "%.1f%%", rate * 100.0));
		flags.add(new TuningFlag("data-quality", "DATA_QUALITY:HIGH_SENTINEL_RATE", ScoreSeverity.WARNING, 100.0 * rate));
	}

	private static void appendCdProxyRangeFlag(String source,
												double cdProxy,
												List<TuningFlag> flags,
												List<String> reasons) {
		if (!Double.isFinite(cdProxy) || (cdProxy >= CD_PROXY_MIN && cdProxy <= CD_PROXY_MAX)) {
			return;
		}
		reasons.add(source + "-cdProxy=" + String.format(java.util.Locale.US, "%.3f", cdProxy));
		flags.add(new TuningFlag("cdProxy", "PHYSICS:CD_PROXY_OUT_OF_RANGE", ScoreSeverity.WARNING, cdProxy));
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
			Double altitude = series.getAltitudeMetersAgl().get(i);
			Double temperature = series.getTemperatureC().get(i);
			if (altitude == null || temperature == null || !Double.isFinite(altitude) || !Double.isFinite(temperature)
					|| altitude <= MIN_LAPSE_FIT_ALT_M) {
				continue;
			}
			sumH += altitude;
			sumT += temperature;
			sumHH += altitude * altitude;
			sumHT += altitude * temperature;
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
			if (altitude != null && Double.isFinite(altitude) && altitude > maxAlt) {
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

	private static List<Double> shiftToAlignedT0(TelemetrySeries series, double lagSec) {
		List<Double> shifted = new ArrayList<>(series.getTimeSec().size());
		int t0Index = series.estimateLaunchAnchorIndex();
		double t0 = (t0Index >= 0 && t0Index < series.getTimeSec().size() && series.getTimeSec().get(t0Index) != null)
				? series.getTimeSec().get(t0Index)
				: 0.0;
		double offset = t0 - lagSec;
		for (Double time : series.getTimeSec()) {
			shifted.add(time == null ? null : time - offset);
		}
		return shifted;
	}

	private static double alignedEventTime(TelemetrySeries series, double launchTimeSec, Double eventTimeSec, double lagSec) {
		if (series == null || eventTimeSec == null || !Double.isFinite(eventTimeSec)) {
			return Double.NaN;
		}
		return eventTimeSec - launchTimeSec + lagSec;
	}

	private static double apogeeTime(List<Double> time, List<Double> altitude) {
		double maxAlt = Double.NEGATIVE_INFINITY;
		double maxTime = Double.NaN;
		for (int i = 0; i < time.size() && i < altitude.size(); i++) {
			Double sampleTime = time.get(i);
			Double sampleAltitude = altitude.get(i);
			if (sampleTime == null || sampleAltitude == null) {
				continue;
			}
			if (sampleAltitude > maxAlt) {
				maxAlt = sampleAltitude;
				maxTime = sampleTime;
			}
		}
		return maxTime;
	}

	private static double boostEndTime(List<Double> time, List<Double> velocityZ, double apogeeTime) {
		double maxV = Double.NEGATIVE_INFINITY;
		double maxVTime = Double.NaN;
		for (int i = 0; i < time.size() && i < velocityZ.size(); i++) {
			Double sampleTime = time.get(i);
			Double sampleVelocity = velocityZ.get(i);
			if (sampleTime == null || sampleVelocity == null || sampleTime < 0.0) {
				continue;
			}
			if (Double.isFinite(apogeeTime) && sampleTime > apogeeTime) {
				continue;
			}
			if (sampleVelocity > maxV) {
				maxV = sampleVelocity;
				maxVTime = sampleTime;
			}
		}
		return maxVTime;
	}

	private static double absolute(double value) {
		return Double.isFinite(value) ? Math.abs(value) : Double.NaN;
	}

	private static double finiteOrZero(Double value) {
		return value != null && Double.isFinite(value) ? value : 0.0;
	}
}

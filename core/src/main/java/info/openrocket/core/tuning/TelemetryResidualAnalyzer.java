package info.openrocket.core.tuning;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TelemetryResidualAnalyzer {
	private static final double EPSILON = 1e-9;

	private TelemetryResidualAnalyzer() {
	}

	public static Map<FlightPhaseWindow, PhaseResidualMetrics> analyzeByPhase(TelemetrySeries reference,
																			   TelemetrySeries candidate,
																			   double sampleRateHz,
																			   InterpolationMode mode,
																			   AlignmentResult alignment,
																			   Map<FlightPhaseWindow, double[]> windows) {
		Map<FlightPhaseWindow, PhaseResidualMetrics> residuals = new EnumMap<>(FlightPhaseWindow.class);
		residuals.put(FlightPhaseWindow.FULL, analyze(reference, candidate, sampleRateHz, mode, alignment, 0.0, Double.POSITIVE_INFINITY));
		for (Map.Entry<FlightPhaseWindow, double[]> entry : windows.entrySet()) {
			residuals.put(entry.getKey(),
					analyze(reference, candidate, sampleRateHz, mode, alignment, entry.getValue()[0], entry.getValue()[1]));
		}
		return residuals;
	}

	public static PhaseResidualMetrics analyze(TelemetrySeries reference,
											   TelemetrySeries candidate,
											   double sampleRateHz,
											   InterpolationMode mode,
											   AlignmentResult alignment,
											   double windowStartSec,
											   double windowEndSec) {
		Map<String, ResidualMetrics> channels = new LinkedHashMap<>();
		channels.put("altitude", residual(reference, candidate, sampleRateHz, mode, alignment,
				reference.getAltitudeMetersAgl(), candidate.getAltitudeMetersAgl(), windowStartSec, windowEndSec));
		channels.put("velocityZ", residual(reference, candidate, sampleRateHz, mode, alignment,
				reference.getVelocityZMetersPerSec(), candidate.getVelocityZMetersPerSec(), windowStartSec, windowEndSec));
		channels.put("accelZ", residual(reference, candidate, sampleRateHz, mode, alignment,
				reference.getAccelerationZMetersPerSec2(), candidate.getAccelerationZMetersPerSec2(), windowStartSec, windowEndSec));
		DragResidualMetrics dragResidual = dragResidual(reference, candidate, sampleRateHz, mode, alignment, windowStartSec, windowEndSec);
		return new PhaseResidualMetrics(channels, dragResidual);
	}

	private static ResidualMetrics residual(TelemetrySeries reference,
											TelemetrySeries candidate,
											double sampleRateHz,
											InterpolationMode mode,
											AlignmentResult alignment,
											List<Double> referenceValues,
											List<Double> candidateValues,
											double windowStartSec,
											double windowEndSec) {
		SampledChannel sampled = sample(reference, candidate, sampleRateHz, mode, alignment, referenceValues, candidateValues, windowStartSec, windowEndSec);
		if (sampled.sampleCount <= 0) {
			return ResidualMetrics.EMPTY;
		}
		double meanError = sampled.errorSum / sampled.sampleCount;
		double mae = sampled.absErrorSum / sampled.sampleCount;
		double rmse = Math.sqrt(sampled.squaredErrorSum / sampled.sampleCount);
		double nrmse = (rmse / Math.max(sampled.referenceRange, EPSILON)) * 100.0;
		return new ResidualMetrics(sampled.sampleCount, meanError, mae, rmse, nrmse);
	}

	private static DragResidualMetrics dragResidual(TelemetrySeries reference,
													TelemetrySeries candidate,
													double sampleRateHz,
													InterpolationMode mode,
													AlignmentResult alignment,
													double windowStartSec,
													double windowEndSec) {
		List<Double> referenceDrag = DerivedTelemetryQuantities.cdProxySeries(reference.getVelocityZMetersPerSec(), reference.getAccelerationZMetersPerSec2());
		List<Double> candidateDrag = DerivedTelemetryQuantities.cdProxySeries(candidate.getVelocityZMetersPerSec(), candidate.getAccelerationZMetersPerSec2());
		SampledChannel sampled = sample(reference, candidate, sampleRateHz, mode, alignment, referenceDrag, candidateDrag, windowStartSec, windowEndSec);
		if (sampled.sampleCount <= 0) {
			return DragResidualMetrics.EMPTY;
		}
		double truthMean = sampled.referenceSum / sampled.sampleCount;
		double candidateMean = sampled.candidateSum / sampled.sampleCount;
		double delta = candidateMean - truthMean;
		double deltaPercent = !Double.isFinite(truthMean) || Math.abs(truthMean) <= EPSILON
				? Double.NaN
				: (delta / truthMean) * 100.0;
		return new DragResidualMetrics(sampled.sampleCount, truthMean, candidateMean, delta, deltaPercent);
	}

	private static SampledChannel sample(TelemetrySeries reference,
										 TelemetrySeries candidate,
										 double sampleRateHz,
										 InterpolationMode mode,
										 AlignmentResult alignment,
										 List<Double> referenceValues,
										 List<Double> candidateValues,
										 double windowStartSec,
										 double windowEndSec) {
		List<Double> referenceTime = TimeSeriesAligner.shiftTimes(reference.getTimeSec(),
				valueAt(reference.getTimeSec(), reference.estimateLaunchAnchorIndex()));
		List<Double> candidateTime = TimeSeriesAligner.shiftTimes(candidate.getTimeSec(),
				valueAt(candidate.getTimeSec(), candidate.estimateLaunchAnchorIndex()) - alignment.getLagSec());
		double overlapEnd = Math.min(maxFinite(referenceTime), maxFinite(candidateTime));
		double start = Math.max(0.0, windowStartSec);
		double end = Math.min(overlapEnd, Double.isFinite(windowEndSec) ? windowEndSec : overlapEnd);
		if (!Double.isFinite(end) || end <= start) {
			return new SampledChannel();
		}
		List<Double> timeline = TimeSeriesAligner.buildUniformTimeline(start, end, sampleRateHz);
		List<Double> referenceResampled = TimeSeriesAligner.resample(referenceTime, referenceValues, timeline, mode);
		List<Double> candidateResampled = TimeSeriesAligner.resample(candidateTime, candidateValues, timeline, mode);

		SampledChannel sampled = new SampledChannel();
		double referenceMin = Double.POSITIVE_INFINITY;
		double referenceMax = Double.NEGATIVE_INFINITY;
		for (int i = 0; i < timeline.size(); i++) {
			Double referenceValue = valueAt(referenceResampled, i);
			Double candidateValue = valueAt(candidateResampled, i);
			if (referenceValue == null || candidateValue == null) {
				continue;
			}
			double error = candidateValue - referenceValue;
			sampled.sampleCount++;
			sampled.errorSum += error;
			sampled.absErrorSum += Math.abs(error);
			sampled.squaredErrorSum += error * error;
			sampled.referenceSum += referenceValue;
			sampled.candidateSum += candidateValue;
			referenceMin = Math.min(referenceMin, referenceValue);
			referenceMax = Math.max(referenceMax, referenceValue);
		}
		sampled.referenceRange = sampled.sampleCount <= 0 ? Double.NaN : Math.max(referenceMax - referenceMin, EPSILON);
		return sampled;
	}

	private static Double valueAt(List<Double> values, int index) {
		if (values == null || index < 0 || index >= values.size()) {
			return null;
		}
		return values.get(index);
	}

	private static double maxFinite(List<Double> values) {
		double max = Double.NaN;
		for (Double value : values) {
			if (value == null || !Double.isFinite(value)) {
				continue;
			}
			if (Double.isNaN(max) || value > max) {
				max = value;
			}
		}
		return max;
	}

	private static final class SampledChannel {
		private int sampleCount;
		private double errorSum;
		private double absErrorSum;
		private double squaredErrorSum;
		private double referenceSum;
		private double candidateSum;
		private double referenceRange = Double.NaN;
	}
}

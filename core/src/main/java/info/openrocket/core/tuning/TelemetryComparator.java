package info.openrocket.core.tuning;

import java.util.List;

public final class TelemetryComparator {
	private static final double EPSILON = 1e-9;
	private static final double DRY_AIR_GAS_CONSTANT = 287.05287;

	private TelemetryComparator() {
	}

	public static TelemetryComparisonResult compare(TelemetrySeries reference,
										TelemetrySeries candidate,
										double sampleRateHz,
										InterpolationMode mode) {
		return compare(reference, candidate, sampleRateHz, mode, 0.0, 0.0, Double.POSITIVE_INFINITY);
	}

	public static TelemetryComparisonResult compare(TelemetrySeries reference,
										TelemetrySeries candidate,
										double sampleRateHz,
										InterpolationMode mode,
										AlignmentResult alignment) {
		double lagSec = alignment == null ? 0.0 : alignment.getLagSec();
		return compare(reference, candidate, sampleRateHz, mode, lagSec, 0.0, Double.POSITIVE_INFINITY);
	}

	public static TelemetryComparisonResult compare(TelemetrySeries reference,
										TelemetrySeries candidate,
										double sampleRateHz,
										InterpolationMode mode,
										double windowStartSec,
										double windowEndSec) {
		return compare(reference, candidate, sampleRateHz, mode, 0.0, windowStartSec, windowEndSec);
	}

	public static TelemetryComparisonResult compare(TelemetrySeries reference,
										TelemetrySeries candidate,
										double sampleRateHz,
										InterpolationMode mode,
										AlignmentResult alignment,
										double windowStartSec,
										double windowEndSec) {
		double lagSec = alignment == null ? 0.0 : alignment.getLagSec();
		return compare(reference, candidate, sampleRateHz, mode, lagSec, windowStartSec, windowEndSec);
	}

	public static TelemetryComparisonResult compare(TelemetrySeries reference,
										TelemetrySeries candidate,
										double sampleRateHz,
										InterpolationMode mode,
										double alignmentLagSec,
										double windowStartSec,
										double windowEndSec) {
		TelemetryComparisonResult result = new TelemetryComparisonResult();

		double referenceT0 = timeAtIndex(reference, reference.estimateLaunchAnchorIndex());
		double candidateT0 = timeAtIndex(candidate, candidate.estimateLaunchAnchorIndex());
		if (Double.isNaN(referenceT0) || Double.isNaN(candidateT0)) {
			result.setInsufficientDataReason("launch-anchor-missing");
			return result;
		}

		List<Double> referenceTime = shiftTimes(reference.getTimeSec(), referenceT0);
		List<Double> candidateTime = shiftTimes(candidate.getTimeSec(), candidateT0 - alignmentLagSec);

		double overlapEnd = Math.min(maxFiniteTime(referenceTime), maxFiniteTime(candidateTime));
		if (!Double.isFinite(overlapEnd)) {
			result.setInsufficientDataReason("no-finite-overlap");
			return result;
		}

		double start = Math.max(0.0, windowStartSec);
		double requestedEnd = Double.isFinite(windowEndSec) ? windowEndSec : overlapEnd;
		double end = Math.min(overlapEnd, requestedEnd);
		if (end <= start) {
			result.setInsufficientDataReason("window-out-of-range");
			return result;
		}

		List<Double> timeline = TimeSeriesAligner.buildUniformTimeline(start, end, sampleRateHz);
		result.setTimelineSampleCount(timeline.size());
		if (timeline.isEmpty()) {
			result.setInsufficientDataReason("empty-timeline");
			return result;
		}

		compareChannel("altitude", referenceTime, reference.getAltitudeMetersAgl(),
				candidateTime, candidate.getAltitudeMetersAgl(), timeline, mode, result);
		compareChannel("velocityZ", referenceTime, reference.getVelocityZMetersPerSec(),
				candidateTime, candidate.getVelocityZMetersPerSec(), timeline, mode, result);
		compareChannel("accelX", referenceTime, reference.getAccelerationXMetersPerSec2(),
				candidateTime, candidate.getAccelerationXMetersPerSec2(), timeline, mode, result);
		compareChannel("accelY", referenceTime, reference.getAccelerationYMetersPerSec2(),
				candidateTime, candidate.getAccelerationYMetersPerSec2(), timeline, mode, result);
		compareChannel("accelZ", referenceTime, reference.getAccelerationZMetersPerSec2(),
				candidateTime, candidate.getAccelerationZMetersPerSec2(), timeline, mode, result);
		compareChannel("pressure", referenceTime, reference.getPressurePa(),
				candidateTime, candidate.getPressurePa(), timeline, mode, result);
		compareChannel("temperature", referenceTime, reference.getTemperatureC(),
				candidateTime, candidate.getTemperatureC(), timeline, mode, result);
		List<Double> referenceDensity = deriveDensity(reference.getPressurePa(), reference.getTemperatureC());
		List<Double> candidateDensity = deriveDensity(candidate.getPressurePa(), candidate.getTemperatureC());
		compareChannel("density", referenceTime, referenceDensity,
				candidateTime, candidateDensity, timeline, mode, result);

		if (result.getChannelMetrics().isEmpty()) {
			result.setInsufficientDataReason("no-matched-channel-samples");
		}

		return result;
	}

	private static void compareChannel(String name,
							   List<Double> referenceTime,
							   List<Double> referenceValues,
							   List<Double> candidateTime,
							   List<Double> candidateValues,
							   List<Double> timeline,
							   InterpolationMode mode,
							   TelemetryComparisonResult out) {
		if (!anyNonNull(referenceValues) || !anyNonNull(candidateValues)) {
			return;
		}

		List<Double> refResampled = TimeSeriesAligner.resample(referenceTime, referenceValues, timeline, mode);
		List<Double> candResampled = TimeSeriesAligner.resample(candidateTime, candidateValues, timeline, mode);

		double min = Double.POSITIVE_INFINITY;
		double max = Double.NEGATIVE_INFINITY;
		int matched = 0;
		for (int i = 0; i < timeline.size(); i++) {
			Double ref = refResampled.get(i);
			Double cand = candResampled.get(i);
			if (ref == null || cand == null) {
				continue;
			}
			min = Math.min(min, ref);
			max = Math.max(max, ref);
			matched++;
		}

		if (matched == 0) {
			return;
		}

		double range = Math.max(max - min, EPSILON);
		double denominatorFloor = Math.max(range * 0.05, EPSILON);

		int n = 0;
		double mapeSum = 0;
		double smapeSum = 0;
		double mseSum = 0;

		for (int i = 0; i < timeline.size(); i++) {
			Double ref = refResampled.get(i);
			Double cand = candResampled.get(i);
			if (ref == null || cand == null) {
				continue;
			}
			double err = cand - ref;
			double absErr = Math.abs(err);
			mapeSum += absErr / Math.max(Math.abs(ref), denominatorFloor);
			smapeSum += 2.0 * absErr / Math.max(Math.abs(ref) + Math.abs(cand), 2.0 * denominatorFloor);
			mseSum += err * err;
			n++;
		}

		if (n == 0) {
			return;
		}

		double mape = (mapeSum / n) * 100.0;
		double smape = (smapeSum / n) * 100.0;
		double rmse = Math.sqrt(mseSum / n);
		double nrmse = (rmse / range) * 100.0;
		double coverage = timeline.isEmpty() ? 0.0 : (double) n / (double) timeline.size();
		out.put(name, new ChannelMetrics(n, coverage, mape, smape, nrmse));
	}

	private static boolean anyNonNull(List<Double> values) {
		if (values == null) {
			return false;
		}
		for (Double value : values) {
			if (value != null) {
				return true;
			}
		}
		return false;
	}

	private static double timeAtIndex(TelemetrySeries s, int index) {
		if (index < 0 || index >= s.getTimeSec().size()) {
			return Double.NaN;
		}
		Double t = s.getTimeSec().get(index);
		return t == null ? Double.NaN : t;
	}

	private static double maxFiniteTime(List<Double> time) {
		double max = Double.NaN;
		for (Double t : time) {
			if (t == null || !Double.isFinite(t)) {
				continue;
			}
			if (Double.isNaN(max) || t > max) {
				max = t;
			}
		}
		return max;
	}

	private static List<Double> shiftTimes(List<Double> times, double offsetSec) {
		List<Double> shifted = new java.util.ArrayList<>(times.size());
		for (Double t : times) {
			shifted.add(t == null ? null : t - offsetSec);
		}
		return shifted;
	}

	private static List<Double> deriveDensity(List<Double> pressurePa, List<Double> temperatureC) {
		int size = Math.max(pressurePa == null ? 0 : pressurePa.size(), temperatureC == null ? 0 : temperatureC.size());
		List<Double> density = new java.util.ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			Double pressure = valueAt(pressurePa, i);
			Double temperature = valueAt(temperatureC, i);
			if (pressure == null || temperature == null || !Double.isFinite(pressure) || !Double.isFinite(temperature)
					|| pressure <= 0.0) {
				density.add(null);
				continue;
			}
			double tempKelvin = temperature + 273.15;
			if (!Double.isFinite(tempKelvin) || tempKelvin <= 0.0) {
				density.add(null);
				continue;
			}
			density.add(pressure / (DRY_AIR_GAS_CONSTANT * tempKelvin));
		}
		return density;
	}

	private static Double valueAt(List<Double> values, int index) {
		if (values == null || index < 0 || index >= values.size()) {
			return null;
		}
		return values.get(index);
	}
}

package info.openrocket.core.tuning;

import java.util.ArrayList;
import java.util.List;

public final class TimeSeriesAligner {
	private static final int MIN_ALIGNMENT_SAMPLES = 20;
	private static final double MAX_ALIGNMENT_WINDOW_SEC = 5.0;

	private TimeSeriesAligner() {
	}

	public static List<Double> buildUniformTimeline(double startSec, double endSec, double sampleRateHz) {
		List<Double> timeline = new ArrayList<>();
		if (sampleRateHz <= 0 || endSec < startSec) {
			return timeline;
		}
		double step = 1.0 / sampleRateHz;
		double t = startSec;
		while (t <= endSec + 1e-12) {
			timeline.add(t);
			t += step;
		}
		return timeline;
	}

	public static List<Double> resample(List<Double> sourceTime, List<Double> sourceValues,
										List<Double> targetTime,
										InterpolationMode mode) {
		List<Double> out = new ArrayList<>(targetTime.size());
		for (Double t : targetTime) {
			if (t == null) {
				out.add(null);
				continue;
			}
			if (mode == InterpolationMode.CUBIC_HERMITE) {
				out.add(sampleCubicHermite(sourceTime, sourceValues, t));
			} else {
				out.add(sampleLinear(sourceTime, sourceValues, t));
			}
		}
		return out;
	}

	public static AlignmentResult estimateAlignment(TelemetrySeries reference,
													 TelemetrySeries candidate,
													 double sampleRateHz) {
		double referenceT0 = timeAtIndex(reference.getTimeSec(), reference.estimateLaunchAnchorIndex());
		double candidateT0 = timeAtIndex(candidate.getTimeSec(), candidate.estimateLaunchAnchorIndex());
		if (!Double.isFinite(referenceT0) || !Double.isFinite(candidateT0) || sampleRateHz <= 0.0) {
			return AlignmentResult.NONE;
		}

		List<Double> referenceTime = shiftTimes(reference.getTimeSec(), referenceT0);
		List<Double> candidateTime = shiftTimes(candidate.getTimeSec(), candidateT0);
		double overlapEnd = Math.min(maxFinite(referenceTime), maxFinite(candidateTime));
		if (!Double.isFinite(overlapEnd) || overlapEnd <= 0.0) {
			return AlignmentResult.NONE;
		}

		for (String channel : List.of("altitude", "velocityZ", "pressure")) {
			List<Double> referenceValues = valuesForChannel(reference, channel);
			List<Double> candidateValues = valuesForChannel(candidate, channel);
			if (referenceValues == null || candidateValues == null) {
				continue;
			}

			List<Double> timeline = buildUniformTimeline(0.0, overlapEnd, sampleRateHz);
			if (timeline.size() < MIN_ALIGNMENT_SAMPLES) {
				continue;
			}
			List<Double> referenceResampled = resample(referenceTime, referenceValues, timeline, InterpolationMode.LINEAR);
			List<Double> candidateResampled = resample(candidateTime, candidateValues, timeline, InterpolationMode.LINEAR);
			AlignmentResult result = estimateLagForChannel(channel, referenceResampled, candidateResampled, sampleRateHz);
			if (result.getMatchedSamples() >= MIN_ALIGNMENT_SAMPLES && Double.isFinite(result.getQuality())) {
				return result;
			}
		}

		return AlignmentResult.NONE;
	}

	public static List<Double> shiftTimes(List<Double> times, double offsetSec) {
		List<Double> shifted = new ArrayList<>(times.size());
		for (Double time : times) {
			shifted.add(time == null ? null : time - offsetSec);
		}
		return shifted;
	}

	private static AlignmentResult estimateLagForChannel(String channel,
														 List<Double> reference,
														 List<Double> candidate,
														 double sampleRateHz) {
		int maxLagSamples = Math.max(1, (int) Math.round(MAX_ALIGNMENT_WINDOW_SEC * sampleRateHz));
		double bestCorrelation = Double.NEGATIVE_INFINITY;
		int bestLag = 0;
		int bestPairs = 0;
		for (int lag = -maxLagSamples; lag <= maxLagSamples; lag++) {
			Correlation correlation = correlation(reference, candidate, lag);
			if (correlation.pairs < MIN_ALIGNMENT_SAMPLES) {
				continue;
			}
			if (correlation.value > bestCorrelation) {
				bestCorrelation = correlation.value;
				bestLag = lag;
				bestPairs = correlation.pairs;
			}
		}
		if (!Double.isFinite(bestCorrelation)) {
			return AlignmentResult.NONE;
		}
		double lagSec = -(bestLag / sampleRateHz);
		return new AlignmentResult(channel, lagSec, bestCorrelation, bestPairs);
	}

	private static Correlation correlation(List<Double> reference, List<Double> candidate, int lag) {
		double referenceSum = 0.0;
		double candidateSum = 0.0;
		int pairs = 0;
		for (int i = 0; i < reference.size(); i++) {
			int candidateIndex = i + lag;
			if (candidateIndex < 0 || candidateIndex >= candidate.size()) {
				continue;
			}
			Double referenceValue = reference.get(i);
			Double candidateValue = candidate.get(candidateIndex);
			if (referenceValue == null || candidateValue == null) {
				continue;
			}
			referenceSum += referenceValue;
			candidateSum += candidateValue;
			pairs++;
		}
		if (pairs < MIN_ALIGNMENT_SAMPLES) {
			return new Correlation(Double.NaN, pairs);
		}
		double referenceMean = referenceSum / pairs;
		double candidateMean = candidateSum / pairs;
		double numerator = 0.0;
		double referenceDenominator = 0.0;
		double candidateDenominator = 0.0;
		for (int i = 0; i < reference.size(); i++) {
			int candidateIndex = i + lag;
			if (candidateIndex < 0 || candidateIndex >= candidate.size()) {
				continue;
			}
			Double referenceValue = reference.get(i);
			Double candidateValue = candidate.get(candidateIndex);
			if (referenceValue == null || candidateValue == null) {
				continue;
			}
			double referenceCentered = referenceValue - referenceMean;
			double candidateCentered = candidateValue - candidateMean;
			numerator += referenceCentered * candidateCentered;
			referenceDenominator += referenceCentered * referenceCentered;
			candidateDenominator += candidateCentered * candidateCentered;
		}
		double denominator = Math.sqrt(referenceDenominator * candidateDenominator);
		if (denominator <= 0.0 || !Double.isFinite(denominator)) {
			return new Correlation(Double.NaN, pairs);
		}
		return new Correlation(numerator / denominator, pairs);
	}

	private static double timeAtIndex(List<Double> times, int index) {
		if (times == null || index < 0 || index >= times.size()) {
			return Double.NaN;
		}
		Double value = times.get(index);
		return value == null ? Double.NaN : value;
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

	private static List<Double> valuesForChannel(TelemetrySeries series, String channel) {
		switch (channel) {
			case "altitude":
				return series.getAltitudeMetersAgl();
			case "velocityZ":
				return series.getVelocityZMetersPerSec();
			case "pressure":
				return series.getPressurePa();
			default:
				return null;
		}
	}

	private static Double sampleLinear(List<Double> x, List<Double> y, double t) {
		int i = findLeftIndex(x, t);
		if (i < 0 || i + 1 >= x.size()) {
			return null;
		}
		Double x0 = x.get(i);
		Double x1 = x.get(i + 1);
		Double y0 = y.get(i);
		Double y1 = y.get(i + 1);
		if (x0 == null || x1 == null || y0 == null || y1 == null) {
			return null;
		}
		double dx = x1 - x0;
		if (dx == 0) {
			return y0;
		}
		double u = (t - x0) / dx;
		return y0 + u * (y1 - y0);
	}

	private static Double sampleCubicHermite(List<Double> x, List<Double> y, double t) {
		int i = findLeftIndex(x, t);
		if (i < 0 || i + 1 >= x.size()) {
			return null;
		}
		Double x0 = x.get(i);
		Double x1 = x.get(i + 1);
		Double y0 = y.get(i);
		Double y1 = y.get(i + 1);
		if (x0 == null || x1 == null || y0 == null || y1 == null) {
			return null;
		}
		double h = x1 - x0;
		if (h == 0) {
			return y0;
		}
		double u = (t - x0) / h;

		double m0 = estimateSlope(x, y, i);
		double m1 = estimateSlope(x, y, i + 1);
		if (Double.isNaN(m0) || Double.isNaN(m1)) {
			return sampleLinear(x, y, t);
		}

		double h00 = 2 * u * u * u - 3 * u * u + 1;
		double h10 = u * u * u - 2 * u * u + u;
		double h01 = -2 * u * u * u + 3 * u * u;
		double h11 = u * u * u - u * u;
		return h00 * y0 + h10 * h * m0 + h01 * y1 + h11 * h * m1;
	}

	private static double estimateSlope(List<Double> x, List<Double> y, int i) {
		int prev = i - 1;
		int next = i + 1;
		if (prev < 0 || next >= x.size()) {
			return estimateBoundarySlope(x, y, i);
		}
		Double xp = x.get(prev);
		Double xn = x.get(next);
		Double yp = y.get(prev);
		Double yn = y.get(next);
		if (xp == null || xn == null || yp == null || yn == null || xn.equals(xp)) {
			return estimateBoundarySlope(x, y, i);
		}
		return (yn - yp) / (xn - xp);
	}

	private static double estimateBoundarySlope(List<Double> x, List<Double> y, int i) {
		if (i + 1 < x.size() && x.get(i) != null && x.get(i + 1) != null && y.get(i) != null && y.get(i + 1) != null) {
			double dx = x.get(i + 1) - x.get(i);
			if (dx != 0) {
				return (y.get(i + 1) - y.get(i)) / dx;
			}
		}
		if (i - 1 >= 0 && x.get(i - 1) != null && x.get(i) != null && y.get(i - 1) != null && y.get(i) != null) {
			double dx = x.get(i) - x.get(i - 1);
			if (dx != 0) {
				return (y.get(i) - y.get(i - 1)) / dx;
			}
		}
		return Double.NaN;
	}

	private static int findLeftIndex(List<Double> x, double t) {
		if (x == null || x.size() < 2) {
			return -1;
		}
		if (x.get(0) == null || x.get(x.size() - 1) == null) {
			return -1;
		}
		if (t < x.get(0) || t > x.get(x.size() - 1)) {
			return -1;
		}
		int lo = 0;
		int hi = x.size() - 1;
		while (lo + 1 < hi) {
			int mid = (lo + hi) >>> 1;
			Double xm = x.get(mid);
			if (xm == null) {
				return -1;
			}
			if (xm <= t) {
				lo = mid;
			} else {
				hi = mid;
			}
		}
		return lo;
	}

	private record Correlation(double value, int pairs) {
	}
}

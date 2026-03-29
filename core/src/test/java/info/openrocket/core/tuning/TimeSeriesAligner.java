package info.openrocket.core.tuning;

import java.util.ArrayList;
import java.util.List;

public final class TimeSeriesAligner {
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
}

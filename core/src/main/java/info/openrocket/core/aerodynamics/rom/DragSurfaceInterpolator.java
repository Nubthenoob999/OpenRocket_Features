package info.openrocket.core.aerodynamics.rom;

public class DragSurfaceInterpolator {

	private static final double MIN_CD = 0.001;

	private final DragSurface surface;
	private final PchipInterpolator1D[][] slicesOff;
	private final PchipInterpolator1D[][] slicesOn;

	public static final class QueryResult {
		public final double cdPlumeOff;
		public final double cdPlumeOn;
		public final double usedMach;
		public final double usedLogRe;
		public final double usedAlphaDeg;
		public final boolean machClamped;
		public final boolean reynoldsClamped;
		public final boolean alphaClamped;

		QueryResult(double cdPlumeOff, double cdPlumeOn,
				double usedMach, double usedLogRe, double usedAlphaDeg,
				boolean machClamped, boolean reynoldsClamped, boolean alphaClamped) {
			this.cdPlumeOff = cdPlumeOff;
			this.cdPlumeOn = cdPlumeOn;
			this.usedMach = usedMach;
			this.usedLogRe = usedLogRe;
			this.usedAlphaDeg = usedAlphaDeg;
			this.machClamped = machClamped;
			this.reynoldsClamped = reynoldsClamped;
			this.alphaClamped = alphaClamped;
		}
	}

	public DragSurfaceInterpolator(DragSurface surface) {
		this.surface = surface;
		int nM = surface.machAxis.length;
		int nR = surface.logReAxis.length;
		int nA = surface.alphaAxis.length;

		slicesOff = new PchipInterpolator1D[nR][nA];
		slicesOn = new PchipInterpolator1D[nR][nA];

		for (int ir = 0; ir < nR; ir++) {
			for (int ia = 0; ia < nA; ia++) {
				double[] off = new double[nM];
				double[] on = new double[nM];
				for (int im = 0; im < nM; im++) {
					off[im] = surface.cdPlumeOff[im][ir][ia];
					on[im] = surface.cdPlumeOn[im][ir][ia];
				}
				slicesOff[ir][ia] = new PchipInterpolator1D(surface.machAxis, off);
				slicesOn[ir][ia] = new PchipInterpolator1D(surface.machAxis, on);
			}
		}
	}

	public QueryResult query(double mach, double reL, double alphaDeg) {
		AxisSample machSample = clampAxis(mach, surface.machAxis);
		AxisSample reSample = clampLogRe(reL, surface.logReAxis);
		AxisSample alphaSample = clampAxis(Math.abs(alphaDeg), surface.alphaAxis);
		double cdOff = interpolate(machSample.value, reSample.value, alphaSample.value, surface.cdPlumeOff, slicesOff);
		double cdOn = interpolate(machSample.value, reSample.value, alphaSample.value, surface.cdPlumeOn, slicesOn);
		return new QueryResult(cdOff, cdOn,
				machSample.value, reSample.value, alphaSample.value,
				machSample.clamped, reSample.clamped, alphaSample.clamped);
	}

	public double queryCdPlumeOff(double mach, double reL, double alphaDeg) {
		return query(mach, reL, alphaDeg).cdPlumeOff;
	}

	public double queryCdPlumeOn(double mach, double reL, double alphaDeg) {
		return query(mach, reL, alphaDeg).cdPlumeOn;
	}

	private double interpolate(double mach, double logRe, double alphaDeg,
			double[][][] grid, PchipInterpolator1D[][] slices) {
		int imExact = exactIndex(surface.machAxis, mach);
		int irExact = exactIndex(surface.logReAxis, logRe);
		int iaExact = exactIndex(surface.alphaAxis, alphaDeg);
		if (imExact >= 0 && irExact >= 0 && iaExact >= 0) {
			return Math.max(MIN_CD, grid[imExact][irExact][iaExact]);
		}

		int nR = surface.logReAxis.length;
		int nA = surface.alphaAxis.length;
		double[] alphaSamples = new double[nA];

		for (int ia = 0; ia < nA; ia++) {
			double[] reSamples = new double[nR];
			for (int ir = 0; ir < nR; ir++) {
				reSamples[ir] = slices[ir][ia].evaluate(mach);
			}
			alphaSamples[ia] = new PchipInterpolator1D(surface.logReAxis, reSamples).evaluate(logRe);
		}

		double cd = new PchipInterpolator1D(surface.alphaAxis, alphaSamples).evaluate(alphaDeg);
		if (!Double.isFinite(cd)) {
			return MIN_CD;
		}
		return Math.max(MIN_CD, cd);
	}

	private static AxisSample clampAxis(double value, double[] axis) {
		if (!Double.isFinite(value)) {
			return new AxisSample(axis[0], true);
		}
		double clamped = Math.max(axis[0], Math.min(axis[axis.length - 1], value));
		return new AxisSample(clamped, Double.compare(clamped, value) != 0);
	}

	private static AxisSample clampLogRe(double reL, double[] logReAxis) {
		if (!Double.isFinite(reL)) {
			return new AxisSample(logReAxis[0], true);
		}
		double safeRe = Math.max(reL, 1e4);
		double logRe = Math.log10(safeRe);
		AxisSample sample = clampAxis(logRe, logReAxis);
		return new AxisSample(sample.value, sample.clamped || Double.compare(safeRe, reL) != 0);
	}

	private static int exactIndex(double[] axis, double value) {
		double scale = Math.max(1.0, Math.abs(value));
		double eps = 1e-11 * scale;
		for (int i = 0; i < axis.length; i++) {
			if (Math.abs(axis[i] - value) <= eps) {
				return i;
			}
		}
		return -1;
	}

	private record AxisSample(double value, boolean clamped) {
	}
}

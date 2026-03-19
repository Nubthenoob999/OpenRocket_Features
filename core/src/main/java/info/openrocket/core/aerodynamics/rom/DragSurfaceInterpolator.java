package info.openrocket.core.aerodynamics.rom;

public class DragSurfaceInterpolator {

	private final DragSurface surface;
	private final PchipInterpolator1D[][] slicesOff;
	private final PchipInterpolator1D[][] slicesOn;

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

	public double queryCdPlumeOff(double mach, double re_L, double alphaDeg) {
		return query(mach, re_L, alphaDeg, surface.cdPlumeOff, slicesOff);
	}

	public double queryCdPlumeOn(double mach, double re_L, double alphaDeg) {
		return query(mach, re_L, alphaDeg, surface.cdPlumeOn, slicesOn);
	}

	private double query(double mach, double re_L, double alphaDeg,
						 double[][][] grid,
						 PchipInterpolator1D[][] slices) {
		mach = Math.max(surface.machAxis[0], Math.min(surface.machAxis[surface.machAxis.length - 1], mach));
		double logRe = Math.log10(Math.max(re_L, 1e4));
		alphaDeg = Math.abs(alphaDeg);
		alphaDeg = Math.max(surface.alphaAxis[0], Math.min(surface.alphaAxis[surface.alphaAxis.length - 1], alphaDeg));
		logRe = Math.max(surface.logReAxis[0], Math.min(surface.logReAxis[surface.logReAxis.length - 1], logRe));

		int imExact = exactIndex(surface.machAxis, mach);
		int irExact = exactIndex(surface.logReAxis, logRe);
		int iaExact = exactIndex(surface.alphaAxis, alphaDeg);
		if (imExact >= 0 && irExact >= 0 && iaExact >= 0) {
			return Math.max(0.001, grid[imExact][irExact][iaExact]);
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
			return 0.001;
		}
		return Math.max(0.001, cd);
	}

	private static int binarySearchFloor(double[] axis, double value) {
		int lo = 0;
		int hi = axis.length - 2;
		if (value <= axis[0]) {
			return 0;
		}
		if (value >= axis[axis.length - 1]) {
			return axis.length - 2;
		}
		while (lo < hi) {
			int mid = (lo + hi + 1) >>> 1;
			if (axis[mid] <= value) {
				lo = mid;
			} else {
				hi = mid - 1;
			}
		}
		return lo;
	}

	private static double fractional(double[] axis, int i, double value) {
		double denom = axis[i + 1] - axis[i];
		if (denom < 1e-14) {
			return 0.0;
		}
		double t = (value - axis[i]) / denom;
		if (Math.abs(t) < 1e-12) {
			return 0.0;
		}
		if (Math.abs(1.0 - t) < 1e-12) {
			return 1.0;
		}
		return Math.max(0.0, Math.min(1.0, t));
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
}

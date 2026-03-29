package info.openrocket.core.aerodynamics.rom.core.sampling;

import info.openrocket.core.aerodynamics.rom.core.basis.PodBasis;

public final class MagicPointSelector {

	private MagicPointSelector() {
	}

	public static int[] selectMagicPoints(PodBasis basis, int nExtra) {
		int q = basis.rank;
		int n = basis.snapshotLength;
		if (q == 0 || n == 0) {
			return new int[0];
		}
		int l = Math.min(q + Math.max(0, nExtra), n);
		int[] points = new int[l];
		double[][] b = new double[l][q];
		boolean[] used = new boolean[n];

		double maxVal = -1.0;
		int p1 = 0;
		for (int i = 0; i < n; i++) {
			double abs = Math.abs(basis.modes[0][i]);
			if (abs > maxVal) {
				maxVal = abs;
				p1 = i;
			}
		}
		points[0] = p1;
		used[p1] = true;
		for (int j = 0; j < q; j++) {
			b[0][j] = basis.modes[j][p1];
		}

		for (int step = 1; step < l; step++) {
			int modeIdx = Math.min(step, q - 1);
			double[] hq = basis.modes[modeIdx];
			double[] fAtPoints = new double[step];
			for (int k = 0; k < step; k++) {
				fAtPoints[k] = hq[points[k]];
			}
			double[] g = solveLeastSquaresPrefix(b, fAtPoints, step, q);

			double maxResidual = -1.0;
			int next = -1;
			for (int i = 0; i < n; i++) {
				if (used[i]) {
					continue;
				}
				double r = hq[i];
				for (int j = 0; j < g.length; j++) {
					r -= g[j] * basis.modes[j][i];
				}
				double abs = Math.abs(r);
				if (abs > maxResidual) {
					maxResidual = abs;
					next = i;
				}
			}
			if (next < 0) {
				return trim(points, step);
			}
			points[step] = next;
			used[next] = true;
			for (int j = 0; j < q; j++) {
				b[step][j] = basis.modes[j][next];
			}
		}

		return points;
	}

	private static int[] trim(int[] points, int size) {
		int[] out = new int[size];
		System.arraycopy(points, 0, out, 0, size);
		return out;
	}

	private static double[] solveLeastSquaresPrefix(double[][] b, double[] rhs, int rows, int cols) {
		double[][] mtm = new double[cols][cols];
		double[] mtb = new double[cols];
		for (int i = 0; i < cols; i++) {
			for (int j = 0; j < cols; j++) {
				double sum = 0.0;
				for (int r = 0; r < rows; r++) {
					sum += b[r][i] * b[r][j];
				}
				mtm[i][j] = sum;
			}
			double sum = 0.0;
			for (int r = 0; r < rows; r++) {
				sum += b[r][i] * rhs[r];
			}
			mtb[i] = sum;
		}
		return solveSymmetric(mtm, mtb);
	}

	private static double[] solveSymmetric(double[][] a, double[] b) {
		int n = a.length;
		double[][] l = new double[n][n];
		for (int i = 0; i < n; i++) {
			for (int j = 0; j <= i; j++) {
				double sum = a[i][j];
				for (int k = 0; k < j; k++) {
					sum -= l[i][k] * l[j][k];
				}
				if (i == j) {
					l[i][j] = Math.sqrt(Math.max(sum, 1e-30));
				} else {
					l[i][j] = (l[j][j] > 1e-15) ? sum / l[j][j] : 0.0;
				}
			}
		}
		double[] y = new double[n];
		for (int i = 0; i < n; i++) {
			y[i] = b[i];
			for (int k = 0; k < i; k++) {
				y[i] -= l[i][k] * y[k];
			}
			y[i] = (l[i][i] > 1e-15) ? y[i] / l[i][i] : 0.0;
		}
		double[] x = new double[n];
		for (int i = n - 1; i >= 0; i--) {
			x[i] = y[i];
			for (int k = i + 1; k < n; k++) {
				x[i] -= l[k][i] * x[k];
			}
			x[i] = (l[i][i] > 1e-15) ? x[i] / l[i][i] : 0.0;
		}
		return x;
	}
}

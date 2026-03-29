package info.openrocket.core.aerodynamics.rom.core.basis;

import java.util.List;

/**
 * POD basis built via method-of-snapshots eigendecomposition.
 */
public final class PodBasis {

	public final double[][] modes;
	public final double[] mean;
	public final double[] singularValues;
	public final double relativeEnergy;
	public final int rank;
	public final int snapshotLength;
	public final double[] machAxis;
	public final double[] logReAxis;
	public final double[] alphaAxis;
	public final double[] betaAxis;

	public PodBasis(List<GeometrySnapshot> snapshots, double tolerance, boolean useCdOff) {
		if (snapshots == null || snapshots.isEmpty()) {
			throw new IllegalArgumentException("At least one snapshot is required");
		}
		this.machAxis = snapshots.get(0).machAxis;
		this.logReAxis = snapshots.get(0).logReAxis;
		this.alphaAxis = snapshots.get(0).alphaAxis;
		this.betaAxis = snapshots.get(0).betaAxis;

		int kCount = snapshots.size();
		int n = useCdOff ? snapshots.get(0).cdOffFlat.length : snapshots.get(0).cdOnFlat.length;
		this.snapshotLength = n;

		this.mean = new double[n];
		for (GeometrySnapshot s : snapshots) {
			double[] flat = useCdOff ? s.cdOffFlat : s.cdOnFlat;
			for (int i = 0; i < n; i++) {
				mean[i] += flat[i];
			}
		}
		for (int i = 0; i < n; i++) {
			mean[i] /= kCount;
		}

		double[][] centered = new double[kCount][n];
		for (int k = 0; k < kCount; k++) {
			double[] flat = useCdOff ? snapshots.get(k).cdOffFlat : snapshots.get(k).cdOnFlat;
			for (int i = 0; i < n; i++) {
				centered[k][i] = flat[i] - mean[i];
			}
		}

		double[][] c = new double[kCount][kCount];
		for (int j = 0; j < kCount; j++) {
			for (int l = j; l < kCount; l++) {
				double dot = 0.0;
				for (int i = 0; i < n; i++) {
					dot += centered[j][i] * centered[l][i];
				}
				dot /= Math.max(n, 1);
				c[j][l] = dot;
				c[l][j] = dot;
			}
		}

		double[][] eigVecs = new double[kCount][kCount];
		double[] eigVals = new double[kCount];
		jacobiEigen(c, eigVecs, eigVals);
		sortEigenDescending(eigVals, eigVecs);

		double totalEnergy = 0.0;
		for (double ev : eigVals) {
			totalEnergy += Math.max(ev, 0.0);
		}
		if (totalEnergy <= 1e-15) {
			this.rank = 1;
			this.relativeEnergy = 1.0;
			double[][] zeroMode = new double[1][n];
			double[] sv = new double[] { 0.0 };
			this.modes = zeroMode;
			this.singularValues = sv;
			return;
		}
		double cumulative = 0.0;
		int qKeep = 0;
		for (int q = 0; q < kCount; q++) {
			cumulative += Math.max(eigVals[q], 0.0);
			qKeep = q + 1;
			if ((cumulative / totalEnergy) >= (1.0 - tolerance)) {
				break;
			}
		}
		this.rank = qKeep;
		this.relativeEnergy = (totalEnergy > 1e-15) ? cumulative / totalEnergy : 1.0;

		double[][] modesTmp = new double[qKeep][n];
		double[] svTmp = new double[qKeep];
		for (int q = 0; q < qKeep; q++) {
			for (int k = 0; k < kCount; k++) {
				double coeff = eigVecs[k][q];
				if (Math.abs(coeff) < 1e-15) {
					continue;
				}
				for (int i = 0; i < n; i++) {
					modesTmp[q][i] += coeff * centered[k][i];
				}
			}
			double norm = 0.0;
			for (int i = 0; i < n; i++) {
				norm += modesTmp[q][i] * modesTmp[q][i];
			}
			norm = Math.sqrt(Math.max(norm, 1e-30));
			for (int i = 0; i < n; i++) {
				modesTmp[q][i] /= norm;
			}
			svTmp[q] = Math.sqrt(Math.max(eigVals[q], 0.0));
		}
		this.modes = modesTmp;
		this.singularValues = svTmp;
	}

	public PodBasis(double[][] modes, double[] mean, double[] singularValues,
			double[] machAxis, double[] logReAxis, double[] alphaAxis, double[] betaAxis,
			double relativeEnergy) {
		this.modes = modes;
		this.mean = mean;
		this.singularValues = singularValues;
		this.machAxis = machAxis;
		this.logReAxis = logReAxis;
		this.alphaAxis = alphaAxis;
		this.betaAxis = betaAxis;
		this.rank = modes.length;
		this.snapshotLength = mean.length;
		this.relativeEnergy = relativeEnergy;
	}

	public double[] project(double[] snapshot) {
		if (snapshot.length != snapshotLength) {
			throw new IllegalArgumentException("Snapshot length mismatch: expected " + snapshotLength
					+ ", got " + snapshot.length);
		}
		double[] coeffs = new double[rank];
		for (int q = 0; q < rank; q++) {
			double dot = 0.0;
			for (int i = 0; i < snapshotLength; i++) {
				dot += (snapshot[i] - mean[i]) * modes[q][i];
			}
			coeffs[q] = dot;
		}
		return coeffs;
	}

	public double[] reconstruct(double[] coeffs) {
		if (coeffs.length != rank) {
			throw new IllegalArgumentException("Coefficient length mismatch: expected " + rank
					+ ", got " + coeffs.length);
		}
		double[] result = new double[snapshotLength];
		for (int i = 0; i < snapshotLength; i++) {
			result[i] = mean[i];
		}
		for (int q = 0; q < rank; q++) {
			if (Math.abs(coeffs[q]) < 1e-15) {
				continue;
			}
			for (int i = 0; i < snapshotLength; i++) {
				result[i] += coeffs[q] * modes[q][i];
			}
		}
		return result;
	}

	public double reconstructionError(double[] snapshot) {
		double[] coeffs = project(snapshot);
		double[] recon = reconstruct(coeffs);
		double errNorm = 0.0;
		double snapNorm = 0.0;
		for (int i = 0; i < snapshotLength; i++) {
			double d = recon[i] - snapshot[i];
			errNorm += d * d;
			snapNorm += snapshot[i] * snapshot[i];
		}
		return (snapNorm > 1e-15) ? Math.sqrt(errNorm / snapNorm) : 0.0;
	}

	private static void jacobiEigen(double[][] aIn, double[][] v, double[] d) {
		int n = aIn.length;
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				v[i][j] = (i == j) ? 1.0 : 0.0;
			}
		}
		double[][] a = new double[n][n];
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				a[i][j] = aIn[i][j];
			}
		}

		final int maxIter = 200 * n * n;
		for (int iter = 0; iter < maxIter; iter++) {
			int p = 0;
			int q = 1;
			double maxOff = (n > 1) ? Math.abs(a[0][1]) : 0.0;
			for (int i = 0; i < n - 1; i++) {
				for (int j = i + 1; j < n; j++) {
					double off = Math.abs(a[i][j]);
					if (off > maxOff) {
						maxOff = off;
						p = i;
						q = j;
					}
				}
			}
			if (maxOff < 1e-14) {
				break;
			}

			double theta = (a[q][q] - a[p][p]) / (2.0 * a[p][q]);
			double t = (theta >= 0.0)
					? 1.0 / (theta + Math.sqrt(1.0 + theta * theta))
					: 1.0 / (theta - Math.sqrt(1.0 + theta * theta));
			double c = 1.0 / Math.sqrt(1.0 + t * t);
			double s = t * c;
			double tau = s / (1.0 + c);

			double apq = a[p][q];
			a[p][q] = 0.0;
			a[q][p] = 0.0;
			a[p][p] -= t * apq;
			a[q][q] += t * apq;
			for (int r = 0; r < n; r++) {
				if (r != p && r != q) {
					double apr = a[p][r];
					double aqr = a[q][r];
					a[p][r] = apr - s * (aqr + tau * apr);
					a[q][r] = aqr + s * (apr - tau * aqr);
					a[r][p] = a[p][r];
					a[r][q] = a[q][r];
				}
			}
			for (int r = 0; r < n; r++) {
				double vrp = v[r][p];
				double vrq = v[r][q];
				v[r][p] = vrp - s * (vrq + tau * vrp);
				v[r][q] = vrq + s * (vrp - tau * vrq);
			}
		}

		for (int i = 0; i < n; i++) {
			d[i] = a[i][i];
		}
	}

	private static void sortEigenDescending(double[] vals, double[][] vecs) {
		int n = vals.length;
		for (int i = 0; i < n - 1; i++) {
			int maxIdx = i;
			for (int j = i + 1; j < n; j++) {
				if (vals[j] > vals[maxIdx]) {
					maxIdx = j;
				}
			}
			if (maxIdx != i) {
				double tmp = vals[i];
				vals[i] = vals[maxIdx];
				vals[maxIdx] = tmp;
				for (int k = 0; k < n; k++) {
					tmp = vecs[k][i];
					vecs[k][i] = vecs[k][maxIdx];
					vecs[k][maxIdx] = tmp;
				}
			}
		}
	}
}

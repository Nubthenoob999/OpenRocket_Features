package info.openrocket.core.aerodynamics.rom.core.basis;

import java.util.ArrayList;
import java.util.List;

import info.openrocket.core.aerodynamics.rom.core.eval.AeroGridEvaluator4D;
import info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput;
import info.openrocket.core.aerodynamics.rom.core.physics.NormalForceModel;
import info.openrocket.core.aerodynamics.rom.core.physics.PitchingMomentModel;

/**
 * Online geometry-parametric ROM reconstruction engine.
 */
public final class GeometryParametricRom {

	private final List<LocalPodRegion> regions;
	private final int[][] magicPointsOff;
	private final int[][] magicPointsOn;

	public final double[] machAxis;
	public final double[] logReAxis;
	public final double[] alphaAxis;
	public final double[] betaAxis;

	private long savedEvaluations = 0L;

	public GeometryParametricRom(List<LocalPodRegion> regions,
			double[] machAxis, double[] logReAxis, double[] alphaAxis, double[] betaAxis,
			int[][] magicPointsOff, int[][] magicPointsOn) {
		if (regions == null || regions.isEmpty()) {
			throw new IllegalArgumentException("At least one local region is required");
		}
		this.regions = regions;
		this.machAxis = machAxis;
		this.logReAxis = logReAxis;
		this.alphaAxis = alphaAxis;
		this.betaAxis = betaAxis;
		this.magicPointsOff = magicPointsOff;
		this.magicPointsOn = magicPointsOn;
	}

	public List<LocalPodRegion> getRegions() {
		return regions;
	}

	public int[][] getMagicPointsOff() {
		return magicPointsOff;
	}

	public int[][] getMagicPointsOn() {
		return magicPointsOn;
	}

	public long getSavedEvaluations() {
		return savedEvaluations;
	}

	public info.openrocket.core.aerodynamics.rom.core.surface.AeroSurface4D reconstructSurface(
			RomGeometryInput g,
			String geometryHash) {
		final double minWeight = 1e-6;
		double[] feat = g.toFeatureVector();
		int nRegions = regions.size();

		double[] weights = new double[nRegions];
		double sum = 0.0;
		for (int r = 0; r < nRegions; r++) {
			weights[r] = regions.get(r).weight(feat);
			sum += weights[r];
		}
		if (sum < minWeight) {
			return AeroGridEvaluator4D.evaluate(g, geometryHash, (AeroGridEvaluator4D.ProgressListener) null);
		}
		for (int r = 0; r < nRegions; r++) {
			weights[r] /= sum;
		}

		int nM = machAxis.length;
		int nR = logReAxis.length;
		int nA = alphaAxis.length;
		int nB = betaAxis.length;
		int n = nM * nR * nA * nB;

		double[] blendedOff = new double[n];
		double[] blendedOn = new double[n];

		for (int r = 0; r < nRegions; r++) {
			if (weights[r] < minWeight / nRegions) {
				continue;
			}
			LocalPodRegion region = regions.get(r);
			double[] sparseOff = evaluateAtMagicPoints(g, magicPointsOff[r], nM, nR, nA, nB, false);
			double[] sparseOn = evaluateAtMagicPoints(g, magicPointsOn[r], nM, nR, nA, nB, true);
			savedEvaluations += Math.max(0, (2L * n) - magicPointsOff[r].length - magicPointsOn[r].length);

			double[] reconOff = gappyReconstruct(region.basisOff, sparseOff, magicPointsOff[r]);
			double[] reconOn = gappyReconstruct(region.basisOn, sparseOn, magicPointsOn[r]);
			for (int i = 0; i < n; i++) {
				blendedOff[i] += weights[r] * reconOff[i];
				blendedOn[i] += weights[r] * reconOn[i];
			}
		}

		double[][][][] cdOff = new double[nM][nR][nA][nB];
		double[][][][] cdOn = new double[nM][nR][nA][nB];
		double[][][][] cdBody = new double[nM][nR][nA][nB];
		double[][][][] cnArr = new double[nM][nR][nA][nB];
		double[][][][] cmArr = new double[nM][nR][nA][nB];

		int idx = 0;
		for (int im = 0; im < nM; im++) {
			for (int ir = 0; ir < nR; ir++) {
				for (int ia = 0; ia < nA; ia++) {
					for (int ib = 0; ib < nB; ib++) {
						cdOff[im][ir][ia][ib] = Math.max(0.001, blendedOff[idx]);
						cdOn[im][ir][ia][ib] = Math.max(0.001, blendedOn[idx]);
						cdBody[im][ir][ia][ib] = Math.max(0.001, Math.min(blendedOff[idx], blendedOn[idx]));
						idx++;
					}
				}
			}
		}

		for (int im = 0; im < nM; im++) {
			for (int ir = 0; ir < nR; ir++) {
				for (int ia = 0; ia < nA; ia++) {
					double alphaRad = Math.toRadians(alphaAxis[ia]);
					for (int ib = 0; ib < nB; ib++) {
						double betaRad = Math.toRadians(betaAxis[ib]);
						double cn = NormalForceModel.CN(alphaRad, betaRad, machAxis[im], g);
						double cm = PitchingMomentModel.Cm(cn, alphaRad, g);
						cnArr[im][ir][ia][ib] = cn;
						cmArr[im][ir][ia][ib] = cm;
					}
				}
			}
		}

		String hash = (geometryHash == null || geometryHash.isBlank()) ? "geom-parametric-rom" : geometryHash;
		return new info.openrocket.core.aerodynamics.rom.core.surface.AeroSurface4D(
				machAxis, logReAxis, alphaAxis, betaAxis,
				cdOff, cdOn, cdBody, cnArr, cmArr,
				hash, g.finCount, 0.0);
	}

	private double[] evaluateAtMagicPoints(RomGeometryInput g, int[] magicPoints,
			int nM, int nR, int nA, int nB, boolean plumeOn) {
		double[] sparse = new double[nM * nR * nA * nB];
		for (int flatIdx : magicPoints) {
			int tmp = flatIdx;
			int ib = tmp % nB;
			tmp /= nB;
			int ia = tmp % nA;
			tmp /= nA;
			int ir = tmp % nR;
			tmp /= nR;
			int im = tmp;

			double mach = machAxis[im];
			double reL = Math.pow(10.0, logReAxis[ir]);
			double alphaRad = Math.toRadians(alphaAxis[ia]);
			double betaRad = Math.toRadians(betaAxis[ib]);

			AeroGridEvaluator4D.PointResult pt = AeroGridEvaluator4D.computePoint(mach, reL, alphaRad, betaRad, g);
			sparse[flatIdx] = plumeOn ? pt.cdPlumeOn : pt.cdPlumeOff;
		}
		return sparse;
	}

	private static double[] gappyReconstruct(PodBasis basis, double[] sparse, int[] magicPoints) {
		int q = basis.rank;
		int l = magicPoints.length;
		double[][] m = new double[l][q];
		double[] rhs = new double[l];
		for (int row = 0; row < l; row++) {
			int idx = magicPoints[row];
			rhs[row] = sparse[idx] - basis.mean[idx];
			for (int col = 0; col < q; col++) {
				m[row][col] = basis.modes[col][idx];
			}
		}

		double[][] mtm = new double[q][q];
		double[] mtrhs = new double[q];
		for (int i = 0; i < q; i++) {
			for (int j = 0; j < q; j++) {
				double sum = 0.0;
				for (int row = 0; row < l; row++) {
					sum += m[row][i] * m[row][j];
				}
				mtm[i][j] = sum;
			}
			double sum = 0.0;
			for (int row = 0; row < l; row++) {
				sum += m[row][i] * rhs[row];
			}
			mtrhs[i] = sum;
		}

		double[] coeffs = solveSymmetric(mtm, mtrhs);
		return basis.reconstruct(coeffs);
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

	public info.openrocket.core.aerodynamics.rom.core.surface.AeroSurface4DInterpolator createInterpolator(
			RomGeometryInput g,
			String geometryHash) {
		return new info.openrocket.core.aerodynamics.rom.core.surface.AeroSurface4DInterpolator(
				reconstructSurface(g, geometryHash));
	}

	public static GeometryParametricRom withAutoMagicPoints(List<LocalPodRegion> regions,
			double[] machAxis, double[] logReAxis, double[] alphaAxis, double[] betaAxis) {
		int[][] magicOff = new int[regions.size()][];
		int[][] magicOn = new int[regions.size()][];
		for (int i = 0; i < regions.size(); i++) {
			int nExtraOff = Math.max(1, regions.get(i).basisOff.rank / 2);
			int nExtraOn = Math.max(1, regions.get(i).basisOn.rank / 2);
			magicOff[i] = info.openrocket.core.aerodynamics.rom.core.sampling.MagicPointSelector
					.selectMagicPoints(regions.get(i).basisOff, nExtraOff);
			magicOn[i] = info.openrocket.core.aerodynamics.rom.core.sampling.MagicPointSelector
					.selectMagicPoints(regions.get(i).basisOn, nExtraOn);
		}
		return new GeometryParametricRom(new ArrayList<>(regions), machAxis, logReAxis, alphaAxis, betaAxis, magicOff, magicOn);
	}
}

package info.openrocket.core.aerodynamics.rom.core.surface;

public final class AeroSurface4DInterpolator {

	private final AeroSurface4D surface;

	private final PchipInterpolator1D[][][] slicesCdOff;
	private final PchipInterpolator1D[][][] slicesCdOn;
	private final PchipInterpolator1D[][][] slicesCdBody;
	private final PchipInterpolator1D[][][] slicesCN;
	private final PchipInterpolator1D[][][] slicesCm;

	public AeroSurface4DInterpolator(AeroSurface4D surface) {
		this.surface = surface;
		int nM = surface.machAxis.length;
		int nR = surface.logReAxis.length;
		int nA = surface.alphaAxis.length;
		int nB = surface.betaAxis.length;

		slicesCdOff = buildSlices(surface.cdPlumeOff, nM, nR, nA, nB, surface.machAxis);
		slicesCdOn = buildSlices(surface.cdPlumeOn, nM, nR, nA, nB, surface.machAxis);
		slicesCdBody = buildSlices(surface.cdBody, nM, nR, nA, nB, surface.machAxis);
		slicesCN = buildSlices(surface.CN, nM, nR, nA, nB, surface.machAxis);
		slicesCm = buildSlices(surface.Cm, nM, nR, nA, nB, surface.machAxis);
	}

	public static final class QueryResult {
		public final double cdPlumeOff;
		public final double cdPlumeOn;
		public final double cdBody;
		public final double dCdFin;
		public final double CN;
		public final double Cm;

		QueryResult(double cdOff, double cdOn, double cdBody, double cn, double cm) {
			this.cdPlumeOff = cdOff;
			this.cdPlumeOn = cdOn;
			this.cdBody = cdBody;
			this.dCdFin = cdOff - cdBody;
			this.CN = cn;
			this.Cm = cm;
		}
	}

	public QueryResult query(double mach, double reL, double alphaDeg, double betaDeg) {
		double logRe = Math.log10(Math.max(reL, 1e4));
		double alphaAbs = Math.abs(alphaDeg);
		double betaAbs = Math.abs(betaDeg);
		double betaMax = surface.betaAxis[surface.betaAxis.length - 1];
		double betaClamped = Math.min(betaAbs, betaMax);

		return new QueryResult(
				tensorPchip(slicesCdOff, mach, logRe, alphaAbs, betaClamped),
				tensorPchip(slicesCdOn, mach, logRe, alphaAbs, betaClamped),
				tensorPchip(slicesCdBody, mach, logRe, alphaAbs, betaClamped),
				tensorPchip(slicesCN, mach, logRe, alphaAbs, betaClamped),
				tensorPchip(slicesCm, mach, logRe, alphaAbs, betaClamped));
	}

	public double queryCdPlumeOff(double mach, double reL, double alphaDeg, double betaDeg) {
		return query(mach, reL, alphaDeg, betaDeg).cdPlumeOff;
	}

	public double queryCdPlumeOn(double mach, double reL, double alphaDeg, double betaDeg) {
		return query(mach, reL, alphaDeg, betaDeg).cdPlumeOn;
	}

	private static PchipInterpolator1D[][][] buildSlices(
			double[][][][] grid,
			int nM,
			int nR,
			int nA,
			int nB,
			double[] machAxis) {
		PchipInterpolator1D[][][] s = new PchipInterpolator1D[nR][nA][nB];
		for (int ir = 0; ir < nR; ir++) {
			for (int ia = 0; ia < nA; ia++) {
				for (int ib = 0; ib < nB; ib++) {
					double[] col = new double[nM];
					for (int im = 0; im < nM; im++) {
						col[im] = grid[im][ir][ia][ib];
					}
					s[ir][ia][ib] = new PchipInterpolator1D(machAxis, col);
				}
			}
		}
		return s;
	}

	private double tensorPchip(
			PchipInterpolator1D[][][] slices,
			double mach,
			double logRe,
			double alphaDeg,
			double betaDeg) {

		int nR = surface.logReAxis.length;
		int nA = surface.alphaAxis.length;
		int nB = surface.betaAxis.length;

		double[][][] v = new double[nR][nA][nB];
		for (int ir = 0; ir < nR; ir++) {
			for (int ia = 0; ia < nA; ia++) {
				for (int ib = 0; ib < nB; ib++) {
					v[ir][ia][ib] = slices[ir][ia][ib].evaluate(mach);
				}
			}
		}

		double[][] w = new double[nA][nB];
		double[] reCol = new double[nR];
		for (int ia = 0; ia < nA; ia++) {
			for (int ib = 0; ib < nB; ib++) {
				for (int ir = 0; ir < nR; ir++) {
					reCol[ir] = v[ir][ia][ib];
				}
				w[ia][ib] = new PchipInterpolator1D(surface.logReAxis, reCol).evaluate(logRe);
			}
		}

		if (nB == 1) {
			double[] aCol = new double[nA];
			for (int ia = 0; ia < nA; ia++) {
				aCol[ia] = w[ia][0];
			}
			double result = new PchipInterpolator1D(surface.alphaAxis, aCol).evaluate(alphaDeg);
			return Double.isFinite(result) ? result : 0.0;
		}

		double[] betaRow = new double[nB];
		double[] aCol = new double[nA];
		for (int ib = 0; ib < nB; ib++) {
			for (int ia = 0; ia < nA; ia++) {
				aCol[ia] = w[ia][ib];
			}
			betaRow[ib] = new PchipInterpolator1D(surface.alphaAxis, aCol).evaluate(alphaDeg);
		}

		double result = new PchipInterpolator1D(surface.betaAxis, betaRow).evaluate(betaDeg);
		return Double.isFinite(result) ? result : 0.0;
	}
}

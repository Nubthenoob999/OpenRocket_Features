package info.openrocket.core.aerodynamics.rom.core.eval;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput;
import info.openrocket.core.aerodynamics.rom.core.physics.BaseDragModel;
import info.openrocket.core.aerodynamics.rom.core.physics.FinDragModel;
import info.openrocket.core.aerodynamics.rom.core.physics.InducedDragModel;
import info.openrocket.core.aerodynamics.rom.core.physics.NormalForceModel;
import info.openrocket.core.aerodynamics.rom.core.physics.PitchingMomentModel;
import info.openrocket.core.aerodynamics.rom.core.physics.SideslipModel;
import info.openrocket.core.aerodynamics.rom.core.physics.SkinFrictionModel;
import info.openrocket.core.aerodynamics.rom.core.physics.TransonicBlendingModel;
import info.openrocket.core.aerodynamics.rom.core.physics.WaveDragModel;
import info.openrocket.core.aerodynamics.rom.RomSurfaceHashUtil;
import info.openrocket.core.aerodynamics.rom.core.surface.AeroSurface4D;
import info.openrocket.core.aerodynamics.rom.core.surface.AeroSurface4DInterpolator;
import info.openrocket.core.aerodynamics.rom.core.surface.PchipInterpolator1D;

public final class AeroGridEvaluator4D {
	private static final double MIN_CD = 0.001;
	private static final double MAX_CD = 8.0;

	// Higher ROM resolution is intentional for smoother interpolated dynamics.
	public static final int N_MACH = 60;
	public static final int N_RE = 20;
	public static final int N_ALPHA = 12;
	public static final int N_BETA = 9;

	@FunctionalInterface
	public interface ProgressListener {
		void onProgress(double fraction);
	}

	private AeroGridEvaluator4D() {
	}

	public static AeroSurface4D evaluate(RomGeometryInput g, ProgressListener progress) {
		return evaluate(g, computeHash(g), false, progress);
	}

	public static AeroSurface4D evaluate(RomGeometryInput g, String geometryHash, ProgressListener progress) {
		return evaluate(g, geometryHash, false, progress);
	}

	public static AeroSurface4D evaluate(
			RomGeometryInput g,
			String geometryHash,
			boolean useGreedyMachAxis,
			ProgressListener progress) {
		double[] machAxis = useGreedyMachAxis
				? buildMachAxisGreedy(g, N_MACH, 5.7, 0.0, 0.3)
				: buildMachAxis();
		double[] logReAxis = buildLogReAxis();
		double[] alphaAxis = buildAlphaAxis();
		double[] betaAxis = buildBetaAxis(g);
		int nBeta = betaAxis.length;

		double[][][][] cdOff = new double[machAxis.length][N_RE][N_ALPHA][nBeta];
		double[][][][] cdOn = new double[machAxis.length][N_RE][N_ALPHA][nBeta];
		double[][][][] cdBody = new double[machAxis.length][N_RE][N_ALPHA][nBeta];
		double[][][][] cn = new double[machAxis.length][N_RE][N_ALPHA][nBeta];
		double[][][][] cm = new double[machAxis.length][N_RE][N_ALPHA][nBeta];

		int total = machAxis.length * N_RE * N_ALPHA * nBeta;
		int done = 0;

		for (int im = 0; im < machAxis.length; im++) {
			double mach = machAxis[im];
			for (int ir = 0; ir < N_RE; ir++) {
				double reL = Math.pow(10.0, logReAxis[ir]);
				for (int ia = 0; ia < N_ALPHA; ia++) {
					double alphaRad = Math.toRadians(alphaAxis[ia]);
					for (int ib = 0; ib < nBeta; ib++) {
						double betaRad = Math.toRadians(betaAxis[ib]);
						PointResult r = computePoint(mach, reL, alphaRad, betaRad, g);
						cdOff[im][ir][ia][ib] = r.cdPlumeOff;
						cdOn[im][ir][ia][ib] = r.cdPlumeOn;
						cdBody[im][ir][ia][ib] = r.cdBody;
						cn[im][ir][ia][ib] = r.CN;
						cm[im][ir][ia][ib] = r.Cm;

						done++;
						if (progress != null && done % 500 == 0) {
							progress.onProgress((double) done / total);
						}
					}
				}
			}
		}

		if (progress != null) {
			progress.onProgress(1.0);
		}

		AeroSurface4D built = new AeroSurface4D(
				machAxis,
				logReAxis,
				alphaAxis,
				betaAxis,
				cdOff,
				cdOn,
				cdBody,
				cn,
				cm,
				RomSurfaceHashUtil.tag(geometryHash, RomSurfaceHashUtil.MODE_4D),
				g.finCount,
				0.0);

		double loo = computeMachLooRmse(built);
		return new AeroSurface4D(
				machAxis,
				logReAxis,
				alphaAxis,
				betaAxis,
				cdOff,
				cdOn,
				cdBody,
				cn,
				cm,
				RomSurfaceHashUtil.tag(geometryHash, RomSurfaceHashUtil.MODE_4D),
				g.finCount,
				loo);
	}

	public static final class PointResult {
		public final double cdPlumeOff;
		public final double cdPlumeOn;
		public final double cdBody;
		public final double CN;
		public final double Cm;

		PointResult(double cdOff, double cdOn, double cdBody, double cn, double cm) {
			this.cdPlumeOff = cdOff;
			this.cdPlumeOn = cdOn;
			this.cdBody = cdBody;
			this.CN = cn;
			this.Cm = cm;
		}
	}

	public static PointResult computePoint(double mach, double reL, double alphaRad, double betaRad, RomGeometryInput g) {
		double cdFriction = sanitizeCd(SkinFrictionModel.cdFriction(mach, reL, g));
		double cfBody = SkinFrictionModel.cfWithRoughness(
				SkinFrictionModel.vanDriestII(
						SkinFrictionModel.cfIncompressible(reL, 5e5),
						mach,
						1.0),
				g.bodyLength,
				g.surfaceRoughness);

		double cdBaseSubsonic = sanitizeCd(BaseDragModel.cdBasePlumeOff(Math.min(mach, 0.59), cfBody, g));
		double cdBaseTransPeak = sanitizeCd(BaseDragModel.cdBasePlumeOff(1.0, cfBody, g));
		double cdBasePlumeOffM = sanitizeCd(BaseDragModel.cdBasePlumeOff(mach, cfBody, g));
		double cdBasePlumeOnM = sanitizeCd(BaseDragModel.cdBasePlumeOn(mach, cfBody, g));

		double cdWaveNose = sanitizeCd(WaveDragModel.cdNoseWaveSupersonic(mach, g));

		double cdFinFric = sanitizeCd(FinDragModel.cdFinFriction(mach, reL, g));
		double cdFinWave = sanitizeCd(FinDragModel.cdFinWaveSupersonic(mach, g));
		double cdFinJunct = sanitizeCd(FinDragModel.cdFinInterference(cdFinFric + cdFinWave, g));
		double cdFinTotal = sanitizeCd(cdFinFric + cdFinWave + cdFinJunct);

		double cdBodySub = sanitizeCd(cdFriction + cdBaseSubsonic);
		double cdBodyTrans = sanitizeCd(TransonicBlendingModel.transonicPeakCd(cdBodySub, g) + cdBaseTransPeak);
		double cdBodySup = sanitizeCd(cdFriction + cdBasePlumeOffM + cdWaveNose);
		double cdBodyValue = withFloor(TransonicBlendingModel.blend(mach, cdBodySub, cdBodyTrans, cdBodySup), MIN_CD);

		double cdSub = sanitizeCd(cdFriction + cdFinTotal + cdBaseSubsonic);
		double cdTrans = sanitizeCd(TransonicBlendingModel.transonicPeakCd(cdSub, g) + cdBaseTransPeak);
		double cdSup = sanitizeCd(cdFriction + cdFinTotal + cdBasePlumeOffM + cdWaveNose);
		double cdZeroAoA = sanitizeCd(TransonicBlendingModel.blend(mach, cdSub, cdTrans, cdSup));

		double cdBodyAoA = sanitizeCd(InducedDragModel.cdInduced(alphaRad, betaRad, mach, g));
		double cdSideslip = sanitizeCd(SideslipModel.cdSideslipIncrement(alphaRad, betaRad, mach, g));
		double kf = InducedDragModel.protuberanceFactor();
		double cdPlumeOff = withFloor((cdZeroAoA + cdBodyAoA + cdSideslip) * kf, MIN_CD);

		double cdSubOn = sanitizeCd(cdFriction + cdFinTotal + cdBasePlumeOnM);
		double cdTransOn = sanitizeCd(TransonicBlendingModel.transonicPeakCd(cdSubOn, g));
		double cdSupOn = sanitizeCd(cdFriction + cdFinTotal + cdBasePlumeOnM + cdWaveNose);
		double cdZeroAoAOn = sanitizeCd(TransonicBlendingModel.blend(mach, cdSubOn, cdTransOn, cdSupOn));
		double cdPlumeOn = withFloor((cdZeroAoAOn + cdBodyAoA + cdSideslip) * kf, MIN_CD);

		double cn = NormalForceModel.CN(alphaRad, betaRad, mach, g);
		double cm = PitchingMomentModel.Cm(cn, alphaRad, mach, g, 0.55 * g.bodyLength);

		return new PointResult(cdPlumeOff, cdPlumeOn, cdBodyValue, cn, cm);
	}

	private static double sanitizeCd(double value) {
		if (!Double.isFinite(value)) {
			return 0.0;
		}
		return Math.max(0.0, Math.min(value, MAX_CD));
	}

	private static double withFloor(double value, double floor) {
		return Math.max(floor, sanitizeCd(value));
	}

	public static double[] buildMachAxis() {
		double[] axis = new double[N_MACH];
		int i = 0;
		for (; i < 15; i++) {
			axis[i] = 0.01 + i * (0.70 - 0.01) / 14.0;
		}
		for (; i < 35; i++) {
			axis[i] = 0.70 + (i - 14) * (1.30 - 0.70) / 20.0;
		}
		for (; i < N_MACH; i++) {
			axis[i] = 1.30 + (i - 34) * (4.00 - 1.30) / 25.0;
		}
		return axis;
	}

	public static double[] buildMachAxisGreedy(
			RomGeometryInput g,
			int nPoints,
			double logReTypical,
			double alphaRad,
			double tolPercent) {
		final int nCand = 600;
		final double machLo = 0.01;
		final double machHi = 4.0;

		double[] cands = new double[nCand];
		for (int i = 0; i < nCand; i++) {
			cands[i] = machLo + i * (machHi - machLo) / (nCand - 1);
		}

		double reL = Math.pow(10.0, logReTypical);
		double[] cdTrue = new double[nCand];
		double cdMean = 0.0;
		for (int i = 0; i < nCand; i++) {
			cdTrue[i] = computePoint(cands[i], reL, alphaRad, 0.0, g).cdPlumeOff;
			cdMean += cdTrue[i];
		}
		cdMean /= nCand;
		double tolAbs = (tolPercent / 100.0) * Math.max(cdMean, 1e-4);

		List<Integer> chosen = new ArrayList<>(
				Arrays.asList(0, nCand / 5, nCand / 2, 3 * nCand / 4, nCand - 1));
		boolean[] used = new boolean[nCand];
		for (int idx : chosen) {
			used[idx] = true;
		}

		while (chosen.size() < nPoints) {
			int n = chosen.size();
			double[] xs = new double[n];
			double[] ys = new double[n];
			List<Integer> sorted = new ArrayList<>(chosen);
			Collections.sort(sorted);
			for (int k = 0; k < n; k++) {
				xs[k] = cands[sorted.get(k)];
				ys[k] = cdTrue[sorted.get(k)];
			}
			PchipInterpolator1D interp = new PchipInterpolator1D(xs, ys);

			double maxErr = -1.0;
			int worstIdx = -1;
			for (int i = 0; i < nCand; i++) {
				if (used[i]) {
					continue;
				}
				double err = Math.abs(interp.evaluate(cands[i]) - cdTrue[i]);
				if (err > maxErr) {
					maxErr = err;
					worstIdx = i;
				}
			}

			if (worstIdx < 0 || maxErr < tolAbs) {
				break;
			}

			chosen.add(worstIdx);
			used[worstIdx] = true;
		}

		chosen.sort(Comparator.comparingDouble(i -> cands[i]));
		double[] axis = new double[chosen.size()];
		for (int k = 0; k < chosen.size(); k++) {
			axis[k] = cands[chosen.get(k)];
		}
		return axis;
	}

	public static double[] buildLogReAxis() {
		double[] axis = new double[N_RE];
		double lo = 4.0;
		double hi = 8.0;
		for (int i = 0; i < N_RE; i++) {
			axis[i] = lo + i * (hi - lo) / (N_RE - 1);
		}
		return axis;
	}

	public static double[] buildAlphaAxis() {
		return new double[] { 0.0, 0.5, 1.0, 2.0, 3.0, 4.5, 6.0, 7.5, 9.0, 11.0, 13.0, 15.0 };
	}

	public static double[] buildBetaAxis(RomGeometryInput g) {
		double maxBeta;
		switch (g.finCount) {
			case 0:
			case 1:
			case 2:
				maxBeta = 0.0;
				break;
			case 3:
				maxBeta = 60.0;
				break;
			default:
				maxBeta = 45.0;
				break;
		}
		if (maxBeta < 1e-6) {
			return new double[] { 0.0 };
		}
		double[] axis = new double[N_BETA];
		for (int i = 0; i < N_BETA; i++) {
			axis[i] = i * maxBeta / (N_BETA - 1);
		}
		return axis;
	}

	public static double computeMachLooRmse(AeroSurface4D surface) {
		int nM = surface.machAxis.length;
		if (nM < 4) {
			return 0.0;
		}

		double[] cdFull = new double[nM];
		for (int im = 0; im < nM; im++) {
			cdFull[im] = surface.cdPlumeOff[im][0][0][0];
		}
		double mean = 0.0;
		for (double v : cdFull) {
			mean += v;
		}
		mean /= nM;
		if (mean < 1e-9) {
			return 0.0;
		}

		double ssq = 0.0;
		double[] xLoo = new double[nM - 1];
		double[] yLoo = new double[nM - 1];
		for (int leave = 0; leave < nM; leave++) {
			int j = 0;
			for (int i = 0; i < nM; i++) {
				if (i == leave) {
					continue;
				}
				xLoo[j] = surface.machAxis[i];
				yLoo[j] = cdFull[i];
				j++;
			}
			double predicted = new PchipInterpolator1D(xLoo, yLoo).evaluate(surface.machAxis[leave]);
			double err = predicted - cdFull[leave];
			ssq += err * err;
		}
		return 100.0 * Math.sqrt(ssq / nM) / mean;
	}

	private static String computeHash(RomGeometryInput g) {
		String data = String.format(
				Locale.ROOT,
				"%.6f|%.6f|%.6f|%s|%d|%.6f|%.6f|%.6f|%.6f|%.6f|%.6f|%.6f|%.6f|%.6f",
				g.bodyLength,
				g.maxDiameter,
				g.noseLength,
				g.noseShape.name(),
				g.finCount,
				g.finRootChord,
				g.finTipChord,
				g.finSpan,
				g.finThickness,
				g.finSweepAngle,
				g.finAxialPosition,
				g.boattailLength,
				g.surfaceRoughness,
				g.motorExitArea);

		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] digest = md.digest(data.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			for (byte b : digest) {
				sb.append(String.format(Locale.ROOT, "%02x", b));
			}
			return sb.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("SHA-256 unavailable", e);
		}
	}

	public static void main(String[] args) throws Exception {
		RomGeometryInput g = new RomGeometryInput(
				1.0,
				0.1,
				Math.PI * 0.0025,
				0.35,
				0.2,
				RomGeometryInput.NoseShape.OGIVE,
				10.0,
				Math.PI * 0.0025,
				0.0,
				0.0,
				4,
				0.12,
				0.06,
				0.06,
				0.003,
				Math.toRadians(30.0),
				0.0216,
				0.88,
				0.0,
				6.4e-6);

		System.out.println("Building AeroSurface4D...");
		long t0 = System.currentTimeMillis();
		AeroSurface4D surface = evaluate(g, progress -> {
			if ((int) (progress * 20) % 5 == 0) {
				System.out.printf("  %.0f%%%n", progress * 100);
			}
		});
		long elapsed = System.currentTimeMillis() - t0;
		System.out.printf("Build complete in %d ms%n", elapsed);
		System.out.printf("LOO RMSE: %.4f%%%n", surface.looRmsePercent);
		System.out.printf("Grid: %d x %d x %d x %d%n",
				surface.machAxis.length, surface.logReAxis.length,
				surface.alphaAxis.length, surface.betaAxis.length);

		AeroSurface4DInterpolator interp = new AeroSurface4DInterpolator(surface);
		System.out.println("mach,Cd_off,CN,Cm");
		for (double mach = 0.3; mach <= 4.0; mach += 0.1) {
			AeroSurface4DInterpolator.QueryResult r = interp.query(mach, 1e6, 2.0, 0.0);
			System.out.printf(Locale.ROOT,
					"%.2f,%.6f,%.6f,%.6f%n", mach, r.cdPlumeOff, r.CN, r.Cm);
		}
	}
}

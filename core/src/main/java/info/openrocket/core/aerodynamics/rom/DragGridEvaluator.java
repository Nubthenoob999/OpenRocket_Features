package info.openrocket.core.aerodynamics.rom;

import java.util.List;

public class DragGridEvaluator {
	private static final double MAX_CD = 8.0;

	// Default grid resolution - balance accuracy vs build time
	public static final int N_MACH = 80;   // 0.01 to 4.0
	public static final int N_RE = 20;     // log10(1e4) to log10(1e8)
	public static final int N_ALPHA = 10;  // 0 to 15 degrees

	/**
	 * Build the complete DragSurface for the given geometry.
	 * Progress is reported via the listener (0.0 to 1.0).
	 */
	public static DragSurface evaluate(RomGeometryParameters g,
									 ProgressListener progress) {
		double[] machAxis = buildMachAxis();
		double[] logReAxis = buildLogReAxis();
		double[] alphaAxis = buildAlphaAxis();

		double[][][] cdOff = new double[N_MACH][N_RE][N_ALPHA];
		double[][][] cdOn = new double[N_MACH][N_RE][N_ALPHA];

		int total = N_MACH * N_RE * N_ALPHA;
		int done = 0;

		for (int im = 0; im < N_MACH; im++) {
			double mach = machAxis[im];
			for (int ir = 0; ir < N_RE; ir++) {
				double re_L = Math.pow(10.0, logReAxis[ir]);
				for (int ia = 0; ia < N_ALPHA; ia++) {
					double alphaRad = Math.toRadians(alphaAxis[ia]);

					cdOff[im][ir][ia] = computeCdPlumeOff(mach, re_L, alphaRad, g);
					cdOn[im][ir][ia] = computeCdPlumeOn(mach, re_L, alphaRad, g);

					done++;
					if (progress != null && done % 50 == 0) {
						progress.onProgress((double) done / total);
					}
				}
			}
		}

		if (progress != null) {
			progress.onProgress(1.0);
		}
		return new DragSurface(machAxis, logReAxis, alphaAxis, cdOff, cdOn,
				RomSurfaceHashUtil.tag(g.geometryHash(), RomSurfaceHashUtil.MODE_3D), 0.0);
	}

	static double computeCdPlumeOff(double mach, double re_L,
								  double alphaRad, RomGeometryParameters g) {
		// 1. Skin friction
		double cd_friction = sanitizeCd(SkinFrictionModel.cdFriction(mach, re_L, g));

		// 2. Fin friction
		double cd_fin_friction = sanitizeCd(finFriction(mach, re_L, g));
		double cd_fin_sub = sanitizeCd(cd_fin_friction + finInterference(cd_fin_friction, g));

		// 3. Base drag - compute regime-appropriate value
		double cf_body = SkinFrictionModel.cfWithRoughness(
				SkinFrictionModel.vanDriestII(
						SkinFrictionModel.cfIncompressible(re_L, 5e5), mach, 1.0),
				g.bodyLength, g.surfaceRoughness);

		double cd_base_subsonic = sanitizeCd(BaseDragModel.cdBasePlumeOff(
				Math.min(mach, 0.59), cf_body, g));
		double cd_base_transonic_peak = sanitizeCd(BaseDragModel.cdBasePlumeOff(
				1.0, cf_body, g));

		// 4. Wave drag (supersonic)
		double cd_wave_nose = sanitizeCd(WaveDragModel.cdNoseWaveSupersonic(mach, g));
		double cd_wave_fins = sanitizeCd(WaveDragModel.cdFinWaveSupersonic(mach, g));
		double cd_fin_sup = sanitizeCd(cd_fin_friction + cd_wave_fins
				+ finInterference(cd_fin_friction + cd_wave_fins, g));

		// 5. Subsonic total (no wave drag)
		double cd_non_base_sub = sanitizeCd(cd_friction + cd_fin_sub);
		double cd_sub = sanitizeCd(cd_non_base_sub + cd_base_subsonic);

		// 6. Supersonic total
		double cd_sup = sanitizeCd(cd_friction + cd_fin_sup
				+ BaseDragModel.cdBasePlumeOff(mach, cf_body, g)
				+ cd_wave_nose);

		// 7. Transonic peak estimate
		double cd_trans = sanitizeCd(TransonicBlendingModel.transonicPeakCd(cd_non_base_sub, g)
				+ cd_base_transonic_peak);

		// 8. Smooth blend
		double cd_zero_aoa = sanitizeCd(TransonicBlendingModel.blend(mach, cd_sub, cd_trans, cd_sup));

		// 9. AoA increment + protuberances
		double cd_aoa = sanitizeCd(InducedDragModel.cdInduced(alphaRad, mach, g));
		double kf = InducedDragModel.protuberanceFactor();

		double cd = (cd_zero_aoa + cd_aoa) * kf;
		return smoothLowerBound(cd, 0.001, 1e-4);
	}

	static double computeCdPlumeOn(double mach, double re_L,
								 double alphaRad, RomGeometryParameters g) {
		// Same as plume-off but substitute plume-on base drag
		double cf_body = SkinFrictionModel.cfWithRoughness(
				SkinFrictionModel.vanDriestII(
						SkinFrictionModel.cfIncompressible(re_L, 5e5), mach, 1.0),
				g.bodyLength, g.surfaceRoughness);

		double cd_friction = sanitizeCd(SkinFrictionModel.cdFriction(mach, re_L, g));
		double cd_fin_friction = sanitizeCd(finFriction(mach, re_L, g));
		double cd_fin_sub = sanitizeCd(cd_fin_friction + finInterference(cd_fin_friction, g));
		double cd_base_on = sanitizeCd(BaseDragModel.cdBasePlumeOn(mach, cf_body, g));
		double cd_base_on_peak = sanitizeCd(BaseDragModel.cdBasePlumeOn(1.0, cf_body, g));
		double cd_wave_nose = sanitizeCd(WaveDragModel.cdNoseWaveSupersonic(mach, g));
		double cd_wave_fins = sanitizeCd(WaveDragModel.cdFinWaveSupersonic(mach, g));
		double cd_fin_sup = sanitizeCd(cd_fin_friction + cd_wave_fins
				+ finInterference(cd_fin_friction + cd_wave_fins, g));

		double cd_non_base_sub = sanitizeCd(cd_friction + cd_fin_sub);
		double cd_sub = sanitizeCd(cd_non_base_sub + cd_base_on);
		double cd_sup = sanitizeCd(cd_friction + cd_fin_sup + cd_base_on + cd_wave_nose);
		double cd_trans = sanitizeCd(TransonicBlendingModel.transonicPeakCd(cd_non_base_sub, g) + cd_base_on_peak);

		double cd_zero_aoa = sanitizeCd(TransonicBlendingModel.blend(mach, cd_sub, cd_trans, cd_sup));
		double cd_aoa = sanitizeCd(InducedDragModel.cdInduced(alphaRad, mach, g));
		double cd = (cd_zero_aoa + cd_aoa) * InducedDragModel.protuberanceFactor();
		return smoothLowerBound(cd, 0.001, 1e-4);
	}

	private static double sanitizeCd(double value) {
		if (!Double.isFinite(value)) {
			return 0.0;
		}
		return Math.max(0.0, Math.min(value, MAX_CD));
	}

	private static double smoothLowerBound(double value, double floor, double width) {
		if (!Double.isFinite(value)) {
			return floor;
		}
		double w = Math.max(width, 1e-12);
		double z = (value - floor) / w;
		if (z > 50.0) {
			return value;
		}
		if (z < -50.0) {
			return floor;
		}
		return floor + w * Math.log1p(Math.exp(z));
	}

	// Fin friction contribution (both sides, all fins, referenced to frontal area)
	private static double finFriction(double mach, double re_L, RomGeometryParameters g) {
		List<RomGeometryParameters.FinGeom> finSets = g.resolvedFinSets();
		if (g.finCount == 0 || finSets.isEmpty() || g.bodyLength <= 0) {
			return 0.0;
		}
		double cd = 0.0;
		for (RomGeometryParameters.FinGeom finSet : finSets) {
			double cMean = finSet.meanChord();
			if (cMean <= 1e-9) {
				continue;
			}
			double tc = finSet.thickness() / cMean;
			double re_fin = re_L * cMean / g.bodyLength;
			double cf_fin = SkinFrictionModel.vanDriestII(
					SkinFrictionModel.cfIncompressible(re_fin, 5e5), mach, 1.0);
			double ff_fin = 1.0 + 2.0 * tc;
			cd += cf_fin * ff_fin * finSet.wettedArea() / g.referenceArea;
		}
		return cd;
	}

	private static double finInterference(double cdFinFrictionPlusWave, RomGeometryParameters g) {
		if (g.finCount == 0) {
			return 0.0;
		}
		return 0.04 * Math.max(0.0, cdFinFrictionPlusWave);
	}

	static double[] buildMachAxis() {
		// Cluster points in transonic region for accuracy
		double[] axis = new double[N_MACH];
		// 0.01-0.70: 20 points (inclusive)
		// 0.70-1.30: 30 interior points (exclude 0.70)
		// 1.30-4.00: 30 interior points (exclude 1.30)
		int idx = 0;
		for (int i = 0; i < 20; i++) {
			axis[idx++] = 0.01 + i * (0.70 - 0.01) / 19.0;
		}
		for (int i = 1; i <= 30; i++) {
			axis[idx++] = 0.70 + i * (1.30 - 0.70) / 30.0;
		}
		for (int i = 1; i <= 30; i++) {
			axis[idx++] = 1.30 + i * (4.00 - 1.30) / 30.0;
		}
		return axis;
	}

	static double[] buildLogReAxis() {
		double[] axis = new double[N_RE];
		double lo = Math.log10(1e4);
		double hi = Math.log10(1e8);
		for (int i = 0; i < N_RE; i++) {
			axis[i] = lo + i * (hi - lo) / (N_RE - 1);
		}
		return axis;
	}

	static double[] buildAlphaAxis() {
		double[] axis = new double[N_ALPHA];
		for (int i = 0; i < N_ALPHA; i++) {
			axis[i] = i * 15.0 / (N_ALPHA - 1);
		}
		return axis;
	}

	public interface ProgressListener {
		void onProgress(double fraction); // 0.0 to 1.0
	}
}

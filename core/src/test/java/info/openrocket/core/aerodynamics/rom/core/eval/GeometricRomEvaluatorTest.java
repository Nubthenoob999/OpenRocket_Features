package info.openrocket.core.aerodynamics.rom.core.eval;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.rom.core.basis.GeometryParametricRom;
import info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput;
import info.openrocket.core.aerodynamics.rom.core.sampling.GeometrySampler;
import info.openrocket.core.aerodynamics.rom.core.surface.AeroSurface4D;

public class GeometricRomEvaluatorTest {

	@Test
	public void testBuildSmallParametricRom() {
		List<RomGeometryInput> pool = GeometrySampler.buildLhsPool(8, 99L);
		GeometryParametricRom rom = org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
				Duration.ofSeconds(120),
				() -> GeometricRomEvaluator.build(pool, List.of(pool.get(0), pool.get(1)), 4, 10.0, 1e-3));
		assertNotNull(rom);
		assertTrue(rom.getRegions().size() >= 1);
	}

	@Test
	public void testHoldoutQualityReportsPlumeOffAndPlumeOnErrors() {
		List<RomGeometryInput> pool = GeometrySampler.buildLhsPool(12, 20260331L);
		List<RomGeometryInput> train = new ArrayList<>(pool.subList(0, 8));
		List<RomGeometryInput> holdout = new ArrayList<>(pool.subList(8, pool.size()));

		GeometryParametricRom rom = org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
				Duration.ofSeconds(180),
				() -> GeometricRomEvaluator.build(train, List.of(train.get(0), train.get(1)), 6, 5.0, 1e-3));

		double sumOffPct = 0.0;
		double sumOnPct = 0.0;
		for (int i = 0; i < holdout.size(); i++) {
			RomGeometryInput g = holdout.get(i);
			AeroSurface4D truth = AeroGridEvaluator4D.evaluate(g, "holdout-truth-" + i, null);
			AeroSurface4D recon = rom.reconstructSurface(g, "holdout-recon-" + i);

			double offPct = relativeRmsePercent(truth.cdPlumeOff, recon.cdPlumeOff);
			double onPct = relativeRmsePercent(truth.cdPlumeOn, recon.cdPlumeOn);
			sumOffPct += offPct;
			sumOnPct += onPct;
		}

		double meanOffPct = sumOffPct / holdout.size();
		double meanOnPct = sumOnPct / holdout.size();

		System.out.println(String.format(Locale.US,
				"Geometric ROM holdout mean RMSE: plume-off=%.3f%%, plume-on=%.3f%%", meanOffPct, meanOnPct));

		assertTrue(Double.isFinite(meanOffPct) && meanOffPct >= 0.0);
		assertTrue(Double.isFinite(meanOnPct) && meanOnPct >= 0.0);
		assertTrue(meanOffPct < 50.0, "Holdout plume-off RMSE unexpectedly high: " + meanOffPct + "%");
		assertTrue(meanOnPct < 50.0, "Holdout plume-on RMSE unexpectedly high: " + meanOnPct + "%");
	}

	private static double relativeRmsePercent(double[][][][] truth, double[][][][] recon) {
		double errSq = 0.0;
		double truthSq = 0.0;
		for (int im = 0; im < truth.length; im++) {
			for (int ir = 0; ir < truth[im].length; ir++) {
				for (int ia = 0; ia < truth[im][ir].length; ia++) {
					for (int ib = 0; ib < truth[im][ir][ia].length; ib++) {
						double t = truth[im][ir][ia][ib];
						double d = recon[im][ir][ia][ib] - t;
						errSq += d * d;
						truthSq += t * t;
					}
				}
			}
		}
		if (truthSq <= 1e-15) {
			return 0.0;
		}
		return 100.0 * Math.sqrt(errSq / truthSq);
	}
}

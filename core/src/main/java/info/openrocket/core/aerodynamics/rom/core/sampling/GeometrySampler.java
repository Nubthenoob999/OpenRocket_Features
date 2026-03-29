package info.openrocket.core.aerodynamics.rom.core.sampling;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import info.openrocket.core.aerodynamics.rom.core.basis.GeometrySnapshot;
import info.openrocket.core.aerodynamics.rom.core.basis.PodBasis;
import info.openrocket.core.aerodynamics.rom.core.eval.AeroGridEvaluator4D;
import info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput;
import info.openrocket.core.aerodynamics.rom.core.surface.AeroSurface4D;

public final class GeometrySampler {

	private GeometrySampler() {
	}

	public interface ProgressListener {
		void onSnapshot(int snapshotIndex, int totalCandidates, double currentMaxError, RomGeometryInput selected);
	}

	public static List<GeometrySnapshot> greedySample(
			List<RomGeometryInput> candidates,
			List<RomGeometryInput> seeds,
			int maxSnapshots,
			double targetErrorPct,
			double podTolerance,
			ProgressListener progress) {
		if (candidates == null || candidates.isEmpty()) {
			throw new IllegalArgumentException("Candidate pool must not be empty");
		}
		if (maxSnapshots <= 0) {
			throw new IllegalArgumentException("maxSnapshots must be > 0");
		}

		List<GeometrySnapshot> cachedSnapshots = new ArrayList<>(candidates.size());
		for (RomGeometryInput g : candidates) {
			AeroSurface4D s = AeroGridEvaluator4D.evaluate(g, (AeroGridEvaluator4D.ProgressListener) null);
			cachedSnapshots.add(new GeometrySnapshot(g, s));
		}

		boolean[] used = new boolean[candidates.size()];
		List<GeometrySnapshot> selected = new ArrayList<>();

		for (RomGeometryInput seed : seeds) {
			int idx = findClosest(seed, candidates, used);
			if (idx >= 0) {
				selected.add(cachedSnapshots.get(idx));
				used[idx] = true;
				if (selected.size() >= maxSnapshots) {
					return selected;
				}
			}
		}

		if (selected.isEmpty()) {
			selected.add(cachedSnapshots.get(0));
			used[0] = true;
		}

		while (selected.size() < maxSnapshots) {
			PodBasis basis = new PodBasis(selected, podTolerance, true);

			double maxErr = -1.0;
			int worstIdx = -1;
			for (int i = 0; i < cachedSnapshots.size(); i++) {
				if (used[i]) {
					continue;
				}
				if (cachedSnapshots.get(i).cdOffFlat.length != basis.snapshotLength) {
					continue;
				}
				double err = basis.reconstructionError(cachedSnapshots.get(i).cdOffFlat);
				double meanCd = mean(cachedSnapshots.get(i).cdOffFlat);
				double errPct = (meanCd > 1e-9) ? 100.0 * err : 0.0;
				if (errPct > maxErr) {
					maxErr = errPct;
					worstIdx = i;
				}
			}

			if (worstIdx < 0 || maxErr < targetErrorPct) {
				break;
			}

			selected.add(cachedSnapshots.get(worstIdx));
			used[worstIdx] = true;
			if (progress != null) {
				progress.onSnapshot(selected.size(), candidates.size(), maxErr, candidates.get(worstIdx));
			}
		}
		return selected;
	}

	public static List<RomGeometryInput> buildLhsPool(int n, long seed) {
		if (n <= 0) {
			throw new IllegalArgumentException("n must be > 0");
		}
		Random rng = new Random(seed);
		double[] bodyL = lhsDimension(n, 0.6, 2.5, rng);
		double[] fineness = lhsDimension(n, 4.0, 20.0, rng);
		double[] noseFraction = lhsDimension(n, 0.15, 0.35, rng);
		double[] finAr = lhsDimension(n, 1.0, 5.0, rng);
		double[] taper = lhsDimension(n, 0.0, 0.6, rng);
		double[] thick = lhsDimension(n, 0.03, 0.10, rng);
		double[] areaFraction = lhsDimension(n, 0.3, 2.0, rng);
		double[] boattailFraction = lhsDimension(n, 0.0, 0.15, rng);
		double[] motorFraction = lhsDimension(n, 0.0, 0.6, rng);
		int[] finCounts = lhsCategorical(n, new int[] { 3, 4 }, rng);
		RomGeometryInput.NoseShape[] noseShapes = lhsNoseShapes(n, rng);

		List<RomGeometryInput> out = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			double d = 0.1;
			double bodyLength = bodyL[i];
			double maxDiameter = bodyLength / fineness[i];
			maxDiameter = Math.max(0.03, Math.min(maxDiameter, 0.2));
			double refArea = Math.PI * maxDiameter * maxDiameter / 4.0;
			double baseArea = refArea;
			double wetArea = Math.PI * maxDiameter * bodyLength;
			double noseLength = noseFraction[i] * bodyLength;
			double boattailLength = boattailFraction[i] * bodyLength;
			double boattailBaseDiameter = Math.max(0.3 * maxDiameter, maxDiameter * (1.0 - 0.6 * boattailFraction[i] / 0.15));

			int finCount = finCounts[i];
			double meanChord = 0.12 * bodyLength;
			double finRootChord = meanChord * 1.2;
			double finTipChord = finRootChord * taper[i];
			double finSpan = 0.5 * finAr[i] * ((finRootChord + finTipChord) / 2.0);
			double finThickness = thick[i] * ((finRootChord + finTipChord) / 2.0);
			double finSweep = Math.toRadians(20.0 + 35.0 * (i / (double) Math.max(1, n - 1)));
			double finPlanform = 0.5 * (finRootChord + finTipChord) * finSpan;
			double targetFinWetted = (finCount > 0) ? (areaFraction[i] * refArea / finCount) : 0.0;
			double finWetted = (finCount > 0) ? Math.max(targetFinWetted, 2.0 * finPlanform) : 0.0;
			double finAxial = Math.max(0.0, bodyLength - finRootChord - boattailLength);
			double motorExitArea = motorFraction[i] * baseArea;

			out.add(new RomGeometryInput(
					bodyLength,
					maxDiameter,
					baseArea,
					wetArea,
					noseLength,
					noseShapes[i],
					bodyLength / Math.max(maxDiameter, 1e-9),
					refArea,
					boattailLength,
					boattailBaseDiameter,
					finCount,
					(finCount > 0) ? finRootChord : 0.0,
					(finCount > 0) ? finTipChord : 0.0,
					(finCount > 0) ? finSpan : 0.0,
					(finCount > 0) ? finThickness : 0.0,
					(finCount > 0) ? finSweep : 0.0,
					(finCount > 0) ? finWetted : 0.0,
					(finCount > 0) ? finAxial : 0.0,
					motorExitArea,
					6.4e-6));
		}
		return out;
	}

	private static int findClosest(RomGeometryInput seed, List<RomGeometryInput> candidates, boolean[] used) {
		double best = Double.POSITIVE_INFINITY;
		int bestIdx = -1;
		for (int i = 0; i < candidates.size(); i++) {
			if (used[i]) {
				continue;
			}
			double d = seed.featureDistance(candidates.get(i));
			if (d < best) {
				best = d;
				bestIdx = i;
			}
		}
		return bestIdx;
	}

	private static double mean(double[] arr) {
		double sum = 0.0;
		for (double v : arr) {
			sum += v;
		}
		return sum / Math.max(1, arr.length);
	}

	private static double[] lhsDimension(int n, double lo, double hi, Random rng) {
		List<Double> values = new ArrayList<>(n);
		double width = (hi - lo) / n;
		for (int i = 0; i < n; i++) {
			double a = lo + i * width;
			values.add(a + rng.nextDouble() * width);
		}
		Collections.shuffle(values, rng);
		double[] out = new double[n];
		for (int i = 0; i < n; i++) {
			out[i] = values.get(i);
		}
		return out;
	}

	private static int[] lhsCategorical(int n, int[] categories, Random rng) {
		List<Integer> list = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			list.add(categories[i % categories.length]);
		}
		Collections.shuffle(list, rng);
		int[] out = new int[n];
		for (int i = 0; i < n; i++) {
			out[i] = list.get(i);
		}
		return out;
	}

	private static RomGeometryInput.NoseShape[] lhsNoseShapes(int n, Random rng) {
		RomGeometryInput.NoseShape[] shapes = RomGeometryInput.NoseShape.values();
		List<RomGeometryInput.NoseShape> list = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			list.add(shapes[i % shapes.length]);
		}
		Collections.shuffle(list, rng);
		RomGeometryInput.NoseShape[] out = new RomGeometryInput.NoseShape[n];
		for (int i = 0; i < n; i++) {
			out[i] = list.get(i);
		}
		return out;
	}
}

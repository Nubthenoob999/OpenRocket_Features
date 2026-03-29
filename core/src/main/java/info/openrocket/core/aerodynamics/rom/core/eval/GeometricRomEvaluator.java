package info.openrocket.core.aerodynamics.rom.core.eval;

import java.util.ArrayList;
import java.util.List;

import info.openrocket.core.aerodynamics.rom.core.basis.GeometryParametricRom;
import info.openrocket.core.aerodynamics.rom.core.basis.GeometrySnapshot;
import info.openrocket.core.aerodynamics.rom.core.basis.LocalPodRegion;
import info.openrocket.core.aerodynamics.rom.core.basis.PodBasis;
import info.openrocket.core.aerodynamics.rom.core.geometry.RomGeometryInput;
import info.openrocket.core.aerodynamics.rom.core.sampling.GeometrySampler;

/**
 * Offline builder for the geometry-parametric ROM.
 */
public final class GeometricRomEvaluator {

	public static final int DEFAULT_POOL_SIZE = 300;
	public static final int DEFAULT_MAX_SNAPSHOTS = 40;
	public static final double DEFAULT_TARGET_ERROR_PCT = 1.0;
	public static final double DEFAULT_POD_TOLERANCE = 1e-4;

	private GeometricRomEvaluator() {
	}

	public static GeometryParametricRom buildDefault(long seed) {
		List<RomGeometryInput> pool = GeometrySampler.buildLhsPool(DEFAULT_POOL_SIZE, seed);
		List<RomGeometryInput> seeds = new ArrayList<>();
		if (!pool.isEmpty()) {
			seeds.add(pool.get(0));
			if (pool.size() > 1) {
				seeds.add(pool.get(pool.size() - 1));
			}
		}
		return build(pool, seeds, DEFAULT_MAX_SNAPSHOTS, DEFAULT_TARGET_ERROR_PCT, DEFAULT_POD_TOLERANCE);
	}

	public static GeometryParametricRom build(
			List<RomGeometryInput> candidates,
			List<RomGeometryInput> seeds,
			int maxSnapshots,
			double targetErrorPct,
			double podTolerance) {
		List<GeometrySnapshot> selected = GeometrySampler.greedySample(
				candidates,
				seeds,
				maxSnapshots,
				targetErrorPct,
				podTolerance,
				null);

		if (selected.isEmpty()) {
			throw new IllegalStateException("Greedy sampler returned no snapshots");
		}

		List<GeometrySnapshot> regionA = new ArrayList<>();
		List<GeometrySnapshot> regionB = new ArrayList<>();
		for (GeometrySnapshot s : selected) {
			double fineness = s.geometry.finenessRatio;
			RomGeometryInput.NoseShape shape = s.geometry.noseShape;
			boolean streamlined = fineness > 8.0
					&& (shape == RomGeometryInput.NoseShape.OGIVE
					|| shape == RomGeometryInput.NoseShape.VON_KARMAN
					|| shape == RomGeometryInput.NoseShape.HAACK);
			if (streamlined) {
				regionA.add(s);
			} else {
				regionB.add(s);
			}
		}
		if (regionA.isEmpty()) {
			regionA.add(regionB.get(0));
		}
		if (regionB.isEmpty()) {
			regionB.add(regionA.get(0));
		}

		LocalPodRegion a = buildRegion(regionA, podTolerance, "slender_streamlined");
		LocalPodRegion b = buildRegion(regionB, podTolerance, "blunt_transonic");

		double[] machAxis = selected.get(0).machAxis;
		double[] logReAxis = selected.get(0).logReAxis;
		double[] alphaAxis = selected.get(0).alphaAxis;
		double[] betaAxis = selected.get(0).betaAxis;

		return GeometryParametricRom.withAutoMagicPoints(
				List.of(a, b),
				machAxis,
				logReAxis,
				alphaAxis,
				betaAxis);
	}

	private static LocalPodRegion buildRegion(List<GeometrySnapshot> snapshots, double podTolerance, String label) {
		PodBasis basisOff = new PodBasis(snapshots, podTolerance, true);
		PodBasis basisOn = new PodBasis(snapshots, podTolerance, false);

		double[] centroid = new double[snapshots.get(0).featureVector.length];
		for (GeometrySnapshot s : snapshots) {
			for (int i = 0; i < centroid.length; i++) {
				centroid[i] += s.featureVector[i];
			}
		}
		for (int i = 0; i < centroid.length; i++) {
			centroid[i] /= snapshots.size();
		}

		double maxDist = 0.0;
		for (GeometrySnapshot s : snapshots) {
			double sumSq = 0.0;
			for (int i = 0; i < centroid.length; i++) {
				double d = s.featureVector[i] - centroid[i];
				sumSq += d * d;
			}
			maxDist = Math.max(maxDist, Math.sqrt(sumSq));
		}
		double sigma = Math.max(maxDist, 1e-3);
		return new LocalPodRegion(centroid, sigma, basisOff, basisOn, label);
	}
}

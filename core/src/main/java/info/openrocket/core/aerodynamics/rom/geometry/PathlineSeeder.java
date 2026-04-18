package info.openrocket.core.aerodynamics.rom.geometry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import info.openrocket.core.aerodynamics.rom.RomSettings;

public class PathlineSeeder {
	public List<PathlineSeed> createSeeds(GeometryFeatures features, RomSettings settings) {
		List<PathlineSeed> seeds = new ArrayList<>();
		addBodySeeds(seeds, features, settings);
		addFinSeeds(seeds, features, settings);
		if (features.getBaseArea() > 1e-9) {
			seeds.add(new PathlineSeed(PathlineSeed.SeedFamily.AFT_BODY_PLACEHOLDER, "base", 0,
					features.getBodyLength(), 0.0, 0.0, 1.0, 0.0, 0.0, features.getBaseArea()));
		}
		seeds.sort(Comparator
				.comparing((PathlineSeed seed) -> seed.getFamily().ordinal())
				.thenComparing(PathlineSeed::getComponentName)
				.thenComparingInt(PathlineSeed::getIndex));
		return seeds;
	}

	private void addBodySeeds(List<PathlineSeed> seeds, GeometryFeatures features, RomSettings settings) {
		int seedCount = settings.getBodyMeridianSeedCount();
		double[] xStations = features.getXStations();
		double length = Math.max(1e-6, features.getBodyLength());
		double areaWeight = Math.max(features.getReferenceArea() / seedCount, 1e-6);
		for (int i = 0; i < seedCount; i++) {
			double fraction = seedCount == 1 ? 0.5 : (double) i / (double) (seedCount - 1);
			double x = fraction * length;
			double y = Math.max(1e-6, features.radiusAt(x));
			double z = 0.0;
			if (xStations.length > 0) {
				x = Math.max(xStations[0], Math.min(xStations[xStations.length - 1], x));
				y = Math.max(1e-6, features.radiusAt(x));
			}
			seeds.add(new PathlineSeed(PathlineSeed.SeedFamily.BODY_MERIDIAN, "body", i, x, y, z,
					1.0, 0.0, 0.0, areaWeight));
		}
	}

	private void addFinSeeds(List<PathlineSeed> seeds, GeometryFeatures features, RomSettings settings) {
		for (FinGeometry fin : features.getFins()) {
			int seedCount = settings.getFinSurfaceSeedCount();
			double weight = Math.max(1e-6, fin.getPlanformArea() * Math.max(1, fin.getFinCount()) / seedCount);
			for (int i = 0; i < seedCount; i++) {
				double fraction = seedCount == 1 ? 0.5 : (double) i / (double) (seedCount - 1);
				double chordX = fin.getXStart() + fraction * fin.getRootChord();
				double spanY = fin.getBodyRadiusAtRoot() + 0.5 * fin.getSpan();
				seeds.add(new PathlineSeed(PathlineSeed.SeedFamily.FIN_SURFACE, fin.getComponentName(), i,
						chordX, spanY, 0.0, 1.0, 0.0, 0.0, weight));
			}
		}
	}
}

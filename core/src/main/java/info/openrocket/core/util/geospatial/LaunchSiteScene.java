package info.openrocket.core.util.geospatial;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import info.openrocket.core.util.TerrainFetcher.TerrainData;

/** Immutable set of render-ready layers for a local launch-site scene. */
public record LaunchSiteScene(TerrainData terrain, BufferedImage groundImage,
		List<Building> buildings, List<Road> roads, List<SceneAttribution> attributions,
		double sourceGroundElevationMeters, LayerStatus terrainStatus,
		LayerStatus imageryStatus, LayerStatus contextStatus, String statusMessage,
		boolean procedural) {

	public enum LayerStatus { LOADING, AVAILABLE, UNAVAILABLE, FAILED }
	public record LocalPoint(double eastMeters, double northMeters) { }
	public record Building(List<LocalPoint> outline, List<LocalPoint> roofTriangles, double heightMeters) {
		public Building {
			outline = List.copyOf(outline);
			roofTriangles = List.copyOf(roofTriangles == null ? List.of() : roofTriangles);
		}
		public Building(List<LocalPoint> outline, double heightMeters) {
			this(outline, List.of(), heightMeters);
		}
	}
	public record Road(List<LocalPoint> points, double widthMeters, String roadClass) {
		public Road { points = List.copyOf(points); }
	}

	public LaunchSiteScene {
		buildings = List.copyOf(buildings == null ? List.of() : buildings);
		roads = List.copyOf(roads == null ? List.of() : roads);
		attributions = List.copyOf(attributions == null ? List.of() : attributions);
	}

	public LaunchSiteScene withTerrain(TerrainData value, double sourceElevation,
			SceneAttribution attribution, String message) {
		return new LaunchSiteScene(value, groundImage, buildings, roads, append(attribution), sourceElevation,
				LayerStatus.AVAILABLE, imageryStatus, contextStatus, message, false);
	}

	public LaunchSiteScene withImagery(BufferedImage value, SceneAttribution attribution, String message) {
		return new LaunchSiteScene(terrain, value, buildings, roads, append(attribution), sourceGroundElevationMeters,
				terrainStatus, LayerStatus.AVAILABLE, contextStatus, message, procedural);
	}

	public LaunchSiteScene withContext(List<Building> newBuildings, List<Road> newRoads,
			SceneAttribution attribution, String message) {
		return new LaunchSiteScene(terrain, groundImage, newBuildings, newRoads, append(attribution),
				sourceGroundElevationMeters, terrainStatus, imageryStatus, LayerStatus.AVAILABLE, message, procedural);
	}

	public LaunchSiteScene withFailure(Layer layer, String message) {
		return new LaunchSiteScene(terrain, groundImage, buildings, roads, attributions, sourceGroundElevationMeters,
				layer == Layer.TERRAIN ? LayerStatus.FAILED : terrainStatus,
				layer == Layer.IMAGERY ? LayerStatus.FAILED : imageryStatus,
				layer == Layer.CONTEXT ? LayerStatus.FAILED : contextStatus, message, procedural);
	}

	public enum Layer { TERRAIN, IMAGERY, CONTEXT }

	private List<SceneAttribution> append(SceneAttribution attribution) {
		if (attribution == null || attributions.stream().anyMatch(a -> a.provider().equals(attribution.provider()))) {
			return attributions;
		}
		List<SceneAttribution> result = new ArrayList<>(attributions);
		result.add(attribution);
		return result;
	}
}

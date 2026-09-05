package info.openrocket.core.util.geospatial;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.triangulate.polygon.PolygonTriangulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import info.openrocket.core.arch.SystemInfo;
import info.openrocket.core.util.BuildProperties;
import info.openrocket.core.util.TerrainFetcher.TerrainData;
import info.openrocket.core.util.geospatial.LaunchSiteScene.Building;
import info.openrocket.core.util.geospatial.LaunchSiteScene.Layer;
import info.openrocket.core.util.geospatial.LaunchSiteScene.LocalPoint;
import info.openrocket.core.util.geospatial.LaunchSiteScene.Road;

/** Open-data implementation backed by USGS, Mapzen, OpenAerialMap and OpenStreetMap. */
public final class OpenDataLaunchSiteSceneProvider implements LaunchSiteSceneProvider {
	private static final Logger log = LoggerFactory.getLogger(OpenDataLaunchSiteSceneProvider.class);
	private static final String USGS_DEM = "https://elevation.nationalmap.gov/arcgis/rest/services/3DEPElevation/ImageServer/exportImage";
	private static final String USGS_IMAGE = "https://basemap.nationalmap.gov/arcgis/rest/services/USGSImageryOnly/MapServer/export";
	private static final String MAPZEN = "https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png";
	private static final String OAM_META = "https://api.openaerialmap.org/meta";
	private static final String OVERPASS = "https://overpass-api.de/api/interpreter";
	private static final int TILE_SIZE = 256;
	private static final int MAX_BUILDINGS = 10_000;
	private static final int MAX_ROADS = 10_000;
	private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

	private final ExecutorService executor;
	private final CachedHttp http;
	private final GeoSceneCache cache;

	public OpenDataLaunchSiteSceneProvider() {
		this(new GeoSceneCache(Path.of(SystemInfo.getUserApplicationDirectory().getPath(), "cache", "geospatial")));
	}

	public OpenDataLaunchSiteSceneProvider(GeoSceneCache cache) {
		this.cache = cache;
		AtomicInteger sequence = new AtomicInteger();
		executor = Executors.newFixedThreadPool(3, runnable -> {
			Thread thread = new Thread(runnable, "launch-scenery-" + sequence.incrementAndGet());
			thread.setDaemon(true);
			return thread;
		});
		http = new CachedHttp(cache);
	}

	public void clearCache() throws IOException { cache.clear(); }

	@Override
	public CompletableFuture<LaunchSiteScene> load(LaunchSiteSceneRequest request,
			LaunchSiteScene initialScene, Consumer<LaunchSiteScene> progressListener) {
		AtomicReference<LaunchSiteScene> state = new AtomicReference<>(initialScene);
		CompletableFuture<Void> terrain = CompletableFuture.runAsync(() -> {
			try {
				TerrainLayer layer = loadTerrain(request);
				publish(state, progressListener, scene -> scene.withTerrain(layer.terrain,
						layer.terrain.centerElevation, layer.attribution, "Real-world terrain ready"));
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
			} catch (Exception exception) {
				log.warn("Real-world terrain unavailable; retaining procedural terrain: {}", exception.getMessage());
				publish(state, progressListener, scene -> scene.withFailure(Layer.TERRAIN,
						"Terrain unavailable; using procedural terrain"));
			}
		}, executor);
		CompletableFuture<Void> imagery = CompletableFuture.runAsync(() -> {
			try {
				ImageLayer layer = loadImagery(request);
				publish(state, progressListener, scene -> scene.withImagery(layer.image, layer.attribution,
						"Aerial imagery ready"));
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
			} catch (Exception exception) {
				log.debug("Aerial imagery unavailable", exception);
				publish(state, progressListener, scene -> scene.withFailure(Layer.IMAGERY,
						"Aerial imagery unavailable; using terrain shading"));
			}
		}, executor);
		CompletableFuture<Void> context = CompletableFuture.runAsync(() -> {
			try {
				ContextLayer layer = loadContext(request);
				publish(state, progressListener, scene -> scene.withContext(layer.buildings, layer.roads,
						layer.attribution, "OpenStreetMap context ready"));
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
			} catch (Exception exception) {
				log.debug("OpenStreetMap context unavailable", exception);
				publish(state, progressListener, scene -> scene.withFailure(Layer.CONTEXT,
						"Roads and buildings unavailable"));
			}
		}, executor);
		return CompletableFuture.allOf(terrain, imagery, context).thenApply(unused -> state.get());
	}

	private TerrainLayer loadTerrain(LaunchSiteSceneRequest request) throws Exception {
		request.checkCancelled();
		try {
			return loadUsgsTerrain(request);
		} catch (Exception exception) {
			log.debug("USGS terrain unavailable, trying global Terrarium tiles", exception);
			request.checkCancelled();
			return loadTerrariumTerrain(request);
		}
	}

	private TerrainLayer loadUsgsTerrain(LaunchSiteSceneRequest request) throws Exception {
		Wgs84.Bounds bounds = Wgs84.bounds(request.launchSite(), request.halfExtentMeters());
		if (bounds.crossesAntimeridian()) throw new IOException("USGS request crosses antimeridian");
		Map<String, String> parameters = new LinkedHashMap<>();
		parameters.put("bbox", bbox(bounds));
		parameters.put("bboxSR", "4326");
		parameters.put("imageSR", "4326");
		parameters.put("size", request.terrainGridSize() + "," + request.terrainGridSize());
		parameters.put("format", "tiff");
		parameters.put("pixelType", "F32");
		parameters.put("interpolation", "RSP_BilinearInterpolation");
		parameters.put("f", "image");
		byte[] body = http.get(withQuery(endpoint("openrocket.geo.usgsDem", USGS_DEM), parameters),
				"image/tiff,image/*", 32 * 1024 * 1024);
		request.checkCancelled();
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(body));
		if (image == null) throw new IOException("USGS returned an unreadable elevation image");
		TerrainData terrain = terrainFromRaster(image.getRaster(), request);
		return new TerrainLayer(terrain, attribution("USGS 3DEP", "USGS public-domain elevation data", USGS_DEM));
	}

	private TerrainData terrainFromRaster(Raster raster, LaunchSiteSceneRequest request) throws IOException {
		int n = request.terrainGridSize();
		double[][] elevations = new double[n][n];
		double min = Double.POSITIVE_INFINITY;
		double max = Double.NEGATIVE_INFINITY;
		for (int row = 0; row < n; row++) {
			int sourceY = Math.min(raster.getHeight() - 1,
					(int) Math.round((n - 1 - row) * (raster.getHeight() - 1.0) / (n - 1.0)));
			for (int col = 0; col < n; col++) {
				int sourceX = Math.min(raster.getWidth() - 1,
						(int) Math.round(col * (raster.getWidth() - 1.0) / (n - 1.0)));
				double value = raster.getSampleDouble(sourceX, sourceY, 0);
				if (!Double.isFinite(value) || Math.abs(value) > 100_000) throw new IOException("Elevation contains no-data values");
				elevations[row][col] = value;
				min = Math.min(min, value);
				max = Math.max(max, value);
			}
		}
		double spacing = 2.0 * request.halfExtentMeters() / (n - 1);
		gradeLaunchApron(elevations, spacing, Math.max(5.0, spacing * 1.25));
		double center = elevations[(n - 1) / 2][(n - 1) / 2];
		return new TerrainData(n, spacing, elevations, center, min, max);
	}

	private TerrainLayer loadTerrariumTerrain(LaunchSiteSceneRequest request) throws Exception {
		int n = request.terrainGridSize();
		int zoom = zoomForResolution(request.launchSite().latitudeDeg(),
				2.0 * request.halfExtentMeters() / (n - 1), 15);
		Wgs84.Bounds bounds = Wgs84.bounds(request.launchSite(), request.halfExtentMeters());
		Map<TileKey, BufferedImage> tiles = loadTiles(MAPZEN, bounds, zoom, request, 8 * 1024 * 1024);
		double[][] elevations = new double[n][n];
		double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
		for (int row = 0; row < n; row++) {
			double north = (row - (n - 1) / 2.0) * (2.0 * request.halfExtentMeters() / (n - 1));
			for (int col = 0; col < n; col++) {
				double east = (col - (n - 1) / 2.0) * (2.0 * request.halfExtentMeters() / (n - 1));
				Wgs84.GeoPoint point = Wgs84.fromEnu(request.launchSite(), new Wgs84.EnuPoint(east, north, 0));
				int rgb = sampleTile(tiles, zoom, point.latitudeDeg(), point.longitudeDeg());
				double value = decodeTerrarium(rgb);
				elevations[row][col] = value;
				min = Math.min(min, value); max = Math.max(max, value);
			}
		}
		double spacing = 2.0 * request.halfExtentMeters() / (n - 1);
		gradeLaunchApron(elevations, spacing, Math.max(5.0, spacing * 1.25));
		double center = elevations[(n - 1) / 2][(n - 1) / 2];
		return new TerrainLayer(new TerrainData(n, spacing, elevations, center, min, max),
				attribution("Mapzen Terrain Tiles", "Open elevation sources; see Tilezen attribution", MAPZEN));
	}

	private ImageLayer loadImagery(LaunchSiteSceneRequest request) throws Exception {
		request.checkCancelled();
		try {
			return loadUsgsImagery(request);
		} catch (Exception exception) {
			log.debug("USGS imagery unavailable, trying OpenAerialMap", exception);
			request.checkCancelled();
			return loadOpenAerialMap(request);
		}
	}

	private ImageLayer loadUsgsImagery(LaunchSiteSceneRequest request) throws Exception {
		Wgs84.Bounds bounds = Wgs84.bounds(request.launchSite(), request.halfExtentMeters());
		if (bounds.crossesAntimeridian()) throw new IOException("USGS imagery request crosses antimeridian");
		Map<String, String> parameters = new LinkedHashMap<>();
		parameters.put("bbox", bbox(bounds)); parameters.put("bboxSR", "4326");
		parameters.put("imageSR", "4326");
		parameters.put("size", request.imagerySize() + "," + request.imagerySize());
		parameters.put("format", "png32"); parameters.put("transparent", "true"); parameters.put("f", "image");
		byte[] body = http.get(withQuery(endpoint("openrocket.geo.usgsImagery", USGS_IMAGE), parameters),
				"image/png,image/*", 48 * 1024 * 1024);
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(body));
		if (image == null || opaqueCoverage(image) < 0.02) throw new IOException("USGS imagery has no coverage at this site");
		return new ImageLayer(image, attribution("USGS ImageryOnly", "USGS public-domain orthoimagery", USGS_IMAGE));
	}

	private ImageLayer loadOpenAerialMap(LaunchSiteSceneRequest request) throws Exception {
		Wgs84.Bounds bounds = Wgs84.bounds(request.launchSite(), request.halfExtentMeters());
		List<JsonObject> candidates = new ArrayList<>();
		for (Wgs84.Bounds part : splitBounds(bounds)) {
			Map<String, String> parameters = Map.of("bbox", bbox(part), "limit", "100");
			byte[] body = http.get(withQuery(endpoint("openrocket.geo.oamMeta", OAM_META), parameters),
					"application/json", 8 * 1024 * 1024);
			JsonArray results = JsonParser.parseString(new String(body, StandardCharsets.UTF_8))
					.getAsJsonObject().getAsJsonArray("results");
			if (results != null) for (JsonElement element : results) {
				if (element.isJsonObject()) candidates.add(element.getAsJsonObject());
			}
		}
		if (candidates.isEmpty()) throw new IOException("OpenAerialMap has no coverage");
		candidates.sort(Comparator.comparingDouble(OpenDataLaunchSiteSceneProvider::oamResolution));
		for (JsonObject candidate : candidates) {
			JsonObject properties = candidate.has("properties") ? candidate.getAsJsonObject("properties") : candidate;
			String template = string(properties, "tms");
			if (template == null || !template.contains("{z}")) continue;
			try {
				int zoom = zoomForResolution(request.launchSite().latitudeDeg(),
						2.0 * request.halfExtentMeters() / request.imagerySize(), 19);
				Map<TileKey, BufferedImage> tiles = loadTiles(template, bounds, zoom, request, 12 * 1024 * 1024);
				BufferedImage image = compositeTiles(tiles, bounds, zoom, request.imagerySize());
				return new ImageLayer(image, attribution("OpenAerialMap", "CC BY 4.0 aerial imagery", template));
			} catch (IOException exception) {
				log.debug("OpenAerialMap candidate failed: {}", exception.getMessage());
			}
		}
		throw new IOException("OpenAerialMap coverage could not be loaded");
	}

	private ContextLayer loadContext(LaunchSiteSceneRequest request) throws Exception {
		if (request.featureRadiusMeters() <= 0) throw new IOException("Context layer disabled");
		Wgs84.Bounds bounds = Wgs84.bounds(request.launchSite(), request.featureRadiusMeters());
		StringBuilder query = new StringBuilder("[out:json][timeout:25];(");
		for (Wgs84.Bounds part : splitBounds(bounds)) {
			query.append("way[building](").append(overpassBbox(part)).append(");")
					.append("way[highway~\"^(motorway|trunk|primary|secondary|tertiary|residential|service)$\"](")
					.append(overpassBbox(part)).append(");");
		}
		query.append(");out geom;");
		byte[] body = http.post(endpoint("openrocket.geo.overpass", OVERPASS),
				"data=" + URLEncoder.encode(query.toString(), StandardCharsets.UTF_8), 25 * 1024 * 1024);
		request.checkCancelled();
		JsonArray elements = JsonParser.parseString(new String(body, StandardCharsets.UTF_8))
				.getAsJsonObject().getAsJsonArray("elements");
		List<Building> buildings = new ArrayList<>();
		List<Road> roads = new ArrayList<>();
		if (elements != null) for (JsonElement element : elements) {
			if (!element.isJsonObject()) continue;
			JsonObject way = element.getAsJsonObject();
			JsonObject tags = way.has("tags") ? way.getAsJsonObject("tags") : new JsonObject();
			List<LocalPoint> points = parseGeometry(way.getAsJsonArray("geometry"), request.launchSite());
			if (tags.has("building") && points.size() >= 3 && buildings.size() < MAX_BUILDINGS) {
				double height = buildingHeight(tags);
				Building building = triangulateBuilding(points, height);
				if (building != null) buildings.add(building);
			} else if (tags.has("highway") && points.size() >= 2 && roads.size() < MAX_ROADS) {
				String roadClass = string(tags, "highway");
				roads.add(new Road(points, roadWidth(tags, roadClass), roadClass));
			}
		}
		return new ContextLayer(buildings, roads,
				attribution("OpenStreetMap", "© OpenStreetMap contributors, ODbL", "https://www.openstreetmap.org/copyright"));
	}

	private static Building triangulateBuilding(List<LocalPoint> outline, double height) {
		try {
			List<LocalPoint> closed = new ArrayList<>(outline);
			if (!closed.get(0).equals(closed.get(closed.size() - 1))) closed.add(closed.get(0));
			Coordinate[] coordinates = closed.stream().map(p -> new Coordinate(p.eastMeters(), p.northMeters())).toArray(Coordinate[]::new);
			LinearRing ring = GEOMETRY_FACTORY.createLinearRing(coordinates);
			Polygon polygon = GEOMETRY_FACTORY.createPolygon(ring);
			if (!polygon.isValid() || polygon.getArea() < 1.0) return null;
			Geometry triangles = PolygonTriangulator.triangulate(polygon);
			List<LocalPoint> roof = new ArrayList<>();
			for (int i = 0; i < triangles.getNumGeometries(); i++) {
				Coordinate[] triangle = triangles.getGeometryN(i).getCoordinates();
				for (int j = 0; j < Math.min(3, triangle.length); j++) roof.add(new LocalPoint(triangle[j].x, triangle[j].y));
			}
			return new Building(closed, roof, height);
		} catch (RuntimeException exception) {
			return null;
		}
	}

	private static List<LocalPoint> parseGeometry(JsonArray geometry, Wgs84.GeoPoint origin) {
		List<LocalPoint> points = new ArrayList<>();
		if (geometry == null) return points;
		for (JsonElement element : geometry) {
			if (!element.isJsonObject()) continue;
			JsonObject point = element.getAsJsonObject();
			if (!point.has("lat") || !point.has("lon")) continue;
			Wgs84.EnuPoint local = Wgs84.toEnu(origin,
					new Wgs84.GeoPoint(point.get("lat").getAsDouble(), point.get("lon").getAsDouble(), 0));
			points.add(new LocalPoint(local.eastMeters(), local.northMeters()));
		}
		return points;
	}

	static double decodeTerrarium(int rgb) {
		return ((rgb >> 16) & 0xff) * 256.0 + ((rgb >> 8) & 0xff) + (rgb & 0xff) / 256.0 - 32768.0;
	}

	static double buildingHeight(JsonObject tags) {
		double height = parseMeasurement(string(tags, "height"));
		if (height > 0) return Math.min(500, height);
		double levels = parseMeasurement(string(tags, "building:levels"));
		return levels > 0 ? Math.min(500, levels * 3.0) : 6.0;
	}

	static double roadWidth(JsonObject tags, String roadClass) {
		double width = parseMeasurement(string(tags, "width"));
		if (width > 0) return Math.min(40, width);
		double lanes = parseMeasurement(string(tags, "lanes"));
		if (lanes > 0) return Math.min(40, Math.max(3, lanes * 3.5));
		return switch (roadClass == null ? "" : roadClass) {
			case "motorway" -> 14; case "trunk" -> 12; case "primary" -> 10;
			case "secondary" -> 8; case "tertiary" -> 7; case "residential" -> 6; default -> 4;
		};
	}

	private static double parseMeasurement(String value) {
		if (value == null) return Double.NaN;
		Matcher matcher = Pattern.compile("[-+]?[0-9]*\\.?[0-9]+").matcher(value);
		if (!matcher.find()) return Double.NaN;
		try {
			double parsed = Double.parseDouble(matcher.group());
			return value.toLowerCase(Locale.ROOT).contains("ft") ? parsed * 0.3048 : parsed;
		} catch (NumberFormatException exception) { return Double.NaN; }
	}

	private Map<TileKey, BufferedImage> loadTiles(String template, Wgs84.Bounds bounds, int zoom,
			LaunchSiteSceneRequest request, int maxBytes) throws Exception {
		int tileCount = 1 << zoom;
		double westX = tileX(bounds.westDeg(), zoom);
		double eastX = tileX(bounds.eastDeg(), zoom);
		if (bounds.crossesAntimeridian() || eastX < westX) eastX += tileCount;
		double northY = tileY(bounds.northDeg(), zoom);
		double southY = tileY(bounds.southDeg(), zoom);
		int x0 = (int) Math.floor(westX) - 1, x1 = (int) Math.floor(eastX) + 1;
		int y0 = Math.max(0, (int) Math.floor(northY) - 1);
		int y1 = Math.min(tileCount - 1, (int) Math.floor(southY) + 1);
		if ((long) (x1 - x0 + 1) * (y1 - y0 + 1) > 100) throw new IOException("Tile request is too large");
		Map<TileKey, BufferedImage> result = new HashMap<>();
		for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) {
			request.checkCancelled();
			int wrappedX = Math.floorMod(x, tileCount);
			String url = template.replace("{z}", Integer.toString(zoom))
					.replace("{x}", Integer.toString(wrappedX)).replace("{y}", Integer.toString(y));
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(http.get(url, "image/png,image/*", maxBytes)));
			if (image == null) throw new IOException("Unreadable map tile");
			result.put(new TileKey(x, y), image);
		}
		return result;
	}

	private static int sampleTile(Map<TileKey, BufferedImage> tiles, int zoom, double latitude, double longitude) throws IOException {
		double x = tileX(longitude, zoom), y = tileY(latitude, zoom);
		TileKey key = new TileKey((int) Math.floor(x), (int) Math.floor(y));
		BufferedImage image = tiles.get(key);
		if (image == null) {
			int count = 1 << zoom;
			image = tiles.get(new TileKey(key.x + count, key.y));
		}
		if (image == null) throw new IOException("Terrain tile missing from mosaic");
		int px = Math.min(image.getWidth() - 1, Math.max(0, (int) ((x - Math.floor(x)) * image.getWidth())));
		int py = Math.min(image.getHeight() - 1, Math.max(0, (int) ((y - Math.floor(y)) * image.getHeight())));
		return image.getRGB(px, py);
	}

	private static BufferedImage compositeTiles(Map<TileKey, BufferedImage> tiles, Wgs84.Bounds bounds,
			int zoom, int size) {
		int count = 1 << zoom;
		double westPx = tileX(bounds.westDeg(), zoom) * TILE_SIZE;
		double eastPx = tileX(bounds.eastDeg(), zoom) * TILE_SIZE;
		if (bounds.crossesAntimeridian() || eastPx < westPx) eastPx += count * TILE_SIZE;
		double northPx = tileY(bounds.northDeg(), zoom) * TILE_SIZE;
		double southPx = tileY(bounds.southDeg(), zoom) * TILE_SIZE;
		BufferedImage target = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = target.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		for (Map.Entry<TileKey, BufferedImage> entry : tiles.entrySet()) {
			TileKey key = entry.getKey();
			int dx0 = (int) Math.floor(((key.x * TILE_SIZE - westPx) / (eastPx - westPx)) * size);
			int dx1 = (int) Math.ceil((((key.x + 1) * TILE_SIZE - westPx) / (eastPx - westPx)) * size);
			int dy0 = (int) Math.floor(((key.y * TILE_SIZE - northPx) / (southPx - northPx)) * size);
			int dy1 = (int) Math.ceil((((key.y + 1) * TILE_SIZE - northPx) / (southPx - northPx)) * size);
			graphics.drawImage(entry.getValue(), dx0, dy0, dx1, dy1, 0, 0,
					entry.getValue().getWidth(), entry.getValue().getHeight(), null);
		}
		graphics.dispose();
		return target;
	}

	private static void gradeLaunchApron(double[][] elevation, double spacing, double apronRadius) {
		int n = elevation.length, mid = (n - 1) / 2;
		double center = elevation[mid][mid];
		double flatRadius = apronRadius * 0.45;
		for (int row = 0; row < n; row++) for (int col = 0; col < n; col++) {
			double distance = Math.hypot((col - mid) * spacing, (row - mid) * spacing);
			if (distance >= apronRadius) continue;
			double t = Math.max(0, (distance - flatRadius) / Math.max(1e-9, apronRadius - flatRadius));
			double blend = t * t * (3 - 2 * t);
			elevation[row][col] = center + (elevation[row][col] - center) * blend;
		}
	}

	private static double opaqueCoverage(BufferedImage image) {
		if (!image.getColorModel().hasAlpha()) return 1.0;
		long opaque = 0, samples = 0;
		int stepX = Math.max(1, image.getWidth() / 64), stepY = Math.max(1, image.getHeight() / 64);
		for (int y = 0; y < image.getHeight(); y += stepY) for (int x = 0; x < image.getWidth(); x += stepX) {
			if (((image.getRGB(x, y) >>> 24) & 0xff) > 16) opaque++;
			samples++;
		}
		return samples == 0 ? 0 : opaque / (double) samples;
	}

	private static int zoomForResolution(double latitude, double metersPerPixel, int maxZoom) {
		double circumference = 2.0 * Math.PI * 6378137.0 * Math.max(0.05, Math.cos(Math.toRadians(latitude)));
		int zoom = (int) Math.floor(Math.log(circumference / (TILE_SIZE * Math.max(0.25, metersPerPixel))) / Math.log(2));
		return Math.max(0, Math.min(maxZoom, zoom));
	}

	private static double tileX(double longitude, int zoom) { return (Wgs84.normalizeLongitude(longitude) + 180.0) / 360.0 * (1 << zoom); }
	private static double tileY(double latitude, int zoom) {
		double lat = Math.toRadians(Math.max(-85.05112878, Math.min(85.05112878, latitude)));
		double mercator = Math.log(Math.tan(lat) + 1.0 / Math.cos(lat));
		return (1.0 - mercator / Math.PI) / 2.0 * (1 << zoom);
	}

	private static String bbox(Wgs84.Bounds bounds) { return bounds.westDeg() + "," + bounds.southDeg() + "," + bounds.eastDeg() + "," + bounds.northDeg(); }
	private static String overpassBbox(Wgs84.Bounds bounds) { return bounds.southDeg() + "," + bounds.westDeg() + "," + bounds.northDeg() + "," + bounds.eastDeg(); }
	private static List<Wgs84.Bounds> splitBounds(Wgs84.Bounds bounds) {
		if (!bounds.crossesAntimeridian()) return List.of(bounds);
		return List.of(
				new Wgs84.Bounds(bounds.southDeg(), bounds.westDeg(), bounds.northDeg(), 180.0, false),
				new Wgs84.Bounds(bounds.southDeg(), -180.0, bounds.northDeg(), bounds.eastDeg(), false));
	}
	private static String endpoint(String property, String fallback) { return System.getProperty(property, fallback); }
	private static String withQuery(String base, Map<String, String> parameters) {
		StringBuilder result = new StringBuilder(base).append(base.contains("?") ? '&' : '?');
		parameters.forEach((key, value) -> result.append(URLEncoder.encode(key, StandardCharsets.UTF_8)).append('=')
				.append(URLEncoder.encode(value, StandardCharsets.UTF_8)).append('&'));
		result.setLength(result.length() - 1);
		return result.toString();
	}
	private static String string(JsonObject object, String key) { return object != null && object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : null; }
	private static double oamResolution(JsonObject item) {
		JsonObject properties = item.has("properties") ? item.getAsJsonObject("properties") : item;
		try { return properties.get("resolution").getAsDouble(); } catch (Exception exception) { return Double.POSITIVE_INFINITY; }
	}
	private static SceneAttribution attribution(String provider, String license, String source) { return new SceneAttribution(provider, license, URI.create(source.replace("{z}", "0").replace("{x}", "0").replace("{y}", "0")), Instant.now()); }

	private static void publish(AtomicReference<LaunchSiteScene> state, Consumer<LaunchSiteScene> listener,
			java.util.function.UnaryOperator<LaunchSiteScene> update) {
		LaunchSiteScene next = state.updateAndGet(update);
		if (listener != null) listener.accept(next);
	}

	@Override
	public void close() { executor.shutdownNow(); }

	private record TerrainLayer(TerrainData terrain, SceneAttribution attribution) { }
	private record ImageLayer(BufferedImage image, SceneAttribution attribution) { }
	private record ContextLayer(List<Building> buildings, List<Road> roads, SceneAttribution attribution) { }
	private record TileKey(int x, int y) { }

	private static final class CachedHttp {
		private static final Pattern MAX_AGE = Pattern.compile("(?:^|,)\\s*max-age=(\\d+)", Pattern.CASE_INSENSITIVE);
		private final GeoSceneCache cache;
		private final String userAgent = "Project-Imperia/" + BuildProperties.getVersion() + " (+https://openrocket.info/)";

		CachedHttp(GeoSceneCache cache) { this.cache = cache; }

		byte[] get(String url, String accept, int maxBytes) throws IOException, InterruptedException {
			return request("GET", url, null, accept, maxBytes);
		}
		byte[] post(String url, String body, int maxBytes) throws IOException, InterruptedException {
			return request("POST", url, body, "application/json", maxBytes);
		}

		private byte[] request(String method, String url, String body, String accept, int maxBytes) throws IOException, InterruptedException {
			String key = method + "\n" + url + "\n" + (body == null ? "" : body);
			Optional<GeoSceneCache.Entry> cached = cache.read(key);
			if (cached.isPresent() && cached.get().fresh()) return cached.get().body();
			IOException failure = null;
			for (int attempt = 0; attempt < 2; attempt++) {
				HttpURLConnection connection = null;
				try {
					connection = (HttpURLConnection) new URL(url).openConnection();
					connection.setRequestMethod(method); connection.setConnectTimeout(10_000); connection.setReadTimeout(30_000);
					connection.setRequestProperty("User-Agent", userAgent); connection.setRequestProperty("Accept", accept);
					if (cached.isPresent()) {
						if (!cached.get().etag().isBlank()) connection.setRequestProperty("If-None-Match", cached.get().etag());
						if (!cached.get().lastModified().isBlank()) connection.setRequestProperty("If-Modified-Since", cached.get().lastModified());
					}
					if (body != null) {
						connection.setDoOutput(true); connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
						connection.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
					}
					int status = connection.getResponseCode();
					if (status == HttpURLConnection.HTTP_NOT_MODIFIED && cached.isPresent()) {
						writeCache(key, cached.get().body(), expiry(connection), cached.get().etag(), cached.get().lastModified());
						return cached.get().body();
					}
					if (status < 200 || status >= 300) throw new HttpStatusException(status, connection.getHeaderField("Retry-After"));
					byte[] response;
					try (InputStream input = connection.getInputStream()) {
						response = input.readNBytes(maxBytes + 1);
					}
					if (response.length == 0 || response.length > maxBytes) throw new IOException("Geospatial response has invalid size");
					writeCache(key, response, expiry(connection), connection.getHeaderField("ETag"), connection.getHeaderField("Last-Modified"));
					return response;
				} catch (HttpStatusException exception) {
					failure = exception;
					if (exception.status >= 400 && exception.status < 500 && exception.status != 429) break;
					if (attempt == 0) Thread.sleep(exception.retryMillis());
				} catch (IOException exception) {
					failure = exception;
					if (attempt == 0) Thread.sleep(2_000);
				} finally { if (connection != null) connection.disconnect(); }
			}
			if (cached.isPresent()) return cached.get().body();
			throw failure == null ? new IOException("Geospatial request failed") : failure;
		}

		private void writeCache(String key, byte[] response, long expires, String etag, String lastModified) {
			try {
				cache.write(key, response, expires, etag, lastModified);
			} catch (IOException exception) {
				log.debug("Could not write geospatial cache; continuing with downloaded data", exception);
			}
		}

		private static long expiry(HttpURLConnection connection) {
			String cacheControl = connection.getHeaderField("Cache-Control");
			if (cacheControl != null) {
				Matcher matcher = MAX_AGE.matcher(cacheControl);
				if (matcher.find()) try { return System.currentTimeMillis() + Long.parseLong(matcher.group(1)) * 1000; }
				catch (NumberFormatException ignored) { }
			}
			String expires = connection.getHeaderField("Expires");
			if (expires != null) try { return ZonedDateTime.parse(expires, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli(); }
			catch (DateTimeParseException ignored) { }
			return System.currentTimeMillis() + GeoSceneCache.DEFAULT_FRESHNESS.toMillis();
		}
	}

	private static final class HttpStatusException extends IOException {
		final int status; final String retryAfter;
		HttpStatusException(int status, String retryAfter) { super("Geospatial service returned HTTP " + status); this.status = status; this.retryAfter = retryAfter; }
		long retryMillis() {
			try { return Math.min(30_000, Math.max(1_000, Long.parseLong(retryAfter) * 1000)); }
			catch (Exception exception) { return 2_000; }
		}
	}
}

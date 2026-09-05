package info.openrocket.swing.gui.simulation;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.awt.geom.AffineTransform;
import java.net.URI;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.style.Graphic;
import org.geotools.api.style.Mark;
import org.geotools.api.style.Style;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.map.FeatureLayer;
import org.geotools.map.Layer;
import org.geotools.map.MapContent;
import org.geotools.ows.wms.WebMapServer;
import org.geotools.ows.wms.map.WMSLayer;
import org.geotools.referencing.CRS;
import org.geotools.renderer.RenderListener;
import org.geotools.renderer.lite.StreamingRenderer;
import org.geotools.styling.StyleBuilder;
import org.geotools.swing.JMapPane;
import org.geotools.swing.event.MapPaneAdapter;
import org.geotools.swing.event.MapPaneEvent;
import org.geotools.util.factory.GeoTools;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Native GeoTools Swing map for geographic Monte Carlo overlays.
 *
 * <p>GeoTools renders the in-memory launch, landing and dispersion features over an optional USGS
 * WMS topographic layer. A lightweight coordinate grid remains available while that layer loads
 * and when the application is offline.</p>
 */
final class GeoToolsMapPanel extends JMapPane {
	private static final Logger log = LoggerFactory.getLogger(GeoToolsMapPanel.class);
	private static final String BASE_MAP_URL = "https://basemap.nationalmap.gov/arcgis/services/"
			+ "USGSTopo/MapServer/WMSServer?SERVICE=WMS&REQUEST=GetCapabilities";
	private static final String BASE_MAP_LAYER_NAME = "0";
	private static final int BASE_MAP_TIMEOUT_MILLIS = 8_000;
	private static final int TILE_SIZE = 256;
	private static final int MIN_ZOOM = 1;
	private static final int MAX_ZOOM = 19;
	private static final int MAX_FIT_ZOOM = 18;
	private static final int FIT_PADDING = 48;
	private static final int KEY_PAN_PIXELS = 60;
	private static final double WHEEL_ZOOM_STEP = 1.0;
	private static final double PRECISE_ZOOM_STEP = 0.35;
	private static final double SCROLL_PAN_PIXELS = 42.0;
	private static final long TRACKPAD_GESTURE_MILLIS = 600;
	private static final double WEB_MERCATOR_LIMIT = 20_037_508.342789244;
	private static final double WEB_MERCATOR_WORLD = WEB_MERCATOR_LIMIT * 2.0;
	private static final double MAX_LATITUDE = 85.05112878;
	private static final CoordinateReferenceSystem WEB_MERCATOR = decodeWebMercator();
	private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();
	enum MarkerType {
		LAUNCH, MEAN, LANDING
	}

	enum SigmaLevel {
		ONE, TWO, THREE, OTHER
	}

	record GeoPoint(double latitudeDeg, double longitudeDeg) { }

	record MapMarker(String id, String label, String details, GeoPoint point,
			Color color, MarkerType type) { }

	record MapPolyline(String label, List<GeoPoint> points, Color color, float width,
			SigmaLevel sigmaLevel) {
		MapPolyline {
			points = List.copyOf(points);
			sigmaLevel = sigmaLevel == null ? SigmaLevel.OTHER : sigmaLevel;
		}

		MapPolyline(String label, List<GeoPoint> points, Color color, float width) {
			this(label, points, color, width, SigmaLevel.OTHER);
		}
	}

	private record MarkerStyle(Color color, MarkerType type) { }
	private record LineStyle(Color color, float width, SigmaLevel sigmaLevel) { }
	private record BaseMapSource(WebMapServer server, org.geotools.ows.wms.Layer layer) { }

	private final StyleBuilder styleBuilder = new StyleBuilder();
	private final AtomicLong renderSequence = new AtomicLong();
	private final AtomicLong renderedFeatures = new AtomicLong();
	private final AtomicLong renderErrors = new AtomicLong();
	private final AtomicLong totalRenderErrors = new AtomicLong();
	private final AtomicLong renderStartedNanos = new AtomicLong();
	private final AtomicBoolean baseMapLoadStarted = new AtomicBoolean();
	private final CountDownLatch baseMapAttemptFinished = new CountDownLatch(1);
	private final List<MapContent> retiredMapContents = new ArrayList<>();
	private final EnumMap<MarkerType, List<Layer>> markerLayers = new EnumMap<>(MarkerType.class);
	private final EnumMap<SigmaLevel, List<Layer>> sigmaLayers = new EnumMap<>(SigmaLevel.class);
	private final EnumMap<MarkerType, Boolean> markerVisibility = new EnumMap<>(MarkerType.class);
	private final EnumMap<SigmaLevel, Boolean> sigmaVisibility = new EnumMap<>(SigmaLevel.class);
	private volatile boolean renderPending;
	private volatile boolean renderInProgress;
	private volatile boolean disposed;
	private volatile BaseMapSource baseMapSource;
	private volatile WMSLayer activeBaseMapLayer;
	private volatile BufferedImage stableImage;
	private volatile boolean stableImageVisible;
	private volatile String baseMapStatus = "GeoTools vector overlays ready; loading USGS basemap";
	private List<MapMarker> markers = List.of();
	private List<MapPolyline> polylines = List.of();
	private List<GeoPoint> viewRegion = List.of();
	private double centerLatitudeDeg;
	private double centerLongitudeDeg;
	private double zoom = 13.0;
	private boolean fitPending;
	private long lastPreciseScrollMillis;
	private java.awt.Point dragOrigin;
	private double dragCenterX;
	private double dragCenterY;
	private Consumer<GeoPoint> coordinateListener = point -> { };
	private Consumer<MapMarker> markerListener = marker -> { };
	private Consumer<String> statusListener = status -> { };

	GeoToolsMapPanel() {
		super(new MapContent());
		for (MarkerType type : MarkerType.values()) markerVisibility.put(type, true);
		for (SigmaLevel level : SigmaLevel.values()) sigmaVisibility.put(level, true);
		StreamingRenderer renderer = new StreamingRenderer();
		installRendererDiagnostics(renderer);
		installMapPaneDiagnostics();
		setRenderer(renderer);
		getMapContent().getViewport().setCoordinateReferenceSystem(WEB_MERCATOR);
		getMapContent().getViewport().setMatchingAspectRatio(true);
		setOpaque(true);
		setBackground(new Color(0xF2, 0xF4, 0xF5));
		setPreferredSize(new Dimension(720, 470));
		setMinimumSize(new Dimension(320, 240));
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setFocusable(true);
		setToolTipText("<html>GeoTools map with USGS topographic context<br>Drag or two-finger scroll to pan "
				+ "&middot; mouse wheel, pinch, or ⌘/Ctrl + scroll to zoom<br>"
				+ "Double-click to zoom in &middot; arrow keys pan &middot; "
				+ "+/− zoom &middot; click a landing marker for details</html>");
		installMouseControls();
		installKeyboardControls();
		installLayoutListener();
		updateDisplayArea();
		log.info("GeoTools map initialized: version={}, java={}, headless={}, CRS={}, renderer={}",
				GeoTools.getVersion(), System.getProperty("java.version"),
				java.awt.GraphicsEnvironment.isHeadless(), WEB_MERCATOR.getName(),
				renderer.getClass().getName());
	}

	@Override
	public void addNotify() {
		super.addNotify();
		startBaseMapLoad();
	}

	void setOverlays(List<MapMarker> newMarkers, List<MapPolyline> newPolylines) {
		setOverlays(newMarkers, newPolylines, () -> { });
	}

	/**
	 * Replace all overlays and apply the related viewport change as one render transaction.
	 * GeoTools rejects a render submission while its previous asynchronous render is active, so
	 * rebuilding layers and then fitting in separate calls can leave the cached image on the old
	 * viewport. Suppress intermediate submissions and render the final state exactly once.
	 */
	void setOverlays(List<MapMarker> newMarkers, List<MapPolyline> newPolylines,
			Runnable viewportUpdate) {
		setIgnoreRepaint(true);
		try {
			markers = List.copyOf(newMarkers);
			polylines = List.copyOf(newPolylines);
			long validMarkers = markers.stream().filter(marker -> isValid(marker.point())).count();
			long validPolylines = polylines.stream().filter(polyline ->
					polyline.points().stream().filter(GeoToolsMapPanel::isValid).count() >= 2).count();
			log.info("Updating GeoTools overlays: markers={} (valid={}), polylines={} (valid={}), "
					+ "center=({}, {}), zoom={}, component={}x{}",
					markers.size(), validMarkers, polylines.size(), validPolylines,
					centerLatitudeDeg, centerLongitudeDeg, zoom, getWidth(), getHeight());
			rebuildFeatureLayers();
			viewportUpdate.run();
		} finally {
			setIgnoreRepaint(false);
		}
		requestFullRender("overlay transaction completed");
		statusListener.accept(baseMapStatus);
	}

	void setMarkerTypeVisible(MarkerType type, boolean visible) {
		if (type == null || visible == markerVisibility.getOrDefault(type, true)) return;
		markerVisibility.put(type, visible);
		setLayerGroupVisible("marker " + type, markerLayers.get(type), visible);
	}

	void setSigmaVisible(SigmaLevel level, boolean visible) {
		if (level == null || visible == sigmaVisibility.getOrDefault(level, true)) return;
		sigmaVisibility.put(level, visible);
		setLayerGroupVisible("sigma " + level, sigmaLayers.get(level), visible);
	}

	boolean isMarkerTypeVisible(MarkerType type) {
		return markerVisibility.getOrDefault(type, true);
	}

	boolean isSigmaVisible(SigmaLevel level) {
		return sigmaVisibility.getOrDefault(level, true);
	}

	private void setLayerGroupVisible(String group, List<Layer> layers, boolean visible) {
		List<Layer> targets = layers == null ? List.of() : List.copyOf(layers);
		setIgnoreRepaint(true);
		try {
			for (Layer layer : targets) layer.setVisible(visible);
		} finally {
			setIgnoreRepaint(false);
		}
		log.info("GeoTools visibility changed: group={}, visible={}, matchedLayers={}, totalLayers={}",
				group, visible, targets.size(), getMapContent().layers().size());
		requestFullRender("visibility changed for " + group);
	}

	void setViewRegion(List<GeoPoint> points) {
		if (points == null) {
			viewRegion = List.of();
		} else {
			viewRegion = points.stream().filter(GeoToolsMapPanel::isValid).toList();
		}
		log.debug("GeoTools view region updated: supplied={}, valid={}",
				points == null ? 0 : points.size(), viewRegion.size());
	}

	void setCoordinateListener(Consumer<GeoPoint> listener) {
		coordinateListener = listener == null ? point -> { } : listener;
	}

	void setMarkerListener(Consumer<MapMarker> listener) {
		markerListener = listener == null ? marker -> { } : listener;
	}

	void setStatusListener(Consumer<String> listener) {
		statusListener = listener == null ? status -> { } : listener;
		statusListener.accept(baseMapStatus);
	}

	void centerOn(double latitudeDeg, double longitudeDeg, int requestedZoom) {
		fitPending = false;
		centerLatitudeDeg = clampLatitude(latitudeDeg);
		centerLongitudeDeg = normalizeLongitude(longitudeDeg);
		zoom = clampZoom(requestedZoom);
		updateDisplayArea();
	}

	void zoomIn() {
		setZoom(zoom + 1.0);
	}

	void zoomOut() {
		setZoom(zoom - 1.0);
	}

	void fitToOverlays() {
		fitPending = true;
		applyPendingFit();
	}

	private void applyPendingFit() {
		if (!fitPending) return;
		List<GeoPoint> points = overlayPoints();
		if (points.isEmpty()) points = viewRegion;
		if (points.isEmpty()) {
			fitPending = false;
			return;
		}
		if (getWidth() <= 0 || getHeight() <= 0) return;

		double meanLongitude = circularMeanLongitude(points);
		double minLatitude = Double.POSITIVE_INFINITY;
		double maxLatitude = Double.NEGATIVE_INFINITY;
		double minLongitudeOffset = Double.POSITIVE_INFINITY;
		double maxLongitudeOffset = Double.NEGATIVE_INFINITY;
		for (GeoPoint point : points) {
			if (!isValid(point)) continue;
			minLatitude = Math.min(minLatitude, point.latitudeDeg());
			maxLatitude = Math.max(maxLatitude, point.latitudeDeg());
			double offset = longitudeOffset(point.longitudeDeg(), meanLongitude);
			minLongitudeOffset = Math.min(minLongitudeOffset, offset);
			maxLongitudeOffset = Math.max(maxLongitudeOffset, offset);
		}
		if (!Double.isFinite(minLatitude)) {
			fitPending = false;
			return;
		}

		double west = meanLongitude + minLongitudeOffset;
		double east = meanLongitude + maxLongitudeOffset;
		double minX = mercatorX(west);
		double maxX = mercatorX(east);
		double minY = mercatorY(minLatitude);
		double maxY = mercatorY(maxLatitude);
		double xSpan = Math.max(1.0, maxX - minX);
		double ySpan = Math.max(1.0, maxY - minY);
		int availableWidth = Math.max(64, getWidth() - 2 * FIT_PADDING);
		int availableHeight = Math.max(64, getHeight() - 2 * FIT_PADDING);
		int best = MIN_ZOOM;
		for (int candidate = MAX_FIT_ZOOM; candidate >= MIN_ZOOM; candidate--) {
			double resolution = resolution(candidate);
			if (xSpan / resolution <= availableWidth && ySpan / resolution <= availableHeight) {
				best = candidate;
				break;
			}
		}
		zoom = best;
		centerLongitudeDeg = normalizeLongitude(longitudeFromMercatorX((minX + maxX) / 2.0));
		centerLatitudeDeg = latitudeFromMercatorY((minY + maxY) / 2.0);
		fitPending = false;
		updateDisplayArea();
	}

	int getZoom() {
		return (int) Math.round(zoom);
	}

	double getZoomLevel() {
		return zoom;
	}

	int getMarkerCount() {
		return markers.size();
	}

	int getPolylineCount() {
		return polylines.size();
	}

	int getFeatureLayerCount() {
		return getMapContent().layers().size();
	}

	long getLastRenderedFeatureCount() {
		return renderedFeatures.get();
	}

	long getLastRenderErrorCount() {
		return renderErrors.get();
	}

	long getTotalRenderErrorCount() {
		return totalRenderErrors.get();
	}

	boolean isRenderBusy() {
		return renderInProgress || renderPending;
	}

	boolean isBaseMapReady() {
		return baseMapSource != null;
	}

	boolean awaitBaseMapAttempt(long timeout, TimeUnit unit) throws InterruptedException {
		return baseMapAttemptFinished.await(timeout, unit);
	}

	String getBaseMapStatus() {
		return baseMapStatus;
	}

	/**
	 * Render the current GeoTools content without requiring a displayable Swing window.
	 * JMapPane's normal renderer is asynchronous and only starts once the component has a
	 * native peer, so diagnostics and CI snapshots use StreamingRenderer directly.
	 */
	BufferedImage renderSnapshot(int width, int height) {
		if (width <= 0 || height <= 0) {
			throw new IllegalArgumentException("Snapshot dimensions must be positive");
		}
		long startedNanos = System.nanoTime();
		AtomicLong featureCount = new AtomicLong();
		AtomicReference<Exception> renderFailure = new AtomicReference<>();
		StreamingRenderer snapshotRenderer = new StreamingRenderer();
		snapshotRenderer.setMapContent(getMapContent());
		snapshotRenderer.addRenderListener(new RenderListener() {
			@Override
			public void featureRenderer(SimpleFeature feature) {
				featureCount.incrementAndGet();
			}

			@Override
			public void errorOccurred(Exception exception) {
				renderFailure.compareAndSet(null, exception);
				log.error("GeoTools snapshot renderer failed: displayArea={}, layers={}",
						describeBounds(getDisplayArea()), getMapContent().layers().size(), exception);
			}
		});

		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try {
			graphics.setColor(getBackground());
			graphics.fillRect(0, 0, width, height);
			snapshotRenderer.paint(graphics, new Rectangle(width, height), getDisplayArea());
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_ON);
			drawCoordinateGrid(graphics);
			drawScaleBar(graphics);
		} finally {
			graphics.dispose();
		}
		if (renderFailure.get() != null) {
			throw new IllegalStateException("GeoTools snapshot renderer reported an error",
					renderFailure.get());
		}
		log.info("GeoTools snapshot rendered: features={}, layers={}, dimensions={}x{}, elapsedMs={}",
				featureCount.get(), getMapContent().layers().size(), width, height,
				(System.nanoTime() - startedNanos) / 1_000_000L);
		return image;
	}

	/** Release the executors created by JMapPane after a standalone diagnostic window closes. */
	void shutdownRendering() {
		disposed = true;
		if (renderingExecutor != null) renderingExecutor.shutdown();
		paneTaskExecutor.shutdownNow();
		disposeRetiredMapContents();
		getMapContent().dispose();
		log.info("GeoTools map rendering resources shut down");
	}

	GeoPoint getCenter() {
		return new GeoPoint(centerLatitudeDeg, centerLongitudeDeg);
	}

	private void rebuildFeatureLayers() {
		MapContent previousContent = getMapContent();
		MapContent content = new MapContent();
		content.getViewport().setCoordinateReferenceSystem(WEB_MERCATOR);
		content.getViewport().setMatchingAspectRatio(true);

		markerLayers.clear();
		sigmaLayers.clear();
		activeBaseMapLayer = null;
		BaseMapSource source = baseMapSource;
		if (source != null) {
			WMSLayer baseLayer = OpenRocketGisView.baseMapLayer(source.server(), source.layer());
			activeBaseMapLayer = baseLayer;
			content.addLayer(baseLayer);
			log.debug("Added GeoTools WMS basemap layer: name={}, title={}, nativeWebMercator={}",
					source.layer().getName(), source.layer().getTitle(),
					baseLayer.isNativelySupported(WEB_MERCATOR));
		}

		Map<LineStyle, List<Polygon>> polygonsByStyle = new LinkedHashMap<>();
		Map<LineStyle, List<LineString>> linesByStyle = new LinkedHashMap<>();
		for (MapPolyline polyline : polylines) {
			LineStyle key = new LineStyle(polyline.color(), polyline.width(), polyline.sigmaLevel());
			Polygon polygon = polygonGeometry(polyline.points());
			if (polyline.sigmaLevel() != SigmaLevel.OTHER && polygon != null) {
				polygonsByStyle.computeIfAbsent(key, ignored -> new ArrayList<>()).add(polygon);
			} else {
				LineString geometry = lineGeometry(polyline.points());
				if (geometry != null) linesByStyle.computeIfAbsent(key, ignored -> new ArrayList<>()).add(geometry);
			}
		}
		for (Map.Entry<LineStyle, List<Polygon>> entry : polygonsByStyle.entrySet()) {
			FeatureLayer layer = OpenRocketGisView.featureLayer(
					"sigma-" + entry.getKey().sigmaLevel().name().toLowerCase(java.util.Locale.ROOT),
					OpenRocketGisView.POLYGON_TYPE, entry.getValue(),
					OpenRocketGisView.polygonStyle(entry.getKey().color(), entry.getKey().width()));
			layer.setVisible(sigmaVisibility.getOrDefault(entry.getKey().sigmaLevel(), true));
			content.addLayer(layer);
			sigmaLayers.computeIfAbsent(entry.getKey().sigmaLevel(), ignored -> new ArrayList<>()).add(layer);
		}
		for (Map.Entry<LineStyle, List<LineString>> entry : linesByStyle.entrySet()) {
			Style style = styleBuilder.createStyle(styleBuilder.createLineSymbolizer(
					entry.getKey().color(), entry.getKey().width()));
			FeatureLayer layer = OpenRocketGisView.featureLayer(
					"dispersion-" + entry.getKey().sigmaLevel().name().toLowerCase(java.util.Locale.ROOT),
					OpenRocketGisView.LINE_TYPE, entry.getValue(), style);
			layer.setTitle("dispersion-" + linesByStyle.size() + "-" + content.layers().size());
			content.addLayer(layer);
			sigmaLayers.computeIfAbsent(entry.getKey().sigmaLevel(), ignored -> new ArrayList<>()).add(layer);
			log.debug("Added GeoTools line layer: title={}, features={}, bounds={}",
					layer.getTitle(), entry.getValue().size(), describeBounds(layer.getBounds()));
		}

		Map<MarkerStyle, List<Point>> pointsByStyle = new LinkedHashMap<>();
		for (MapMarker marker : markers) {
			if (!isValid(marker.point())) continue;
			pointsByStyle.computeIfAbsent(new MarkerStyle(marker.color(), marker.type()),
					key -> new ArrayList<>()).add(pointGeometry(marker.point()));
		}
		for (Map.Entry<MarkerStyle, List<Point>> entry : pointsByStyle.entrySet()) {
			FeatureLayer layer = OpenRocketGisView.featureLayer(
					"markers-" + entry.getKey().type().name().toLowerCase(java.util.Locale.ROOT),
					OpenRocketGisView.POINT_TYPE, entry.getValue(), markerStyle(entry.getKey()));
			layer.setTitle("markers-" + entry.getKey().type().name().toLowerCase(java.util.Locale.ROOT)
					+ "-" + content.layers().size());
			content.addLayer(layer);
			layer.setVisible(markerVisibility.getOrDefault(entry.getKey().type(), true));
			markerLayers.computeIfAbsent(entry.getKey().type(), ignored -> new ArrayList<>()).add(layer);
			log.debug("Added GeoTools marker layer: title={}, features={}, color=#{}, bounds={}",
					layer.getTitle(), entry.getValue().size(),
					String.format(java.util.Locale.ROOT, "%06X", entry.getKey().color().getRGB() & 0xFFFFFF),
					describeBounds(layer.getBounds()));
		}
		log.info("GeoTools overlay layers rebuilt: layers={}, lineStyles={}, markerStyles={}, mapBounds={}",
				content.layers().size(), linesByStyle.size() + polygonsByStyle.size(), pointsByStyle.size(),
				describeBounds(content.getMaxBounds()));

		// Never mutate content that an earlier asynchronous paint might still be reading. A fresh
		// renderer/content pair makes the swap atomic; old content is disposed after that paint stops.
		StreamingRenderer renderer = new StreamingRenderer();
		installRendererDiagnostics(renderer);
		setRenderer(renderer);
		setMapContent(content);
		retireMapContent(previousContent);
	}

	private void startBaseMapLoad() {
		if (java.awt.GraphicsEnvironment.isHeadless()
				|| Boolean.getBoolean("openrocket.geotools.basemap.disabled")) {
			baseMapStatus = "GeoTools vector map ready (offline grid)";
			baseMapAttemptFinished.countDown();
			statusListener.accept(baseMapStatus);
			log.info("GeoTools USGS basemap disabled: headless={}, property={}",
					java.awt.GraphicsEnvironment.isHeadless(),
					Boolean.getBoolean("openrocket.geotools.basemap.disabled"));
			return;
		}
		if (!baseMapLoadStarted.compareAndSet(false, true)) return;

		baseMapStatus = "Loading USGS topographic basemap…";
		statusListener.accept(baseMapStatus);
		log.info("Loading GeoTools WMS basemap asynchronously: endpoint={}, layer={}, timeoutMs={}",
				BASE_MAP_URL, BASE_MAP_LAYER_NAME, BASE_MAP_TIMEOUT_MILLIS);
		CompletableFuture.supplyAsync(() -> {
			try {
				WebMapServer server = new WebMapServer(URI.create(BASE_MAP_URL).toURL(),
						BASE_MAP_TIMEOUT_MILLIS);
				org.geotools.ows.wms.Layer layer = server.getCapabilities().getLayerList().stream()
						.filter(candidate -> BASE_MAP_LAYER_NAME.equals(candidate.getName()))
						.findFirst()
						.orElseThrow(() -> new IllegalStateException(
								"USGS WMS capabilities omitted layer " + BASE_MAP_LAYER_NAME));
				return new BaseMapSource(server, layer);
			} catch (Exception exception) {
				throw new java.util.concurrent.CompletionException(exception);
			}
		}).whenComplete((source, failure) -> SwingUtilities.invokeLater(() -> {
			try {
				if (disposed) return;
				if (failure != null) {
					baseMapStatus = "USGS basemap unavailable; showing offline coordinate grid";
					statusListener.accept(baseMapStatus);
					log.warn("GeoTools WMS basemap load failed; retaining offline grid: endpoint={}",
							BASE_MAP_URL, unwrapCompletionFailure(failure));
					return;
				}

				baseMapSource = source;
				baseMapStatus = "USGS topographic basemap ready (GeoTools WMS)";
				statusListener.accept(baseMapStatus);
				log.info("GeoTools WMS basemap ready: layer={}, title={}, supportedCRS={}",
						source.layer().getName(), source.layer().getTitle(), source.layer().getSrs());
				setIgnoreRepaint(true);
				try {
					rebuildFeatureLayers();
					updateDisplayArea();
				} finally {
					setIgnoreRepaint(false);
				}
				requestFullRender("USGS WMS basemap became ready");
			} finally {
				baseMapAttemptFinished.countDown();
			}
		}));
	}

	private static Throwable unwrapCompletionFailure(Throwable failure) {
		return failure.getCause() == null ? failure : failure.getCause();
	}

	private void requestFullRender(String reason) {
		if (!isShowing() || getVisibleRect().isEmpty()) {
			renderPending = true;
			log.debug("GeoTools full render deferred until map is visible: reason={}, visibleRect={}",
					reason, getVisibleRect());
			return;
		}
		if (renderInProgress) {
			renderPending = true;
			log.debug("GeoTools render queued behind active pass: reason={}", reason);
			return;
		}
		log.debug("GeoTools full render requested: reason={}, displayArea={}, layers={}, visibleRect={}",
				reason, describeBounds(getDisplayArea()), getMapContent().layers().size(), getVisibleRect());
		drawLayers(true);
		repaint();
	}

	@Override
	protected void drawLayers(boolean createNewImage) {
		if (renderInProgress) {
			renderPending = true;
			return;
		}
		if (isAcceptingRepaints()) refreshBaseMapReader();
		stableImageVisible = stableImage != null;
		super.drawLayers(createNewImage);
	}

	/** A WMSLayer reader is single-use in practice. Reusing it can leave its image null. */
	private void refreshBaseMapReader() {
		BaseMapSource source = baseMapSource;
		WMSLayer previous = activeBaseMapLayer;
		if (source == null || previous == null || !getMapContent().layers().contains(previous)) return;
		WMSLayer replacement = OpenRocketGisView.baseMapLayer(source.server(), source.layer());
		replacement.setVisible(previous.isVisible());
		setIgnoreRepaint(true);
		try {
			MapContent content = getMapContent();
			content.addLayer(replacement);
			content.moveLayer(content.layers().size() - 1, 0);
			content.removeLayer(previous);
			activeBaseMapLayer = replacement;
		} finally {
			setIgnoreRepaint(false);
		}
		previous.dispose();
		log.debug("GeoTools WMS reader refreshed for render {}", renderSequence.get() + 1);
	}

	@Override
	protected void onShownOrResized() {
		if (resizedFuture != null && !resizedFuture.isDone()) resizedFuture.cancel(true);
		resizedFuture = paneTaskExecutor.schedule(() -> {
			setForNewSize();
			// GeoTools skips equal-size resize events. If content arrived while this tab was hidden,
			// that shortcut can otherwise leave baseImage null forever.
			if ((renderPending || getBaseImage() == null) && isShowing()
					&& !getVisibleRect().isEmpty()) {
				requestFullRender("map shown or resized with pending content");
			}
			repaint();
		}, getPaintDelay(), TimeUnit.MILLISECONDS);
	}

	private void retireMapContent(MapContent content) {
		if (content == null) return;
		synchronized (retiredMapContents) {
			if (renderInProgress) {
				retiredMapContents.add(content);
			} else {
				content.dispose();
			}
		}
	}

	private void disposeRetiredMapContents() {
		synchronized (retiredMapContents) {
			for (MapContent content : retiredMapContents) content.dispose();
			retiredMapContents.clear();
		}
	}

	private void installRendererDiagnostics(StreamingRenderer renderer) {
		renderer.addRenderListener(new RenderListener() {
			@Override
			public void featureRenderer(SimpleFeature feature) {
				renderedFeatures.incrementAndGet();
			}

			@Override
			public void errorOccurred(Exception exception) {
				renderErrors.incrementAndGet();
				totalRenderErrors.incrementAndGet();
				log.error("GeoTools renderer failed for displayArea={}, layers={}",
						describeBounds(getDisplayArea()), getMapContent().layers().size(), exception);
			}

			@Override
			public void layerStart(Layer layer) {
				log.trace("GeoTools rendering layer started: title={}, bounds={}",
						layer.getTitle(), describeBounds(layer.getBounds()));
			}

			@Override
			public void layerEnd(Layer layer) {
				log.trace("GeoTools rendering layer finished: title={}", layer.getTitle());
			}
		});
	}

	private void installMapPaneDiagnostics() {
		addMapPaneListener(new MapPaneAdapter() {
			@Override
			public void onRenderingStarted(MapPaneEvent event) {
				renderInProgress = true;
				renderPending = false;
				long sequence = renderSequence.incrementAndGet();
				renderedFeatures.set(0);
				renderErrors.set(0);
				renderStartedNanos.set(System.nanoTime());
				log.debug("GeoTools render {} started: displayArea={}, layers={}, component={}x{}",
						sequence, describeBounds(getDisplayArea()), getMapContent().layers().size(),
						getWidth(), getHeight());
			}

			@Override
			public void onRenderingStopped(MapPaneEvent event) {
				renderInProgress = false;
				disposeRetiredMapContents();
				boolean successful = getBaseImage() != null && renderErrors.get() == 0;
				if (successful) {
					stableImage = copyImage(getBaseImage());
				}
				boolean renderAgain = renderPending;
				stableImageVisible = (!successful || renderAgain) && stableImage != null;
				long elapsedNanos = Math.max(0L, System.nanoTime() - renderStartedNanos.get());
				log.info("GeoTools render {} stopped: features={}, errors={}, elapsedMs={}, baseImage={}x{}",
						renderSequence.get(), renderedFeatures.get(), renderErrors.get(),
						elapsedNanos / 1_000_000L,
						getBaseImage() == null ? 0 : getBaseImage().getWidth(),
						getBaseImage() == null ? 0 : getBaseImage().getHeight());
				if (renderAgain) {
					SwingUtilities.invokeLater(() ->
							requestFullRender("previous asynchronous render completed"));
				}
				repaint();
			}
		});
	}

	private static String describeBounds(ReferencedEnvelope bounds) {
		if (bounds == null) return "null";
		return String.format(java.util.Locale.ROOT,
				"[minX=%.3f,maxX=%.3f,minY=%.3f,maxY=%.3f,crs=%s]",
				bounds.getMinX(), bounds.getMaxX(), bounds.getMinY(), bounds.getMaxY(),
				bounds.getCoordinateReferenceSystem() == null ? "null"
						: bounds.getCoordinateReferenceSystem().getName());
	}

	private Style markerStyle(MarkerStyle markerStyle) {
		String shape = switch (markerStyle.type()) {
			case LAUNCH -> "triangle";
			case MEAN -> "square";
			case LANDING -> "circle";
		};
		double size = markerStyle.type() == MarkerType.LANDING ? 8.0 : 13.0;
		Mark mark = styleBuilder.createMark(shape, markerStyle.color(), Color.WHITE, 1.5);
		Graphic graphic = styleBuilder.createGraphic(null, mark, null, 1.0, size, 0.0);
		return styleBuilder.createStyle(styleBuilder.createPointSymbolizer(graphic));
	}

	private static LineString lineGeometry(List<GeoPoint> points) {
		Coordinate[] coordinates = points.stream()
				.filter(GeoToolsMapPanel::isValid)
				.map(point -> new Coordinate(point.longitudeDeg(), point.latitudeDeg()))
				.toArray(Coordinate[]::new);
		return coordinates.length < 2 ? null : GEOMETRY_FACTORY.createLineString(coordinates);
	}

	private static Polygon polygonGeometry(List<GeoPoint> points) {
		List<Coordinate> coordinates = points.stream()
				.filter(GeoToolsMapPanel::isValid)
				.map(point -> new Coordinate(point.longitudeDeg(), point.latitudeDeg()))
				.collect(java.util.stream.Collectors.toCollection(ArrayList::new));
		if (coordinates.size() < 3) return null;
		Coordinate first = coordinates.get(0);
		if (!first.equals2D(coordinates.get(coordinates.size() - 1))) {
			coordinates.add(new Coordinate(first));
		}
		return coordinates.size() < 4 ? null
				: GEOMETRY_FACTORY.createPolygon(coordinates.toArray(Coordinate[]::new));
	}

	private static Point pointGeometry(GeoPoint point) {
		return GEOMETRY_FACTORY.createPoint(new Coordinate(
				point.longitudeDeg(), point.latitudeDeg()));
	}

	private List<GeoPoint> overlayPoints() {
		List<GeoPoint> points = new ArrayList<>();
		for (MapMarker marker : markers) points.add(marker.point());
		for (MapPolyline polyline : polylines) points.addAll(polyline.points());
		points.removeIf(point -> !isValid(point));
		return points;
	}

	private void setZoom(double requestedZoom) {
		zoom = clampZoom(requestedZoom);
		fitPending = false;
		updateDisplayArea();
	}

	private void updateDisplayArea() {
		double centerX = mercatorX(centerLongitudeDeg);
		double centerY = mercatorY(centerLatitudeDeg);
		double resolution = resolution(zoom);
		double halfWidth = Math.max(1, getWidth()) * resolution / 2.0;
		double halfHeight = Math.max(1, getHeight()) * resolution / 2.0;
		boolean alreadyBatching = !isAcceptingRepaints();
		if (!alreadyBatching) setIgnoreRepaint(true);
		try {
			setDisplayArea(new ReferencedEnvelope(centerX - halfWidth, centerX + halfWidth,
					centerY - halfHeight, centerY + halfHeight, WEB_MERCATOR));
		} finally {
			if (!alreadyBatching) setIgnoreRepaint(false);
		}
		log.trace("GeoTools display area changed: center=({}, {}), zoom={}, resolution={}, bounds={}",
				centerLatitudeDeg, centerLongitudeDeg, zoom, resolution, describeBounds(getDisplayArea()));
		if (!alreadyBatching) requestFullRender("display area changed");
	}

	private void panPixels(double dx, double dy) {
		double resolution = resolution(zoom);
		double x = mercatorX(centerLongitudeDeg) + dx * resolution;
		double y = mercatorY(centerLatitudeDeg) - dy * resolution;
		centerLongitudeDeg = normalizeLongitude(longitudeFromMercatorX(x));
		centerLatitudeDeg = latitudeFromMercatorY(y);
		fitPending = false;
		updateDisplayArea();
	}

	private void zoomAround(java.awt.Point screenPoint, double newZoom) {
		GeoPoint anchorBefore = pointAt(screenPoint.x, screenPoint.y);
		zoom = clampZoom(newZoom);
		double resolution = resolution(zoom);
		double anchorX = mercatorX(anchorBefore.longitudeDeg());
		double anchorY = mercatorY(anchorBefore.latitudeDeg());
		double centerX = anchorX - (screenPoint.x - getWidth() / 2.0) * resolution;
		double centerY = anchorY + (screenPoint.y - getHeight() / 2.0) * resolution;
		centerLongitudeDeg = normalizeLongitude(longitudeFromMercatorX(centerX));
		centerLatitudeDeg = latitudeFromMercatorY(centerY);
		fitPending = false;
		updateDisplayArea();
	}

	private GeoPoint pointAt(int screenX, int screenY) {
		double resolution = resolution(zoom);
		double x = mercatorX(centerLongitudeDeg) + (screenX - getWidth() / 2.0) * resolution;
		double y = mercatorY(centerLatitudeDeg) - (screenY - getHeight() / 2.0) * resolution;
		return new GeoPoint(latitudeFromMercatorY(y), normalizeLongitude(longitudeFromMercatorX(x)));
	}

	private java.awt.Point screenPoint(GeoPoint point) {
		double resolution = resolution(zoom);
		double dx = mercatorX(point.longitudeDeg()) - mercatorX(centerLongitudeDeg);
		if (dx > WEB_MERCATOR_LIMIT) dx -= WEB_MERCATOR_WORLD;
		if (dx < -WEB_MERCATOR_LIMIT) dx += WEB_MERCATOR_WORLD;
		double dy = mercatorY(point.latitudeDeg()) - mercatorY(centerLatitudeDeg);
		return new java.awt.Point((int) Math.round(getWidth() / 2.0 + dx / resolution),
				(int) Math.round(getHeight() / 2.0 - dy / resolution));
	}

	private void installMouseControls() {
		addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent event) {
				requestFocusInWindow();
				dragOrigin = event.getPoint();
				dragCenterX = mercatorX(centerLongitudeDeg);
				dragCenterY = mercatorY(centerLatitudeDeg);
			}

			@Override
			public void mouseReleased(MouseEvent event) {
				dragOrigin = null;
			}

			@Override
			public void mouseClicked(MouseEvent event) {
				if (event.getClickCount() >= 2) {
					zoomAround(event.getPoint(), zoom + 1.0);
					return;
				}
				MapMarker nearest = nearestMarker(event.getPoint(), 12.0);
				if (nearest != null) markerListener.accept(nearest);
			}
		});
		addMouseMotionListener(new MouseMotionAdapter() {
			@Override
			public void mouseMoved(MouseEvent event) {
				coordinateListener.accept(pointAt(event.getX(), event.getY()));
			}

			@Override
			public void mouseDragged(MouseEvent event) {
				if (dragOrigin == null) return;
				double resolution = resolution(zoom);
				double x = dragCenterX - (event.getX() - dragOrigin.x) * resolution;
				double y = dragCenterY + (event.getY() - dragOrigin.y) * resolution;
				centerLongitudeDeg = normalizeLongitude(longitudeFromMercatorX(x));
				centerLatitudeDeg = latitudeFromMercatorY(y);
				fitPending = false;
				updateDisplayArea();
			}
		});
		addMouseWheelListener(this::handleMouseWheel);
	}

	private void handleMouseWheel(MouseWheelEvent event) {
		double precise = event.getPreciseWheelRotation();
		boolean modified = (event.getModifiersEx()
				& (InputEvent.CTRL_DOWN_MASK | InputEvent.META_DOWN_MASK)) != 0;
		long now = System.currentTimeMillis();
		boolean fractional = Math.abs(precise - Math.rint(precise)) > 1.0e-6;
		boolean trackpadGesture = fractional || now - lastPreciseScrollMillis < TRACKPAD_GESTURE_MILLIS;
		if (fractional) lastPreciseScrollMillis = now;
		if (trackpadGesture && !modified) {
			panPixels(0.0, precise * SCROLL_PAN_PIXELS);
		} else {
			double step = trackpadGesture ? PRECISE_ZOOM_STEP : WHEEL_ZOOM_STEP;
			zoomAround(event.getPoint(), zoom - precise * step);
		}
		event.consume();
	}

	private MapMarker nearestMarker(java.awt.Point target, double maximumDistance) {
		MapMarker best = null;
		double bestDistance = maximumDistance;
		for (MapMarker marker : markers) {
			java.awt.Point markerPoint = screenPoint(marker.point());
			double distance = markerPoint.distance(target);
			if (distance <= bestDistance) {
				best = marker;
				bestDistance = distance;
			}
		}
		return best;
	}

	private void installKeyboardControls() {
		InputMap inputs = getInputMap(JComponent.WHEN_FOCUSED);
		ActionMap actions = getActionMap();
		bind(inputs, actions, KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "pan-left",
				() -> panPixels(-KEY_PAN_PIXELS, 0));
		bind(inputs, actions, KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "pan-right",
				() -> panPixels(KEY_PAN_PIXELS, 0));
		bind(inputs, actions, KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "pan-up",
				() -> panPixels(0, -KEY_PAN_PIXELS));
		bind(inputs, actions, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "pan-down",
				() -> panPixels(0, KEY_PAN_PIXELS));
		bind(inputs, actions, KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, 0), "zoom-in", this::zoomIn);
		bind(inputs, actions, KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, 0), "zoom-in-equals", this::zoomIn);
		bind(inputs, actions, KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, 0), "zoom-out", this::zoomOut);
	}

	private static void bind(InputMap inputs, ActionMap actions, KeyStroke keyStroke,
			String name, Runnable action) {
		inputs.put(keyStroke, name);
		actions.put(name, new AbstractAction() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent event) {
				action.run();
			}
		});
	}

	private void installLayoutListener() {
		addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent event) {
				if (fitPending) {
					applyPendingFit();
				} else {
					updateDisplayArea();
				}
			}
		});
	}

	@Override
	protected void paintComponent(Graphics graphics) {
		super.paintComponent(graphics);
		Graphics2D g2 = (Graphics2D) graphics.create();
		try {
			BufferedImage retained = stableImage;
			if (stableImageVisible && retained != null) {
				g2.drawImage(retained, 0, 0, getWidth(), getHeight(), null);
			}
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			drawCoordinateGrid(g2);
			drawScaleBar(g2);
		} finally {
			g2.dispose();
		}
	}

	private static BufferedImage copyImage(RenderedImage source) {
		BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(),
				BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = copy.createGraphics();
		try {
			graphics.drawRenderedImage(source, new AffineTransform());
		} finally {
			graphics.dispose();
		}
		return copy;
	}

	private void drawCoordinateGrid(Graphics2D graphics) {
		if (getWidth() <= 0 || getHeight() <= 0) return;
		GeoPoint northWest = pointAt(0, 0);
		GeoPoint southEast = pointAt(getWidth(), getHeight());
		double spacing = gridSpacing(zoom);
		double west = northWest.longitudeDeg();
		double east = southEast.longitudeDeg();
		if (east < west) east += 360.0;
		double north = northWest.latitudeDeg();
		double south = southEast.latitudeDeg();
		graphics.setStroke(new BasicStroke(1.0f));
		graphics.setColor(new Color(0x63, 0x74, 0x7D, 45));
		for (double longitude = Math.ceil(west / spacing) * spacing;
				longitude <= east; longitude += spacing) {
			java.awt.Point top = screenPoint(new GeoPoint(north, normalizeLongitude(longitude)));
			graphics.drawLine(top.x, 0, top.x, getHeight());
		}
		for (double latitude = Math.ceil(south / spacing) * spacing;
				latitude <= north; latitude += spacing) {
			java.awt.Point left = screenPoint(new GeoPoint(latitude, west));
			graphics.drawLine(0, left.y, getWidth(), left.y);
		}
	}

	private void drawScaleBar(Graphics2D graphics) {
		double rawMeters = resolution(zoom) * 120.0;
		double magnitude = Math.pow(10.0, Math.floor(Math.log10(rawMeters)));
		double normalized = rawMeters / magnitude;
		double nice = normalized >= 5.0 ? 5.0 : normalized >= 2.0 ? 2.0 : 1.0;
		double meters = nice * magnitude;
		int width = (int) Math.round(meters / resolution(zoom));
		int x = 16;
		int y = getHeight() - 18;
		graphics.setColor(new Color(255, 255, 255, 210));
		graphics.fillRoundRect(x - 7, y - 20, width + 14, 30, 8, 8);
		graphics.setColor(new Color(0x28, 0x33, 0x3A));
		graphics.setStroke(new BasicStroke(2.0f));
		graphics.drawLine(x, y, x + width, y);
		graphics.drawLine(x, y - 4, x, y + 4);
		graphics.drawLine(x + width, y - 4, x + width, y + 4);
		String label = meters >= 1_000.0
				? String.format(java.util.Locale.US, "%.0f km", meters / 1_000.0)
				: String.format(java.util.Locale.US, "%.0f m", meters);
		FontMetrics metrics = graphics.getFontMetrics();
		graphics.drawString(label, x + (width - metrics.stringWidth(label)) / 2, y - 7);
	}

	private static double gridSpacing(double zoomLevel) {
		if (zoomLevel < 4) return 30.0;
		if (zoomLevel < 7) return 5.0;
		if (zoomLevel < 10) return 1.0;
		if (zoomLevel < 13) return 0.25;
		if (zoomLevel < 16) return 0.05;
		return 0.01;
	}

	private static CoordinateReferenceSystem decodeWebMercator() {
		try {
			OpenRocketGisView.configureEnvironment();
			return CRS.decode("EPSG:3857", true);
		} catch (FactoryException exception) {
			throw new ExceptionInInitializerError(exception);
		}
	}

	private static boolean isValid(GeoPoint point) {
		return point != null && Double.isFinite(point.latitudeDeg())
				&& Double.isFinite(point.longitudeDeg())
				&& point.latitudeDeg() >= -90.0 && point.latitudeDeg() <= 90.0;
	}

	private static double resolution(double zoomLevel) {
		return WEB_MERCATOR_WORLD / (TILE_SIZE * Math.pow(2.0, zoomLevel));
	}

	private static double mercatorX(double longitudeDeg) {
		return WEB_MERCATOR_LIMIT * longitudeDeg / 180.0;
	}

	private static double mercatorY(double latitudeDeg) {
		double latitude = Math.toRadians(clampLatitude(latitudeDeg));
		return WEB_MERCATOR_LIMIT / Math.PI
				* Math.log(Math.tan(Math.PI / 4.0 + latitude / 2.0));
	}

	private static double longitudeFromMercatorX(double x) {
		return x / WEB_MERCATOR_LIMIT * 180.0;
	}

	private static double latitudeFromMercatorY(double y) {
		return Math.toDegrees(2.0 * Math.atan(Math.exp(y * Math.PI / WEB_MERCATOR_LIMIT))
				- Math.PI / 2.0);
	}

	private static double clampLatitude(double latitudeDeg) {
		return Math.max(-MAX_LATITUDE, Math.min(MAX_LATITUDE, latitudeDeg));
	}

	private static double clampZoom(double requestedZoom) {
		return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, requestedZoom));
	}

	private static double normalizeLongitude(double longitudeDeg) {
		double result = longitudeDeg % 360.0;
		if (result < -180.0) result += 360.0;
		if (result >= 180.0) result -= 360.0;
		return result;
	}

	private static double longitudeOffset(double longitudeDeg, double referenceLongitudeDeg) {
		return normalizeLongitude(longitudeDeg - referenceLongitudeDeg);
	}

	private static double circularMeanLongitude(List<GeoPoint> points) {
		double sin = 0.0;
		double cos = 0.0;
		for (GeoPoint point : points) {
			if (!isValid(point)) continue;
			double radians = Math.toRadians(point.longitudeDeg());
			sin += Math.sin(radians);
			cos += Math.cos(radians);
		}
		return Math.toDegrees(Math.atan2(sin, cos));
	}
}

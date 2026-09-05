package info.openrocket.swing.gui.simulation;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/**
 * Lightweight slippy-map component for OpenStreetMap raster tiles and geographic overlays.
 */
final class OpenStreetMapPanel extends JPanel {
	interface TileProvider {
		BufferedImage load(int zoom, int x, int y) throws IOException, InterruptedException;
	}

	enum MarkerType {
		LAUNCH, MEAN, LANDING
	}

	record GeoPoint(double latitudeDeg, double longitudeDeg) { }

	record MapMarker(String id, String label, String details, GeoPoint point,
			Color color, MarkerType type) { }

	record MapPolyline(String label, List<GeoPoint> points, Color color, float width) {
		MapPolyline {
			points = List.copyOf(points);
		}
	}

	private record TileKey(int zoom, int x, int y) { }
	private record TileFailure(long retryAfter, String reason) { }

	private static final int TILE_SIZE = 256;
	private static final int MIN_ZOOM = 1;
	private static final int MAX_ZOOM = 19;
	/** Highest zoom {@link #fitToOverlays()} will choose; leaves room to zoom in manually. */
	private static final int MAX_FIT_ZOOM = 18;
	/** Margin, in pixels, kept clear on each side when fitting overlays. */
	private static final int FIT_PADDING = 48;
	/** Number of coarser zoom levels searched for a stand-in while a tile is still loading. */
	private static final int MAX_FALLBACK_LEVELS = 4;
	/** Keeps return-panning smooth without allowing an unbounded in-memory image cache. */
	private static final int MAX_MEMORY_TILES = 256;
	private static final int TILE_LOADER_THREADS = 4;
	private static final long RETRY_DELAY_MILLIS = 30_000;
	private static final int KEY_PAN_PIXELS = 60;
	/** Pixels panned per unit of precise wheel rotation when scrolling pans. */
	private static final double SCROLL_PAN_PIXELS = 42.0;
	/** Zoom levels per wheel click for a stepped mouse wheel. */
	private static final double WHEEL_ZOOM_STEP = 1.0;
	/** Zoom levels per unit of precise rotation for a high-resolution (trackpad) scroll. */
	private static final double PRECISE_ZOOM_STEP = 0.35;
	/**
	 * A trackpad emits a burst of fractional scroll events; once one is seen the whole gesture is
	 * treated as trackpad input, even for the occasional event that lands on a whole number.
	 */
	private static final long TRACKPAD_GESTURE_MILLIS = 600;
	private static final AtomicInteger TILE_THREAD_NUMBER = new AtomicInteger();
	private static final ExecutorService TILE_EXECUTOR = Executors.newFixedThreadPool(
			TILE_LOADER_THREADS, runnable -> {
		Thread thread = new Thread(runnable,
				"osm-tile-loader-" + TILE_THREAD_NUMBER.incrementAndGet());
		thread.setDaemon(true);
		return thread;
	});

	private final TileProvider tileProvider;
	private final Map<TileKey, BufferedImage> tileCache = new ConcurrentHashMap<>();
	private final ConcurrentLinkedQueue<TileKey> tileCacheOrder = new ConcurrentLinkedQueue<>();
	private final Set<TileKey> pendingTiles = ConcurrentHashMap.newKeySet();
	private final Map<TileKey, TileFailure> failedTiles = new ConcurrentHashMap<>();
	private volatile Set<TileKey> visibleTiles = Set.of();
	private volatile int visibleTileZoom = 13;
	private List<MapMarker> markers = List.of();
	private List<MapPolyline> polylines = List.of();
	private double centerLatitudeDeg;
	private double centerLongitudeDeg;
	private double zoom = 13.0;
	private boolean fitPending;
	private long lastPreciseScrollMillis;
	private String tileFailureMessage;
	private String lastTileStatus;
	private Point dragOrigin;
	private double dragCenterX;
	private double dragCenterY;
	private Consumer<GeoPoint> coordinateListener = point -> { };
	private Consumer<MapMarker> markerListener = marker -> { };
	private Consumer<String> statusListener = status -> { };

	OpenStreetMapPanel() {
		this(new OpenStreetMapTileProvider());
	}

	OpenStreetMapPanel(TileProvider tileProvider) {
		this.tileProvider = tileProvider;
		setOpaque(true);
		setBackground(new Color(0xE8, 0xE8, 0xE8));
		setPreferredSize(new Dimension(720, 470));
		// The containing landing view manages the useful minimum.  A large component
		// minimum made Swing push the map outside the viewport in a short window.
		setMinimumSize(new Dimension(120, 100));
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setToolTipText("<html>Drag or two-finger scroll to pan &middot; "
				+ "mouse wheel, pinch, or ⌘/Ctrl + scroll to zoom<br>"
				+ "Double-click to zoom in &middot; arrow keys pan &middot; "
				+ "+/− zoom &middot; click a landing marker for details</html>");
		installMouseControls();
		installKeyboardControls();
		installLayoutListeners();
	}

	void setOverlays(List<MapMarker> newMarkers, List<MapPolyline> newPolylines) {
		markers = List.copyOf(newMarkers);
		polylines = List.copyOf(newPolylines);
		repaint();
	}

	void setCoordinateListener(Consumer<GeoPoint> listener) {
		coordinateListener = listener == null ? point -> { } : listener;
	}

	void setMarkerListener(Consumer<MapMarker> listener) {
		markerListener = listener == null ? marker -> { } : listener;
	}

	void setStatusListener(Consumer<String> listener) {
		statusListener = listener == null ? status -> { } : listener;
	}

	void centerOn(double latitudeDeg, double longitudeDeg, int requestedZoom) {
		fitPending = false;
		centerLatitudeDeg = clampLatitude(latitudeDeg);
		centerLongitudeDeg = normalizeLongitude(longitudeDeg);
		zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, requestedZoom));
		repaint();
	}

	void zoomIn() {
		setZoom(zoom + 1);
	}

	void zoomOut() {
		setZoom(zoom - 1);
	}

	/** Drops the back-off on tiles that failed so the next paint retries them immediately. */
	void retryFailedTiles() {
		failedTiles.keySet().removeAll(visibleTiles);
		tileFailureMessage = null;
		lastTileStatus = null;
		requestMissingVisibleTiles();
		updateVisibleTileStatus();
		repaint();
	}

	/**
	 * Frames the launch site, landings and rings.
	 *
	 * Callers normally invoke this while building overlays, which happens before the panel has
	 * been laid out. Choosing a zoom against a zero-sized viewport collapses the whole dispersion
	 * into a dot, so the zoom decision is deferred until the component actually has a size (see
	 * {@link #installLayoutListeners()}).
	 */
	void fitToOverlays() {
		fitPending = true;
		applyPendingFit();
	}

	private void applyPendingFit() {
		if (!fitPending) return;
		List<GeoPoint> points = new ArrayList<>();
		for (MapMarker marker : markers) points.add(marker.point());
		for (MapPolyline polyline : polylines) points.addAll(polyline.points());
		points.removeIf(point -> !isValid(point));
		if (points.isEmpty()) {
			fitPending = false;
			return;
		}

		double meanLongitude = circularMeanLongitude(points);
		double minLatitude = Double.POSITIVE_INFINITY;
		double maxLatitude = Double.NEGATIVE_INFINITY;
		double minLongitudeOffset = Double.POSITIVE_INFINITY;
		double maxLongitudeOffset = Double.NEGATIVE_INFINITY;
		for (GeoPoint point : points) {
			minLatitude = Math.min(minLatitude, point.latitudeDeg());
			maxLatitude = Math.max(maxLatitude, point.latitudeDeg());
			double offset = longitudeOffset(point.longitudeDeg(), meanLongitude);
			minLongitudeOffset = Math.min(minLongitudeOffset, offset);
			maxLongitudeOffset = Math.max(maxLongitudeOffset, offset);
		}
		centerLongitudeDeg = normalizeLongitude(meanLongitude
				+ (minLongitudeOffset + maxLongitudeOffset) / 2.0);

		if (getWidth() <= 0 || getHeight() <= 0) {
			// Stay pending: a resize will re-run the fit once the viewport is real.
			repaint();
			return;
		}

		int availableWidth = Math.max(64, getWidth() - 2 * FIT_PADDING);
		int availableHeight = Math.max(64, getHeight() - 2 * FIT_PADDING);
		int best = MIN_ZOOM;
		for (int candidate = MAX_FIT_ZOOM; candidate >= MIN_ZOOM; candidate--) {
			double world = worldSize(candidate);
			double xSpan = (maxLongitudeOffset - minLongitudeOffset) / 360.0 * world;
			double ySpan = Math.abs(worldY(minLatitude, candidate) - worldY(maxLatitude, candidate));
			if (xSpan <= availableWidth && ySpan <= availableHeight) {
				best = candidate;
				break;
			}
		}
		zoom = best;
		// Centre in projected space.  A degree-space midpoint drifts vertically at higher
		// latitudes and can leave one edge of the fitted cloud closer to the viewport boundary.
		centerLatitudeDeg = latitudeFromWorldY(
				(worldY(minLatitude, zoom) + worldY(maxLatitude, zoom)) / 2.0, zoom);
		fitPending = false;
		repaint();
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

	GeoPoint getCenter() {
		return new GeoPoint(centerLatitudeDeg, centerLongitudeDeg);
	}

	@Override
	protected void paintComponent(Graphics graphics) {
		super.paintComponent(graphics);
		Graphics2D g2 = (Graphics2D) graphics.create();
		try {
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
					RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			drawTiles(g2);
			drawPolylines(g2);
			drawMarkers(g2);
			drawAttribution(g2);
			drawTileFailureBanner(g2);
		} finally {
			g2.dispose();
		}
	}

	private void drawTiles(Graphics2D graphics) {
		if (getWidth() <= 0 || getHeight() <= 0) return;
		int tileZoom = viewportTileZoom();
		double tileScale = Math.pow(2.0, zoom - tileZoom);
		double centerX = worldX(centerLongitudeDeg, tileZoom);
		double centerY = worldY(centerLatitudeDeg, tileZoom);
		double left = centerX - getWidth() / (2.0 * tileScale);
		double top = centerY - getHeight() / (2.0 * tileScale);
		int firstX = (int) Math.floor(left / TILE_SIZE);
		int lastX = (int) Math.ceil((left + getWidth() / tileScale) / TILE_SIZE) - 1;
		int firstY = Math.max(0, (int) Math.floor(top / TILE_SIZE));
		int tileCount = 1 << tileZoom;
		int lastY = Math.min(tileCount - 1,
				(int) Math.ceil((top + getHeight() / tileScale) / TILE_SIZE) - 1);

		Set<TileKey> nextVisibleTiles = ConcurrentHashMap.newKeySet();
		for (int tileY = firstY; tileY <= lastY; tileY++) {
			for (int rawTileX = firstX; rawTileX <= lastX; rawTileX++) {
				nextVisibleTiles.add(new TileKey(tileZoom,
						Math.floorMod(rawTileX, tileCount), tileY));
			}
		}
		updateVisibleTiles(tileZoom, Set.copyOf(nextVisibleTiles));
		requestMissingVisibleTiles();
		updateVisibleTileStatus();

		for (int tileY = firstY; tileY <= lastY; tileY++) {
			for (int rawTileX = firstX; rawTileX <= lastX; rawTileX++) {
				int tileX = Math.floorMod(rawTileX, tileCount);
				TileKey key = new TileKey(tileZoom, tileX, tileY);
				int screenX = (int) Math.round((rawTileX * TILE_SIZE - left) * tileScale);
				int screenY = (int) Math.round((tileY * TILE_SIZE - top) * tileScale);
				int screenRight = (int) Math.round(((rawTileX + 1) * TILE_SIZE - left) * tileScale);
				int screenBottom = (int) Math.round(((tileY + 1) * TILE_SIZE - top) * tileScale);
				BufferedImage image = tileCache.get(key);
				if (image != null) {
					graphics.drawImage(image, screenX, screenY, screenRight, screenBottom,
							0, 0, TILE_SIZE, TILE_SIZE, null);
					continue;
				}
				if (!drawCoarserTile(graphics, tileZoom, tileX, tileY,
						screenX, screenY, screenRight, screenBottom)) {
					drawTilePlaceholder(graphics, screenX, screenY,
							screenRight - screenX, screenBottom - screenY);
				}
			}
		}
	}

	private void updateVisibleTiles(int tileZoom, Set<TileKey> nextVisibleTiles) {
		if (tileZoom == visibleTileZoom && nextVisibleTiles.equals(visibleTiles)) return;
		visibleTileZoom = tileZoom;
		visibleTiles = nextVisibleTiles;
		failedTiles.keySet().retainAll(nextVisibleTiles);
		tileFailureMessage = null;
	}

	private void requestMissingVisibleTiles() {
		List<TileKey> missing = new ArrayList<>(visibleTiles);
		double centerTileX = worldX(centerLongitudeDeg, visibleTileZoom) / TILE_SIZE;
		double centerTileY = worldY(centerLatitudeDeg, visibleTileZoom) / TILE_SIZE;
		missing.sort((first, second) -> Double.compare(
				tileDistanceSquared(first, centerTileX, centerTileY),
				tileDistanceSquared(second, centerTileX, centerTileY)));
		for (TileKey key : missing) {
			if (!tileCache.containsKey(key)) requestTile(key);
		}
	}

	private static double tileDistanceSquared(TileKey key, double centerX, double centerY) {
		double tileCount = 1 << key.zoom();
		double dx = Math.abs(key.x() + 0.5 - centerX);
		dx = Math.min(dx, tileCount - dx);
		double dy = key.y() + 0.5 - centerY;
		return dx * dx + dy * dy;
	}

	/**
	 * Paints the matching region of an already-cached coarser tile so panning and zooming show a
	 * blurry map instead of empty grey while the sharp tiles are still in flight.
	 */
	private boolean drawCoarserTile(Graphics2D graphics, int tileZoom, int tileX, int tileY,
			int screenX, int screenY, int screenRight, int screenBottom) {
		for (int level = 1; level <= MAX_FALLBACK_LEVELS; level++) {
			int parentZoom = tileZoom - level;
			if (parentZoom < MIN_ZOOM) return false;
			int factor = 1 << level;
			BufferedImage parent = tileCache.get(new TileKey(parentZoom, tileX / factor, tileY / factor));
			if (parent == null) continue;
			int source = TILE_SIZE / factor;
			int sourceX = (tileX % factor) * source;
			int sourceY = (tileY % factor) * source;
			graphics.drawImage(parent, screenX, screenY, screenRight, screenBottom,
					sourceX, sourceY, sourceX + source, sourceY + source, null);
			return true;
		}
		return false;
	}

	private void requestTile(TileKey key) {
		TileFailure previousFailure = failedTiles.get(key);
		if (previousFailure != null && previousFailure.retryAfter() > System.currentTimeMillis()) return;
		if (!pendingTiles.add(key)) return;
		TILE_EXECUTOR.execute(() -> {
			String failureReason = null;
			try {
				// A quick pan can leave queued work behind. Do not contact the tile server for a tile
				// that is no longer visible by the time a loader thread reaches it.
				if (!visibleTiles.contains(key)) return;
				BufferedImage image = tileProvider.load(key.zoom(), key.x(), key.y());
				if (image == null) throw new IOException("OpenStreetMap returned an empty tile");
				cacheTile(key, image);
				failedTiles.remove(key);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				failureReason = "tile loading was interrupted";
				failedTiles.put(key, new TileFailure(
						System.currentTimeMillis() + RETRY_DELAY_MILLIS, failureReason));
			} catch (IOException | RuntimeException | LinkageError exception) {
				// LinkageError covers a runtime image that is missing a transport class: without
				// this the tile thread would die silently and the map would stay blank forever.
				failureReason = describe(exception);
				failedTiles.put(key, new TileFailure(
						System.currentTimeMillis() + RETRY_DELAY_MILLIS, failureReason));
			} finally {
				pendingTiles.remove(key);
				String completedFailure = failureReason;
				SwingUtilities.invokeLater(() -> {
					if (completedFailure != null && visibleTiles.contains(key)) {
						tileFailureMessage = completedFailure;
					}
					updateVisibleTileStatus();
					repaint();
				});
			}
		});
	}

	private void cacheTile(TileKey key, BufferedImage image) {
		if (tileCache.put(key, image) == null) tileCacheOrder.add(key);
		while (tileCache.size() > MAX_MEMORY_TILES) {
			TileKey oldest = tileCacheOrder.poll();
			if (oldest == null) break;
			tileCache.remove(oldest);
			failedTiles.remove(oldest);
		}
	}

	private void updateVisibleTileStatus() {
		Set<TileKey> currentTiles = visibleTiles;
		if (currentTiles.isEmpty()) return;
		int loaded = 0;
		boolean loading = false;
		String failureReason = null;
		for (TileKey key : currentTiles) {
			if (tileCache.containsKey(key)) {
				loaded++;
			} else if (pendingTiles.contains(key)) {
				loading = true;
			} else {
				TileFailure failure = failedTiles.get(key);
				if (failure != null) failureReason = failure.reason();
			}
		}

		if (loaded == currentTiles.size()) {
			tileFailureMessage = null;
			publishTileStatus("OpenStreetMap tiles loaded");
		} else if (loading) {
			publishTileStatus(String.format("Loading OpenStreetMap tiles... (%d/%d)",
					loaded, currentTiles.size()));
		} else if (failureReason != null) {
			tileFailureMessage = failureReason;
			publishTileStatus("Map tiles unavailable (" + failureReason
					+ "); landing coordinates and overlays remain available.");
		}
	}

	private void publishTileStatus(String status) {
		if (status.equals(lastTileStatus)) return;
		lastTileStatus = status;
		statusListener.accept(status);
	}

	private static String describe(Throwable exception) {
		String message = exception.getMessage();
		return (message == null || message.isBlank())
				? exception.getClass().getSimpleName() : message;
	}

	private static void drawTilePlaceholder(Graphics2D graphics, int x, int y, int width, int height) {
		graphics.setColor(new Color(0xE4, 0xE7, 0xE9));
		graphics.fillRect(x, y, width, height);
		graphics.setColor(new Color(0xD2, 0xD6, 0xD9));
		graphics.drawRect(x, y, width, height);
	}

	private void drawPolylines(Graphics2D graphics) {
		for (MapPolyline polyline : polylines) {
			if (polyline.points().size() < 2) continue;
			Path2D path = new Path2D.Double();
			boolean first = true;
			for (GeoPoint point : polyline.points()) {
				if (!isValid(point)) {
					first = true;
					continue;
				}
				Point screen = toScreen(point);
				if (first) {
					path.moveTo(screen.x, screen.y);
					first = false;
				} else {
					path.lineTo(screen.x, screen.y);
				}
			}
			graphics.setColor(polyline.color());
			graphics.setStroke(new BasicStroke(polyline.width(), BasicStroke.CAP_ROUND,
					BasicStroke.JOIN_ROUND));
			graphics.draw(path);
		}
	}

	private void drawMarkers(Graphics2D graphics) {
		for (MapMarker marker : markers) {
			if (!isValid(marker.point())) continue;
			Point point = toScreen(marker.point());
			if (point.x < -20 || point.x > getWidth() + 20 || point.y < -20 || point.y > getHeight() + 20) {
				continue;
			}
			int radius = marker.type() == MarkerType.LANDING ? 4 : 7;
			graphics.setColor(new Color(255, 255, 255, 210));
			graphics.fillOval(point.x - radius - 2, point.y - radius - 2,
					(radius + 2) * 2, (radius + 2) * 2);
			graphics.setColor(marker.color());
			graphics.fillOval(point.x - radius, point.y - radius, radius * 2, radius * 2);
			graphics.setColor(Color.DARK_GRAY);
			graphics.drawOval(point.x - radius, point.y - radius, radius * 2, radius * 2);
		}
	}

	private void drawAttribution(Graphics2D graphics) {
		String attribution = "© OpenStreetMap contributors";
		FontMetrics metrics = graphics.getFontMetrics();
		int width = metrics.stringWidth(attribution) + 10;
		int x = Math.max(0, getWidth() - width);
		int y = Math.max(metrics.getHeight(), getHeight());
		graphics.setColor(new Color(255, 255, 255, 205));
		graphics.fillRect(x, y - metrics.getHeight(), width, metrics.getHeight());
		graphics.setColor(new Color(0x20, 0x20, 0x20));
		graphics.drawString(attribution, x + 5, y - metrics.getDescent());
	}

	/** Explains an empty map in place rather than leaving the user staring at a blank panel. */
	private void drawTileFailureBanner(Graphics2D graphics) {
		if (tileFailureMessage == null
				|| visibleTiles.stream().anyMatch(tileCache::containsKey)) return;
		String headline = "OpenStreetMap tiles could not be loaded";
		FontMetrics metrics = graphics.getFontMetrics();
		int width = Math.max(metrics.stringWidth(headline), metrics.stringWidth(tileFailureMessage)) + 24;
		int height = metrics.getHeight() * 2 + 18;
		int x = Math.max(8, (getWidth() - width) / 2);
		int y = Math.max(8, (getHeight() - height) / 2);
		graphics.setColor(new Color(255, 255, 255, 235));
		graphics.fillRoundRect(x, y, width, height, 10, 10);
		graphics.setColor(new Color(0xB0, 0xB4, 0xB8));
		graphics.drawRoundRect(x, y, width, height, 10, 10);
		graphics.setColor(new Color(0x20, 0x20, 0x20));
		graphics.drawString(headline, x + 12, y + 9 + metrics.getAscent());
		graphics.setColor(new Color(0x60, 0x60, 0x60));
		graphics.drawString(tileFailureMessage, x + 12, y + 9 + metrics.getHeight() + metrics.getAscent());
	}

	private void installLayoutListeners() {
		addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent event) {
				applyPendingFit();
			}
		});
		// The map often lives in an unselected tab, so it is first sized when the tab is shown.
		addHierarchyListener(event -> {
			if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
				applyPendingFit();
			}
		});
	}

	private void installMouseControls() {
		MouseAdapter mouse = new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent event) {
				if (!SwingUtilities.isLeftMouseButton(event)
						&& !SwingUtilities.isMiddleMouseButton(event)) return;
				requestFocusInWindow();
				dragOrigin = event.getPoint();
				dragCenterX = worldX(centerLongitudeDeg, zoom);
				dragCenterY = worldY(centerLatitudeDeg, zoom);
				setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
			}

			@Override
			public void mouseReleased(MouseEvent event) {
				if (dragOrigin != null && dragOrigin.distance(event.getPoint()) < 5.0) {
					MapMarker marker = markerAt(event.getPoint());
					if (marker != null) markerListener.accept(marker);
				}
				dragOrigin = null;
				setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			}

			@Override
			public void mouseClicked(MouseEvent event) {
				if (event.getClickCount() != 2) return;
				boolean out = event.isShiftDown() || event.isAltDown()
						|| !SwingUtilities.isLeftMouseButton(event);
				zoomBy(out ? -1.0 : 1.0, event.getPoint());
			}

			@Override
			public void mouseWheelMoved(MouseWheelEvent event) {
				handleScroll(event);
			}
		};
		addMouseListener(mouse);
		addMouseWheelListener(mouse);
		addMouseMotionListener(new MouseMotionAdapter() {
			@Override
			public void mouseDragged(MouseEvent event) {
				if (dragOrigin == null) return;
				setCenterFromWorld(dragCenterX - (event.getX() - dragOrigin.x),
						dragCenterY - (event.getY() - dragOrigin.y));
			}

			@Override
			public void mouseMoved(MouseEvent event) {
				coordinateListener.accept(fromScreen(event.getPoint()));
			}
		});
	}

	/**
	 * Routes scroll input the way desktop maps do: a stepped mouse wheel zooms, a trackpad's
	 * two-finger scroll pans, and holding {@code Cmd}/{@code Ctrl} zooms either way. Trackpad
	 * gestures are recognised by their fractional rotation, which the old integer-only handler
	 * rounded away to zero — leaving two-finger scrolling doing nothing at all.
	 */
	private void handleScroll(MouseWheelEvent event) {
		double precise = event.getPreciseWheelRotation();
		if (precise == 0.0) return;
		event.consume();
		long now = System.currentTimeMillis();
		boolean fractional = precise != Math.rint(precise);
		if (fractional) lastPreciseScrollMillis = now;
		boolean trackpad = fractional || now - lastPreciseScrollMillis < TRACKPAD_GESTURE_MILLIS;
		boolean zoomRequested = event.isControlDown() || event.isMetaDown() || !trackpad;

		if (zoomRequested) {
			zoomBy(-precise * (trackpad ? PRECISE_ZOOM_STEP : WHEEL_ZOOM_STEP), event.getPoint());
		} else if (event.isShiftDown()) {
			// macOS and Windows both report horizontal two-finger scroll as shift + wheel.
			panByPixels(precise * SCROLL_PAN_PIXELS, 0.0);
		} else {
			panByPixels(0.0, precise * SCROLL_PAN_PIXELS);
		}
	}

	private void installKeyboardControls() {
		setFocusable(true);
		InputMap input = getInputMap(WHEN_FOCUSED);
		ActionMap actions = getActionMap();
		bind(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "pan-left",
				() -> panByPixels(-KEY_PAN_PIXELS, 0));
		bind(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "pan-right",
				() -> panByPixels(KEY_PAN_PIXELS, 0));
		bind(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "pan-up",
				() -> panByPixels(0, -KEY_PAN_PIXELS));
		bind(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "pan-down",
				() -> panByPixels(0, KEY_PAN_PIXELS));
		bind(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, 0), "zoom-in-plus", this::zoomIn);
		bind(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, 0), "zoom-in-equals", this::zoomIn);
		bind(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_ADD, 0), "zoom-in-add", this::zoomIn);
		bind(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, 0), "zoom-out-minus", this::zoomOut);
		bind(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, 0), "zoom-out-subtract", this::zoomOut);
		bind(input, actions, KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0), "fit", this::fitToOverlays);
	}

	private static void bind(InputMap input, ActionMap actions, KeyStroke stroke, String name,
			Runnable action) {
		input.put(stroke, name);
		actions.put(name, new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent event) {
				action.run();
			}
		});
	}

	private void setZoom(double requestedZoom) {
		fitPending = false;
		zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, requestedZoom));
		repaint();
	}

	/** Applies fractional zoom immediately, keeping a trackpad pinch smooth and cursor-anchored. */
	private void zoomBy(double levels, Point anchorPoint) {
		double newZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom + levels));
		if (Math.abs(newZoom - zoom) < 1.0e-9) return;

		GeoPoint anchor = fromScreen(anchorPoint);
		fitPending = false;
		zoom = newZoom;
		setCenterFromWorld(worldX(anchor.longitudeDeg(), zoom) - (anchorPoint.x - getWidth() / 2.0),
				worldY(anchor.latitudeDeg(), zoom) - (anchorPoint.y - getHeight() / 2.0));
	}

	private void panByPixels(double dx, double dy) {
		fitPending = false;
		setCenterFromWorld(worldX(centerLongitudeDeg, zoom) + dx,
				worldY(centerLatitudeDeg, zoom) + dy);
	}

	private void setCenterFromWorld(double x, double y) {
		centerLongitudeDeg = longitudeFromWorldX(x, zoom);
		centerLatitudeDeg = latitudeFromWorldY(y, zoom);
		repaint();
	}

	private MapMarker markerAt(Point target) {
		MapMarker nearest = null;
		double nearestDistance = 10.0;
		for (MapMarker marker : markers) {
			double distance = toScreen(marker.point()).distance(target);
			if (distance < nearestDistance) {
				nearest = marker;
				nearestDistance = distance;
			}
		}
		return nearest;
	}

	private Point toScreen(GeoPoint point) {
		if (!isValid(point)) return new Point(Integer.MIN_VALUE, Integer.MIN_VALUE);
		double size = worldSize(zoom);
		double dx = worldX(point.longitudeDeg(), zoom) - worldX(centerLongitudeDeg, zoom);
		if (dx > size / 2.0) dx -= size;
		if (dx < -size / 2.0) dx += size;
		double dy = worldY(point.latitudeDeg(), zoom) - worldY(centerLatitudeDeg, zoom);
		return new Point((int) Math.round(getWidth() / 2.0 + dx),
				(int) Math.round(getHeight() / 2.0 + dy));
	}

	private GeoPoint fromScreen(Point point) {
		double centerX = worldX(centerLongitudeDeg, zoom);
		double centerY = worldY(centerLatitudeDeg, zoom);
		return new GeoPoint(latitudeFromWorldY(centerY + point.y - getHeight() / 2.0, zoom),
				longitudeFromWorldX(centerX + point.x - getWidth() / 2.0, zoom));
	}

	private int viewportTileZoom() {
		return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, (int) Math.floor(zoom)));
	}

	int getVisibleTileCount() {
		return visibleTiles.size();
	}

	int getLoadedVisibleTileCount() {
		int loaded = 0;
		for (TileKey key : visibleTiles) {
			if (tileCache.containsKey(key)) loaded++;
		}
		return loaded;
	}

	int getVisibleTileZoom() {
		return visibleTileZoom;
	}

	static double worldX(double longitudeDeg, double zoom) {
		return (normalizeLongitude(longitudeDeg) + 180.0) / 360.0 * worldSize(zoom);
	}

	static double worldY(double latitudeDeg, double zoom) {
		double latitudeRad = Math.toRadians(clampLatitude(latitudeDeg));
		return (1.0 - Math.log(Math.tan(latitudeRad) + 1.0 / Math.cos(latitudeRad)) / Math.PI)
				/ 2.0 * worldSize(zoom);
	}

	static double longitudeFromWorldX(double x, double zoom) {
		double size = worldSize(zoom);
		double wrapped = x % size;
		if (wrapped < 0.0) wrapped += size;
		return wrapped / size * 360.0 - 180.0;
	}

	static double latitudeFromWorldY(double y, double zoom) {
		double size = worldSize(zoom);
		double clampedY = Math.max(0.0, Math.min(size, y));
		double mercator = Math.PI * (1.0 - 2.0 * clampedY / size);
		return Math.toDegrees(Math.atan(Math.sinh(mercator)));
	}

	private static double worldSize(double zoom) {
		return TILE_SIZE * Math.pow(2.0, zoom);
	}

	private static double clampLatitude(double latitudeDeg) {
		return Math.max(-85.05112878, Math.min(85.05112878, latitudeDeg));
	}

	private static double normalizeLongitude(double longitudeDeg) {
		double result = (longitudeDeg + 180.0) % 360.0;
		if (result < 0.0) result += 360.0;
		return result - 180.0;
	}

	private static double longitudeOffset(double longitudeDeg, double originDeg) {
		return normalizeLongitude(longitudeDeg - originDeg);
	}

	private static double circularMeanLongitude(List<GeoPoint> points) {
		double sin = 0.0;
		double cos = 0.0;
		for (GeoPoint point : points) {
			double radians = Math.toRadians(point.longitudeDeg());
			sin += Math.sin(radians);
			cos += Math.cos(radians);
		}
		return Math.toDegrees(Math.atan2(sin, cos));
	}

	private static boolean isValid(GeoPoint point) {
		return point != null && Double.isFinite(point.latitudeDeg())
				&& Double.isFinite(point.longitudeDeg());
	}
}

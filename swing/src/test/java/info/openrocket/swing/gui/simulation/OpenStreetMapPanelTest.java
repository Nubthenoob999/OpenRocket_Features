package info.openrocket.swing.gui.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import info.openrocket.swing.gui.simulation.OpenStreetMapPanel.GeoPoint;
import info.openrocket.swing.gui.simulation.OpenStreetMapPanel.MapMarker;
import info.openrocket.swing.gui.simulation.OpenStreetMapPanel.MapPolyline;
import info.openrocket.swing.gui.simulation.OpenStreetMapPanel.MarkerType;

public class OpenStreetMapPanelTest {
	@Test
	public void testWebMercatorProjectionRoundTrips() {
		for (GeoPoint point : List.of(
				new GeoPoint(0.0, 0.0),
				new GeoPoint(39.7392, -104.9903),
				new GeoPoint(-33.8688, 151.2093),
				new GeoPoint(64.1466, -21.9426))) {
			double x = OpenStreetMapPanel.worldX(point.longitudeDeg(), 14);
			double y = OpenStreetMapPanel.worldY(point.latitudeDeg(), 14);
			assertEquals(point.longitudeDeg(), OpenStreetMapPanel.longitudeFromWorldX(x, 14), 1.0e-9);
			assertEquals(point.latitudeDeg(), OpenStreetMapPanel.latitudeFromWorldY(y, 14), 1.0e-9);
		}
	}

	@Test
	public void testFitIncludesLandingMarkersAndEllipse() {
		OpenStreetMapPanel panel = new OpenStreetMapPanel((zoom, x, y) ->
				new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB));
		panel.setSize(800, 500);
		List<MapMarker> markers = List.of(
				new MapMarker("launch", "Launch", "", new GeoPoint(39.0, -105.0),
						Color.GREEN, MarkerType.LAUNCH),
				new MapMarker("landing", "Landing", "", new GeoPoint(39.02, -104.97),
						Color.BLUE, MarkerType.LANDING));
		List<MapPolyline> rings = List.of(new MapPolyline("1 sigma", List.of(
				new GeoPoint(38.99, -105.01), new GeoPoint(39.03, -104.96)), Color.ORANGE, 2.0f));
		panel.setOverlays(markers, rings);
		panel.fitToOverlays();

		assertEquals(2, panel.getMarkerCount());
		assertEquals(1, panel.getPolylineCount());
		assertEquals(13, panel.getZoom());
	}

	@Test
	public void testFitWaitsForHiddenMapToReceiveItsRealViewport() throws Exception {
		OpenStreetMapPanel panel = new OpenStreetMapPanel((zoom, x, y) ->
				new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB));
		panel.setOverlays(List.of(
				new MapMarker("launch", "Launch", "", new GeoPoint(39.0, -105.0),
						Color.GREEN, MarkerType.LAUNCH),
				new MapMarker("landing", "Landing", "", new GeoPoint(39.004, -104.995),
						Color.BLUE, MarkerType.LANDING)),
				List.of(new MapPolyline("3 sigma", List.of(
						new GeoPoint(38.999, -105.001), new GeoPoint(39.005, -104.994)),
						Color.ORANGE, 2.0f)));

		panel.fitToOverlays();
		assertEquals(13, panel.getZoom(), "zero-size panel must defer its zoom decision");

		SwingUtilities.invokeAndWait(() -> panel.setSize(800, 500));
		SwingUtilities.invokeAndWait(() -> { });
		assertEquals(16, panel.getZoom(), "fit must use the actual visible map dimensions");
		assertEquals(39.002, panel.getCenter().latitudeDeg(), 0.001);
	}

	@Test
	public void testMouseWheelZoomsAndTrackpadScrollPans() {
		OpenStreetMapPanel panel = new OpenStreetMapPanel((zoom, x, y) ->
				new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB));
		panel.setSize(800, 500);
		panel.centerOn(39.0, -105.0, 13);

		panel.dispatchEvent(wheelEvent(panel, 0, 1, 1.0));
		assertEquals(12, panel.getZoom(), "a stepped mouse wheel should zoom around the cursor");

		GeoPoint beforePan = panel.getCenter();
		panel.dispatchEvent(wheelEvent(panel, 0, 1, 0.5));
		assertEquals(12, panel.getZoom(), "fractional two-finger scrolling should pan");
		assertNotEquals(beforePan.latitudeDeg(), panel.getCenter().latitudeDeg());

		panel.dispatchEvent(wheelEvent(panel, InputEvent.CTRL_DOWN_MASK, 1, 0.5));
		assertEquals(11.825, panel.getZoomLevel(), 1.0e-9,
				"Ctrl/Command + trackpad scroll should resize continuously");
	}

	@Test
	public void testDownloadedTilesReplaceThePlaceholder() throws Exception {
		Color tileColor = new Color(0x2A, 0x6F, 0xB0);
		OpenStreetMapPanel panel = new OpenStreetMapPanel((zoom, x, y) -> {
			BufferedImage tile = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
			Graphics2D graphics = tile.createGraphics();
			graphics.setColor(tileColor);
			graphics.fillRect(0, 0, tile.getWidth(), tile.getHeight());
			graphics.dispose();
			return tile;
		});
		panel.setSize(400, 300);
		panel.centerOn(35.16, -72.82, 13);

		BufferedImage firstFrame = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
		Graphics2D firstGraphics = firstFrame.createGraphics();
		panel.paint(firstGraphics);
		firstGraphics.dispose();
		awaitVisibleTiles(panel);

		BufferedImage loadedFrame = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
		Graphics2D loadedGraphics = loadedFrame.createGraphics();
		panel.paint(loadedGraphics);
		loadedGraphics.dispose();
		assertEquals(tileColor.getRGB(), loadedFrame.getRGB(200, 150));
		assertEquals(tileColor.getRGB(), loadedFrame.getRGB(20, 20));
		assertEquals(tileColor.getRGB(), loadedFrame.getRGB(380, 20));
	}

	@Test
	public void testPanningAndZoomingRequestTheNewVisibleTiles() throws Exception {
		Set<String> requested = ConcurrentHashMap.newKeySet();
		OpenStreetMapPanel panel = new OpenStreetMapPanel((zoom, x, y) -> {
			requested.add(zoom + "/" + x + "/" + y);
			return new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
		});
		panel.setSize(1200, 600);
		panel.centerOn(35.16, -72.82, 14);

		paint(panel);
		awaitVisibleTiles(panel);
		Set<String> firstViewport = Set.copyOf(requested);
		int firstZoom = panel.getVisibleTileZoom();
		assertTrue(panel.getVisibleTileCount() > 16,
				"a wide viewport must fetch enough tiles to fill the whole panel");

		for (int i = 0; i < 16; i++) panel.dispatchEvent(wheelEvent(panel, 0, 1, 0.5));
		paint(panel);
		awaitVisibleTiles(panel);
		assertTrue(requested.size() > firstViewport.size(),
				"panning beyond the cached viewport must request the newly visible tiles");

		panel.zoomIn();
		paint(panel);
		awaitVisibleTiles(panel);
		assertEquals(firstZoom + 1, panel.getVisibleTileZoom());
		assertTrue(requested.stream().anyMatch(value -> value.startsWith((firstZoom + 1) + "/")),
				"zooming must request tiles at the new native zoom level");
	}

	@Test
	public void testChangingViewportIgnoresAnOldRequestFailure() throws Exception {
		GeoPoint northCarolina = new GeoPoint(35.1758, -76.8283);
		GeoPoint alabama = new GeoPoint(34.90128, -86.57376);
		AtomicBoolean firstRequest = new AtomicBoolean(true);
		CountDownLatch oldRequestStarted = new CountDownLatch(1);
		CountDownLatch releaseOldRequest = new CountDownLatch(1);
		CountDownLatch oldRequestFinished = new CountDownLatch(1);
		List<String> statuses = new CopyOnWriteArrayList<>();
		OpenStreetMapPanel panel = new OpenStreetMapPanel((zoom, x, y) -> {
			if (firstRequest.getAndSet(false)) {
				oldRequestStarted.countDown();
				if (!releaseOldRequest.await(5, TimeUnit.SECONDS)) {
					throw new IOException("test timed out waiting to release old tile request");
				}
				oldRequestFinished.countDown();
				throw new IOException("old launch-site request failed");
			}
			return new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
		});
		panel.setStatusListener(statuses::add);
		panel.setSize(400, 300);
		panel.centerOn(northCarolina.latitudeDeg(), northCarolina.longitudeDeg(), 16);
		paint(panel);
		assertTrue(oldRequestStarted.await(2, TimeUnit.SECONDS));

		panel.centerOn(alabama.latitudeDeg(), alabama.longitudeDeg(), 16);
		paint(panel);
		awaitVisibleTiles(panel);
		int statusesBeforeOldFailure = statuses.size();
		releaseOldRequest.countDown();
		assertTrue(oldRequestFinished.await(2, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> { });

		assertFalse(statuses.subList(statusesBeforeOldFailure, statuses.size()).stream()
				.anyMatch(status -> status.contains("old launch-site")),
				"a completed request from the replaced coordinate region must not fail the new map");
		assertEquals(panel.getVisibleTileCount(), panel.getLoadedVisibleTileCount());
	}

	@Test
	public void testReloadRetriesVisibleFailuresAndReportsCompletion() throws Exception {
		AtomicBoolean failRequests = new AtomicBoolean(true);
		List<String> statuses = new CopyOnWriteArrayList<>();
		OpenStreetMapPanel panel = new OpenStreetMapPanel((zoom, x, y) -> {
			if (failRequests.get()) throw new IOException("temporary network failure");
			return new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
		});
		panel.setStatusListener(statuses::add);
		panel.setSize(400, 300);
		panel.centerOn(35.16, -72.82, 14);
		paint(panel);
		awaitCondition(() -> !statuses.isEmpty()
				&& statuses.get(statuses.size() - 1).contains("temporary network failure"));

		failRequests.set(false);
		panel.retryFailedTiles();
		awaitVisibleTiles(panel);
		awaitCondition(() -> "OpenStreetMap tiles loaded".equals(
				statuses.get(statuses.size() - 1)));
	}

	@Test
	public void testVisibleTilesStayInBoundsAtDateLineAndMercatorLimits() throws Exception {
		for (GeoPoint point : List.of(
				new GeoPoint(85.05112878, 179.999999),
				new GeoPoint(-85.05112878, -179.999999))) {
			Set<RequestedTile> requests = ConcurrentHashMap.newKeySet();
			OpenStreetMapPanel panel = new OpenStreetMapPanel((zoom, x, y) -> {
				requests.add(new RequestedTile(zoom, x, y));
				return new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
			});
			panel.setSize(400, 300);
			panel.centerOn(point.latitudeDeg(), point.longitudeDeg(), 16);
			paint(panel);
			awaitVisibleTiles(panel);
			assertEquals(panel.getVisibleTileCount(), requests.size());
			for (RequestedTile request : requests) {
				int tileCount = 1 << request.zoom();
				assertTrue(request.x() >= 0 && request.x() < tileCount);
				assertTrue(request.y() >= 0 && request.y() < tileCount);
			}
		}
	}

	private record RequestedTile(int zoom, int x, int y) { }

	private static void awaitVisibleTiles(OpenStreetMapPanel panel) throws Exception {
		awaitCondition(() -> panel.getVisibleTileCount() > 0
				&& panel.getLoadedVisibleTileCount() == panel.getVisibleTileCount());
	}

	private static void awaitCondition(BooleanSupplier condition) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
		while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
			SwingUtilities.invokeAndWait(() -> { });
			Thread.sleep(10);
		}
		assertTrue(condition.getAsBoolean(), "condition did not become true before timeout");
	}

	private static void paint(OpenStreetMapPanel panel) {
		BufferedImage frame = new BufferedImage(panel.getWidth(), panel.getHeight(),
				BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = frame.createGraphics();
		panel.paint(graphics);
		graphics.dispose();
	}

	private static MouseWheelEvent wheelEvent(OpenStreetMapPanel panel, int modifiers,
			int wheelRotation, double preciseRotation) {
		int x = panel.getWidth() / 2;
		int y = panel.getHeight() / 2;
		return new MouseWheelEvent(panel, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(),
				modifiers, x, y, x, y, 0, false,
				MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, wheelRotation, preciseRotation);
	}
}

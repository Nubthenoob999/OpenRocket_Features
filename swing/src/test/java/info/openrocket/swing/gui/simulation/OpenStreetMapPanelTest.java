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
		CountDownLatch loaded = new CountDownLatch(16);
		OpenStreetMapPanel panel = new OpenStreetMapPanel((zoom, x, y) -> {
			BufferedImage tile = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
			Graphics2D graphics = tile.createGraphics();
			graphics.setColor(tileColor);
			graphics.fillRect(0, 0, tile.getWidth(), tile.getHeight());
			graphics.dispose();
			loaded.countDown();
			return tile;
		});
		panel.setSize(400, 300);

		BufferedImage firstFrame = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
		Graphics2D firstGraphics = firstFrame.createGraphics();
		panel.paint(firstGraphics);
		firstGraphics.dispose();
		assertTrue(loaded.await(2, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> { });

		BufferedImage loadedFrame = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
		Graphics2D loadedGraphics = loadedFrame.createGraphics();
		panel.paint(loadedGraphics);
		loadedGraphics.dispose();
		assertEquals(tileColor.getRGB(), loadedFrame.getRGB(200, 150));
	}

	@Test
	public void testTileRequestsStayInsideFixedMosaicWhilePanning() throws Exception {
		Set<String> requested = ConcurrentHashMap.newKeySet();
		CountDownLatch mosaicLoaded = new CountDownLatch(16);
		OpenStreetMapPanel panel = new OpenStreetMapPanel((zoom, x, y) -> {
			if (requested.add(zoom + "/" + x + "/" + y)) mosaicLoaded.countDown();
			return new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
		});
		panel.setSize(400, 300);
		panel.centerOn(35.16, -72.82, 18);

		paint(panel);
		assertTrue(mosaicLoaded.await(2, TimeUnit.SECONDS));
		assertEquals(16, requested.size());
		assertEquals(16, panel.getTileRegionSize());
		assertTrue(panel.getTileRegionZoom() < panel.getZoom(),
				"the fixed mosaic should be rendered from a coarser native zoom");

		for (int i = 0; i < 20; i++) panel.dispatchEvent(wheelEvent(panel, 0, 1, 0.5));
		panel.zoomIn();
		paint(panel);
		SwingUtilities.invokeAndWait(() -> { });
		assertEquals(16, requested.size(),
				"panning must reuse the bounded mosaic instead of contacting OSM again");
	}

	@Test
	public void testChangingBetweenScreenshotCoordinatesIgnoresOldRegionFailure() throws Exception {
		GeoPoint northCarolina = new GeoPoint(35.1758, -76.8283);
		GeoPoint alabama = new GeoPoint(34.90128, -86.57376);
		AtomicBoolean firstRequest = new AtomicBoolean(true);
		CountDownLatch oldRequestStarted = new CountDownLatch(1);
		CountDownLatch releaseOldRequest = new CountDownLatch(1);
		CountDownLatch newRequestStarted = new CountDownLatch(1);
		CountDownLatch releaseNewRequests = new CountDownLatch(1);
		CountDownLatch newMosaicLoaded = new CountDownLatch(16);
		List<String> statuses = new CopyOnWriteArrayList<>();
		Set<RequestedTile> newRegionRequests = ConcurrentHashMap.newKeySet();
		OpenStreetMapPanel panel = new OpenStreetMapPanel((zoom, x, y) -> {
			if (firstRequest.getAndSet(false)) {
				oldRequestStarted.countDown();
				assertTrue(releaseOldRequest.await(2, TimeUnit.SECONDS));
				throw new IOException("old launch-site request failed");
			}
			newRequestStarted.countDown();
			assertTrue(releaseNewRequests.await(2, TimeUnit.SECONDS));
			newRegionRequests.add(new RequestedTile(zoom, x, y));
			newMosaicLoaded.countDown();
			return new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
		});
		panel.setStatusListener(statuses::add);
		panel.setSize(400, 300);
		panel.setTileRegion(List.of(northCarolina));
		panel.centerOn(northCarolina.latitudeDeg(), northCarolina.longitudeDeg(), 16);
		assertEquals(15, panel.getTileRegionZoom());
		paint(panel);
		assertTrue(oldRequestStarted.await(2, TimeUnit.SECONDS));

		panel.setTileRegion(List.of(alabama));
		panel.centerOn(alabama.latitudeDeg(), alabama.longitudeDeg(), 16);
		assertEquals(16, panel.getTileRegionZoom());
		paint(panel);
		releaseOldRequest.countDown();
		assertTrue(newRequestStarted.await(2, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> { });

		assertFalse(statuses.stream().anyMatch(status -> status.contains("old launch-site")),
				"a completed request from the replaced coordinate region must not fail the new map");
		releaseNewRequests.countDown();
		assertTrue(newMosaicLoaded.await(2, TimeUnit.SECONDS));
		assertEquals(expectedMosaic(16, 17005, 25978), newRegionRequests,
				"the dynamically entered Alabama site must generate the expected valid OSM URLs");
	}

	@Test
	public void testTileRegionStaysInBoundsAtDateLineAndMercatorLimits() throws Exception {
		for (GeoPoint point : List.of(
				new GeoPoint(85.05112878, 179.999999),
				new GeoPoint(-85.05112878, -179.999999))) {
			Set<RequestedTile> requests = ConcurrentHashMap.newKeySet();
			CountDownLatch loaded = new CountDownLatch(16);
			OpenStreetMapPanel panel = new OpenStreetMapPanel((zoom, x, y) -> {
				requests.add(new RequestedTile(zoom, x, y));
				loaded.countDown();
				return new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
			});
			panel.setSize(400, 300);
			panel.setTileRegion(List.of(point));
			panel.centerOn(point.latitudeDeg(), point.longitudeDeg(), 16);
			paint(panel);
			assertTrue(loaded.await(2, TimeUnit.SECONDS));
			assertEquals(16, requests.size());
			for (RequestedTile request : requests) {
				int tileCount = 1 << request.zoom();
				assertTrue(request.x() >= 0 && request.x() < tileCount);
				assertTrue(request.y() >= 0 && request.y() < tileCount);
			}
		}
	}

	private static Set<RequestedTile> expectedMosaic(int zoom, int firstX, int firstY) {
		Set<RequestedTile> expected = ConcurrentHashMap.newKeySet();
		for (int row = 0; row < 4; row++) {
			for (int column = 0; column < 4; column++) {
				expected.add(new RequestedTile(zoom, firstX + column, firstY + row));
			}
		}
		return Set.copyOf(expected);
	}

	private record RequestedTile(int zoom, int x, int y) { }

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

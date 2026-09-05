package info.openrocket.swing.gui.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.geotools.map.FeatureLayer;
import org.junit.jupiter.api.Test;

import info.openrocket.swing.gui.simulation.GeoToolsMapPanel.GeoPoint;
import info.openrocket.swing.gui.simulation.GeoToolsMapPanel.MapMarker;
import info.openrocket.swing.gui.simulation.GeoToolsMapPanel.MapPolyline;
import info.openrocket.swing.gui.simulation.GeoToolsMapPanel.MarkerType;
import info.openrocket.swing.gui.simulation.GeoToolsMapPanel.SigmaLevel;

public class GeoToolsMapPanelTest {
	@Test
	public void testWgs84SchemasUseSeparateGeometryTypes() {
		assertEquals(org.locationtech.jts.geom.Point.class,
				OpenRocketGisView.POINT_TYPE.getGeometryDescriptor().getType().getBinding());
		assertEquals(org.locationtech.jts.geom.LineString.class,
				OpenRocketGisView.LINE_TYPE.getGeometryDescriptor().getType().getBinding());
		assertEquals(org.locationtech.jts.geom.Polygon.class,
				OpenRocketGisView.POLYGON_TYPE.getGeometryDescriptor().getType().getBinding());
		assertTrue(OpenRocketGisView.POINT_TYPE.getCoordinateReferenceSystem().getName().toString()
				.contains("WGS 84"));
	}

	@Test
	public void testVisibilityChangesOnlyMatchingPersistentLayer() {
		GeoToolsMapPanel panel = new GeoToolsMapPanel();
		panel.setSize(800, 500);
		List<GeoPoint> ring = List.of(
				new GeoPoint(38.99, -105.01), new GeoPoint(39.01, -105.01),
				new GeoPoint(39.01, -104.99), new GeoPoint(38.99, -104.99),
				new GeoPoint(38.99, -105.01));
		panel.setOverlays(List.of(
				new MapMarker("launch", "Launch", "", new GeoPoint(39.0, -105.0),
						Color.GREEN, MarkerType.LAUNCH),
				new MapMarker("mean", "Mean", "", new GeoPoint(39.001, -105.0),
						Color.RED, MarkerType.MEAN),
				new MapMarker("landing", "Landing", "", new GeoPoint(39.002, -105.0),
						Color.BLUE, MarkerType.LANDING)),
				List.of(
						new MapPolyline("1 sigma", ring, Color.YELLOW, 2.2f, SigmaLevel.ONE),
						new MapPolyline("2 sigma", ring, Color.ORANGE, 2.2f, SigmaLevel.TWO),
						new MapPolyline("3 sigma", ring, Color.GREEN, 2.2f, SigmaLevel.THREE)));

		var content = panel.getMapContent();
		int layerCount = content.layers().size();
		var untouched = content.layers().stream()
				.filter(layer -> !layer.getTitle().startsWith("sigma-one"))
				.toList();
		panel.setSigmaVisible(SigmaLevel.ONE, false);

		assertSame(content, panel.getMapContent());
		assertEquals(layerCount, content.layers().size());
		assertTrue(content.layers().stream()
				.filter(layer -> layer.getTitle().startsWith("sigma-one"))
				.allMatch(layer -> !layer.isVisible()));
		assertTrue(untouched.stream().allMatch(org.geotools.map.Layer::isVisible));
		assertTrue(!panel.isSigmaVisible(SigmaLevel.ONE));
		assertTrue(panel.isMarkerTypeVisible(MarkerType.LANDING));

		panel.setMarkerTypeVisible(MarkerType.LANDING, false);
		assertSame(content, panel.getMapContent());
		assertTrue(content.layers().stream()
				.filter(layer -> layer.getTitle().startsWith("markers-landing"))
				.allMatch(layer -> !layer.isVisible()));
		assertTrue(content.layers().stream()
				.filter(layer -> layer.getTitle().startsWith("markers-mean"))
				.allMatch(org.geotools.map.Layer::isVisible));
	}

	@Test
	public void testPolygonStyleRendersUnderCommaDecimalLocale() {
		Locale original = Locale.getDefault();
		try {
			Locale.setDefault(Locale.GERMANY);
			GeoToolsMapPanel panel = new GeoToolsMapPanel();
			panel.setSize(500, 350);
			panel.centerOn(39.0, -105.0, 13);
			List<GeoPoint> ring = List.of(
					new GeoPoint(38.995, -105.005), new GeoPoint(39.005, -105.005),
					new GeoPoint(39.005, -104.995), new GeoPoint(38.995, -104.995),
					new GeoPoint(38.995, -105.005));
			panel.setOverlays(List.of(), List.of(
					new MapPolyline("1 sigma", ring, Color.RED, 2.2f, SigmaLevel.ONE)));
			BufferedImage image = panel.renderSnapshot(500, 350);
			assertTrue(countDistinctColors(image) > 3);
		} finally {
			Locale.setDefault(original);
		}
	}

	@Test
	public void testOverlaysBecomeNativeGeoToolsFeatureLayers() {
		GeoToolsMapPanel panel = new GeoToolsMapPanel();
		panel.setSize(800, 500);
		panel.setOverlays(List.of(
				new MapMarker("launch", "Launch", "", new GeoPoint(39.0, -105.0),
						Color.GREEN, MarkerType.LAUNCH),
				new MapMarker("landing", "Landing", "", new GeoPoint(39.02, -104.97),
						Color.BLUE, MarkerType.LANDING)),
				List.of(new MapPolyline("1 sigma", List.of(
						new GeoPoint(38.99, -105.01), new GeoPoint(39.03, -104.96)),
						Color.ORANGE, 2.0f)));

		assertEquals(2, panel.getMarkerCount());
		assertEquals(1, panel.getPolylineCount());
		assertEquals(3, panel.getFeatureLayerCount());
		assertTrue(panel.getMapContent().layers().stream().allMatch(FeatureLayer.class::isInstance));
	}

	@Test
	public void testFitIncludesLandingMarkersAndEllipse() {
		GeoToolsMapPanel panel = new GeoToolsMapPanel();
		panel.setSize(800, 500);
		panel.setOverlays(List.of(
				new MapMarker("launch", "Launch", "", new GeoPoint(39.0, -105.0),
						Color.GREEN, MarkerType.LAUNCH),
				new MapMarker("landing", "Landing", "", new GeoPoint(39.02, -104.97),
						Color.BLUE, MarkerType.LANDING)),
				List.of(new MapPolyline("1 sigma", List.of(
						new GeoPoint(38.99, -105.01), new GeoPoint(39.03, -104.96)),
						Color.ORANGE, 2.0f)));

		panel.fitToOverlays();

		assertEquals(13, panel.getZoom());
		assertEquals(39.01, panel.getCenter().latitudeDeg(), 0.02);
		assertEquals(-104.985, panel.getCenter().longitudeDeg(), 0.02);
	}

	@Test
	public void testFitWaitsForHiddenMapToReceiveItsRealViewport() throws Exception {
		GeoToolsMapPanel panel = new GeoToolsMapPanel();
		panel.setOverlays(List.of(
				new MapMarker("launch", "Launch", "", new GeoPoint(39.0, -105.0),
						Color.GREEN, MarkerType.LAUNCH),
				new MapMarker("landing", "Landing", "", new GeoPoint(39.004, -104.995),
						Color.BLUE, MarkerType.LANDING)), List.of());

		panel.fitToOverlays();
		assertEquals(13, panel.getZoom());

		SwingUtilities.invokeAndWait(() -> panel.setSize(800, 500));
		SwingUtilities.invokeAndWait(() -> { });
		assertEquals(16, panel.getZoom());
		assertEquals(39.002, panel.getCenter().latitudeDeg(), 0.001);
	}

	@Test
	public void testMouseWheelZoomsAndTrackpadScrollPans() {
		GeoToolsMapPanel panel = new GeoToolsMapPanel();
		panel.setSize(800, 500);
		panel.centerOn(39.0, -105.0, 13);

		panel.dispatchEvent(wheelEvent(panel, 0, 1, 1.0));
		assertEquals(12, panel.getZoom());

		GeoPoint beforePan = panel.getCenter();
		panel.dispatchEvent(wheelEvent(panel, 0, 1, 0.5));
		assertEquals(12, panel.getZoom());
		assertNotEquals(beforePan.latitudeDeg(), panel.getCenter().latitudeDeg());

		panel.dispatchEvent(wheelEvent(panel, InputEvent.CTRL_DOWN_MASK, 1, 0.5));
		assertEquals(11.825, panel.getZoomLevel(), 1.0e-9);
	}

	@Test
	public void testClickingMarkerPublishesItsDetails() {
		GeoToolsMapPanel panel = new GeoToolsMapPanel();
		panel.setSize(800, 500);
		panel.centerOn(39.0, -105.0, 13);
		MapMarker marker = new MapMarker("landing", "Landing", "Run 2",
				new GeoPoint(39.0, -105.0), Color.BLUE, MarkerType.LANDING);
		panel.setOverlays(List.of(marker), List.of());
		AtomicReference<MapMarker> selected = new AtomicReference<>();
		panel.setMarkerListener(selected::set);

		panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_CLICKED,
				System.currentTimeMillis(), 0, 400, 250, 1, false));

		assertSame(marker, selected.get());
	}

	@Test
	public void testHeadlessSnapshotUsesStreamingRenderer() {
		GeoToolsMapPanel panel = new GeoToolsMapPanel();
		try {
			panel.setSize(800, 500);
			panel.centerOn(39.0, -105.0, 13);
			panel.setOverlays(List.of(new MapMarker("landing", "Landing", "Run 1",
					new GeoPoint(39.0, -105.0), Color.BLUE, MarkerType.LANDING)), List.of());

			BufferedImage snapshot = panel.renderSnapshot(800, 500);

			assertEquals(800, snapshot.getWidth());
			assertEquals(500, snapshot.getHeight());
			assertNotEquals(panel.getBackground().getRGB(), snapshot.getRGB(400, 250));
		} finally {
			panel.shutdownRendering();
		}
	}

	private static MouseWheelEvent wheelEvent(GeoToolsMapPanel panel, int modifiers,
			int wheelRotation, double preciseRotation) {
		int x = panel.getWidth() / 2;
		int y = panel.getHeight() / 2;
		return new MouseWheelEvent(panel, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(),
				modifiers, x, y, x, y, 0, false,
				MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, wheelRotation, preciseRotation);
	}

	private static int countDistinctColors(BufferedImage image) {
		java.util.Set<Integer> colors = new java.util.HashSet<>();
		for (int y = 0; y < image.getHeight(); y += 3) {
			for (int x = 0; x < image.getWidth(); x += 3) colors.add(image.getRGB(x, y));
		}
		return colors.size();
	}
}

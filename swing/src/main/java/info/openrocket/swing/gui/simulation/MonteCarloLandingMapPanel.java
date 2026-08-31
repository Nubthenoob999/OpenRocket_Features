package info.openrocket.swing.gui.simulation;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.Font;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

import info.openrocket.core.montecarlo.LandingDispersion6DOF;
import info.openrocket.core.montecarlo.LandingDispersion6DOF.BodyPoints;
import info.openrocket.core.montecarlo.LandingDispersion6DOF.LandingPoint;
import info.openrocket.core.montecarlo.LandingDispersion6DOF.Summary;
import info.openrocket.core.montecarlo.MonteCarloRunRecord;
import info.openrocket.swing.gui.simulation.OpenStreetMapPanel.GeoPoint;
import info.openrocket.swing.gui.simulation.OpenStreetMapPanel.MapMarker;
import info.openrocket.swing.gui.simulation.OpenStreetMapPanel.MapPolyline;
import info.openrocket.swing.gui.simulation.OpenStreetMapPanel.MarkerType;
import net.miginfocom.swing.MigLayout;

/** OpenStreetMap view of Monte Carlo landing coordinates and KML-equivalent dispersion rings. */
final class MonteCarloLandingMapPanel extends JPanel {
	private static final String ALL_BODIES = "All bodies";
	private static final Color LAUNCH_COLOR = new Color(0x27, 0xAE, 0x60);
	private static final Color MEAN_COLOR = new Color(0xC0, 0x39, 0x2B);
	private static final Color ONE_SIGMA_COLOR = new Color(0xF1, 0xC4, 0x0F);
	private static final Color TWO_SIGMA_COLOR = new Color(0xE6, 0x7E, 0x22);
	private static final Color THREE_SIGMA_COLOR = new Color(0x2E, 0xCC, 0x71);
	private static final Color[] BODY_COLORS = {
			new Color(0x2E, 0x86, 0xC1), new Color(0x8E, 0x44, 0xAD), new Color(0x16, 0xA0, 0x85),
			new Color(0xD3, 0x54, 0x00), new Color(0x7F, 0x8C, 0x8D), new Color(0x2C, 0x3E, 0x50)
	};

	private record LandingCloud(String id, String name, List<LandingPoint> points, Color color) { }
	private record TableLanding(String markerId, String bodyName, LandingPoint point) { }

	private final OpenStreetMapPanel mapPanel;
	private final JComboBox<String> bodyCombo = new JComboBox<>();
	private final JCheckBox showLandings = new JCheckBox("Landing points", true);
	private final JCheckBox showMeans = new JCheckBox("Mean impacts", true);
	private final JCheckBox showOneSigma = new JCheckBox("1\u03c3", true);
	private final JCheckBox showTwoSigma = new JCheckBox("2\u03c3", true);
	private final JCheckBox showThreeSigma = new JCheckBox("3\u03c3", true);
	private final JLabel coordinateLabel = new JLabel("Move over the map to inspect GPS coordinates.");
	private final JLabel statusLabel = new JLabel(" ");
	private final DefaultTableModel coordinateModel;
	private final JTable coordinateTable;
	private final Map<String, Integer> markerRows = new LinkedHashMap<>();
	private List<LandingCloud> clouds = List.of();
	private double launchLatitudeDeg;
	private double launchLongitudeDeg;

	MonteCarloLandingMapPanel() {
		this(new OpenStreetMapPanel());
	}

	MonteCarloLandingMapPanel(OpenStreetMapPanel mapPanel) {
		this.mapPanel = mapPanel;
		setLayout(new MigLayout("fill, ins 0, wrap 1, hidemode 3", "[grow,fill]", "[]4[grow,fill]4[]4[150!]4[]"));
		add(buildToolbar(), "growx");
		add(mapPanel, "grow, push");

		coordinateLabel.setFont(coordinateLabel.getFont().deriveFont(Font.PLAIN));
		add(coordinateLabel, "growx");

		coordinateModel = new DefaultTableModel(new Object[] {
				"Run", "Body", "Latitude", "Longitude", "East (m)", "North (m)"
		}, 0) {
			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};
		coordinateTable = new JTable(coordinateModel);
		coordinateTable.setAutoCreateRowSorter(true);
		add(new JScrollPane(coordinateTable), "growx, hmin 130");

		statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN));
		add(statusLabel, "growx");

		mapPanel.setCoordinateListener(point -> coordinateLabel.setText(String.format(Locale.US,
				"Cursor: %.7f, %.7f | zoom %.1f", point.latitudeDeg(), point.longitudeDeg(),
				mapPanel.getZoomLevel())));
		mapPanel.setMarkerListener(this::selectMarker);
		mapPanel.setStatusListener(statusLabel::setText);
	}

	private JPanel buildToolbar() {
		JPanel toolbar = new JPanel(new MigLayout("ins 0, gap 6",
				"[][][][][][][]push[][][][][]"));
		toolbar.add(new JLabel("Body"));
		bodyCombo.addActionListener(event -> rebuildOverlays(false));
		toolbar.add(bodyCombo, "wmin 150");

		showLandings.addActionListener(event -> rebuildOverlays(false));
		showMeans.addActionListener(event -> rebuildOverlays(false));
		showOneSigma.addActionListener(event -> rebuildOverlays(false));
		showTwoSigma.addActionListener(event -> rebuildOverlays(false));
		showThreeSigma.addActionListener(event -> rebuildOverlays(false));
		toolbar.add(showLandings);
		toolbar.add(showMeans);
		toolbar.add(showOneSigma);
		toolbar.add(showTwoSigma);
		toolbar.add(showThreeSigma);

		JButton zoomOut = new JButton("\u2212");
		zoomOut.setToolTipText("Zoom out");
		zoomOut.addActionListener(event -> mapPanel.zoomOut());
		toolbar.add(zoomOut);
		JButton zoomIn = new JButton("+");
		zoomIn.setToolTipText("Zoom in");
		zoomIn.addActionListener(event -> mapPanel.zoomIn());
		toolbar.add(zoomIn);
		JButton fit = new JButton("Fit");
		fit.setToolTipText("Fit the launch site, landings, and visible dispersion rings.");
		fit.addActionListener(event -> mapPanel.fitToOverlays());
		toolbar.add(fit);

		JButton reloadTiles = new JButton("Reload map");
		reloadTiles.setToolTipText("Retry any OpenStreetMap tiles that failed to download.");
		reloadTiles.addActionListener(event -> {
			statusLabel.setText("Retrying OpenStreetMap tiles...");
			mapPanel.retryFailedTiles();
		});
		toolbar.add(reloadTiles);

		JLabel attribution = new JLabel("<html><a href=''>\u00a9 OpenStreetMap contributors</a></html>");
		attribution.setToolTipText("Open OpenStreetMap copyright and attribution information.");
		attribution.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mouseClicked(java.awt.event.MouseEvent event) {
				openAttributionPage();
			}
		});
		toolbar.add(attribution, "gapleft 8");
		return toolbar;
	}

	void setResults(List<MonteCarloRunRecord> records, double launchLatitude, double launchLongitude) {
		launchLatitudeDeg = launchLatitude;
		launchLongitudeDeg = launchLongitude;
		List<LandingCloud> found = new ArrayList<>();
		int colorIndex = 0;
		for (BodyPoints body : LandingDispersion6DOF.collectBodyLandingPoints(records)) {
			String name = body.branchName == null || body.branchName.isBlank() ? body.bodyId : body.branchName;
			found.add(new LandingCloud(body.bodyId, name, List.copyOf(body.points),
					BODY_COLORS[colorIndex++ % BODY_COLORS.length]));
		}
		if (found.isEmpty()) {
			List<LandingPoint> primary = LandingDispersion6DOF.collectLandingPoints(
					records, launchLatitudeDeg, launchLongitudeDeg);
			if (!primary.isEmpty()) found.add(new LandingCloud("primary", "Primary", primary, BODY_COLORS[0]));
		}
		clouds = List.copyOf(found);
		List<GeoPoint> tileRegionPoints = new ArrayList<>();
		tileRegionPoints.add(new GeoPoint(launchLatitudeDeg, launchLongitudeDeg));
		for (LandingCloud cloud : clouds) {
			for (LandingPoint point : cloud.points()) {
				tileRegionPoints.add(new GeoPoint(point.lat_deg, point.lon_deg));
			}
		}
		mapPanel.setTileRegion(tileRegionPoints);

		String selection = (String) bodyCombo.getSelectedItem();
		bodyCombo.removeAllItems();
		bodyCombo.addItem(ALL_BODIES);
		for (LandingCloud cloud : clouds) bodyCombo.addItem(cloud.name());
		if (selection != null) bodyCombo.setSelectedItem(selection);
		if (bodyCombo.getSelectedIndex() < 0) bodyCombo.setSelectedIndex(0);
		rebuildOverlays(true);
	}

	private void rebuildOverlays(boolean fit) {
		if (bodyCombo.getItemCount() == 0) return;
		String selectedBody = (String) bodyCombo.getSelectedItem();
		List<MapMarker> markers = new ArrayList<>();
		List<MapPolyline> polylines = new ArrayList<>();
		List<TableLanding> tableLandings = new ArrayList<>();
		markers.add(new MapMarker("launch", "Launch site",
				formatCoordinates(launchLatitudeDeg, launchLongitudeDeg),
				new GeoPoint(launchLatitudeDeg, launchLongitudeDeg), LAUNCH_COLOR, MarkerType.LAUNCH));

		for (LandingCloud cloud : clouds) {
			if (!ALL_BODIES.equals(selectedBody) && !cloud.name().equals(selectedBody)) continue;
			if (showLandings.isSelected()) {
				for (LandingPoint point : cloud.points()) {
					String id = cloud.id() + ":" + point.runIndex;
					String details = String.format(Locale.US,
							"Run %d, %s | %.7f, %.7f | E %.2f m, N %.2f m",
							point.runIndex, cloud.name(), point.lat_deg, point.lon_deg,
							point.east_m, point.north_m);
					markers.add(new MapMarker(id, "Run " + point.runIndex, details,
							new GeoPoint(point.lat_deg, point.lon_deg), cloud.color(), MarkerType.LANDING));
					tableLandings.add(new TableLanding(id, cloud.name(), point));
				}
			}

			Summary summary = LandingDispersion6DOF.summarize(
					cloud.points(), launchLatitudeDeg, launchLongitudeDeg);
			if (summary.n > 0 && showMeans.isSelected()) {
				markers.add(new MapMarker("mean:" + cloud.id(), "Mean: " + cloud.name(),
						formatCoordinates(summary.meanLat_deg, summary.meanLon_deg),
						new GeoPoint(summary.meanLat_deg, summary.meanLon_deg), MEAN_COLOR, MarkerType.MEAN));
			}
			if (summary.n >= 2) {
				if (showOneSigma.isSelected()) addRing(polylines, cloud.name() + " 1\u03c3", summary,
						summary.oneSigma, ONE_SIGMA_COLOR);
				if (showTwoSigma.isSelected()) addRing(polylines, cloud.name() + " 2\u03c3", summary,
						summary.twoSigma, TWO_SIGMA_COLOR);
				if (showThreeSigma.isSelected()) addRing(polylines, cloud.name() + " 3\u03c3", summary,
						summary.threeSigma, THREE_SIGMA_COLOR);
			}
		}

		mapPanel.setOverlays(markers, polylines);
		rebuildCoordinateTable(tableLandings);
		if (fit) {
			mapPanel.centerOn(launchLatitudeDeg, launchLongitudeDeg, 13);
			// With no completed landing cloud, fitting the launch marker alone jumps to street
			// level.  Keep the useful launch-area overview until there are results to frame.
			if (!clouds.isEmpty()) mapPanel.fitToOverlays();
		}
		statusLabel.setText(String.format(Locale.US,
				"%d landing coordinate%s plotted%s", tableLandings.size(),
				tableLandings.size() == 1 ? "" : "s",
				clouds.isEmpty() ? "; run a batch to populate this map." : "."));
	}

	private static void addRing(List<MapPolyline> target, String label, Summary summary,
			LandingDispersion6DOF.Ellipse ellipse, Color color) {
		List<GeoPoint> points = LandingDispersion6DOF.buildEllipseRing(summary, ellipse).stream()
				.map(point -> new GeoPoint(point.lat_deg, point.lon_deg))
				.toList();
		if (!points.isEmpty()) target.add(new MapPolyline(label, points, color, 2.2f));
	}

	private void rebuildCoordinateTable(List<TableLanding> landings) {
		coordinateModel.setRowCount(0);
		markerRows.clear();
		for (TableLanding landing : landings) {
			LandingPoint point = landing.point();
			int row = coordinateModel.getRowCount();
			markerRows.put(landing.markerId(), row);
			coordinateModel.addRow(new Object[] {
					point.runIndex, landing.bodyName(),
					String.format(Locale.US, "%.7f", point.lat_deg),
					String.format(Locale.US, "%.7f", point.lon_deg),
					String.format(Locale.US, "%.2f", point.east_m),
					String.format(Locale.US, "%.2f", point.north_m)
			});
		}
	}

	private void selectMarker(MapMarker marker) {
		coordinateLabel.setText(marker.label() + ": " + marker.details());
		Integer modelRow = markerRows.get(marker.id());
		if (modelRow != null) {
			int viewRow = coordinateTable.convertRowIndexToView(modelRow);
			coordinateTable.getSelectionModel().setSelectionInterval(viewRow, viewRow);
			coordinateTable.scrollRectToVisible(coordinateTable.getCellRect(viewRow, 0, true));
		}
	}

	OpenStreetMapPanel getMapPanel() {
		return mapPanel;
	}

	JTable getCoordinateTable() {
		return coordinateTable;
	}

	private static String formatCoordinates(double latitude, double longitude) {
		return String.format(Locale.US, "%.7f, %.7f", latitude, longitude);
	}

	private void openAttributionPage() {
		if (!Desktop.isDesktopSupported()) return;
		try {
			Desktop.getDesktop().browse(URI.create("https://www.openstreetmap.org/copyright"));
		} catch (Exception exception) {
			statusLabel.setText("Could not open OpenStreetMap attribution: " + exception.getMessage());
		}
	}
}

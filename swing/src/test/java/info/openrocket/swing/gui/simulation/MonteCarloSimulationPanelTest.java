package info.openrocket.swing.gui.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JCheckBox;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.junit.jupiter.api.Test;

import info.openrocket.core.document.Simulation;
import info.openrocket.core.montecarlo.LandingDispersion6DOF;
import info.openrocket.core.montecarlo.MonteCarloBatchRunner;
import info.openrocket.core.montecarlo.MonteCarloExtension;
import info.openrocket.core.montecarlo.MonteCarloRunRecord;
import info.openrocket.core.util.TestRockets;
import info.openrocket.swing.util.BaseTestCase;

public class MonteCarloSimulationPanelTest extends BaseTestCase {

	@Test
	public void testSetupTabKeepsTheDisturbanceControlsAndDropsNothingElse() {
		MonteCarloSimulationPanel panel = new MonteCarloSimulationPanel(newSimulation());

		JTabbedPane outer = findFirst(panel, JTabbedPane.class);
		assertNotNull(outer);
		assertEquals(List.of("Setup", "Results"), tabTitles(outer));

		JTabbedPane settings = findAll(panel, JTabbedPane.class).get(1);
		assertEquals(List.of("Launch", "Atmosphere", "Disturbances", "Vehicle"), tabTitles(settings));

		Component disturbances = settings.getComponentAt(settings.indexOfTab("Disturbances"));
		List<String> checkBoxLabels = findAll(disturbances, JCheckBox.class).stream()
				.map(JCheckBox::getText)
				.toList();
		assertEquals(List.of("Enable gust events", "Enable shear layer"), checkBoxLabels);
	}

	@Test
	public void testSetupTabTracksNarrowViewportAndContainsWideBatchResults() throws Exception {
		MonteCarloSetupPanel[] setupHolder = new MonteCarloSetupPanel[1];
		JScrollPane[] outerScrollHolder = new JScrollPane[1];
		SwingUtilities.invokeAndWait(() -> {
			MonteCarloSetupPanel setup = new MonteCarloSetupPanel(
					new MonteCarloExtension(), newSimulation());
			setupHolder[0] = setup;
			outerScrollHolder[0] = setup.wrapInScrollPane();

			JTextField exportPath = findAll(setup, JTextField.class).stream()
					.filter(field -> "Output folder for Monte Carlo exports.".equals(field.getToolTipText()))
					.findFirst().orElseThrow();
			exportPath.setText("/a/very/long/export/path/that/must/not/determine/the/dialog/viewport/width");
			for (JTextArea summary : findAll(setup, JTextArea.class)) {
				SimulationTabLayoutUtils.setWrappingDisplayText(summary,
						"Runs=100 + nominal | Failures=0 | Apogee points=100 | Landing points=100 | "
								+ "Mean apogee=3359.573 m | Mean flight=237.155 s | "
								+ "Landing R50/R90/R95=47.974/105.613/117.404 m");
			}

			JScrollPane outer = outerScrollHolder[0];
			outer.setSize(new Dimension(760, 650));
			layoutTree(outer);
		});

		MonteCarloSetupPanel setup = setupHolder[0];
		JScrollPane outerScroll = outerScrollHolder[0];
		assertTrue(setup instanceof Scrollable);
		assertTrue(setup.getScrollableTracksViewportWidth());
		assertEquals(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER,
				outerScroll.getHorizontalScrollBarPolicy());
		assertTrue(setup.getWidth() <= outerScroll.getViewport().getExtentSize().width,
				"Monte Carlo setup content must stay within the simulation dialog viewport");

		JTable resultsTable = findFirst(setup, JTable.class);
		JScrollPane resultsScroll = (JScrollPane) SwingUtilities.getAncestorOfClass(
				JScrollPane.class, resultsTable);
		assertNotNull(resultsScroll);
		assertEquals(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED,
				resultsScroll.getHorizontalScrollBarPolicy());
		assertTrue(resultsTable.getPreferredSize().width > resultsScroll.getViewport().getExtentSize().width,
				"only the detailed results table should retain horizontal scrolling");
		assertTrue(findAll(setup, JTextArea.class).stream().anyMatch(area -> area.getRows() > 1),
				"long batch summaries should wrap instead of widening the tab");
	}

	@Test
	public void testExtensionIsAttachedOnlyOnceTheUserOptsIn() {
		Simulation simulation = newSimulation();
		MonteCarloSimulationPanel panel = new MonteCarloSimulationPanel(simulation);
		AtomicBoolean simulationChangeObserved = new AtomicBoolean();
		simulation.addChangeListener(event -> simulationChangeObserved.set(true));

		assertTrue(simulation.getSimulationExtensions().isEmpty(),
				"opening the tab must not add an extension to an untouched simulation");

		JCheckBox enabled = findAll(panel, JCheckBox.class).stream()
				.filter(box -> "Enabled".equals(box.getText()))
				.findFirst()
				.orElseThrow();
		enabled.doClick();

		assertEquals(List.of(panel.getExtension()), simulation.getSimulationExtensions(),
				"touching a control must attach exactly one extension");
		assertTrue(simulationChangeObserved.get(),
				"native Monte Carlo edits must participate in simulation dirty-state handling");
		assertFalse(panel.getExtension().isEnabled(), "the click must still toggle the setting");

		enabled.doClick();
		assertEquals(List.of(panel.getExtension()), simulation.getSimulationExtensions(),
				"further edits must not attach the extension twice");
		assertTrue(panel.getExtension().isEnabled());
	}

	@Test
	public void testAnAlreadyAttachedExtensionIsEditedInPlace() {
		Simulation simulation = newSimulation();
		MonteCarloExtension existing = new MonteCarloExtension();
		existing.setShearLayerEnabled(true);
		simulation.getSimulationExtensions().add(existing);

		MonteCarloSimulationPanel panel = new MonteCarloSimulationPanel(simulation);

		assertSame(existing, panel.getExtension());
		assertTrue(existing.isShearLayerEnabled(), "opening the UI must preserve the shear setting");
		assertEquals(1, simulation.getSimulationExtensions().size());
	}

	@Test
	public void testVisualizationPanelPlotsAndSummarizesBatchResults() throws Exception {
		Simulation simulation = newSimulation();
		simulation.getOptions().setLaunchRodLength(3.0);
		simulation.getOptions().getAverageWindModel().setAverage(2.0);

		MonteCarloExtension extension = new MonteCarloExtension();
		extension.setUseDeterministicSeed(true);
		extension.setRandomSeed(4242);
		extension.setCdMultiplierSigma(0.05);
		simulation.getSimulationExtensions().add(extension);

		List<MonteCarloRunRecord> records =
				MonteCarloBatchRunner.runBatchParallel(simulation, 2, 2, null);

		MonteCarloVisualizationPanel panel = new MonteCarloVisualizationPanel(simulation);
		JTable stats = findFirst(panel, JTable.class);
		assertNotNull(stats);
		assertEquals(0, stats.getRowCount(), "an empty batch must not fabricate statistics");

		panel.setResults(records);

		assertTrue(stats.getRowCount() > 0, "every finite metric must appear in the statistics table");
		ChartPanel chartPanel = findFirst(panel, ChartPanel.class);
		assertNotNull(chartPanel);
		assertTrue(chartPanel.getChart().getTitle().getText().startsWith("Landing dispersion"));
		assertTrue(chartPanel.getChart().getXYPlot().getDataset().getSeriesCount() > 0);

		JTabbedPane resultViews = findAll(panel, JTabbedPane.class).stream()
				.filter(tabs -> tabs.indexOfTab("Landing map") >= 0)
				.findFirst()
				.orElseThrow();
		assertEquals(List.of("Charts & statistics", "Landing map"), tabTitles(resultViews));

		MonteCarloLandingMapPanel landingMap = findFirst(panel, MonteCarloLandingMapPanel.class);
		assertNotNull(landingMap);
		assertTrue(landingMap.getCoordinateTable().getRowCount() > 0,
				"the map coordinate table must expose the landings exported to KML");
		assertTrue(landingMap.getMapPanel().getMarkerCount() > 1,
				"the map must include launch and landing markers");
		assertTrue(landingMap.getMapPanel().getPolylineCount() > 0,
				"a multi-run batch must render KML-equivalent sigma rings");
	}

	@Test
	public void testLandingMapUsesBatchCoordinatesAfterSimulationCoordinatesChange() throws Exception {
		Simulation simulation = newSimulation();
		simulation.getOptions().setLaunchLatitude(35.1758);
		simulation.getOptions().setLaunchLongitude(-76.8283);
		MonteCarloExtension extension = new MonteCarloExtension();
		extension.setUseDeterministicSeed(true);
		extension.setRandomSeed(8675309);
		simulation.getSimulationExtensions().add(extension);
		var batchOptions = simulation.getOptions().clone();
		var analysis = MonteCarloBatchRunner.runAnalysis(simulation, 2, 2, null);

		// This is the second launch site from the supplied screenshots. Results from the first
		// site must remain tied to their snapshot even if the editable simulation changes before
		// the analysis is converted into the records consumed by the map.
		simulation.getOptions().setLaunchLatitude(34.90128);
		simulation.getOptions().setLaunchLongitude(-86.57376);
		List<MonteCarloRunRecord> records =
				MonteCarloBatchRunner.toLegacyRecords(simulation, analysis, batchOptions);
		assertEquals(35.1758, records.get(0).launchLatitudeDeg, 1.0e-9);
		assertEquals(-76.8283, records.get(0).launchLongitudeDeg, 1.0e-9);
		MonteCarloVisualizationPanel panel = new MonteCarloVisualizationPanel(simulation);
		panel.setResults(records);
		MonteCarloLandingMapPanel landingMap = findFirst(panel, MonteCarloLandingMapPanel.class);
		assertNotNull(landingMap);

		OpenStreetMapPanel.GeoPoint center = landingMap.getMapPanel().getCenter();
		assertEquals(35.1758, center.latitudeDeg(), 0.01);
		assertEquals(-76.8283, center.longitudeDeg(), 0.01);
		assertTrue(Math.abs(center.longitudeDeg() - (-86.57376)) > 9.0,
				"the map must not jump to the simulation's subsequently edited launch site");
	}

	@Test
	public void testLaunchCoordinateTransitionEnteredThroughUiRecentersOpenStreetMap() throws Exception {
		Simulation simulation = newSimulation();
		SimulationConditionsPanel conditions = new SimulationConditionsPanel(simulation);
		JSpinner latitude = findAll(conditions, JSpinner.class).stream()
				.filter(spinner -> "LaunchLatitude".equals(spinner.getName()))
				.findFirst().orElseThrow();
		JSpinner longitude = findAll(conditions, JSpinner.class).stream()
				.filter(spinner -> "LaunchLongitude".equals(spinner.getName()))
				.findFirst().orElseThrow();
		OpenStreetMapPanel mapPanel = new OpenStreetMapPanel((zoom, x, y) ->
				new java.awt.image.BufferedImage(256, 256, java.awt.image.BufferedImage.TYPE_INT_ARGB));
		MonteCarloLandingMapPanel landingMap = new MonteCarloLandingMapPanel(mapPanel);

		SwingUtilities.invokeAndWait(() -> {
			enterSpinnerText(latitude, "35.1758");
			enterSpinnerText(longitude, "-76.8283");
			landingMap.setResults(List.of(), simulation.getOptions().getLaunchLatitude(),
					simulation.getOptions().getLaunchLongitude());
		});
		assertEquals(35.1758, simulation.getOptions().getLaunchLatitude(), 1.0e-9);
		assertEquals(-76.8283, simulation.getOptions().getLaunchLongitude(), 1.0e-9);
		assertEquals(35.1758, mapPanel.getCenter().latitudeDeg(), 1.0e-9);
		assertEquals(-76.8283, mapPanel.getCenter().longitudeDeg(), 1.0e-9);
		assertEquals(1, mapPanel.getMarkerCount());

		SwingUtilities.invokeAndWait(() -> {
			enterSpinnerText(latitude, "34.90128");
			enterSpinnerText(longitude, "-86.57376");
			landingMap.setResults(List.of(), simulation.getOptions().getLaunchLatitude(),
					simulation.getOptions().getLaunchLongitude());
		});
		assertEquals(34.90128, simulation.getOptions().getLaunchLatitude(), 1.0e-9);
		assertEquals(-86.57376, simulation.getOptions().getLaunchLongitude(), 1.0e-9);
		assertEquals(34.90128, mapPanel.getCenter().latitudeDeg(), 1.0e-9);
		assertEquals(-86.57376, mapPanel.getCenter().longitudeDeg(), 1.0e-9);
		assertEquals(1, mapPanel.getMarkerCount());
	}

	@Test
	public void testLandingScatterShowsEverySimulatedBodyByDefault() throws Exception {
		var rocket = TestRockets.makeMultiStageEventTestRocket();
		rocket.getSelectedConfiguration().setAllStages();
		Simulation simulation = new Simulation(rocket);
		simulation.setFlightConfigurationId(
				rocket.getSelectedConfiguration().getFlightConfigurationID());
		simulation.getOptions().setISAAtmosphere(true);
		simulation.getOptions().setTimeStep(0.05);
		simulation.getOptions().getAverageWindModel().setAverage(0.1);

		MonteCarloExtension extension = new MonteCarloExtension();
		extension.setUseDeterministicSeed(true);
		extension.setRandomSeed(12345);
		simulation.getSimulationExtensions().add(extension);
		List<MonteCarloRunRecord> records =
				MonteCarloBatchRunner.runBatchParallel(simulation, 2, 2, null);
		var bodies = LandingDispersion6DOF.collectBodyLandingPoints(records);
		assertTrue(bodies.size() >= 2, "the test rocket must produce independently landed bodies");

		MonteCarloVisualizationPanel panel = new MonteCarloVisualizationPanel(simulation);
		panel.setResults(records);
		JCheckBox perBody = findAll(panel, JCheckBox.class).stream()
				.filter(box -> "Per body".equals(box.getText())).findFirst().orElseThrow();
		assertTrue(perBody.isSelected(), "multi-body landing clouds must be visible on first open");

		var dataset = findFirst(panel, ChartPanel.class).getChart().getXYPlot().getDataset();
		List<String> seriesNames = new ArrayList<>();
		for (int index = 0; index < dataset.getSeriesCount(); index++) {
			seriesNames.add(String.valueOf(dataset.getSeriesKey(index)));
		}
		for (var body : bodies) {
			String expectedName = (body.branchName == null || body.branchName.isBlank())
					? body.bodyId : body.branchName;
			assertTrue(seriesNames.contains(expectedName),
					"landing scatter is missing body series " + expectedName + ": " + seriesNames);
		}
	}

	@Test
	public void testHistogramBarAndBoxPlotOptionsRender() throws Exception {
		Simulation simulation = newSimulation();
		MonteCarloExtension extension = new MonteCarloExtension();
		extension.setUseDeterministicSeed(true);
		extension.setRandomSeed(9191);
		simulation.getSimulationExtensions().add(extension);
		List<MonteCarloRunRecord> records = MonteCarloBatchRunner.runBatchParallel(simulation, 3, 2, null);

		MonteCarloVisualizationPanel panel = new MonteCarloVisualizationPanel(simulation);
		panel.setResults(records);
		ChartPanel chart = findFirst(panel, ChartPanel.class);
		JComboBox<?> plotSelector = findAll(panel, JComboBox.class).stream()
				.filter(combo -> comboContains(combo, "Histogram"))
				.findFirst()
				.orElseThrow();

		selectComboItem(plotSelector, "Histogram");
		assertTrue(chart.getChart().getPlot() instanceof XYPlot);
		assertTrue(chart.getChart().getTitle().getText().endsWith("distribution"));

		selectComboItem(plotSelector, "Bar chart (summary)");
		assertTrue(chart.getChart().getPlot() instanceof CategoryPlot);
		assertTrue(chart.getChart().getTitle().getText().endsWith("summary"));

		selectComboItem(plotSelector, "Box-and-whisker");
		assertTrue(chart.getChart().getPlot() instanceof CategoryPlot);
		assertTrue(chart.getChart().getTitle().getText().endsWith("box-and-whisker plot"));
	}

	@Test
	public void testLandingMapKeepsThreeSigmaWithTheOverlayControls() {
		MonteCarloLandingMapPanel map = new MonteCarloLandingMapPanel();
		JCheckBox twoSigma = findAll(map, JCheckBox.class).stream()
				.filter(box -> "2\u03c3".equals(box.getText())).findFirst().orElseThrow();
		JCheckBox threeSigma = findAll(map, JCheckBox.class).stream()
				.filter(box -> "3\u03c3".equals(box.getText())).findFirst().orElseThrow();
		JButton zoomOut = findAll(map, JButton.class).stream()
				.filter(button -> "\u2212".equals(button.getText())).findFirst().orElseThrow();
		JPanel toolbar = (JPanel) threeSigma.getParent();
		toolbar.setSize(1400, toolbar.getPreferredSize().height);
		toolbar.doLayout();

		assertTrue(threeSigma.getX() > twoSigma.getX());
		assertTrue(threeSigma.getX() - twoSigma.getX() < 100,
				"3\u03c3 must remain grouped with 1\u03c3 and 2\u03c3 before the flexible toolbar gap");
		assertTrue(threeSigma.getX() < zoomOut.getX());
	}

	@Test
	public void testLandingPlotFitsYToItsOwnCoordinateExtrema() {
		XYSeries coordinates = new XYSeries("landings");
		coordinates.add(-1000.0, 990.0);
		coordinates.add(1000.0, 1010.0);
		XYSeriesCollection dataset = new XYSeriesCollection(coordinates);
		XYSeries distantPad = new XYSeries("launch pad");
		distantPad.add(0.0, 0.0);
		dataset.addSeries(distantPad);
		JFreeChart chart = ChartFactory.createScatterPlot("", "East", "North", dataset,
				PlotOrientation.VERTICAL, false, false, false);

		MonteCarloVisualizationPanel.fitAxesToPlottedCoordinates(chart.getXYPlot(), dataset, 1);

		assertEquals(989.0, chart.getXYPlot().getRangeAxis().getLowerBound(), 1.0e-9);
		assertEquals(1011.0, chart.getXYPlot().getRangeAxis().getUpperBound(), 1.0e-9);
		assertEquals(-1100.0, chart.getXYPlot().getDomainAxis().getLowerBound(), 1.0e-9);
		assertEquals(1100.0, chart.getXYPlot().getDomainAxis().getUpperBound(), 1.0e-9);
	}

	@Test
	public void testResultsStoreKeepsResultsPerSimulation() throws Exception {
		Simulation first = newSimulation();
		Simulation second = newSimulation();
		List<MonteCarloRunRecord> records =
				MonteCarloBatchRunner.runBatchParallel(first, 2, 1, null);

		MonteCarloResultsStore.put(first, records);

		assertEquals(records.size(), MonteCarloResultsStore.get(first).size());
		assertTrue(MonteCarloResultsStore.get(second).isEmpty(),
				"results must not leak between simulations");

		MonteCarloResultsStore.clear(first);
		assertTrue(MonteCarloResultsStore.get(first).isEmpty());
	}

	private static Simulation newSimulation() {
		Simulation simulation = new Simulation(TestRockets.makeEstesAlphaIII());
		simulation.setFlightConfigurationId(TestRockets.TEST_FCID_0);
		return simulation;
	}

	private static List<String> tabTitles(JTabbedPane tabs) {
		List<String> titles = new ArrayList<>();
		for (int index = 0; index < tabs.getTabCount(); index++) {
			titles.add(tabs.getTitleAt(index));
		}
		return titles;
	}

	private static boolean comboContains(JComboBox<?> combo, String label) {
		for (int index = 0; index < combo.getItemCount(); index++) {
			if (label.equals(String.valueOf(combo.getItemAt(index)))) return true;
		}
		return false;
	}

	private static void selectComboItem(JComboBox<?> combo, String label) {
		for (int index = 0; index < combo.getItemCount(); index++) {
			if (label.equals(String.valueOf(combo.getItemAt(index)))) {
				combo.setSelectedIndex(index);
				return;
			}
		}
		throw new AssertionError("Missing combo-box item: " + label);
	}

	private static void enterSpinnerText(JSpinner spinner, String value) {
		JTextField field = ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
		field.setText(value);
		try {
			spinner.commitEdit();
		} catch (java.text.ParseException exception) {
			throw new AssertionError("Could not enter coordinate " + value, exception);
		}
	}

	private static <T extends Component> T findFirst(Component root, Class<T> type) {
		List<T> matches = findAll(root, type);
		return matches.isEmpty() ? null : matches.get(0);
	}

	private static <T extends Component> List<T> findAll(Component root, Class<T> type) {
		List<T> matches = new ArrayList<>();
		if (type.isInstance(root)) matches.add(type.cast(root));
		if (root instanceof Container container) {
			for (Component child : container.getComponents()) {
				matches.addAll(findAll(child, type));
			}
		}
		return matches;
	}

	private static void layoutTree(Container container) {
		container.doLayout();
		for (Component child : container.getComponents()) {
			if (child instanceof Container nested) layoutTree(nested);
		}
	}
}

package info.openrocket.swing.gui.simulation;

import info.openrocket.core.document.Simulation;
import info.openrocket.core.montecarlo.LandingDispersion6DOF;
import info.openrocket.core.montecarlo.LandingDispersion6DOF.BodyPoints;
import info.openrocket.core.montecarlo.LandingDispersion6DOF.Ellipse;
import info.openrocket.core.montecarlo.LandingDispersion6DOF.LandingPoint;
import info.openrocket.core.montecarlo.LandingDispersion6DOF.Summary;
import info.openrocket.core.montecarlo.MonteCarloRunRecord;
import info.openrocket.core.l10n.Translator;
import info.openrocket.core.simulation.SimulationOptions;
import info.openrocket.core.startup.Application;
import net.miginfocom.swing.MigLayout;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.BoxAndWhiskerRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.chart.renderer.xy.AbstractXYItemRenderer;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset;
import org.jfree.data.statistics.HistogramDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.geom.Ellipse2D;
import java.io.File;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.ToDoubleFunction;

/**
 * Viewer and plotter for Monte Carlo batch results.
 *
 * Reads the records published by {@link MonteCarloSetupPanel} (directly through
 * {@link #setResults(List)} while the dialog is open, or from
 * the simulation-owned persisted analysis when the window is reopened) and renders them as
 * a landing-dispersion scatter with sigma ellipses, histograms, empirical CDFs,
 * metric-vs-metric scatters and per-run series, alongside a summary statistics table.
 *
 * Plot values are in SI units (m, m/s, s, degrees), matching the exported CSV columns.
 */
public class MonteCarloVisualizationPanel extends JPanel {
	private static final Translator trans = Application.getTranslator();

	private static final DecimalFormat DECIMAL =
			new DecimalFormat("0.###", DecimalFormatSymbols.getInstance(Locale.US));

	private static final Color POINT_COLOR = new Color(0x2E, 0x86, 0xC1);
	private static final Color NOMINAL_COLOR = new Color(0xF3, 0x9C, 0x12);
	private static final Color PAD_COLOR = new Color(0x27, 0xAE, 0x60);
	private static final Color MEAN_COLOR = new Color(0xC0, 0x39, 0x2B);
	private static final Color[] BODY_COLORS = {
			new Color(0x2E, 0x86, 0xC1), new Color(0x8E, 0x44, 0xAD), new Color(0x16, 0xA0, 0x85),
			new Color(0xD3, 0x54, 0x00), new Color(0x7F, 0x8C, 0x8D), new Color(0x2C, 0x3E, 0x50)
	};

	/** Charts that can be rendered from a batch. */
	private enum PlotType {
		HISTOGRAM("Histogram"),
		BAR_CHART("Bar chart (summary)"),
		BOX_AND_WHISKER("Box-and-whisker"),
		CDF("Cumulative distribution"),
		SCATTER("Scatter (X vs Y)"),
		RUN_SERIES("Value per run"),
		LINE("Line (per run)"),
		AREA("Area (per run)"),
		RUN_BAR("Bar chart (per run)");

		private final String label;

		PlotType(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	/** Per-run scalar that can be plotted or summarized. Non-finite values are dropped. */
	private enum Metric {
		APOGEE("Apogee", "m", r -> r.apogee_m),
		MAX_VELOCITY("Max velocity", "m/s", r -> r.maxVelocity_mps),
		MAX_ACCELERATION("Max acceleration", "m/s\u00b2", r -> r.maxAcceleration_mps2),
		FLIGHT_TIME("Flight time", "s", r -> r.flightTime_s),
		LANDING_RANGE("Landing range", "m",
				r -> landed(r) ? Math.hypot(r.landingEast_m, r.landingNorth_m) : Double.NaN),
		LANDING_BEARING("Landing bearing", "\u00b0",
				r -> landed(r) ? bearingDeg(r.landingEast_m, r.landingNorth_m) : Double.NaN),
		LANDING_EAST("Landing east", "m", r -> landed(r) ? r.landingEast_m : Double.NaN),
		LANDING_NORTH("Landing north", "m", r -> landed(r) ? r.landingNorth_m : Double.NaN),
		MAX_TILT("Max tilt", "\u00b0", r -> r.maxTilt_deg),
		MAX_AOA("Max angle of attack", "\u00b0", r -> r.maxAoA_deg),
		GUST_EVENTS("Gust events", "", r -> r.gustEventCountRealized),
		MAX_DELTA_WIND("Max \u0394wind", "m/s", r -> r.gustMaxDeltaWind_mps),
		WIND_IMPULSE("\u0394wind impulse", "m/s\u00b7s", r -> r.deltaWindImpulse_mps_s),
		CD_MULTIPLIER("Cd multiplier", "\u00d7", r -> r.cdMultiplierUsed),
		THRUST_MULTIPLIER("Thrust multiplier", "\u00d7", r -> r.thrustMultiplierUsed),
		MASS_MULTIPLIER("Mass multiplier", "\u00d7", r -> r.massMultiplierUsed);

		private final String label;
		private final String unit;
		private final ToDoubleFunction<MonteCarloRunRecord> accessor;

		Metric(String label, String unit, ToDoubleFunction<MonteCarloRunRecord> accessor) {
			this.label = label;
			this.unit = unit;
			this.accessor = accessor;
		}

		double valueOf(MonteCarloRunRecord record) {
			return accessor.applyAsDouble(record);
		}

		String axisLabel() {
			return unit.isEmpty() ? label : label + " (" + unit + ")";
		}

		private static boolean landed(MonteCarloRunRecord r) {
			return r.results != null && r.results.hasLanding;
		}

		private static double bearingDeg(double east, double north) {
			double bearing = Math.toDegrees(Math.atan2(east, north));
			return (bearing < 0) ? bearing + 360.0 : bearing;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	private final Simulation simulation;

	private final JComboBox<PlotType> plotTypeCombo = new JComboBox<>(PlotType.values());
	private final JComboBox<Metric> xMetricCombo = new JComboBox<>(Metric.values());
	private final JComboBox<Metric> yMetricCombo = new JComboBox<>(Metric.values());
	private final JSpinner binSpinner = new JSpinner(new SpinnerNumberModel(20, 5, 200, 1));
	private final JCheckBox showEllipses = new JCheckBox("\u03c3 ellipses", true);
	private final JCheckBox showNominal = new JCheckBox("Nominal run", true);
	private final JCheckBox splitBodies = new JCheckBox("Per body", true);
	private final JLabel xMetricLabel = new JLabel("X");
	private final JLabel yMetricLabel = new JLabel("Y");
	private final JLabel binLabel = new JLabel("Bins");
	private final JTextArea statusLabel = SimulationTabLayoutUtils.createWrappingDisplayText("");
	private final JTextArea landingStatusLabel = SimulationTabLayoutUtils.createWrappingDisplayText("");

	private final ChartPanel landingChartPanel;
	private final ChartPanel chartPanel;
	private final DefaultTableModel statsModel;
	private final MonteCarloLandingMapPanel landingMapPanel;
	private final DefaultTableModel runDetailsModel = new DefaultTableModel(new Object[] {
			trans.get("MonteCarloResults.run"), trans.get("MonteCarloResults.seed"),
			trans.get("MonteCarloResults.status"), trans.get("MonteCarloResults.apogee"),
			trans.get("MonteCarloResults.flightTime"), trans.get("MonteCarloResults.tableQueries"),
			trans.get("MonteCarloResults.tableFallbacks"), trans.get("MonteCarloResults.tableDiagnostic") }, 0) {
		@Override public boolean isCellEditable(int row, int column) { return false; }
	};

	private List<MonteCarloRunRecord> records = List.of();

	public MonteCarloVisualizationPanel(Simulation simulation) {
		this.simulation = simulation;

		setLayout(new BorderLayout());
		JPanel landingView = new JPanel(new MigLayout("fill, ins 6, wrap 1, hidemode 3",
				"[grow, fill]", "[]6[grow, fill]6[]"));
		landingView.setMinimumSize(new Dimension(0, 0));
		landingView.add(buildLandingToolbar(), "growx");
		landingChartPanel = createChartPanel(ChartFactory.createXYLineChart(
				"Monte Carlo landing dispersion", "", "", new XYSeriesCollection()));
		landingView.add(landingChartPanel, "grow, push");
		landingStatusLabel.setFont(landingStatusLabel.getFont().deriveFont(Font.PLAIN));
		landingView.add(landingStatusLabel, "growx");

		// Keep the controls at their preferred height; a growing chart and table must not
		// squeeze the plot selector and Fit buttons out of the window.
		JPanel chartView = new JPanel(new BorderLayout(0, 6));
		chartView.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		chartView.setMinimumSize(new Dimension(0, 0));
		chartView.add(buildToolbar(), BorderLayout.NORTH);
		JPanel chartContent = new JPanel(new MigLayout("fill, ins 0, wrap 1",
				"[0::,grow,fill]", "[120::,grow,fill]6[90:110:140,fill]6[pref!,fill]"));
		chartContent.setMinimumSize(new Dimension(0, 0));
		chartView.add(chartContent, BorderLayout.CENTER);

		JFreeChart placeholder = ChartFactory.createXYLineChart(
				"Monte Carlo results", "", "", new XYSeriesCollection());
		chartPanel = createChartPanel(placeholder);
		chartContent.add(chartPanel, "grow, push, wmin 0");

		statsModel = new DefaultTableModel(new Object[] {
				"Metric", "n", "Mean", "Std dev", "Min", "P5", "Median", "P95", "Max"
		}, 0) {
			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};
		JTable statsTable = new JTable(statsModel);
		// Scroll the table itself on narrow panes instead of clipping every metric and value.
		statsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		for (int column = 0; column < statsTable.getColumnCount(); column++) {
			statsTable.getColumnModel().getColumn(column).setPreferredWidth(column == 0 ? 180 : column == 1 ? 45 : 95);
		}
		JScrollPane statsScroll = new JScrollPane(statsTable);
		statsScroll.setColumnHeaderView(statsTable.getTableHeader());
		statsScroll.setMinimumSize(new Dimension(0, 90));
		statsScroll.setBorder(BorderFactory.createTitledBorder("Summary statistics"));
		chartContent.add(statsScroll, "growx, wmin 0");

		statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN));
		chartContent.add(statusLabel, "growx, wmin 0");

		landingMapPanel = new MonteCarloLandingMapPanel();
		JTable runDetails = new JTable(runDetailsModel);
		runDetails.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		JScrollPane runDetailsScroll = new JScrollPane(runDetails);
		JTabbedPane resultViews = new JTabbedPane();
		resultViews.addTab("Landing dispersion", landingView);
		resultViews.addTab("Landing map", landingMapPanel);
		resultViews.addTab("Run details", runDetailsScroll);
		resultViews.addTab("Statistical plots", chartView);
		resultViews.setMinimumSize(new Dimension(0, 0));
		add(resultViews, BorderLayout.CENTER);

		xMetricCombo.setSelectedItem(Metric.APOGEE);
		yMetricCombo.setSelectedItem(Metric.LANDING_RANGE);

		setResults(simulation.getMonteCarloAnalysis() == null
				? List.of() : simulation.getMonteCarloAnalysis().getRecords());
	}

	private static ChartPanel createChartPanel(JFreeChart chart) {
		ChartPanel panel = new ChartPanel(chart);
		panel.setMinimumSize(new Dimension(0, 120));
		panel.setMinimumDrawWidth(0);
		panel.setMinimumDrawHeight(0);
		panel.setMaximumDrawWidth(Integer.MAX_VALUE);
		panel.setMaximumDrawHeight(Integer.MAX_VALUE);
		panel.setMouseWheelEnabled(true);
		panel.setBorder(BorderFactory.createEtchedBorder());
		return panel;
	}

	private JPanel buildLandingToolbar() {
		JPanel bar = new JPanel(new MigLayout("ins 0, fillx", "[][][][grow][][]"));
		bar.setMinimumSize(new Dimension(0, 0));

		showEllipses.setToolTipText("Draw the 1\u03c3, 2\u03c3 and 3\u03c3 dispersion ellipses.");
		showEllipses.addActionListener(e -> refreshLandingChart());
		bar.add(showEllipses);

		showNominal.setToolTipText("Overlay the unperturbed nominal run.");
		showNominal.addActionListener(e -> {
			refreshLandingChart();
			refreshChart();
		});
		bar.add(showNominal);

		splitBodies.setToolTipText("Plot each independently simulated body (sustainer, booster, ...) as its own series.");
		splitBodies.addActionListener(e -> refreshLandingChart());
		bar.add(splitBodies);

		JButton fit = new JButton("Fit");
		fit.setToolTipText("Reset the view to the landing cloud and visible dispersion ellipses.");
		fit.addActionListener(e -> refreshLandingChart());
		bar.add(fit, "cell 4 0");

		JButton saveImage = new JButton("Save image...");
		saveImage.setToolTipText("Save the landing-dispersion chart as a PNG file.");
		saveImage.addActionListener(e -> saveChartImage(landingChartPanel));
		bar.add(saveImage, "cell 5 0");
		return bar;
	}

	private JPanel buildToolbar() {
		JPanel bar = new JPanel(new MigLayout("ins 0, fillx, wrap 2, gap 6 4, hidemode 3, novisualpadding",
				"[][0::,grow,fill]"));
		bar.setMinimumSize(new Dimension(0, 0));

		bar.add(new JLabel("Plot type"));
		plotTypeCombo.setMaximumRowCount(PlotType.values().length);
		plotTypeCombo.setToolTipText("Choose how to visualize the batch results.");
		plotTypeCombo.addActionListener(e -> {
			updateControlVisibility();
			refreshChart();
		});
		bar.add(plotTypeCombo);

		bar.add(xMetricLabel);
		xMetricCombo.addActionListener(e -> refreshChart());
		bar.add(xMetricCombo);

		bar.add(yMetricLabel);
		yMetricCombo.addActionListener(e -> refreshChart());
		bar.add(yMetricCombo);

		bar.add(binLabel);
		binSpinner.addChangeListener(e -> refreshChart());
		bar.add(binSpinner, "growx, wmin 0");

		JButton reload = new JButton("Reload");
		reload.setToolTipText("Reload the most recent batch results for this simulation.");
		reload.addActionListener(e -> setResults(simulation.getMonteCarloAnalysis() == null
				? List.of() : simulation.getMonteCarloAnalysis().getRecords()));
		JPanel actions = new JPanel(new MigLayout("ins 0, fillx, gap 6 4, novisualpadding", "[][grow][][]"));
		JButton fit = new JButton("Fit");
		fit.setToolTipText("Reset both axes to show the complete statistical plot.");
		fit.addActionListener(e -> chartPanel.restoreAutoBounds());
		actions.add(fit);
		actions.add(reload, "cell 2 0");

		JButton saveImage = new JButton("Save image...");
		saveImage.setToolTipText("Save the current statistical chart as a PNG file.");
		saveImage.addActionListener(e -> saveChartImage(chartPanel));
		actions.add(saveImage, "cell 3 0");
		bar.add(actions, "span 2, growx");

		return bar;
	}

	/** Replaces the plotted data set; safe to call with {@code null} or an empty list. */
	public void setResults(List<MonteCarloRunRecord> results) {
		this.records = (results == null) ? List.of() : List.copyOf(results);
		updateControlVisibility();
		refreshLandingChart();
		refreshChart();
		refreshStatistics();
		refreshRunDetails();
		LaunchSite launchSite = resultLaunchSite();
		landingMapPanel.setResults(this.records, launchSite.latitudeDeg(), launchSite.longitudeDeg());
	}

	private void refreshRunDetails() {
		runDetailsModel.setRowCount(0);
		for (MonteCarloRunRecord record : records) {
			var report = record.physicsAeroRuntimeReport;
			runDetailsModel.addRow(new Object[] {
					record.nominal ? trans.get("MonteCarloResults.nominal") : record.runIndex,
					record.seedUsed,
					record.failureMessage == null ? trans.get("MonteCarloResults.success") : record.failureMessage,
					format(record.apogee_m), format(record.flightTime_s),
					report == null ? 0 : report.totalQueries(),
					report == null ? 0 : report.fallbackCount(),
					firstTableDiagnostic(report)
			});
		}
	}

	private static String firstTableDiagnostic(
			info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroRuntimeReport report) {
		if (report == null || report.firstOccurrences().isEmpty()) return "";
		var entry = report.firstOccurrences().entrySet().iterator().next();
		var occurrence = entry.getValue();
		var coordinate = occurrence.coordinates();
		return entry.getKey().name() + " @ Mach " + format(coordinate.mach()) +
				", Re " + format(coordinate.reynoldsNumber()) + ": " + occurrence.detail();
	}

	private List<MonteCarloRunRecord> dispersedRuns() {
		List<MonteCarloRunRecord> out = new ArrayList<>();
		for (MonteCarloRunRecord r : records) {
			if (!r.nominal && r.failureMessage == null) {
				out.add(r);
			}
		}
		return out;
	}

	private MonteCarloRunRecord nominalRun() {
		for (MonteCarloRunRecord r : records) {
			if (r.nominal) {
				return r;
			}
		}
		return null;
	}

	private void updateControlVisibility() {
		PlotType type = selectedPlotType();
		boolean needsY = type == PlotType.SCATTER;
		boolean needsBins = type == PlotType.HISTOGRAM;

		xMetricLabel.setVisible(true);
		xMetricCombo.setVisible(true);
		xMetricLabel.setText(needsY ? "X" : "Metric");
		yMetricLabel.setVisible(needsY);
		yMetricCombo.setVisible(needsY);
		binLabel.setVisible(needsBins);
		binSpinner.setVisible(needsBins);
		revalidate();
	}

	private PlotType selectedPlotType() {
		PlotType type = (PlotType) plotTypeCombo.getSelectedItem();
		return (type == null) ? PlotType.HISTOGRAM : type;
	}

	private Metric selectedX() {
		Metric metric = (Metric) xMetricCombo.getSelectedItem();
		return (metric == null) ? Metric.APOGEE : metric;
	}

	private Metric selectedY() {
		Metric metric = (Metric) yMetricCombo.getSelectedItem();
		return (metric == null) ? Metric.LANDING_RANGE : metric;
	}

	// =====================================================================
	// Chart construction
	// =====================================================================
	private void refreshLandingChart() {
		List<MonteCarloRunRecord> runs = dispersedRuns();
		if (runs.isEmpty()) {
			landingChartPanel.setChart(ChartFactory.createXYLineChart(
					"No Monte Carlo results — run a batch from the Setup tab",
					"", "", new XYSeriesCollection()));
			SimulationTabLayoutUtils.setWrappingDisplayText(landingStatusLabel, "No results loaded. Run a batch, then return here.");
			return;
		}
		landingChartPanel.setChart(buildDispersionChart(runs));
		configurePlotInteraction(landingChartPanel.getChart());
	}

	private void refreshChart() {
		List<MonteCarloRunRecord> runs = dispersedRuns();
		if (runs.isEmpty()) {
			chartPanel.setChart(ChartFactory.createXYLineChart(
					"No Monte Carlo results \u2014 run a batch from the Setup tab",
					"", "", new XYSeriesCollection()));
			SimulationTabLayoutUtils.setWrappingDisplayText(statusLabel, "No results loaded. Run a batch in the Setup tab, then return here.");
			return;
		}

		JFreeChart chart = switch (selectedPlotType()) {
			case HISTOGRAM -> buildHistogramChart(runs, selectedX());
			case BAR_CHART -> buildBarChart(runs, selectedX());
			case BOX_AND_WHISKER -> buildBoxAndWhiskerChart(runs, selectedX());
			case CDF -> buildCdfChart(runs, selectedX());
			case SCATTER -> buildScatterChart(runs, selectedX(), selectedY());
			case RUN_SERIES -> buildRunSeriesChart(runs, selectedX());
			case LINE, AREA, RUN_BAR -> buildRunComparisonChart(runs, selectedX(), selectedPlotType());
		};
		configurePlotInteraction(chart);
		chartPanel.setChart(chart);
	}

	private static void configurePlotInteraction(JFreeChart chart) {
		if (chart.getPlot() instanceof XYPlot plot) {
			plot.setDomainPannable(true);
			plot.setRangePannable(true);
			if (plot.getRenderer() instanceof AbstractXYItemRenderer renderer) {
				// Auto-ranging must include the full dataset even after X is panned away from the data.
				renderer.setDataBoundsIncludesVisibleSeriesOnly(false);
			}
		} else if (chart.getPlot() instanceof CategoryPlot plot) {
			plot.setRangePannable(true);
		}
	}

	private JFreeChart buildDispersionChart(List<MonteCarloRunRecord> runs) {
		LaunchSite launchSite = resultLaunchSite();
		double launchLat = launchSite.latitudeDeg();
		double launchLon = launchSite.longitudeDeg();

		XYSeriesCollection dataset = new XYSeriesCollection();
		List<Color> seriesColors = new ArrayList<>();
		List<Boolean> seriesAsLine = new ArrayList<>();

		List<LandingPoint> primary = LandingDispersion6DOF.collectLandingPoints(records, launchLat, launchLon);
		if (splitBodies.isSelected()) {
			List<BodyPoints> bodies = LandingDispersion6DOF.collectBodyLandingPoints(records);
			int colorIndex = 0;
			for (BodyPoints body : bodies) {
				String name = (body.branchName == null || body.branchName.isBlank())
						? body.bodyId : body.branchName;
				XYSeries series = new XYSeries(name, false, true);
				for (LandingPoint point : body.points) {
					series.add(point.east_m, point.north_m);
				}
				dataset.addSeries(series);
				seriesColors.add(BODY_COLORS[colorIndex++ % BODY_COLORS.length]);
				seriesAsLine.add(Boolean.FALSE);
			}
			if (bodies.isEmpty()) {
				addPointSeries(dataset, seriesColors, seriesAsLine, "Landing points", primary, POINT_COLOR);
			}
		} else {
			addPointSeries(dataset, seriesColors, seriesAsLine, "Landing points", primary, POINT_COLOR);
		}

		Summary summary = LandingDispersion6DOF.summarize(primary, launchLat, launchLon);

		if (showEllipses.isSelected() && summary.n >= 2) {
			addEllipseSeries(dataset, seriesColors, seriesAsLine, "1\u03c3", summary, summary.oneSigma,
					new Color(0xC0, 0x39, 0x2B));
			addEllipseSeries(dataset, seriesColors, seriesAsLine, "2\u03c3", summary, summary.twoSigma,
					new Color(0xE6, 0x7E, 0x22));
			addEllipseSeries(dataset, seriesColors, seriesAsLine, "3\u03c3", summary, summary.threeSigma,
					new Color(0xF1, 0xC4, 0x0F));
		}
		// Only the landing cloud and its visible sigma rings define the initial viewport.
		// The launch pad and optional nominal marker may be far away and otherwise collapse the
		// distribution into a thin line.
		int fittedSeriesCount = dataset.getSeriesCount();

		XYSeries pad = new XYSeries("Launch pad", false, true);
		pad.add(0.0, 0.0);
		dataset.addSeries(pad);
		seriesColors.add(PAD_COLOR);
		seriesAsLine.add(Boolean.FALSE);

		if (summary.n >= 1) {
			XYSeries mean = new XYSeries("Mean impact", false, true);
			mean.add(summary.meanEast_m, summary.meanNorth_m);
			dataset.addSeries(mean);
			seriesColors.add(MEAN_COLOR);
			seriesAsLine.add(Boolean.FALSE);
		}

		MonteCarloRunRecord nominal = nominalRun();
		if (showNominal.isSelected() && nominal != null && nominal.results != null && nominal.results.hasLanding) {
			XYSeries nominalSeries = new XYSeries("Nominal", false, true);
			nominalSeries.add(nominal.landingEast_m, nominal.landingNorth_m);
			dataset.addSeries(nominalSeries);
			seriesColors.add(NOMINAL_COLOR);
			seriesAsLine.add(Boolean.FALSE);
		}

		JFreeChart chart = ChartFactory.createScatterPlot(
				"Landing dispersion (" + summary.n + " landings)",
				"East (m)", "North (m)", dataset, PlotOrientation.VERTICAL, true, true, false);

		XYPlot plot = chart.getXYPlot();
		styleSeries(plot, seriesColors, seriesAsLine);
		fitAxesToPlottedCoordinates(plot, dataset, fittedSeriesCount);

		SimulationTabLayoutUtils.setWrappingDisplayText(landingStatusLabel, String.format(Locale.US,
				"Landings: %d | mean impact E=%s m, N=%s m | R50=%s m, R90=%s m, R95=%s m | " +
						"1\u03c3 ellipse %s \u00d7 %s m @ %s\u00b0",
				summary.n,
				format(summary.meanEast_m), format(summary.meanNorth_m),
				format(summary.containment50_m), format(summary.containment90_m),
				format(summary.containment95_m),
				format(summary.oneSigma == null ? Double.NaN : summary.oneSigma.a_m),
				format(summary.oneSigma == null ? Double.NaN : summary.oneSigma.b_m),
				format(summary.oneSigma == null ? Double.NaN : summary.oneSigma.bearing_deg)));
		return chart;
	}

	private JFreeChart buildBarChart(List<MonteCarloRunRecord> runs, Metric metric) {
		double[] values = valuesOf(runs, metric);
		Arrays.sort(values);
		DefaultCategoryDataset dataset = new DefaultCategoryDataset();
		if (values.length > 0) {
			dataset.addValue(values[0], metric.label, "Minimum");
			dataset.addValue(percentile(values, 0.05), metric.label, "P5");
			dataset.addValue(percentile(values, 0.50), metric.label, "Median");
			dataset.addValue(mean(values), metric.label, "Mean");
			dataset.addValue(percentile(values, 0.95), metric.label, "P95");
			dataset.addValue(values[values.length - 1], metric.label, "Maximum");
		}

		JFreeChart chart = ChartFactory.createBarChart(
				metric.label + " summary", "Statistic", metric.axisLabel(), dataset,
				PlotOrientation.VERTICAL, false, true, false);
		CategoryPlot plot = chart.getCategoryPlot();
		if (plot.getRenderer() instanceof BarRenderer renderer) {
			renderer.setBarPainter(new StandardBarPainter());
			renderer.setShadowVisible(false);
			renderer.setSeriesPaint(0, POINT_COLOR);
		}
		SimulationTabLayoutUtils.setWrappingDisplayText(statusLabel, describeSample(metric, values));
		return chart;
	}

	private JFreeChart buildBoxAndWhiskerChart(List<MonteCarloRunRecord> runs, Metric metric) {
		double[] values = valuesOf(runs, metric);
		DefaultBoxAndWhiskerCategoryDataset dataset = new DefaultBoxAndWhiskerCategoryDataset();
		if (values.length > 0) {
			List<Double> boxedValues = Arrays.stream(values).boxed().toList();
			dataset.add(boxedValues, metric.label, "Dispersed runs");
		}

		JFreeChart chart = ChartFactory.createBoxAndWhiskerChart(
				metric.label + " box-and-whisker plot", "Batch", metric.axisLabel(), dataset, false);
		CategoryPlot plot = chart.getCategoryPlot();
		if (plot.getRenderer() instanceof BoxAndWhiskerRenderer renderer) {
			renderer.setSeriesPaint(0, POINT_COLOR);
			renderer.setFillBox(true);
			// A single category otherwise fills the plot and scales its mean marker into a giant disk.
			renderer.setMaximumBarWidth(0.08);
			renderer.setMeanVisible(false);
			renderer.setMedianVisible(true);
		}
		plot.setBackgroundPaint(Color.WHITE);
		plot.setRangeGridlinePaint(new Color(0xDD, 0xDD, 0xDD));
		SimulationTabLayoutUtils.setWrappingDisplayText(statusLabel, describeSample(metric, values));
		return chart;
	}

	private JFreeChart buildHistogramChart(List<MonteCarloRunRecord> runs, Metric metric) {
		double[] values = valuesOf(runs, metric);
		HistogramDataset dataset = new HistogramDataset();
		if (values.length > 0) {
			double minimum = Arrays.stream(values).min().orElseThrow();
			double maximum = Arrays.stream(values).max().orElseThrow();
			if (minimum == maximum) {
				double padding = Math.max(1.0e-6, Math.abs(minimum) * 0.05);
				minimum -= padding;
				maximum += padding;
			}
			dataset.addSeries(metric.label, values, ((Number) binSpinner.getValue()).intValue(),
					minimum, maximum);
		}

		JFreeChart chart = ChartFactory.createHistogram(
				metric.label + " distribution", metric.axisLabel(), "Runs",
				dataset, PlotOrientation.VERTICAL, true, true, false);

		XYPlot plot = chart.getXYPlot();
		XYItemRenderer renderer = plot.getRenderer();
		if (renderer instanceof XYBarRenderer barRenderer) {
			barRenderer.setBarPainter(new org.jfree.chart.renderer.xy.StandardXYBarPainter());
			barRenderer.setShadowVisible(false);
		}
		renderer.setSeriesPaint(0, POINT_COLOR);

		SimulationTabLayoutUtils.setWrappingDisplayText(statusLabel, describeSample(metric, values));
		return chart;
	}

	private JFreeChart buildCdfChart(List<MonteCarloRunRecord> runs, Metric metric) {
		double[] values = valuesOf(runs, metric);
		Arrays.sort(values);

		XYSeries series = new XYSeries(metric.label, false, true);
		for (int i = 0; i < values.length; i++) {
			series.add(values[i], (i + 1) * 100.0 / values.length);
		}
		XYSeriesCollection dataset = new XYSeriesCollection(series);

		JFreeChart chart = ChartFactory.createXYLineChart(
				metric.label + " cumulative distribution", metric.axisLabel(), "Percentile (%)",
				dataset, PlotOrientation.VERTICAL, true, true, false);
		chart.getXYPlot().getRenderer().setSeriesPaint(0, POINT_COLOR);

		SimulationTabLayoutUtils.setWrappingDisplayText(statusLabel, describeSample(metric, values));
		return chart;
	}

	private JFreeChart buildScatterChart(List<MonteCarloRunRecord> runs, Metric xMetric, Metric yMetric) {
		XYSeries series = new XYSeries("Runs", false, true);
		for (MonteCarloRunRecord record : runs) {
			double x = xMetric.valueOf(record);
			double y = yMetric.valueOf(record);
			if (Double.isFinite(x) && Double.isFinite(y)) {
				series.add(x, y);
			}
		}
		XYSeriesCollection dataset = new XYSeriesCollection(series);
		List<Color> colors = new ArrayList<>(List.of(POINT_COLOR));
		List<Boolean> asLine = new ArrayList<>(List.of(Boolean.FALSE));

		MonteCarloRunRecord nominal = nominalRun();
		if (showNominal.isSelected() && nominal != null) {
			double x = xMetric.valueOf(nominal);
			double y = yMetric.valueOf(nominal);
			if (Double.isFinite(x) && Double.isFinite(y)) {
				XYSeries nominalSeries = new XYSeries("Nominal", false, true);
				nominalSeries.add(x, y);
				dataset.addSeries(nominalSeries);
				colors.add(NOMINAL_COLOR);
				asLine.add(Boolean.FALSE);
			}
		}

		JFreeChart chart = ChartFactory.createScatterPlot(
				yMetric.label + " vs " + xMetric.label,
				xMetric.axisLabel(), yMetric.axisLabel(),
				dataset, PlotOrientation.VERTICAL, true, true, false);
		styleSeries(chart.getXYPlot(), colors, asLine);

		SimulationTabLayoutUtils.setWrappingDisplayText(statusLabel, String.format(Locale.US, "%d runs plotted | correlation r = %s",
				series.getItemCount(), format(correlation(runs, xMetric, yMetric))));
		return chart;
	}

	private JFreeChart buildRunSeriesChart(List<MonteCarloRunRecord> runs, Metric metric) {
		XYSeries series = new XYSeries(metric.label, false, true);
		for (MonteCarloRunRecord record : runs) {
			double value = metric.valueOf(record);
			if (Double.isFinite(value)) {
				series.add(record.runIndex, value);
			}
		}
		XYSeriesCollection dataset = new XYSeriesCollection(series);
		List<Color> colors = new ArrayList<>(List.of(POINT_COLOR));
		List<Boolean> asLine = new ArrayList<>(List.of(Boolean.FALSE));

		double[] values = valuesOf(runs, metric);
		if (values.length > 0) {
			double mean = mean(values);
			XYSeries meanSeries = new XYSeries("Mean", false, true);
			meanSeries.add(series.getMinX(), mean);
			meanSeries.add(series.getMaxX(), mean);
			dataset.addSeries(meanSeries);
			colors.add(MEAN_COLOR);
			asLine.add(Boolean.TRUE);
		}

		MonteCarloRunRecord nominal = nominalRun();
		if (!series.isEmpty() && showNominal.isSelected() && nominal != null
				&& Double.isFinite(metric.valueOf(nominal))) {
			XYSeries nominalSeries = new XYSeries("Nominal", false, true);
			nominalSeries.add(series.getMinX(), metric.valueOf(nominal));
			nominalSeries.add(series.getMaxX(), metric.valueOf(nominal));
			dataset.addSeries(nominalSeries);
			colors.add(NOMINAL_COLOR);
			asLine.add(Boolean.TRUE);
		}

		JFreeChart chart = ChartFactory.createScatterPlot(
				metric.label + " per run", "Run index", metric.axisLabel(),
				dataset, PlotOrientation.VERTICAL, true, true, false);
		styleSeries(chart.getXYPlot(), colors, asLine);

		SimulationTabLayoutUtils.setWrappingDisplayText(statusLabel, describeSample(metric, values));
		return chart;
	}

	private JFreeChart buildRunComparisonChart(List<MonteCarloRunRecord> runs, Metric metric, PlotType type) {
		// Parallel batches need not arrive in run order; connected plots must use the run index.
		XYSeries series = new XYSeries(metric.label, true, true);
		for (MonteCarloRunRecord record : runs) {
			double value = metric.valueOf(record);
			if (Double.isFinite(value)) {
				series.add(record.runIndex, value);
			}
		}
		XYSeriesCollection dataset = new XYSeriesCollection(series);
		String title = metric.label + " per run";
		JFreeChart chart;
		if (type == PlotType.RUN_BAR) {
			DefaultCategoryDataset bars = new DefaultCategoryDataset();
			for (int i = 0; i < series.getItemCount(); i++) {
				bars.addValue(series.getY(i), metric.label, series.getX(i).intValue());
			}
			chart = ChartFactory.createBarChart(title, "Run index", metric.axisLabel(), bars,
					PlotOrientation.VERTICAL, false, true, false);
			BarRenderer renderer = (BarRenderer) chart.getCategoryPlot().getRenderer();
			renderer.setBarPainter(new StandardBarPainter());
			renderer.setShadowVisible(false);
			renderer.setSeriesPaint(0, POINT_COLOR);
		} else if (type == PlotType.AREA) {
			chart = ChartFactory.createXYAreaChart(title, "Run index", metric.axisLabel(), dataset,
					PlotOrientation.VERTICAL, false, true, false);
			chart.getXYPlot().getRenderer().setSeriesPaint(0, POINT_COLOR);
		} else {
			chart = ChartFactory.createXYLineChart(title, "Run index", metric.axisLabel(), dataset,
					PlotOrientation.VERTICAL, false, true, false);
			XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) chart.getXYPlot().getRenderer();
			renderer.setDefaultShapesVisible(true);
			renderer.setSeriesPaint(0, POINT_COLOR);
		}
		SimulationTabLayoutUtils.setWrappingDisplayText(statusLabel, describeSample(metric, valuesOf(runs, metric)));
		return chart;
	}

	private static void addPointSeries(XYSeriesCollection dataset, List<Color> colors, List<Boolean> asLine,
									   String name, List<LandingPoint> points, Color color) {
		XYSeries series = new XYSeries(name, false, true);
		for (LandingPoint point : points) {
			series.add(point.east_m, point.north_m);
		}
		dataset.addSeries(series);
		colors.add(color);
		asLine.add(Boolean.FALSE);
	}

	/** Adds a sigma-ellipse ring, using the same ENU parametrization as the KML/PNG exports. */
	private static void addEllipseSeries(XYSeriesCollection dataset, List<Color> colors, List<Boolean> asLine,
										 String name, Summary summary, Ellipse ellipse, Color color) {
		if (ellipse == null || !(ellipse.a_m > 0)) {
			return;
		}
		XYSeries series = new XYSeries(name, false, true);
		for (LandingPoint point : LandingDispersion6DOF.buildEllipseRing(summary, ellipse)) {
			series.add(point.east_m, point.north_m);
		}
		dataset.addSeries(series);
		colors.add(color);
		asLine.add(Boolean.TRUE);
	}

	private static void styleSeries(XYPlot plot, List<Color> colors, List<Boolean> asLine) {
		XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(false, true);
		for (int i = 0; i < colors.size(); i++) {
			boolean line = asLine.get(i);
			renderer.setSeriesPaint(i, colors.get(i));
			renderer.setSeriesLinesVisible(i, line);
			renderer.setSeriesShapesVisible(i, !line);
			if (line) {
				renderer.setSeriesStroke(i, new BasicStroke(1.8f));
			} else {
				renderer.setSeriesShape(i, new Ellipse2D.Double(-2.5, -2.5, 5.0, 5.0));
			}
		}
		plot.setRenderer(renderer);
	}

	/** Fits each landing-coordinate axis to its own plotted extrema. */
	static void fitAxesToPlottedCoordinates(XYPlot plot, XYSeriesCollection dataset) {
		fitAxesToPlottedCoordinates(plot, dataset, dataset.getSeriesCount());
	}

	static void fitAxesToPlottedCoordinates(XYPlot plot, XYSeriesCollection dataset,
			int fittedSeriesCount) {
		double minX = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;

		int seriesLimit = Math.max(0, Math.min(fittedSeriesCount, dataset.getSeriesCount()));
		for (int s = 0; s < seriesLimit; s++) {
			XYSeries series = dataset.getSeries(s);
			for (int i = 0; i < series.getItemCount(); i++) {
				double x = series.getX(i).doubleValue();
				double y = series.getY(i).doubleValue();
				minX = Math.min(minX, x);
				maxX = Math.max(maxX, x);
				minY = Math.min(minY, y);
				maxY = Math.max(maxY, y);
			}
		}
		if (!Double.isFinite(minX) || !Double.isFinite(minY)) {
			return;
		}

		NumberAxis domain = (NumberAxis) plot.getDomainAxis();
		NumberAxis range = (NumberAxis) plot.getRangeAxis();
		domain.setAutoRange(false);
		range.setAutoRange(false);
		setCoordinateRange(domain, minX, maxX);
		setCoordinateRange(range, minY, maxY);
	}

	private static void setCoordinateRange(NumberAxis axis, double minimum, double maximum) {
		double span = maximum - minimum;
		double padding = span > 1.0e-9
				? span * 0.05 : Math.max(1.0, Math.abs(minimum) * 0.05);
		axis.setRange(minimum - padding, maximum + padding);
	}

	// =====================================================================
	// Statistics
	// =====================================================================

	private void refreshStatistics() {
		statsModel.setRowCount(0);
		List<MonteCarloRunRecord> runs = dispersedRuns();
		if (runs.isEmpty()) {
			return;
		}

		for (Metric metric : Metric.values()) {
			double[] values = valuesOf(runs, metric);
			if (values.length == 0) {
				continue;
			}
			Arrays.sort(values);
			statsModel.addRow(new Object[] {
					metric.axisLabel(),
					values.length,
					format(mean(values)),
					format(standardDeviation(values)),
					format(values[0]),
					format(percentile(values, 0.05)),
					format(percentile(values, 0.50)),
					format(percentile(values, 0.95)),
					format(values[values.length - 1])
			});
		}
	}

	private static double[] valuesOf(List<MonteCarloRunRecord> runs, Metric metric) {
		return runs.stream()
				.mapToDouble(metric::valueOf)
				.filter(Double::isFinite)
				.toArray();
	}

	private static double mean(double[] values) {
		double sum = 0.0;
		for (double v : values) {
			sum += v;
		}
		return values.length == 0 ? Double.NaN : sum / values.length;
	}

	private static double standardDeviation(double[] values) {
		if (values.length < 2) {
			return Double.NaN;
		}
		double mean = mean(values);
		double sum = 0.0;
		for (double v : values) {
			double d = v - mean;
			sum += d * d;
		}
		return Math.sqrt(sum / (values.length - 1));
	}

	/** Nearest-rank percentile over an ascending array. */
	private static double percentile(double[] sorted, double probability) {
		if (sorted.length == 0) {
			return Double.NaN;
		}
		int index = Math.max(0, (int) Math.ceil(probability * sorted.length) - 1);
		return sorted[Math.min(index, sorted.length - 1)];
	}

	private static double correlation(List<MonteCarloRunRecord> runs, Metric xMetric, Metric yMetric) {
		List<double[]> pairs = new ArrayList<>();
		for (MonteCarloRunRecord record : runs) {
			double x = xMetric.valueOf(record);
			double y = yMetric.valueOf(record);
			if (Double.isFinite(x) && Double.isFinite(y)) {
				pairs.add(new double[] { x, y });
			}
		}
		if (pairs.size() < 2) {
			return Double.NaN;
		}

		double meanX = pairs.stream().mapToDouble(p -> p[0]).average().orElse(Double.NaN);
		double meanY = pairs.stream().mapToDouble(p -> p[1]).average().orElse(Double.NaN);
		double sxy = 0.0;
		double sxx = 0.0;
		double syy = 0.0;
		for (double[] pair : pairs) {
			double dx = pair[0] - meanX;
			double dy = pair[1] - meanY;
			sxy += dx * dy;
			sxx += dx * dx;
			syy += dy * dy;
		}
		double denominator = Math.sqrt(sxx * syy);
		return (denominator == 0.0) ? Double.NaN : sxy / denominator;
	}

	private static String describeSample(Metric metric, double[] values) {
		if (values.length == 0) {
			return "No finite " + metric.label.toLowerCase(Locale.US) + " values in this batch.";
		}
		double[] sorted = values.clone();
		Arrays.sort(sorted);
		return String.format(Locale.US,
				"n=%d | mean=%s | \u03c3=%s | min=%s | P5=%s | median=%s | P95=%s | max=%s %s",
				sorted.length, format(mean(sorted)), format(standardDeviation(sorted)),
				format(sorted[0]), format(percentile(sorted, 0.05)), format(percentile(sorted, 0.50)),
				format(percentile(sorted, 0.95)), format(sorted[sorted.length - 1]), metric.unit).trim();
	}

	// =====================================================================
	// Helpers
	// =====================================================================

	private void saveChartImage(ChartPanel source) {
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Save Monte Carlo chart");
		chooser.setFileFilter(new FileNameExtensionFilter("PNG image", "png"));
		chooser.setSelectedFile(new File("montecarlo_chart.png"));
		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		File file = chooser.getSelectedFile();
		if (!file.getName().toLowerCase(Locale.US).endsWith(".png")) {
			file = new File(file.getParentFile(), file.getName() + ".png");
		}
		try {
			ChartUtils.saveChartAsPNG(file, source.getChart(),
					Math.max(640, source.getWidth()), Math.max(480, source.getHeight()));
			SimulationTabLayoutUtils.setWrappingDisplayText(statusLabel, "Chart saved to " + file.getAbsolutePath());
		} catch (Exception ex) {
			JOptionPane.showMessageDialog(this, "Could not save the chart:\n" + ex.getMessage(),
					"Save Failed", JOptionPane.ERROR_MESSAGE);
		}
	}

	private double launchLatitudeDeg() {
		SimulationOptions options = (simulation == null) ? null : simulation.getOptions();
		return (options == null) ? 0.0 : options.getLaunchLatitude();
	}

	private double launchLongitudeDeg() {
		SimulationOptions options = (simulation == null) ? null : simulation.getOptions();
		return (options == null) ? 0.0 : options.getLaunchLongitude();
	}

	private LaunchSite resultLaunchSite() {
		for (MonteCarloRunRecord record : records) {
			if (record != null && Double.isFinite(record.launchLatitudeDeg)
					&& Double.isFinite(record.launchLongitudeDeg)) {
				return new LaunchSite(record.launchLatitudeDeg, record.launchLongitudeDeg);
			}
		}
		return new LaunchSite(launchLatitudeDeg(), launchLongitudeDeg());
	}

	private record LaunchSite(double latitudeDeg, double longitudeDeg) { }

	private static String format(double value) {
		return Double.isFinite(value) ? DECIMAL.format(value) : "n/a";
	}
}

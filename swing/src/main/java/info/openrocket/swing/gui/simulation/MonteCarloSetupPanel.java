package info.openrocket.swing.gui.simulation;

import info.openrocket.core.document.Simulation;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroTableResolver;
import info.openrocket.core.aerodynamics.physicsaero.table.AerodynamicTable;
import info.openrocket.core.montecarlo.LandingDispersion6DOF;
import info.openrocket.core.montecarlo.MonteCarloAnalysis;
import info.openrocket.core.montecarlo.MonteCarloBatchRunner;
import info.openrocket.core.montecarlo.MonteCarloCsvExporter;
import info.openrocket.core.montecarlo.MonteCarloExtension;
import info.openrocket.core.montecarlo.MonteCarloRunRecord;
import info.openrocket.core.simulation.SimulationOptions;
import info.openrocket.core.simulation.montecarlo.MonteCarloResult;
import info.openrocket.core.simulation.montecarlo.MonteCarloSettings;
import info.openrocket.core.unit.UnitGroup;
import info.openrocket.swing.gui.SpinnerEditor;
import info.openrocket.swing.gui.adaptors.DoubleModel;
import info.openrocket.swing.gui.components.UnitSelector;
import info.openrocket.swing.simulation.extension.impl.MonteCarloPdfExporter;
import net.miginfocom.swing.MigLayout;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * Monte Carlo setup UI: every perturbation setting plus the batch run / export strip.
 *
 * Hosted by the dedicated Monte Carlo analysis window (the legacy panel can still
 * embed it for compatibility tests).
 *
 * Layout:
 *   1) General settings strip (always visible at top)
 *   2) Tabbed pane: Launch | Atmosphere | Disturbances | Vehicle
 *   3) Batch run / export strip (always visible at bottom)
 */
public class MonteCarloSetupPanel extends SimulationScrollablePanel {

	// Unicode constants for clean display
	private static final String SIGMA      = "\u03c3";
	private static final String DELTA      = "\u0394";
	private static final String DEG_C      = "\u00b0C";
	private static final String PLUS_MINUS = "\u00b1";
	private static final DecimalFormat DECIMAL =
			new DecimalFormat("0.###", DecimalFormatSymbols.getInstance(Locale.US));

	private final MonteCarloExtension extension;
	private final Simulation simulation;

	// Keep last results in-memory so user can export after completion
	private final AtomicReference<List<MonteCarloRunRecord>> lastBatchResults = new AtomicReference<>();
	private final List<Consumer<List<MonteCarloRunRecord>>> resultsListeners = new CopyOnWriteArrayList<>();

	private DefaultTableModel runDetailTableModel;
	private JTextArea runSummaryText;
	private JTextArea dispersionSummaryText;
	private JTextArea exportStatusText;

	/** Called before the extension settings are first used, so an owner can attach it to the simulation. */
	private Runnable extensionAttachRequest = () -> { };

	public MonteCarloSetupPanel(MonteCarloExtension extension, Simulation simulation) {
		this(extension, simulation, true);
	}

	public MonteCarloSetupPanel(MonteCarloExtension extension, Simulation simulation, boolean includeBatchControls) {
		this.extension = extension;
		this.simulation = simulation;

		setLayout(new MigLayout("fill, ins 6, wrap 1, hidemode 3",
				"[grow, fill]", "[]6[]6[]"));

		// ---- 1. General settings ----
		add(buildGeneralPanel(extension), "growx");

		// ---- 2. Tabbed pane ----
		JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP);
		tabs.setFont(tabs.getFont().deriveFont(Font.BOLD, 12f));
		tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
		tabs.addTab("Launch", buildLaunchTab(extension));
		tabs.addTab("Atmosphere", buildAtmosphereTab(extension, simulation));
		tabs.addTab("Disturbances", buildDisturbancesTab(extension));
		tabs.addTab("Vehicle", buildVehicleTab(extension));
		add(tabs, "growx, growy, push");

		// ---- 3. Batch run / export ----
		if (includeBatchControls) {
			add(buildBatchPanel(extension, simulation), "growx");
		}
	}

	/** Wraps this panel in a scroll pane sized for narrow dialogs. */
	public JScrollPane wrapInScrollPane() {
		return SimulationTabLayoutUtils.wrapFormScrollable(this);
	}

	/**
	 * Registers a hook invoked as soon as the user touches any control on this panel,
	 * so the owner can attach the extension to the simulation on first use instead of
	 * on every dialog open. Only user edits trigger it: the listeners are installed
	 * after the controls have been populated from the extension.
	 */
	public void setExtensionAttachRequest(Runnable request) {
		this.extensionAttachRequest = (request == null) ? () -> { } : request;
		installAttachTriggers(this);
	}

	/** Fires the attach request from every interactive control below {@code component}. */
	private void installAttachTriggers(Component component) {
		if (component instanceof AbstractButton button) {
			button.addActionListener(e -> extensionAttachRequest.run());
		} else if (component instanceof JSpinner spinner) {
			spinner.addChangeListener(e -> extensionAttachRequest.run());
		} else if (component instanceof JComboBox<?> comboBox) {
			comboBox.addActionListener(e -> extensionAttachRequest.run());
		} else if (component instanceof JTextField textField) {
			textField.addActionListener(e -> extensionAttachRequest.run());
		}

		if (component instanceof Container container) {
			for (Component child : container.getComponents()) {
				installAttachTriggers(child);
			}
		}
	}

	/** Notified on the EDT whenever a batch run completes. */
	public void addResultsListener(Consumer<List<MonteCarloRunRecord>> listener) {
		if (listener != null) {
			resultsListeners.add(listener);
		}
	}

	public MonteCarloExtension getExtension() {
		return extension;
	}

	/** Results of the most recent batch run started from this panel, or {@code null}. */
	public List<MonteCarloRunRecord> getLastBatchResults() {
		return lastBatchResults.get();
	}

	// =====================================================================
	// Section builders
	// =====================================================================

	/**
	 * General settings: enabled, debug, sim count, deterministic seed.
	 */
	private JPanel buildGeneralPanel(MonteCarloExtension ext) {
		JPanel p = titledPanel("General");
		p.setLayout(new MigLayout("ins 8, wrap 4, gap 8 4",
				"[grow 0][grow 0][grow][grow 0]"));

		// Row 1: Enabled + Debug
		JCheckBox enabled = new JCheckBox("Enabled", ext.isEnabled());
		enabled.setToolTipText("Master switch \u2014 uncheck to disable all Monte Carlo perturbations.");
		enabled.addActionListener(e -> ext.setEnabled(enabled.isSelected()));
		p.add(enabled, "span 4, wrap");

		JCheckBox debug = new JCheckBox("Debug logging", ext.isDebugEnabled());
		debug.setToolTipText("Log per-run perturbation values to the OpenRocket console.");
		debug.addActionListener(e -> ext.setDebugEnabled(debug.isSelected()));
		p.add(debug, "span 4, wrap");

		// Row 2: Number of simulations
		p.add(new JLabel("Simulations"), "align label");
		int initialRunCount = Math.max(MonteCarloSettings.MIN_RUN_COUNT,
				Math.min(MonteCarloSettings.MAX_RUN_COUNT, ext.getNumberOfSimulations()));
		if (initialRunCount != ext.getNumberOfSimulations()) ext.setNumberOfSimulations(initialRunCount);
		JSpinner nSpinner = new JSpinner(new SpinnerNumberModel(
				(Number) Integer.valueOf(initialRunCount),
				(Comparable<Integer>) Integer.valueOf(MonteCarloSettings.MIN_RUN_COUNT),
				(Comparable<Integer>) Integer.valueOf(MonteCarloSettings.MAX_RUN_COUNT),
				(Number) Integer.valueOf(1)));
		nSpinner.setEditor(new SpinnerEditor(nSpinner));
		nSpinner.setToolTipText("Total number of Monte Carlo simulation runs.");
		nSpinner.addChangeListener(e -> ext.setNumberOfSimulations(((Number) nSpinner.getValue()).intValue()));
		p.add(nSpinner, "span 3, growx, wrap");

		// Parallelism is a run setting, not an export-only setting.  Keep it in the
		// always-visible General section so hosts that provide their own Run button
		// (notably MonteCarloDialog) do not accidentally hide it with the legacy
		// batch/export controls.
		p.add(new JLabel("Worker threads"), "align label");
		int maxThreads = Math.max(4, Runtime.getRuntime().availableProcessors());
		int initialThreads = Math.min(Math.max(1, ext.getWorkerThreads()), maxThreads);
		if (initialThreads != ext.getWorkerThreads()) ext.setWorkerThreads(initialThreads);
		JSpinner threadsSpinner = new JSpinner(new SpinnerNumberModel(
				(Number) Integer.valueOf(initialThreads),
				(Comparable<Integer>) Integer.valueOf(1),
				(Comparable<Integer>) Integer.valueOf(maxThreads),
				(Number) Integer.valueOf(1)));
		threadsSpinner.setName("MonteCarloWorkerThreads");
		threadsSpinner.setEditor(new SpinnerEditor(threadsSpinner));
		threadsSpinner.setToolTipText("Number of parallel JVM threads for the batch run (" +
				Runtime.getRuntime().availableProcessors() + " cores detected).");
		threadsSpinner.addChangeListener(e -> ext.setWorkerThreads(
				((Number) threadsSpinner.getValue()).intValue()));
		p.add(threadsSpinner, "span 3, growx, wrap");

		// Row 4: Deterministic seed
		p.add(new JLabel("Seed"), "align label");
		JCheckBox deterministicSeed = new JCheckBox("Deterministic", ext.isUseDeterministicSeed());
		deterministicSeed.setToolTipText("Use a fixed seed for reproducible results.");
		deterministicSeed.addActionListener(e -> ext.setUseDeterministicSeed(deterministicSeed.isSelected()));
		p.add(deterministicSeed);

		JSpinner seedSpinner = new JSpinner(new SpinnerNumberModel(
				ext.getRandomSeed(), Long.MIN_VALUE, Long.MAX_VALUE, 1L));
		seedSpinner.setEditor(new SpinnerEditor(seedSpinner));
		seedSpinner.setToolTipText("Base seed value (only used when Deterministic is checked).");
		seedSpinner.addChangeListener(e -> ext.setRandomSeed(((Number) seedSpinner.getValue()).longValue()));
		p.add(seedSpinner, "span 2, growx");

		return p;
	}

	/**
	 * Launch / Orientation variation tab.
	 */
	private JPanel buildLaunchTab(MonteCarloExtension ext) {
		JPanel p = new JPanel(new MigLayout("ins 12, wrap 3, gap 8 6",
				"[grow 0, 180::][grow][grow 0]"));

		addAngleRow(p, "Launch rail angle " + SIGMA, ext, "LaunchRodAngleStdDevDeg",
				"Standard deviation of the launch rail elevation angle.");
		addAngleRow(p, "Launch rail direction " + SIGMA, ext, "LaunchRodDirectionStdDevDeg",
				"Standard deviation of the launch rail azimuth direction.");

		p.add(new JSeparator(), "span 3, growx, gaptop 6, gapbottom 6");

		addLengthRow(p, "Launch altitude " + SIGMA, ext, "LaunchAltitudeStdDevM",
				"Standard deviation of launch site altitude (AGL).");
		addAngleRow(p, "Launch latitude " + SIGMA, ext, "LaunchLatitudeStdDevDeg",
				"Standard deviation of launch site latitude.");
		addAngleRow(p, "Launch longitude " + SIGMA, ext, "LaunchLongitudeStdDevDeg",
				"Standard deviation of launch site longitude.");

		// Push remaining space to bottom so fields stay at top
		p.add(new JLabel(), "span 3, growy, pushy");
		return p;
	}

	/**
	 * Atmosphere / Wind variation tab.
	 */
	private JPanel buildAtmosphereTab(MonteCarloExtension ext, Simulation sim) {
		JPanel p = new JPanel(new MigLayout("ins 12, wrap 3, gap 8 6",
				"[grow 0, 180::][grow][grow 0]"));

		final boolean multiLevel = isMultiLevelWindSelected(sim.getOptions());

		// Wind speed average sigma
		JLabel windAvgLabel = new JLabel("Wind speed average " + SIGMA);
		p.add(windAvgLabel, "align label");

		DoubleModel windAvgModel = new DoubleModel(ext, "WindSpeedAverageSigmaMps",
				UnitGroup.UNITS_VELOCITY, 0);
		JSpinner windAvgSpinner = new JSpinner(windAvgModel.getSpinnerModel());
		windAvgSpinner.setEditor(new SpinnerEditor(windAvgSpinner));
		UnitSelector windAvgUnits = new UnitSelector(windAvgModel);

		if (multiLevel) {
			String tip = "A single sampled mean-speed offset is applied to every active multi-level wind layer.";
			windAvgLabel.setToolTipText(tip);
			windAvgSpinner.setToolTipText(tip);
			windAvgUnits.setToolTipText(tip);
		} else {
			String tip = "Standard deviation of mean wind speed perturbation.";
			windAvgLabel.setToolTipText(tip);
			windAvgSpinner.setToolTipText(tip);
		}
		p.add(windAvgSpinner, "growx");
		p.add(windAvgUnits);

		// Wind speed turbulence sigma
		addVelocityRow(p, "Wind speed turbulence " + SIGMA, ext,
				"WindSpeedTurbulenceSigmaMps",
				"Standard deviation of wind gust / turbulence intensity perturbation.");

		// Wind direction sigma
		addAngleRow(p, "Wind direction " + SIGMA, ext, "WindDirectionStdDevDeg",
				"Standard deviation of wind direction perturbation.");

		p.add(new JSeparator(), "span 3, growx, gaptop 6, gapbottom 6");

		// Temperature sigma
		addTemperatureRow(p, "Temperature " + SIGMA, ext.getTemperatureStdDevC(),
				ext::setTemperatureStdDevC,
				"Standard deviation of launch temperature perturbation (" + DEG_C + ").");

		// Pressure sigma
		addPressureRow(p, "Pressure " + SIGMA, ext.getPressureStdDevMbar(),
				ext::setPressureStdDevMbar,
				"Standard deviation of launch pressure perturbation (mbar).");

		addPercentSigma(p, "Air density multiplier " + SIGMA,
				ext.getDensityMultiplierSigma() * 100.0,
				v -> ext.setDensityMultiplierSigma(v / 100.0),
				"Relative atmospheric-density uncertainty applied throughout the flight.");

		p.add(new JLabel(), "span 3, growy, pushy");
		return p;
	}

	/**
	 * Wind disturbances tab: Gust events + Shear layer, each in a titled sub-panel
	 * with an enable checkbox that grays out all child controls.
	 */
	private JPanel buildDisturbancesTab(MonteCarloExtension ext) {
		JPanel p = new JPanel(new MigLayout("ins 8, wrap 1, gap 0 8", "[grow, fill]"));

		p.add(buildGustPanel(ext), "growx");
		p.add(buildShearPanel(ext), "growx");

		p.add(new JLabel(), "growy, pushy");
		return p;
	}

	/** Gust events sub-panel with enable checkbox controlling child enabled state. */
	private JPanel buildGustPanel(MonteCarloExtension ext) {
		JPanel gust = titledPanel("Gust Events");
		gust.setLayout(new MigLayout("ins 8, wrap 3, gap 8 4",
				"[grow 0, 160::][grow][grow 0]"));

		final List<JComponent> gustChildren = new ArrayList<>();

		JCheckBox gustEnable = new JCheckBox("Enable gust events", ext.isGustEventsEnabled());
		gustEnable.setToolTipText("Add random discrete gust events during the simulation.");
		gustEnable.addActionListener(e -> {
			ext.setGustEventsEnabled(gustEnable.isSelected());
			setChildrenEnabled(gustChildren, gustEnable.isSelected());
		});
		gust.add(gustEnable, "span 3, wrap");

		gustChildren.addAll(addIntRow(gust, "Event count", ext.getGustEventCount(), 0, 50,
				ext::setGustEventCount, "events",
				"Number of discrete gust events per simulation run."));

		gustChildren.addAll(addTimeRow(gust, "Window start", ext.getGustWindowStartS(),
				ext::setGustWindowStartS,
				"Earliest time (s) a gust can begin."));
		gustChildren.addAll(addTimeRow(gust, "Window end", ext.getGustWindowEndS(),
				ext::setGustWindowEndS,
				"Latest time (s) a gust can begin."));

		gust.add(new JSeparator(), "span 3, growx, gaptop 4, gapbottom 4");

		gustChildren.addAll(addTimeRow(gust, "Duration mean", ext.getGustDurationMeanS(),
				ext::setGustDurationMeanS,
				"Mean duration of each gust event (s)."));
		gustChildren.addAll(addTimeRow(gust, "Duration " + SIGMA, ext.getGustDurationSigmaS(),
				ext::setGustDurationSigmaS,
				"Standard deviation of gust duration (s)."));

		gustChildren.addAll(addVelocityRowCollect(gust, "Peak " + DELTA + "V mean", ext,
				"GustPeakDeltaMeanMps",
				"Mean peak wind speed change during a gust event."));
		gustChildren.addAll(addVelocityRowCollect(gust, "Peak " + DELTA + "V " + SIGMA, ext,
				"GustPeakDeltaSigmaMps",
				"Standard deviation of peak wind speed change."));

		setChildrenEnabled(gustChildren, ext.isGustEventsEnabled());
		return gust;
	}

	/** Wind shear layer sub-panel with enable checkbox controlling child enabled state. */
	private JPanel buildShearPanel(MonteCarloExtension ext) {
		JPanel shear = titledPanel("Wind Shear Layer");
		shear.setLayout(new MigLayout("ins 8, wrap 3, gap 8 4",
				"[grow 0, 160::][grow][grow 0]"));

		final List<JComponent> shearChildren = new ArrayList<>();

		JCheckBox shearEnable = new JCheckBox("Enable shear layer", ext.isShearLayerEnabled());
		shearEnable.setToolTipText("Apply a wind shear layer at a specified altitude band.");
		shearEnable.addActionListener(e -> {
			ext.setShearLayerEnabled(shearEnable.isSelected());
			setChildrenEnabled(shearChildren, shearEnable.isSelected());
		});
		shear.add(shearEnable, "span 3, wrap");

		shearChildren.addAll(addLengthRowCollect(shear, "Center altitude", ext,
				"ShearCenterAltM",
				"Altitude at the center of the shear layer."));
		shearChildren.addAll(addLengthRowCollect(shear, "Thickness", ext,
				"ShearThicknessM",
				"Vertical thickness of the shear transition zone."));

		shear.add(new JSeparator(), "span 3, growx, gaptop 4, gapbottom 4");

		shearChildren.addAll(addVelocityRowCollect(shear, DELTA + "V mean", ext,
				"ShearDeltaMeanMps",
				"Mean wind speed change across the shear layer."));
		shearChildren.addAll(addVelocityRowCollect(shear, DELTA + "V " + SIGMA, ext,
				"ShearDeltaSigmaMps",
				"Standard deviation of wind speed change across the shear layer."));

		setChildrenEnabled(shearChildren, ext.isShearLayerEnabled());
		return shear;
	}

	/**
	 * Vehicle / Motor variation tab.
	 */
	private JPanel buildVehicleTab(MonteCarloExtension ext) {
		JPanel p = new JPanel(new MigLayout("ins 12, wrap 3, gap 8 6",
				"[grow 0, 180::][grow][grow 0]"));

		addPercentSigma(p, "CD multiplier " + SIGMA,
				ext.getCdMultiplierSigma() * 100.0,
				v -> ext.setCdMultiplierSigma(v / 100.0),
				"Drag coefficient variation (e.g. 5 = " + PLUS_MINUS + "5% at 1" + SIGMA + "). " +
				"Accounts for surface roughness, paint, fin alignment, launch lugs.");

		addPercentSigma(p, "Thrust multiplier " + SIGMA,
				ext.getThrustMultiplierSigma() * 100.0,
				v -> ext.setThrustMultiplierSigma(v / 100.0),
				"Motor total impulse variation (e.g. 3 = " + PLUS_MINUS + "3% at 1" + SIGMA + "). " +
				"Typical motor-to-motor variation is 2\u20135%.");

		addPercentSigma(p, "Mass multiplier " + SIGMA,
				ext.getMassMultiplierSigma() * 100.0,
				v -> ext.setMassMultiplierSigma(v / 100.0),
				"Rocket mass variation (e.g. 2 = " + PLUS_MINUS + "2% at 1" + SIGMA + "). " +
				"Accounts for epoxy, paint, and hardware tolerances.");

		addLengthRow(p, "Axial CG offset " + SIGMA, ext, "CgAxialSigmaM",
				"Axial center-of-gravity uncertainty.");
		addPercentSigma(p, "Normal force multiplier " + SIGMA,
				ext.getNormalForceMultiplierSigma() * 100.0,
				v -> ext.setNormalForceMultiplierSigma(v / 100.0),
				"Normal-force, moment, and damping uncertainty.");
		addPercentSigma(p, "Recovery drag multiplier " + SIGMA,
				ext.getRecoveryDragMultiplierSigma() * 100.0,
				v -> ext.setRecoveryDragMultiplierSigma(v / 100.0),
				"Recovery-device drag uncertainty, including Euler recovery/tumble flight.");
		addTimeRow(p, "Ignition delay " + SIGMA, ext.getIgnitionDelaySigmaS(),
				ext::setIgnitionDelaySigmaS, "Upper-stage ignition timing uncertainty.");
		addTimeRow(p, "Deployment delay " + SIGMA, ext.getDeploymentDelaySigmaS(),
				ext::setDeploymentDelaySigmaS, "Recovery deployment timing uncertainty.");

		p.add(new JLabel(), "span 3, growy, pushy");
		return p;
	}

	/**
	 * Batch run / export panel (always visible at bottom).
	 */
	private JPanel buildBatchPanel(MonteCarloExtension ext, Simulation sim) {
		JPanel p = titledPanel("Batch Run / Export");
		p.setLayout(new MigLayout("ins 8, wrap 4, gap 6 4",
			"[grow 0][grow][grow][grow 0]"));

		// Auto-export controls
		JCheckBox autoExportCheckBox = new JCheckBox("Auto-export after batch", ext.isAutoExportEnabled());
		autoExportCheckBox.setToolTipText("Automatically export CSV/KML/PNG/PDF files when a batch run finishes.");
		autoExportCheckBox.addActionListener(e -> ext.setAutoExportEnabled(autoExportCheckBox.isSelected()));
		p.add(autoExportCheckBox, "span 4, wrap");

		p.add(new JLabel("Export folder"), "align label");
		Path initialExportDir = resolveExportDirectory("", ext, sim);
		JTextField exportDirectoryField = new JTextField(initialExportDir.toString());
		exportDirectoryField.setColumns(1);
		exportDirectoryField.setMinimumSize(new Dimension(0, exportDirectoryField.getPreferredSize().height));
		exportDirectoryField.setToolTipText("Output folder for Monte Carlo exports.");
		p.add(exportDirectoryField, "span 2, growx, wmin 0");

		JButton browseExportFolder = new JButton("Browse...");
		browseExportFolder.addActionListener(e -> {
			JFileChooser chooser = new JFileChooser();
			chooser.setDialogTitle("Select output folder for Monte Carlo exports");
			chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			chooser.setAcceptAllFileFilterUsed(false);
			chooser.setCurrentDirectory(resolveExportDirectory(exportDirectoryField.getText(), ext, sim).toFile());
			if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
				return;
			}
			File selected = chooser.getSelectedFile();
			if (selected != null) {
				exportDirectoryField.setText(selected.getAbsolutePath());
				ext.setAutoExportDirectory(selected.getAbsolutePath());
			}
		});
		p.add(browseExportFolder, "growx, wrap");

		// Progress bar
		final JProgressBar progress = new JProgressBar(0, Math.max(1, ext.getNumberOfSimulations()));
		progress.setStringPainted(true);
		progress.setValue(0);
		progress.setString("Idle");
		progress.setPreferredSize(new Dimension(0, 26));
		progress.setMinimumSize(new Dimension(0, 26));
		p.add(progress, "span 4, growx, wmin 0, gaptop 4, wrap");

		runSummaryText = SimulationTabLayoutUtils.createWrappingDisplayText(
				"No Monte Carlo runs executed yet.");
		p.add(runSummaryText, "span 4, growx, wmin 0, wrap");
		dispersionSummaryText = SimulationTabLayoutUtils.createWrappingDisplayText(
				"Landing dispersion: n/a");
		p.add(dispersionSummaryText, "span 4, growx, wmin 0, wrap");

		runDetailTableModel = new DefaultTableModel(new Object[] {
				"Run", "Seed", "Apogee m", "Max V m/s", "Flight s", "Land E m", "Land N m",
				"Land Lat", "Land Lon", "Gusts", "Max \u0394Wind", "Cd x", "Thrust x", "Mass x"
		}, 0) {
			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};
		JTable runDetailsTable = new JTable(runDetailTableModel);
		runDetailsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		JScrollPane runDetailsScroll = new JScrollPane(runDetailsTable);
		runDetailsScroll.setPreferredSize(new Dimension(0, 180));
		runDetailsScroll.setMinimumSize(new Dimension(0, 180));
		p.add(runDetailsScroll, "span 4, growx, wmin 0, hmin 180, wrap");

		// Buttons
		final JButton runBatch = new JButton("Run Monte Carlo Batch");
		final JButton exportAll = new JButton("Export All Results (CSV + KML + PNG + PDF)");

		// Style the run button to stand out
		runBatch.setFont(runBatch.getFont().deriveFont(Font.BOLD));
		runBatch.setToolTipText("Start the Monte Carlo batch simulation.");

		exportAll.setEnabled(false);
		exportAll.setToolTipText("Export sampled inputs, branches, failures, KML/PNG dispersion plots, and a PDF report.");

		// ---- Run batch action ----
		runBatch.addActionListener(e -> {
			runBatch.setEnabled(false);
			exportAll.setEnabled(false);
			lastBatchResults.set(null);
			runDetailTableModel.setRowCount(0);
			setRunSummary("Running Monte Carlo batch...");
			setDispersionSummary("Landing dispersion: calculating...");
			setExportStatus("Export status: waiting for run completion");

			progress.setMaximum(Math.max(1, ext.getNumberOfSimulations()));
			progress.setValue(0);
			progress.setString("Starting...");

			final int runs = ext.getNumberOfSimulations();
			final int threads = Math.max(1, ext.getWorkerThreads());
			final SimulationOptions batchOptions = sim.getOptions().clone();

			SwingWorker<List<MonteCarloRunRecord>, String> worker = new SwingWorker<>() {
				@Override
				protected List<MonteCarloRunRecord> doInBackground() throws Exception {
					MonteCarloSettings settings = MonteCarloBatchRunner.buildSettings(ext, runs, threads);
					MonteCarloResult analysis = LandingDispersionAnalysisCache.get(sim, settings);
					if (analysis == null) {
						boolean useTable = ext.isUsePhysicsAeroTable();
						AerodynamicTable table = useTable ? new PhysicsAeroTableResolver().resolve(
								sim.getActiveConfiguration(), sim.getOptions().getPhysicsAeroSettings()) : null;
						analysis = MonteCarloBatchRunner.runAnalysis(sim, settings, useTable, table,
								(completed, total) -> SwingUtilities.invokeLater(() -> {
									progress.setMaximum(total);
									progress.setValue(completed);
									progress.setString(completed + " / " + total);
								}));
						LandingDispersionAnalysisCache.put(sim, analysis);
					} else {
						int total = settings.getRunCount() + 1;
						SwingUtilities.invokeLater(() -> {
							progress.setMaximum(total);
							progress.setValue(total);
							progress.setString("Reused valid cached result");
						});
					}
					return MonteCarloBatchRunner.toLegacyRecords(sim, analysis, batchOptions);
				}

				@Override
				protected void done() {
					try {
						List<MonteCarloRunRecord> results = get();
						lastBatchResults.set(results);
						updateBatchSummaryLabels(results);
						populateRunDetailsTable(results);
						publishResults(results);
						progress.setValue(progress.getMaximum());
						progress.setString("Done (" + (results.size() - 1) + " dispersed + nominal)");
						exportAll.setEnabled(true);

						Path exportDirectory = resolveExportDirectory(exportDirectoryField.getText(), ext, sim);
						ext.setAutoExportDirectory(exportDirectory.toString());
						ext.setAutoExportEnabled(autoExportCheckBox.isSelected());

						if (autoExportCheckBox.isSelected()) {
							String[] files = exportBatchResults(sim, results, exportDirectory);
							setExportStatus("Export status: auto-exported to " + exportDirectory.toAbsolutePath());
							JOptionPane.showMessageDialog(MonteCarloSetupPanel.this,
									buildExportSuccessMessage(exportDirectory, files[0], files[1]),
									"Monte Carlo Auto Export Complete",
									JOptionPane.INFORMATION_MESSAGE);
						} else {
							setExportStatus("Export status: auto-export disabled (manual export available)");
						}
					} catch (Exception ex) {
						progress.setString("Failed");
						setExportStatus("Export status: run failed");
						JOptionPane.showMessageDialog(MonteCarloSetupPanel.this,
								"Batch run failed:\n" + ex.getMessage(),
								"Monte Carlo Batch Error",
								JOptionPane.ERROR_MESSAGE);
					} finally {
						runBatch.setEnabled(true);
					}
				}
			};
			worker.execute();
		});

		// ---- Unified export action (detailed CSV + landing dispersion bundle) ----
		exportAll.addActionListener(e -> {
			List<MonteCarloRunRecord> results = lastBatchResults.get();
			if (results == null || results.isEmpty()) {
				JOptionPane.showMessageDialog(this,
						"No batch results available. Run a batch first.",
						"Nothing to Export", JOptionPane.WARNING_MESSAGE);
				return;
			}
			JFileChooser chooser = new JFileChooser();
			chooser.setDialogTitle("Select output folder for Monte Carlo exports");
			chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			chooser.setAcceptAllFileFilterUsed(false);
			chooser.setCurrentDirectory(resolveExportDirectory(exportDirectoryField.getText(), ext, sim).toFile());
			if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

			File dir = chooser.getSelectedFile();
			if (dir == null) return;
			Path outDir = Paths.get(dir.getAbsolutePath());

			exportDirectoryField.setText(outDir.toString());
			ext.setAutoExportDirectory(outDir.toString());

			try {
				String[] files = exportBatchResults(sim, results, outDir);
				setExportStatus("Export status: manually exported to " + outDir.toAbsolutePath());
				JOptionPane.showMessageDialog(this,
						buildExportSuccessMessage(outDir, files[0], files[1]),
						"Export Complete", JOptionPane.INFORMATION_MESSAGE);
			} catch (Exception ex) {
				setExportStatus("Export status: export failed");
				JOptionPane.showMessageDialog(this,
						"Export failed:\n" + ex.getMessage(),
						"Export Error", JOptionPane.ERROR_MESSAGE);
			}
		});

		p.add(runBatch, "span 2, growx, gaptop 4");
		p.add(exportAll, "span 2, growx, gaptop 4, wrap");

		exportStatusText = SimulationTabLayoutUtils.createWrappingDisplayText("Export status: idle");
		p.add(exportStatusText, "span 4, growx, wmin 0");
		SimulationTabLayoutUtils.forceViewportWidth(p);

		return p;
	}

	/** Attaches completed results to the simulation and notifies registered listeners. */
	private void publishResults(List<MonteCarloRunRecord> results) {
		if (simulation != null && results != null) {
			simulation.setMonteCarloAnalysis(MonteCarloAnalysis.completed(
					simulation, extension, results, extension.isUsePhysicsAeroTable()));
		}
		for (Consumer<List<MonteCarloRunRecord>> listener : resultsListeners) {
			listener.accept(results);
		}
	}

	private void updateBatchSummaryLabels(List<MonteCarloRunRecord> results) {
		if (results == null || results.isEmpty()) {
			setRunSummary("No Monte Carlo runs executed yet.");
			setDispersionSummary("Landing dispersion: n/a");
			return;
		}

		List<MonteCarloRunRecord> dispersed = results.stream().filter(r -> !r.nominal).toList();
		List<MonteCarloRunRecord> successful = dispersed.stream()
				.filter(r -> r.failureMessage == null).toList();
		long withApogee = successful.stream().filter(r -> Double.isFinite(r.apogee_m)).count();
		long withLanding = successful.stream().filter(r -> r.results.hasLanding).count();
		double meanApogee = successful.stream().mapToDouble(r -> r.apogee_m)
				.filter(Double::isFinite).average().orElse(Double.NaN);
		double meanFlightTime = successful.stream().mapToDouble(r -> r.flightTime_s)
				.filter(Double::isFinite).average().orElse(Double.NaN);
		double maxRadius = successful.stream()
				.filter(r -> r.results.hasLanding)
				.mapToDouble(r -> Math.hypot(r.landingEast_m, r.landingNorth_m))
				.max()
				.orElse(Double.NaN);

		double[] containment = empiricalContainment(successful);
		setRunSummary("Runs=" + dispersed.size() + " + nominal" +
				" | Failures=" + (dispersed.size() - successful.size()) +
				" | Apogee points=" + withApogee +
				" | Landing points=" + withLanding +
				" | Mean apogee=" + formatDouble(meanApogee) + " m" +
				" | Mean flight=" + formatDouble(meanFlightTime) + " s");
		setDispersionSummary("Landing R50/R90/R95=" + formatDouble(containment[0]) + "/" +
				formatDouble(containment[1]) + "/" + formatDouble(containment[2]) +
				" m | max pad range=" + formatDouble(maxRadius) + " m");
	}

	private void setRunSummary(String text) {
		SimulationTabLayoutUtils.setWrappingDisplayText(runSummaryText, text);
	}

	private void setDispersionSummary(String text) {
		SimulationTabLayoutUtils.setWrappingDisplayText(dispersionSummaryText, text);
	}

	private void setExportStatus(String text) {
		SimulationTabLayoutUtils.setWrappingDisplayText(exportStatusText, text);
	}

	private static double[] empiricalContainment(List<MonteCarloRunRecord> records) {
		List<MonteCarloRunRecord> landed = records.stream().filter(r -> r.results.hasLanding).toList();
		if (landed.isEmpty()) return new double[] { Double.NaN, Double.NaN, Double.NaN };
		double meanEast = landed.stream().mapToDouble(r -> r.landingEast_m).average().orElse(0);
		double meanNorth = landed.stream().mapToDouble(r -> r.landingNorth_m).average().orElse(0);
		double[] radii = landed.stream().mapToDouble(r ->
				Math.hypot(r.landingEast_m - meanEast, r.landingNorth_m - meanNorth)).sorted().toArray();
		return new double[] { nearestRank(radii, 0.50), nearestRank(radii, 0.90), nearestRank(radii, 0.95) };
	}

	private static double nearestRank(double[] sorted, double probability) {
		return sorted[Math.max(0, (int) Math.ceil(probability * sorted.length) - 1)];
	}

	private void populateRunDetailsTable(List<MonteCarloRunRecord> results) {
		runDetailTableModel.setRowCount(0);
		if (results == null) {
			return;
		}

		for (MonteCarloRunRecord record : results) {
			runDetailTableModel.addRow(new Object[] {
					record.nominal ? "Nominal" : record.runIndex,
					record.seedUsed,
					formatDouble(record.apogee_m),
					formatDouble(record.maxVelocity_mps),
					formatDouble(record.flightTime_s),
					formatDouble(record.landingEast_m),
					formatDouble(record.landingNorth_m),
					formatDouble(record.landingLat_deg),
					formatDouble(record.landingLon_deg),
					record.gustEventCountRealized,
					formatDouble(record.gustMaxDeltaWind_mps),
					formatDouble(record.cdMultiplierUsed),
					formatDouble(record.thrustMultiplierUsed),
					formatDouble(record.massMultiplierUsed)
			});
		}
	}

	private static String formatDouble(double value) {
		if (!Double.isFinite(value)) {
			return "n/a";
		}
		return DECIMAL.format(value);
	}

	public static String[] exportBatchResults(Simulation sim,
											   List<MonteCarloRunRecord> results,
											   Path outDir) throws Exception {
		String orkStem = resolveCurrentOrkStem(sim);
		String detailedCsvName = orkStem + "_montecarlo_detailed.csv";
		String dispersionStem = orkStem + "_landing_dispersion";

		Files.createDirectories(outDir);
		File detailedCsv = outDir.resolve(detailedCsvName).toFile();

		SimulationOptions opts = sim.getOptions();
		double launchLatDeg = opts.getLaunchLatitude();
		double launchLonDeg = opts.getLaunchLongitude();

		MonteCarloCsvExporter.exportDetailedCsv(detailedCsv, results);
		MonteCarloCsvExporter.exportBranchesCsv(
				outDir.resolve(orkStem + "_montecarlo_branches.csv").toFile(), results);
		LandingDispersion6DOF.exportAll(outDir, dispersionStem, results, launchLatDeg, launchLonDeg);
		MonteCarloPdfExporter.export(outDir.resolve(orkStem + "_montecarlo_report.pdf").toFile(),
				orkStem, results, outDir.resolve(dispersionStem + ".png").toFile());

		return new String[] { detailedCsvName, dispersionStem };
	}

	private static String buildExportSuccessMessage(Path outDir, String detailedCsvName, String dispersionStem) {
		return "Exported to:\n" + outDir.toAbsolutePath() + "\n\n" +
				"Files:\n" +
				" - " + detailedCsvName + "\n" +
				" - " + detailedCsvName.replace("_detailed.csv", "_branches.csv") + "\n" +
				" - " + detailedCsvName.replace("_detailed.csv", "_report.pdf") + "\n" +
				" - " + dispersionStem + ".kml\n" +
				" - " + dispersionStem + ".png\n" +
				" - " + dispersionStem + "_points.csv\n" +
				" - " + dispersionStem + "_summary.csv\n" +
				" - one KML/PNG/points/summary bundle per landing body";
	}

	private static Path resolveExportDirectory(String configuredDirectory, MonteCarloExtension ext, Simulation sim) {
		String dir = safeString(configuredDirectory).trim();
		if (!dir.isBlank()) {
			return Paths.get(dir);
		}

		if (ext != null) {
			String extensionPath = safeString(ext.getAutoExportDirectory()).trim();
			if (!extensionPath.isBlank()) {
				return Paths.get(extensionPath);
			}
		}

		return resolveDefaultExportDirectory(sim);
	}

	private static Path resolveDefaultExportDirectory(Simulation sim) {
		if (sim != null) {
			Object doc = invokeObject(sim, "getDocument");
			if (doc == null) {
				Object rocket = invokeObject(sim, "getRocket");
				doc = invokeObject(rocket, "getDocument");
			}

			Object fileLike = firstNonNull(
					invokeObject(doc, "getFile"),
					invokeObject(doc, "getDocumentFile"),
					invokeObject(doc, "getSourceFile"),
					invokeObject(doc, "getPath")
			);
			Path filePath = fileLikeToPath(fileLike);
			if (filePath != null) {
				Path baseDirectory = Files.isDirectory(filePath) ? filePath : filePath.getParent();
				if (baseDirectory != null) {
					return baseDirectory.resolve("montecarlo_exports");
				}
			}
		}

		return Paths.get(System.getProperty("user.home", "."), "OpenRocket", "montecarlo_exports");
	}

	// =====================================================================
	// Row-builder helpers (with tooltip support)
	// =====================================================================

	/** Angle row using OpenRocket's DoubleModel + UnitSelector. */
	private static void addAngleRow(JPanel panel, String label, MonteCarloExtension ext,
									String property, String tooltip) {
		JLabel lbl = tooltipLabel(label, tooltip);
		panel.add(lbl, "align label");
		DoubleModel model = new DoubleModel(ext, property, UnitGroup.UNITS_ANGLE, 0);
		JSpinner spinner = new JSpinner(model.getSpinnerModel());
		spinner.setEditor(new SpinnerEditor(spinner));
		if (tooltip != null) spinner.setToolTipText(tooltip);
		panel.add(spinner, "growx");
		panel.add(new UnitSelector(model));
	}

	/** Velocity row using OpenRocket's DoubleModel + UnitSelector. */
	private static void addVelocityRow(JPanel panel, String label, MonteCarloExtension ext,
									   String property, String tooltip) {
		JLabel lbl = tooltipLabel(label, tooltip);
		panel.add(lbl, "align label");
		DoubleModel model = new DoubleModel(ext, property, UnitGroup.UNITS_VELOCITY, 0);
		JSpinner spinner = new JSpinner(model.getSpinnerModel());
		spinner.setEditor(new SpinnerEditor(spinner));
		if (tooltip != null) spinner.setToolTipText(tooltip);
		panel.add(spinner, "growx");
		panel.add(new UnitSelector(model));
	}

	/** Velocity row that returns all created components for enable/disable toggling. */
	private static List<JComponent> addVelocityRowCollect(JPanel panel, String label,
														  MonteCarloExtension ext,
														  String property, String tooltip) {
		List<JComponent> components = new ArrayList<>();
		JLabel labelComponent = tooltipLabel(label, tooltip);
		panel.add(labelComponent, "align label");
		components.add(labelComponent);

		DoubleModel model = new DoubleModel(ext, property, UnitGroup.UNITS_VELOCITY, 0);
		JSpinner spinner = new JSpinner(model.getSpinnerModel());
		spinner.setEditor(new SpinnerEditor(spinner));
		if (tooltip != null) spinner.setToolTipText(tooltip);
		panel.add(spinner, "growx");
		components.add(spinner);

		UnitSelector units = new UnitSelector(model);
		panel.add(units);
		components.add(units);
		return components;
	}

	/** Length row using OpenRocket's DoubleModel + UnitSelector. */
	private static void addLengthRow(JPanel panel, String label, MonteCarloExtension ext,
									 String property, String tooltip) {
		JLabel lbl = tooltipLabel(label, tooltip);
		panel.add(lbl, "align label");
		DoubleModel model = new DoubleModel(ext, property, UnitGroup.UNITS_LENGTH, 0);
		JSpinner spinner = new JSpinner(model.getSpinnerModel());
		spinner.setEditor(new SpinnerEditor(spinner));
		if (tooltip != null) spinner.setToolTipText(tooltip);
		panel.add(spinner, "growx");
		panel.add(new UnitSelector(model));
	}

	/** Length row that returns all created components for enable/disable toggling. */
	private static List<JComponent> addLengthRowCollect(JPanel panel, String label,
														MonteCarloExtension ext,
														String property, String tooltip) {
		List<JComponent> components = new ArrayList<>();
		JLabel labelComponent = tooltipLabel(label, tooltip);
		panel.add(labelComponent, "align label");
		components.add(labelComponent);

		DoubleModel model = new DoubleModel(ext, property, UnitGroup.UNITS_LENGTH, 0);
		JSpinner spinner = new JSpinner(model.getSpinnerModel());
		spinner.setEditor(new SpinnerEditor(spinner));
		if (tooltip != null) spinner.setToolTipText(tooltip);
		panel.add(spinner, "growx");
		components.add(spinner);

		UnitSelector units = new UnitSelector(model);
		panel.add(units);
		components.add(units);
		return components;
	}

	/** Temperature row with fixed degree-C unit. */
	private static void addTemperatureRow(JPanel panel, String label, double initialC,
										  DoubleConsumer setter, String tooltip) {
		JLabel lbl = tooltipLabel(label, tooltip);
		panel.add(lbl, "align label");

		JSpinner spinner = new JSpinner(new SpinnerNumberModel(initialC, 0.0, 500.0, 0.1));
		spinner.setEditor(new SpinnerEditor(spinner));
		spinner.addChangeListener(e -> setter.accept(((Number) spinner.getValue()).doubleValue()));
		if (tooltip != null) spinner.setToolTipText(tooltip);
		panel.add(spinner, "growx");
		panel.add(new JLabel(DEG_C));
	}

	/** Pressure row with fixed mbar unit. */
	private static void addPressureRow(JPanel panel, String label, double initialMbar,
									   DoubleConsumer setter, String tooltip) {
		JLabel lbl = tooltipLabel(label, tooltip);
		panel.add(lbl, "align label");

		JSpinner spinner = new JSpinner(new SpinnerNumberModel(initialMbar, 0.0, 20000.0, 1.0));
		spinner.setEditor(new SpinnerEditor(spinner));
		spinner.addChangeListener(e -> setter.accept(((Number) spinner.getValue()).doubleValue()));
		if (tooltip != null) spinner.setToolTipText(tooltip);
		panel.add(spinner, "growx");
		panel.add(new JLabel("mbar"));
	}

	/** Time-in-seconds row that returns all created components for enable/disable toggling. */
	private static List<JComponent> addTimeRow(JPanel panel, String label,
											   double initialSeconds, DoubleConsumer setter,
											   String tooltip) {
		List<JComponent> components = new ArrayList<>();
		JLabel lbl = tooltipLabel(label, tooltip);
		panel.add(lbl, "align label");
		components.add(lbl);

		JSpinner spinner = new JSpinner(new SpinnerNumberModel(initialSeconds, 0.0, 10_000.0, 0.05));
		spinner.setEditor(new SpinnerEditor(spinner));
		spinner.addChangeListener(e -> setter.accept(((Number) spinner.getValue()).doubleValue()));
		if (tooltip != null) spinner.setToolTipText(tooltip);
		panel.add(spinner, "growx");
		components.add(spinner);

		JLabel unit = new JLabel("s");
		panel.add(unit);
		components.add(unit);
		return components;
	}

	/** Integer row that returns all created components for enable/disable toggling. */
	private static List<JComponent> addIntRow(JPanel panel, String label,
											  int initial, int min, int max,
											  java.util.function.IntConsumer setter,
											  String unitText, String tooltip) {
		List<JComponent> components = new ArrayList<>();
		JLabel labelComponent = tooltipLabel(label, tooltip);
		panel.add(labelComponent, "align label");
		components.add(labelComponent);

		JSpinner spinner = new JSpinner(new SpinnerNumberModel(
				(Number) Integer.valueOf(initial),
				(Comparable<Integer>) Integer.valueOf(min),
				(Comparable<Integer>) Integer.valueOf(max),
				(Number) Integer.valueOf(1)));
		spinner.setEditor(new SpinnerEditor(spinner));
		spinner.addChangeListener(e -> setter.accept(((Number) spinner.getValue()).intValue()));
		if (tooltip != null) spinner.setToolTipText(tooltip);
		panel.add(spinner, "growx");
		components.add(spinner);

		JLabel unit = new JLabel(unitText);
		panel.add(unit);
		components.add(unit);
		return components;
	}

	/**
	 * Percentage sigma spinner (e.g. "5" meaning 5%).
	 * Displayed/edited in percent; setter receives percent.
	 */
	private static void addPercentSigma(JPanel panel, String label, double initialPercent,
										DoubleConsumer setter, String tooltip) {
		JLabel lbl = tooltipLabel(label, tooltip);
		panel.add(lbl, "align label");

		JSpinner spinner = new JSpinner(new SpinnerNumberModel(initialPercent, 0.0, 100.0, 0.5));
		spinner.setEditor(new SpinnerEditor(spinner));
		spinner.addChangeListener(e -> setter.accept(((Number) spinner.getValue()).doubleValue()));
		if (tooltip != null) spinner.setToolTipText(tooltip);
		panel.add(spinner, "growx");
		panel.add(new JLabel("%"));
	}

	// =====================================================================
	// UI utility helpers
	// =====================================================================

	/** Creates a JPanel with a titled etched border. */
	private static JPanel titledPanel(String title) {
		JPanel p = new JPanel();
		TitledBorder border = BorderFactory.createTitledBorder(
				BorderFactory.createEtchedBorder(), title);
		border.setTitleFont(border.getTitleFont().deriveFont(Font.BOLD, 12f));
		p.setBorder(border);
		return p;
	}

	/** Creates a JLabel with a tooltip. */
	private static JLabel tooltipLabel(String text, String tooltip) {
		JLabel l = new JLabel(text);
		if (tooltip != null && !tooltip.isEmpty()) {
			l.setToolTipText(tooltip);
		}
		return l;
	}

	/** Enables or disables the disturbance controls and their child widgets. */
	private static void setChildrenEnabled(List<JComponent> components, boolean enabled) {
		for (JComponent component : components) {
			component.setEnabled(enabled);
			for (Component child : component.getComponents()) {
				child.setEnabled(enabled);
			}
		}
	}

	/**
	 * Best-effort check of the simulation's wind model selection.
	 * Uses reflection to remain compatible across OpenRocket versions.
	 */
	private static boolean isMultiLevelWindSelected(SimulationOptions opts) {
		if (opts == null) return false;

		// 1) Wind model type enum/name (most reliable)
		try {
			Object t = opts.getClass().getMethod("getWindModelType").invoke(opts);
			if (t != null) {
				String name = String.valueOf(t).trim().toLowerCase();
				if (name.contains("multi") && name.contains("level")) return true;
				if (name.contains("average")) return false;
			}
		} catch (Exception ignored) { }

		// 2) Explicit boolean flag in some versions
		try {
			Object b = opts.getClass().getMethod("isMultiLevelWindModel").invoke(opts);
			if (b instanceof Boolean bb) return bb;
		} catch (Exception ignored) { }
		try {
			Object b = opts.getClass().getMethod("isMultiLevelWindModelEnabled").invoke(opts);
			if (b instanceof Boolean bb) return bb;
		} catch (Exception ignored) { }

		// 3) Wind model instance type/name
		try {
			Object wm = opts.getClass().getMethod("getWindModel").invoke(opts);
			if (wm != null) {
				String cls = wm.getClass().getName().toLowerCase();
				if (cls.contains("multilevel")) return true;
			}
		} catch (Exception ignored) { }

		return false;
	}

	/**
	 * Attempts to resolve the currently loaded .ork filename stem and sanitize it
	 * for safe filesystem use.
	 */
	private static String resolveCurrentOrkStem(Simulation sim) {
		String fallback = "unsaved_ork";
		if (sim == null) return fallback;

		Object doc = invokeObject(sim, "getDocument");
		if (doc == null) {
			Object rocket = invokeObject(sim, "getRocket");
			doc = invokeObject(rocket, "getDocument");
		}

		String stem = extractDocStem(doc);
		if (stem == null || stem.isBlank()) {
			stem = safeString(sim.getName());
		}
		if (stem == null || stem.isBlank()) {
			stem = fallback;
		}

		stem = stripKnownExtension(stem, ".ork");
		stem = sanitizeFileStem(stem);
		if (stem.isBlank()) return fallback;
		return stem;
	}

	private static String extractDocStem(Object doc) {
		if (doc == null) return null;

		Object fileLike = firstNonNull(
				invokeObject(doc, "getFile"),
				invokeObject(doc, "getDocumentFile"),
				invokeObject(doc, "getSourceFile"),
				invokeObject(doc, "getPath")
		);

		String fromFileLike = fileLikeToName(fileLike);
		if (fromFileLike != null && !fromFileLike.isBlank()) return fromFileLike;

		String byMethod = firstNonBlank(
				invokeString(doc, "getFileName"),
				invokeString(doc, "getName"),
				invokeString(doc, "toString")
		);
		if (byMethod == null || byMethod.isBlank()) return null;

		return new File(byMethod).getName();
	}

	private static Object firstNonNull(Object... vals) {
		for (Object v : vals) {
			if (v != null) return v;
		}
		return null;
	}

	private static String firstNonBlank(String... vals) {
		for (String v : vals) {
			if (v != null && !v.isBlank()) return v;
		}
		return null;
	}

	private static String fileLikeToName(Object fileLike) {
		if (fileLike == null) return null;
		if (fileLike instanceof File f) return f.getName();
		if (fileLike instanceof Path p) {
			Path n = p.getFileName();
			return (n != null) ? n.toString() : p.toString();
		}
		String s = String.valueOf(fileLike);
		if (s.isBlank()) return null;
		return new File(s).getName();
	}

	private static Path fileLikeToPath(Object fileLike) {
		if (fileLike == null) return null;
		if (fileLike instanceof Path p) return p;
		if (fileLike instanceof File f) return f.toPath();

		String s = String.valueOf(fileLike);
		if (s.isBlank()) return null;
		try {
			return Paths.get(s);
		} catch (Exception ignored) {
			return null;
		}
	}

	private static String invokeString(Object target, String method) {
		Object v = invokeObject(target, method);
		return (v == null) ? null : String.valueOf(v);
	}

	private static Object invokeObject(Object target, String methodName) {
		if (target == null || methodName == null) return null;
		try {
			Method m = target.getClass().getMethod(methodName);
			return m.invoke(target);
		} catch (Exception ignored) {
			return null;
		}
	}

	private static String stripKnownExtension(String name, String extLower) {
		if (name == null) return "";
		String out = name.trim();
		if (out.toLowerCase(Locale.US).endsWith(extLower)) {
			out = out.substring(0, out.length() - extLower.length());
		}
		return out;
	}

	private static String sanitizeFileStem(String input) {
		if (input == null) return "";
		String s = input.trim();
		// Replace characters invalid on Windows/macOS/Linux filesystems.
		s = s.replaceAll("[\\\\/:*?\"<>|]", "_");
		// Collapse whitespace to single underscores for cleaner names.
		s = s.replaceAll("\\s+", "_");
		// Remove leading/trailing dots/underscores.
		s = s.replaceAll("^[._]+", "").replaceAll("[._]+$", "");
		return s;
	}

	private static String safeString(String s) {
		return (s == null) ? "" : s;
	}
}

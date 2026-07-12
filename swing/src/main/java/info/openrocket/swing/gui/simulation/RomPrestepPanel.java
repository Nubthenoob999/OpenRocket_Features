package info.openrocket.swing.gui.simulation;

import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.ChangeListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;

import net.miginfocom.swing.MigLayout;

import info.openrocket.core.aerodynamics.rom.RomFallbackMode;
import info.openrocket.core.aerodynamics.rom.RomMode;
import info.openrocket.core.aerodynamics.rom.RomPreviewDiagnosticsRunner;
import info.openrocket.core.aerodynamics.rom.RomPreviewSample;
import info.openrocket.core.aerodynamics.rom.RomSettings;
import info.openrocket.core.aerodynamics.rom.geometry.FinGeometry;
import info.openrocket.core.aerodynamics.rom.geometry.GeometryFeatureExtractor;
import info.openrocket.core.aerodynamics.rom.geometry.GeometryFeatures;
import info.openrocket.core.aerodynamics.rom.geometry.PathlineSeed;
import info.openrocket.core.aerodynamics.rom.geometry.PathlineSeeder;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.rocketcomponent.FlightConfiguration;

class RomPrestepPanel extends SimulationScrollablePanel {

	private static final long serialVersionUID = 2770487667033110266L;

	private static final String[] DIAG_COLUMNS = {
			"Mach", "AoA_deg", "Theta_deg", "Beta_deg", "Plume", "Regime",
			"CD_legacy", "CD_rom", "CD_final",
			"CN_legacy", "CN_rom", "CN_final",
			"Cm_legacy", "Cm_rom", "Cm_final",
			"CPx_final_m",
			"Confidence", "Fallback_weight", "Fallback_used",
			"Separation_fraction", "Seed_count", "Marching_steps",
			"Transitioned_count", "Separated_count",
			"Mean_stiffness", "Max_stiffness", "Mean_Cf",
			"Min_edge_Cp", "Max_edge_Cp", "Min_edge_Mach", "Max_edge_Mach",
			"Status", "Notes", "Warnings"
	};

	private static final String EMPTY_PROMPT =
			"Generate preview diagnostics to evaluate the selected Mach/AoA sweep. "
					+ "Flight simulations compute ROM forces dynamically during integration.";

	private final Simulation simulation;
	private final GeometryFeatureExtractor geometryFeatureExtractor = new GeometryFeatureExtractor();
	private final PathlineSeeder pathlineSeeder = new PathlineSeeder();

	// ---- Geometry / status labels ----
	private final JLabel geometryHashValue = new JLabel("-");
	private final JLabel bodyLengthValue = new JLabel("-");
	private final JLabel maxDiameterValue = new JLabel("-");
	private final JLabel finCountValue = new JLabel("-");
	private final JLabel shoulderValue = new JLabel("-");
	private final JLabel romStatusValue = new JLabel("-");
	private final JLabel modeValue = new JLabel("-");
	private final JLabel fallbackValue = new JLabel("-");

	// ---- Pathline count controls ----
	private final JSpinner bodyPathlineSpinner = new JSpinner(new SpinnerNumberModel(12, 2, 64, 1));
	private final JSpinner finPathlineSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 16, 1));
	private final JButton applyDesignDefaultsButton = new JButton("Apply design defaults");
	private final JLabel defaultHintLabel = new JLabel("-");

	// ---- Preview sweep controls ----
	private final JSpinner machMinSpinner = new JSpinner(new SpinnerNumberModel(0.20, 0.0, 8.0, 0.05));
	private final JSpinner machMaxSpinner = new JSpinner(new SpinnerNumberModel(1.40, 0.0, 8.0, 0.05));
	private final JSpinner machStepSpinner = new JSpinner(new SpinnerNumberModel(0.10, 0.001, 8.0, 0.01));
	private final JSpinner aoaMinSpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 45.0, 0.5));
	private final JSpinner aoaMaxSpinner = new JSpinner(new SpinnerNumberModel(12.0, 0.0, 45.0, 0.5));
	private final JSpinner aoaStepSpinner = new JSpinner(new SpinnerNumberModel(2.0, 0.001, 45.0, 0.5));
	private final JSpinner thetaSpinner = new JSpinner(new SpinnerNumberModel(0.0, -180.0, 180.0, 5.0));
	private final JSpinner plumeSpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 1.0, 0.05));
	private final JLabel rowCountLabel = new JLabel("-");
	private final JButton generateButton = new JButton("Generate Preview Diagnostics");
	private final JButton cancelButton = new JButton("Cancel");
	private final JButton exportCsvButton = new JButton("Export CSV");
	private final JProgressBar progressBar = new JProgressBar();

	// ---- Seed table ----
	private final DefaultTableModel seedTableModel = new DefaultTableModel(
			new Object[]{"Family", "Component", "x (m)", "Area weight"}, 0) {
		private static final long serialVersionUID = 1L;
		@Override public boolean isCellEditable(int r, int c) { return false; }
	};
	private final JLabel seedCountLabel = new JLabel("-");

	// ---- Diagnostics table ----
	private final DefaultTableModel diagTableModel = new DefaultTableModel(DIAG_COLUMNS, 0) {
		private static final long serialVersionUID = 1L;
		@Override public boolean isCellEditable(int r, int c) { return false; }
	};
	private final JTable diagnosticsTable = new JTable(diagTableModel);
	private final JTextArea selectedRowDetails = createTextArea(6);

	// ---- Status strip labels ----
	private final JLabel rowsComputedLabel = new JLabel("-");
	private final JLabel worstConfidenceLabel = new JLabel("-");
	private final JLabel maxFallbackLabel = new JLabel("-");
	private final JLabel maxSeparationLabel = new JLabel("-");
	private final JLabel singularityLabel = new JLabel("-");
	private final JLabel tableStatusLabel = new JLabel(" ");

	// ---- Async sweep state ----
	private SwingWorker<RomPreviewDiagnosticsRunner.Result, RomPreviewSample> activeWorker;
	private AtomicBoolean activeCancel;
	private RomPreviewDiagnosticsRunner.Result lastResult;

	// Suppress feedback loop when loading settings into spinners
	private boolean loadingSettings = false;
	private boolean designDefaultsInitialized = false;

	RomPrestepPanel(Simulation simulation) {
		super(new MigLayout("fillx, insets 8, gap 8 8, wrap 1", "[grow,fill]", ""));
		this.simulation = simulation;
		defaultHintLabel.setFont(defaultHintLabel.getFont().deriveFont(Font.ITALIC, 11f));
		defaultHintLabel.setToolTipText("Uses rocket geometry characteristics to suggest practical ROM seed counts.");

		add(buildStatusAndGeometryPanel(), "growx");
		add(buildPathlinePanelWithSeedTable(), "growx");
		add(buildPreviewSweepPanel(), "growx");
		add(buildDiagnosticsResultsPanel(), "grow, push");

		wireEvents();
		refreshFromModel();
		updateRowCountEstimate();
		setSweepRunning(false);
		exportCsvButton.setEnabled(false);
	}

	// ─── panel builders ───────────────────────────────────────────────────────

	private JPanel buildStatusAndGeometryPanel() {
		JPanel p = new JPanel(new MigLayout("fillx, insets 0, gap 8 8, wrap 1", "[grow,fill]", ""));

		JPanel status = new JPanel(new MigLayout("fillx, insets 6, gapx 8, gapy 5", "[right][grow]", ""));
		status.setBorder(BorderFactory.createTitledBorder("ROM status"));
		status.add(createInfoButton("ROM status",
				"Enabled: whether the pathline reduced-order model is active for this simulation.\n"
						+ "Mode: seed-density preset (Standard, Conservative, or Diagnostic).\n"
						+ "Fallback: behavior when confidence drops in harsh flow conditions."), "span 2, right, wrap");
		status.add(new JLabel("Enabled:"));  status.add(romStatusValue, "wrap");
		status.add(new JLabel("Mode:"));     status.add(modeValue, "wrap");
		status.add(new JLabel("Fallback:")); status.add(fallbackValue, "wrap");
		p.add(status, "growx, top");

		JPanel geo = new JPanel(new MigLayout("fillx, insets 6, gapx 8, gapy 5",
				"[right][grow]", ""));
		geo.setBorder(BorderFactory.createTitledBorder("Geometry snapshot"));
		geo.add(createInfoButton("Geometry snapshot",
				"Geometry hash: stable signature used to detect design changes.\n"
						+ "Body length and max diameter define fineness and flow scaling.\n"
						+ "Fin count and shoulder/boattail transitions drive seed budgeting."), "span 2, right, wrap");
		geo.add(new JLabel("Geometry hash:"));    geo.add(geometryHashValue, "wrap");
		geo.add(new JLabel("Body length:"));      geo.add(bodyLengthValue, "wrap");
		geo.add(new JLabel("Max diameter:"));     geo.add(maxDiameterValue, "wrap");
		geo.add(new JLabel("Total fins:"));       geo.add(finCountValue, "wrap");
		geo.add(new JLabel("Shoulders / boattails:")); geo.add(shoulderValue, "growx, wrap");
		p.add(geo, "growx, top");

		return p;
	}

	private JPanel buildPathlinePanelWithSeedTable() {
		JPanel p = new JPanel(new MigLayout("fillx, insets 0, gap 8 8, wrap 1", "[grow,fill]", ""));

		JPanel controls = new JPanel(new MigLayout("fillx, insets 6, gapx 8, gapy 6",
				"[right][grow]", ""));
		controls.setBorder(BorderFactory.createTitledBorder("Pathline controls"));
		controls.add(createInfoButton("Pathline controls",
				"Body meridian pathlines: axial seed lines over body surfaces.\n"
						+ "Fin surface pathlines: seed lines per fin set for vortex/separation behavior.\n"
						+ "Higher counts improve fidelity but increase preview runtime."), "span 2, right, wrap");
		controls.add(new JLabel("Body meridian pathlines:"));
		controls.add(bodyPathlineSpinner, "growx, wrap");
		controls.add(new JLabel("Fin surface pathlines:"));
		controls.add(finPathlineSpinner, "growx, wrap");
		applyDesignDefaultsButton.setToolTipText("Set pathline counts from current rocket geometry characteristics.");
		controls.add(applyDesignDefaultsButton, "span 2, alignx left, wrap");
		controls.add(defaultHintLabel, "span 2, growx, wrap");
		controls.add(seedCountLabel, "span 2, growx, wrap");
		p.add(controls, "growx, top");

		JPanel seedPanel = new JPanel(new MigLayout("fill, insets 6, gapy 4", "[grow,fill]", "[][grow]"));
		seedPanel.setBorder(BorderFactory.createTitledBorder("Pathline seed plan (secondary)"));
		JTable seedTable = new JTable(seedTableModel);
		seedTable.setFillsViewportHeight(true);
		seedPanel.add(new JScrollPane(seedTable), "grow, hmin 120");
		p.add(seedPanel, "growx, top");

		return p;
	}

	private JPanel buildPreviewSweepPanel() {
		JPanel p = new JPanel(new MigLayout("fillx, insets 6, gapx 8, gapy 6",
				"[right][grow,fill]", ""));
		p.setBorder(BorderFactory.createTitledBorder("Preview sweep controls"));
		p.add(createInfoButton("Preview sweep controls",
				"Define a 2D Mach/AoA grid; theta and plume are held constant for the sweep.\n"
						+ "Preview diagnostics evaluate ROM/Barrowman coefficients at each cell.\n"
						+ "This does NOT precompute simulation forces — flight simulations compute "
						+ "ROM forces dynamically during integration."), "span 2, right, wrap");

		p.add(new JLabel("Mach min:"));   p.add(machMinSpinner, "growx, wrap");
		p.add(new JLabel("Mach max:"));   p.add(machMaxSpinner, "growx, wrap");
		p.add(new JLabel("Mach step:"));  p.add(machStepSpinner, "growx, wrap");

		p.add(new JLabel("AoA min (deg):"));   p.add(aoaMinSpinner, "growx, wrap");
		p.add(new JLabel("AoA max (deg):"));   p.add(aoaMaxSpinner, "growx, wrap");
		p.add(new JLabel("AoA step (deg):"));  p.add(aoaStepSpinner, "growx, wrap");

		p.add(new JLabel("Theta (deg):"));     p.add(thetaSpinner, "growx, wrap");
		p.add(new JLabel("Plume state:"));     p.add(plumeSpinner, "growx, wrap");
		p.add(new JLabel("Estimated rows:"));  p.add(rowCountLabel, "growx, wrap");

		generateButton.setFont(generateButton.getFont().deriveFont(Font.BOLD, 13f));
		generateButton.setBackground(new Color(60, 120, 200));
		generateButton.setForeground(Color.WHITE);
		generateButton.setOpaque(true);
		generateButton.setToolTipText("Evaluate the configured Mach/AoA sweep. "
				+ "This does NOT precompute simulation forces — flight simulations compute ROM "
				+ "forces dynamically during integration.");
		generateButton.addActionListener(e -> startSweep());

		cancelButton.addActionListener(e -> cancelSweep());
		exportCsvButton.addActionListener(e -> exportCsv());

		p.add(generateButton, "span 2, growx, h 30!, wrap");
		p.add(cancelButton, "span 2, growx, h 30!, wrap");
		p.add(exportCsvButton, "span 2, growx, h 30!, wrap");
		p.add(progressBar, "span 2, growx, wrap");

		return p;
	}

	private JPanel buildDiagnosticsResultsPanel() {
		JPanel p = new JPanel(new MigLayout("fill, insets 6, gapy 6", "[grow,fill]", "[][grow][]"));
		p.setBorder(BorderFactory.createTitledBorder("Preview diagnostics"));

		JPanel strip = new JPanel(new MigLayout("fillx, insets 0, gapx 8, gapy 2",
				"[right][grow][right][grow]", ""));
		strip.add(new JLabel("Rows:"));        strip.add(rowsComputedLabel);
		strip.add(new JLabel("Worst conf:"));  strip.add(worstConfidenceLabel, "wrap");
		strip.add(new JLabel("Max fallback:")); strip.add(maxFallbackLabel, "wrap");
		strip.add(new JLabel("Max separation:")); strip.add(maxSeparationLabel);
		strip.add(new JLabel("Singularities:")); strip.add(singularityLabel, "wrap");
		strip.add(tableStatusLabel, "span 2, growx, wrap");
		p.add(strip, "growx, wrap");

		diagnosticsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		diagnosticsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		diagnosticsTable.setFillsViewportHeight(true);
		diagnosticsTable.getSelectionModel().addListSelectionListener(e -> updateSelectedRowDetails());
		JScrollPane tableScroll = new JScrollPane(diagnosticsTable);
		tableScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		p.add(tableScroll, "grow, push, wrap");

		selectedRowDetails.setText(EMPTY_PROMPT);
		p.add(new JScrollPane(selectedRowDetails), "growx, h 140!");

		return p;
	}

	// ─── wiring ───────────────────────────────────────────────────────────────

	private void wireEvents() {
		simulation.getOptions().addChangeListener(
				e -> SwingUtilities.invokeLater(this::refreshFromModel));

		bodyPathlineSpinner.addChangeListener(e -> {
			if (loadingSettings) return;
			applyPathlineCounts();
		});
		finPathlineSpinner.addChangeListener(e -> {
			if (loadingSettings) return;
			applyPathlineCounts();
		});
		applyDesignDefaultsButton.addActionListener(e -> applyDesignDefaults());

		ChangeListener sweepListener = e -> {
			if (loadingSettings) return;
			applySweepInputs();
			updateRowCountEstimate();
		};
		machMinSpinner.addChangeListener(sweepListener);
		machMaxSpinner.addChangeListener(sweepListener);
		machStepSpinner.addChangeListener(sweepListener);
		aoaMinSpinner.addChangeListener(sweepListener);
		aoaMaxSpinner.addChangeListener(sweepListener);
		aoaStepSpinner.addChangeListener(sweepListener);
		thetaSpinner.addChangeListener(sweepListener);
		plumeSpinner.addChangeListener(sweepListener);
	}

	private void applyPathlineCounts() {
		RomSettings settings = simulation.getOptions().getRomSettings().copy();
		settings.setBodyMeridianSeedCount((int) spinnerInt(bodyPathlineSpinner));
		settings.setFinSurfaceSeedCount((int) spinnerInt(finPathlineSpinner));
		simulation.getOptions().setRomSettings(settings);
		designDefaultsInitialized = true;
		GeometryFeatures geometry = geometryFeatureExtractor.extract(activeConfiguration());
		RecommendedPathlineCounts recommendation = recommendPathlineCounts(geometry);
		updateDesignDefaultHint(settings, recommendation);
		refreshSeedPreview();
	}

	private void applySweepInputs() {
		RomSettings settings = simulation.getOptions().getRomSettings().copy();
		settings.setPreviewMachMin(spinnerValue(machMinSpinner));
		settings.setPreviewMachMax(spinnerValue(machMaxSpinner));
		settings.setPreviewMachStep(spinnerValue(machStepSpinner));
		settings.setPreviewAoADegMin(spinnerValue(aoaMinSpinner));
		settings.setPreviewAoADegMax(spinnerValue(aoaMaxSpinner));
		settings.setPreviewAoADegStep(spinnerValue(aoaStepSpinner));
		settings.setPreviewThetaDeg(spinnerValue(thetaSpinner));
		settings.setPreviewPlumeState(spinnerValue(plumeSpinner));
		simulation.getOptions().setRomSettings(settings);
	}

	private void applyDesignDefaults() {
		GeometryFeatures geometry = geometryFeatureExtractor.extract(activeConfiguration());
		RecommendedPathlineCounts recommendation = recommendPathlineCounts(geometry);
		loadingSettings = true;
		bodyPathlineSpinner.setValue(recommendation.bodySeedCount);
		finPathlineSpinner.setValue(recommendation.finSeedCount);
		loadingSettings = false;
		designDefaultsInitialized = true;
		applyPathlineCounts();
	}

	// ─── refresh ─────────────────────────────────────────────────────────────

	private void refreshFromModel() {
		RomSettings settings = simulation.getOptions().getRomSettings();
		GeometryFeatures geometry = geometryFeatureExtractor.extract(activeConfiguration());
		RecommendedPathlineCounts recommendation = recommendPathlineCounts(geometry);
		if (!designDefaultsInitialized && shouldInitializeDesignDefaults(settings, recommendation)) {
			RomSettings updated = settings.copy();
			updated.setBodyMeridianSeedCount(recommendation.bodySeedCount);
			updated.setFinSurfaceSeedCount(recommendation.finSeedCount);
			simulation.getOptions().setRomSettings(updated);
			settings = updated;
			designDefaultsInitialized = true;
		}

		romStatusValue.setText(settings.isEnabled() ? "Enabled" : "Disabled");
		modeValue.setText(formatMode(settings.getMode()));
		fallbackValue.setText(formatFallback(settings.getFallbackMode()));

		int totalFins = geometry.getFins().stream().mapToInt(FinGeometry::getFinCount).sum();
		int finSets = geometry.getFins().size();
		finCountValue.setText(totalFins + (finSets > 0
				? String.format("  (%d set%s)", finSets, finSets == 1 ? "" : "s") : ""));
		geometryHashValue.setText(shortHash(geometry.getGeometryHash()));
		bodyLengthValue.setText(formatMeters(geometry.getBodyLength()));
		maxDiameterValue.setText(formatMeters(geometry.getMaxDiameter()));
		shoulderValue.setText(geometry.getShoulderCount() + " / " + geometry.getBoattailCount());

		loadingSettings = true;
		bodyPathlineSpinner.setValue(settings.getBodyMeridianSeedCount());
		finPathlineSpinner.setValue(settings.getFinSurfaceSeedCount());
		machMinSpinner.setValue(settings.getPreviewMachMin());
		machMaxSpinner.setValue(settings.getPreviewMachMax());
		machStepSpinner.setValue(settings.getPreviewMachStep());
		aoaMinSpinner.setValue(settings.getPreviewAoADegMin());
		aoaMaxSpinner.setValue(settings.getPreviewAoADegMax());
		aoaStepSpinner.setValue(settings.getPreviewAoADegStep());
		thetaSpinner.setValue(settings.getPreviewThetaDeg());
		plumeSpinner.setValue(settings.getPreviewPlumeState());
		loadingSettings = false;

		updateDesignDefaultHint(settings, recommendation);
		refreshSeedPreview();
		updateRowCountEstimate();
	}

	private void refreshSeedPreview() {
		RomSettings settings = simulation.getOptions().getRomSettings();
		GeometryFeatures geometry = geometryFeatureExtractor.extract(activeConfiguration());
		List<PathlineSeed> seeds = pathlineSeeder.createSeeds(geometry, settings);

		int totalFins = geometry.getFins().stream().mapToInt(FinGeometry::getFinCount).sum();
		seedCountLabel.setText(String.format(Locale.ROOT,
				"%d total seeds  (body: %d  |  fin per set: %d  |  %d individual fins)",
				seeds.size(),
				settings.getBodyMeridianSeedCount(),
				settings.getFinSurfaceSeedCount(),
				totalFins));

		seedTableModel.setRowCount(0);
		for (PathlineSeed seed : seeds) {
			seedTableModel.addRow(new Object[]{
					formatSeedFamily(seed),
					seed.getComponentName(),
					String.format(Locale.ROOT, "%.3f", seed.getX()),
					String.format(Locale.ROOT, "%.4f", seed.getAreaWeight())
			});
		}
	}

	private void updateRowCountEstimate() {
		RomSettings settings = simulation.getOptions().getRomSettings();
		int rows = RomPreviewDiagnosticsRunner.estimatedRowCount(settings);
		int cap = settings.getPreviewMaxRows();
		rowCountLabel.setText(String.format(Locale.ROOT, "%d  (cap: %d)", rows, cap));
		if (rows > cap) {
			rowCountLabel.setForeground(Color.RED);
		} else {
			rowCountLabel.setForeground(UIDefaultForeground());
		}
	}

	private static Color UIDefaultForeground() {
		Color c = javax.swing.UIManager.getColor("Label.foreground");
		return c != null ? c : Color.BLACK;
	}

	// ─── sweep execution ──────────────────────────────────────────────────────

	private void startSweep() {
		if (activeWorker != null) {
			return;
		}
		applySweepInputs();
		RomSettings settings = simulation.getOptions().getRomSettings();
		int rows = RomPreviewDiagnosticsRunner.estimatedRowCount(settings);
		if (rows > settings.getPreviewMaxRows()) {
			tableStatusLabel.setText(String.format(Locale.ROOT,
					"Sweep of %d rows exceeds the row cap of %d. Narrow Mach or AoA range, or coarsen the step.",
					rows, settings.getPreviewMaxRows()));
			tableStatusLabel.setForeground(Color.RED);
			return;
		}
		tableStatusLabel.setForeground(UIDefaultForeground());
		tableStatusLabel.setText("Running...");

		diagTableModel.setRowCount(0);
		exportCsvButton.setEnabled(false);
		progressBar.setValue(0);
		progressBar.setMaximum(rows);
		progressBar.setString("0 / " + rows);
		progressBar.setStringPainted(true);

		final FlightConfiguration configuration = activeConfiguration();
		final RomSettings sweepSettings = settings.copy();
		final AtomicBoolean cancel = new AtomicBoolean(false);
		activeCancel = cancel;

		setSweepRunning(true);

		activeWorker = new SwingWorker<>() {
			@Override
			protected RomPreviewDiagnosticsRunner.Result doInBackground() {
				return new RomPreviewDiagnosticsRunner().run(
						configuration, simulation.getOptions(), sweepSettings, cancel,
						(computed, total) -> setProgress(Math.min(100,
								(int) Math.round(100.0 * computed / Math.max(1, total)))));
			}

			@Override
			protected void done() {
				try {
					RomPreviewDiagnosticsRunner.Result result = get();
					applyResult(result);
				} catch (Exception ex) {
					tableStatusLabel.setText("Sweep failed: " + ex.getMessage());
					tableStatusLabel.setForeground(Color.RED);
				} finally {
					activeWorker = null;
					activeCancel = null;
					setSweepRunning(false);
				}
			}
		};
		activeWorker.addPropertyChangeListener(evt -> {
			if ("progress".equals(evt.getPropertyName())) {
				int pct = (int) evt.getNewValue();
				int computed = (int) Math.round(pct / 100.0 * progressBar.getMaximum());
				progressBar.setValue(computed);
				progressBar.setString(computed + " / " + progressBar.getMaximum());
			}
		});
		activeWorker.execute();
	}

	private void cancelSweep() {
		if (activeCancel != null) {
			activeCancel.set(true);
			tableStatusLabel.setText("Cancelling...");
		}
	}

	private void applyResult(RomPreviewDiagnosticsRunner.Result result) {
		lastResult = result;
		diagTableModel.setRowCount(0);
		for (RomPreviewSample s : result.getSamples()) {
			diagTableModel.addRow(toRow(s));
		}

		rowsComputedLabel.setText(String.format(Locale.ROOT, "%d / %d",
				result.getComputedRows(), result.getRequestedRows()));
		worstConfidenceLabel.setText(String.format(Locale.ROOT, "%.1f%%", result.getWorstConfidence() * 100.0));
		maxFallbackLabel.setText(String.format(Locale.ROOT, "%.1f%%", result.getMaxFallbackPercent()));
		maxSeparationLabel.setText(String.format(Locale.ROOT, "%.1f%%", result.getMaxSeparationPercent()));
		singularityLabel.setText(String.format(Locale.ROOT, "%d sing / %d err",
				result.getSingularityCount(), result.getErrorCount()));
		tableStatusLabel.setText(result.getStatusMessage());
		if (result.isRowCapExceeded() || result.getErrorCount() > 0 || result.getSingularityCount() > 0) {
			tableStatusLabel.setForeground(Color.RED);
		} else if (result.isCancelled()) {
			tableStatusLabel.setForeground(new Color(180, 100, 0));
		} else {
			tableStatusLabel.setForeground(UIDefaultForeground());
		}

		exportCsvButton.setEnabled(!result.getSamples().isEmpty());

		if (result.getSamples().isEmpty()) {
			selectedRowDetails.setText(result.getStatusMessage().isEmpty() ? EMPTY_PROMPT : result.getStatusMessage());
		} else {
			diagnosticsTable.getSelectionModel().setSelectionInterval(0, 0);
		}
	}

	private void updateSelectedRowDetails() {
		int row = diagnosticsTable.getSelectedRow();
		if (row < 0 || lastResult == null || row >= lastResult.getSamples().size()) {
			return;
		}
		RomPreviewSample s = lastResult.getSamples().get(row);
		StringBuilder sb = new StringBuilder();
		sb.append(String.format(Locale.ROOT,
				"Mach=%.3f  AoA=%.2f°  theta=%.2f°  plume=%.2f%n",
				s.getMach(), s.getAoaDeg(), s.getThetaDeg(), s.getPlumeState()));
		sb.append("Status: ").append(s.getStatus()).append('\n');
		sb.append("Regime: ").append(s.getRegime()).append('\n');
		sb.append(String.format(Locale.ROOT,
				"Confidence: %.3f   Fallback: %.1f%%   Separation: %.1f%%%n",
				s.getConfidence(), s.getFallbackWeight() * 100.0, s.getSeparationFraction() * 100.0));
		if (!s.getNotes().isBlank()) {
			sb.append("Notes: ").append(s.getNotes()).append('\n');
		}
		if (!s.getWarnings().isEmpty()) {
			sb.append("Warnings:\n");
			for (String w : s.getWarnings()) {
				sb.append("  • ").append(w).append('\n');
			}
		}
		selectedRowDetails.setText(sb.toString());
		selectedRowDetails.setCaretPosition(0);
	}

	private void setSweepRunning(boolean running) {
		generateButton.setEnabled(!running);
		cancelButton.setEnabled(running);
		cancelButton.setVisible(running);
		machMinSpinner.setEnabled(!running);
		machMaxSpinner.setEnabled(!running);
		machStepSpinner.setEnabled(!running);
		aoaMinSpinner.setEnabled(!running);
		aoaMaxSpinner.setEnabled(!running);
		aoaStepSpinner.setEnabled(!running);
		thetaSpinner.setEnabled(!running);
		plumeSpinner.setEnabled(!running);
		bodyPathlineSpinner.setEnabled(!running);
		finPathlineSpinner.setEnabled(!running);
		applyDesignDefaultsButton.setEnabled(!running);
		progressBar.setVisible(running);
	}

	private static Object[] toRow(RomPreviewSample s) {
		Object[] row = new Object[DIAG_COLUMNS.length];
		int i = 0;
		row[i++] = fmt(s.getMach(), 3);
		row[i++] = fmt(s.getAoaDeg(), 2);
		row[i++] = fmt(s.getThetaDeg(), 2);
		row[i++] = fmt(s.getBetaDeg(), 2);
		row[i++] = fmt(s.getPlumeState(), 2);
		row[i++] = s.getRegime();
		row[i++] = fmt(s.getCdLegacy(), 4);
		row[i++] = fmt(s.getCdRom(), 4);
		row[i++] = fmt(s.getCdFinal(), 4);
		row[i++] = fmt(s.getCnLegacy(), 4);
		row[i++] = fmt(s.getCnRom(), 4);
		row[i++] = fmt(s.getCnFinal(), 4);
		row[i++] = fmt(s.getCmLegacy(), 4);
		row[i++] = fmt(s.getCmRom(), 4);
		row[i++] = fmt(s.getCmFinal(), 4);
		row[i++] = fmt(s.getCpxFinalMeters(), 4);
		row[i++] = fmt(s.getConfidence(), 3);
		row[i++] = fmt(s.getFallbackWeight(), 3);
		row[i++] = Boolean.toString(s.isFallbackUsed());
		row[i++] = fmt(s.getSeparationFraction(), 3);
		row[i++] = Integer.toString(s.getSeedCount());
		row[i++] = Integer.toString(s.getMarchingSteps());
		row[i++] = Integer.toString(s.getTransitionedCount());
		row[i++] = Integer.toString(s.getSeparatedCount());
		row[i++] = fmt(s.getMeanStiffness(), 4);
		row[i++] = fmt(s.getMaxStiffness(), 4);
		row[i++] = fmt(s.getMeanCf(), 5);
		row[i++] = fmt(s.getMinEdgeCp(), 4);
		row[i++] = fmt(s.getMaxEdgeCp(), 4);
		row[i++] = fmt(s.getMinEdgeMach(), 3);
		row[i++] = fmt(s.getMaxEdgeMach(), 3);
		row[i++] = s.getStatus().name();
		row[i++] = s.getNotes();
		row[i++] = String.join(" | ", s.getWarnings());
		return row;
	}

	private static String fmt(double v, int decimals) {
		if (!Double.isFinite(v)) {
			return "";
		}
		return String.format(Locale.ROOT, "%." + decimals + "f", v);
	}

	// ─── CSV export ──────────────────────────────────────────────────────────

	private void exportCsv() {
		if (diagTableModel.getRowCount() == 0) {
			return;
		}
		JFileChooser chooser = new JFileChooser();
		chooser.setSelectedFile(new File("rom-preview-diagnostics.csv"));
		int returnVal = chooser.showSaveDialog(this);
		if (returnVal != JFileChooser.APPROVE_OPTION) {
			return;
		}
		File file = chooser.getSelectedFile();
		try (Writer w = new BufferedWriter(Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8))) {
			writeCsv(diagTableModel, w);
		} catch (IOException ex) {
			JOptionPane.showMessageDialog(this,
					"Failed to write CSV: " + ex.getMessage(),
					"Export error",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	static void writeCsv(TableModel model, Writer writer) throws IOException {
		int cols = model.getColumnCount();
		for (int c = 0; c < cols; c++) {
			if (c > 0) writer.write(',');
			writer.write(escapeCsv(model.getColumnName(c)));
		}
		writer.write('\n');
		for (int r = 0; r < model.getRowCount(); r++) {
			for (int c = 0; c < cols; c++) {
				if (c > 0) writer.write(',');
				Object v = model.getValueAt(r, c);
				writer.write(v == null ? "" : escapeCsv(v.toString()));
			}
			writer.write('\n');
		}
	}

	private static String escapeCsv(String v) {
		if (v == null || v.isEmpty()) {
			return "";
		}
		boolean needsQuote = v.indexOf(',') >= 0 || v.indexOf('"') >= 0
				|| v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0;
		String escaped = v.replace("\"", "\"\"");
		return needsQuote ? "\"" + escaped + "\"" : escaped;
	}

	// ─── helpers ─────────────────────────────────────────────────────────────

	private FlightConfiguration activeConfiguration() {
		return simulation.getRocket().getFlightConfiguration(simulation.getFlightConfigurationId());
	}

	private static JTextArea createTextArea(int rows) {
		JTextArea area = new JTextArea(rows, 1);
		area.setEditable(false);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		return area;
	}

	private static String formatMeters(double v) {
		return String.format(Locale.ROOT, "%.4f m", v);
	}

	private static String shortHash(String hash) {
		if (hash == null || hash.isBlank()) return "–";
		return hash.length() <= 12 ? hash : hash.substring(0, 12);
	}

	private static String formatMode(RomMode mode) {
		if (mode == null) return "Standard";
		String raw = mode.name().toLowerCase(Locale.ROOT);
		return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
	}

	private static String formatFallback(RomFallbackMode m) {
		if (m == null) return "Blend";
		return switch (m) {
			case BLEND          -> "Blend";
			case BARROWMAN_ONLY -> "Barrowman only";
			case FORCE_ROM      -> "Force ROM";
		};
	}

	private static String formatSeedFamily(PathlineSeed seed) {
		return switch (seed.getFamily()) {
			case BODY_MERIDIAN        -> "Body";
			case FIN_SURFACE          -> "Fin";
			case AFT_BODY_PLACEHOLDER -> "Aft body";
		};
	}

	private static JButton createInfoButton(String title, String body) {
		JButton infoButton = new JButton("(i)");
		infoButton.setFocusable(false);
		infoButton.setMargin(new Insets(1, 4, 1, 4));
		infoButton.setToolTipText("Show " + title + " details");
		infoButton.addActionListener(e -> showInfoMenu(infoButton, title, body));
		return infoButton;
	}

	private static void showInfoMenu(JButton owner, String title, String body) {
		JPopupMenu menu = new JPopupMenu();
		JPanel content = new JPanel(new MigLayout("insets 8, gapy 4, wrap 1", "[grow,fill]", ""));
		JLabel header = new JLabel(title);
		header.setFont(header.getFont().deriveFont(Font.BOLD));
		JTextArea bodyArea = new JTextArea(body);
		bodyArea.setEditable(false);
		bodyArea.setFocusable(false);
		bodyArea.setOpaque(false);
		bodyArea.setLineWrap(true);
		bodyArea.setWrapStyleWord(true);
		bodyArea.setColumns(44);
		content.add(header, "growx");
		content.add(bodyArea, "growx");
		menu.add(content);
		menu.show(owner, 0, owner.getHeight());
	}

	private void updateDesignDefaultHint(RomSettings settings, RecommendedPathlineCounts recommendation) {
		defaultHintLabel.setText(String.format(Locale.ROOT,
				"<html>Design suggestion: body %d, fin %d per set (fineness %.1f, fins %d). Current: %d / %d.</html>",
				recommendation.bodySeedCount,
				recommendation.finSeedCount,
				recommendation.finenessRatio,
				recommendation.totalFins,
				settings.getBodyMeridianSeedCount(),
				settings.getFinSurfaceSeedCount()));
	}

	private static boolean shouldInitializeDesignDefaults(RomSettings settings, RecommendedPathlineCounts recommendation) {
		RomMode mode = settings.getMode() != null ? settings.getMode() : RomMode.STANDARD;
		boolean usesModeDefaults = settings.getBodyMeridianSeedCount() == mode.getBodySeedCount()
				&& settings.getFinSurfaceSeedCount() == mode.getFinSeedCount();
		boolean recommendationDiffers = settings.getBodyMeridianSeedCount() != recommendation.bodySeedCount
				|| settings.getFinSurfaceSeedCount() != recommendation.finSeedCount;
		return usesModeDefaults && recommendationDiffers;
	}

	private static RecommendedPathlineCounts recommendPathlineCounts(GeometryFeatures geometry) {
		double maxDiameter = Math.max(1e-3, geometry.getMaxDiameter());
		double finenessRatio = geometry.getBodyLength() / maxDiameter;
		int totalFins = geometry.getFins().stream().mapToInt(FinGeometry::getFinCount).sum();
		int finSets = geometry.getFins().size();
		int contourComplexity = geometry.getShoulderCount() + geometry.getBoattailCount()
				+ Math.min(4, geometry.getSlopeChangeCount() / 4);

		double averageSpanToChord = geometry.getFins().stream()
				.mapToDouble(fin -> {
					double avgChord = 0.5 * (fin.getRootChord() + fin.getTipChord());
					return fin.getSpan() / Math.max(1e-3, avgChord);
				})
				.average()
				.orElse(0.0);

		int bodySeedCount = 8
				+ (int) Math.round(Math.min(18.0, finenessRatio * 0.7))
				+ contourComplexity
				+ (totalFins >= 4 ? 1 : 0);
		bodySeedCount = clampInt(bodySeedCount, 8, 40);

		int finSeedCount = 1;
		if (totalFins > 0) {
			finSeedCount = 2
					+ (int) Math.round(Math.min(3.0, averageSpanToChord))
					+ (finSets > 1 ? 1 : 0)
					+ (totalFins >= 4 ? 1 : 0);
		}
		finSeedCount = clampInt(finSeedCount, 1, 12);

		return new RecommendedPathlineCounts(bodySeedCount, finSeedCount, finenessRatio, totalFins);
	}

	private static int clampInt(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static final class RecommendedPathlineCounts {
		private final int bodySeedCount;
		private final int finSeedCount;
		private final double finenessRatio;
		private final int totalFins;

		private RecommendedPathlineCounts(int bodySeedCount, int finSeedCount, double finenessRatio, int totalFins) {
			this.bodySeedCount = bodySeedCount;
			this.finSeedCount = finSeedCount;
			this.finenessRatio = finenessRatio;
			this.totalFins = totalFins;
		}
	}

	private static double spinnerValue(JSpinner s) {
		Object v = s.getValue();
		return v instanceof Number n ? n.doubleValue() : 0.0;
	}

	private static int spinnerInt(JSpinner s) {
		Object v = s.getValue();
		return v instanceof Number n ? n.intValue() : 0;
	}

	static String[] diagnosticsColumns() {
		return Arrays.copyOf(DIAG_COLUMNS, DIAG_COLUMNS.length);
	}
}

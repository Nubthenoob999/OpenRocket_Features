package info.openrocket.swing.gui.simulation;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

import info.openrocket.core.aerodynamics.rom.RomFallbackMode;
import info.openrocket.core.aerodynamics.rom.RomMode;
import info.openrocket.core.aerodynamics.rom.RomSettings;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.tuning.PhaseThreeBatchRunner;
import info.openrocket.core.tuning.PhaseThreeSingleSimulationRunner;
import info.openrocket.core.tuning.PhaseThreeTuningPaths;
import info.openrocket.core.tuning.PhaseTwoBatchResult;
import net.miginfocom.swing.MigLayout;

class RomTuningPanel extends SimulationScrollablePanel {
	private static final long serialVersionUID = -463962520799963637L;

	private static final String SUMMARY = "phase-two-summary.csv";
	private static final String QUANTITIES = "phase-two-quantities.csv";
	private static final String IMPROVEMENTS = "phase-two-improvements.csv";
	private static final String ANALYSIS = "phase-three-analysis.csv";
	private static final String RESIDUALS = "phase-three-drag-residuals.csv";
	private static final String DEFAULT_REPORTS = "build/reports/phase-three";

	private final OpenRocketDocument document;
	private final Simulation simulation;
	private final JLabel documentValue = new JLabel("-");
	private final JLabel simulationValue = new JLabel("-");
	private final JLabel romStateValue = new JLabel("-");
	private final JLabel romModeValue = new JLabel("-");
	private final JLabel fallbackValue = new JLabel("-");
	private final JLabel diagnosticsValue = new JLabel("-");
	private final JLabel seedPlanValue = new JLabel("-");
	private final JLabel trustedEnvelopeValue = new JLabel("-");
	private final JLabel runtimeValue = new JLabel("-");
	private final JLabel datasetClassValue = new JLabel("-");
	private final JLabel alignedTimingValue = new JLabel("-");
	private final JLabel truthSourceValue = new JLabel("-");
	private final JLabel alignmentChannelValue = new JLabel("-");
	private final JLabel alignmentLagValue = new JLabel("-");
	private final JLabel alignmentQualityValue = new JLabel("-");
	private final JLabel machValue = new JLabel("-");
	private final JLabel statusLabel = new JLabel("Ready");
	private final JTextField referenceField = new JTextField();
	private final JTextField configField = new JTextField();
	private final JTextField reportsField = new JTextField();
	private final JButton runButton = new JButton("Run pathline comparison");
	private final JButton runBatchButton = new JButton("Run pathline batch");
	private final JButton refreshButton = new JButton("Refresh");
	private final JButton openFolderButton = new JButton("Open Reports Folder");
	private final JTabbedPane resultsTabs = new JTabbedPane();
	private final JTextArea executionLogArea = new JTextArea();
	private final DefaultListModel<Path> reportLogListModel = new DefaultListModel<>();
	private final JList<Path> reportLogList = new JList<>(reportLogListModel);
	private final JTextArea reportLogTextArea = new JTextArea();

	private SwingWorker<PhaseTwoBatchResult, String> runWorker;

	RomTuningPanel(OpenRocketDocument document, Simulation simulation) {
		super(new MigLayout("fill, insets 6, gap 8 8, wrap 1", "[grow,fill]", "[][][grow][]"));
		this.document = document;
		this.simulation = simulation;

		configureDefaults();
		configureLogsView();

		add(buildCurrentSimulationPanel(), "growx");
		add(buildRunnerPanel(), "growx");

		addOrReplaceTab("Summary", buildCsvPanel(getReportsPath().resolve(SUMMARY)));
		addOrReplaceTab("Quantities", buildCsvPanel(getReportsPath().resolve(QUANTITIES)));
		addOrReplaceTab("Improvements", buildCsvPanel(getReportsPath().resolve(IMPROVEMENTS)));
		addOrReplaceTab("Analysis", buildCsvPanel(getReportsPath().resolve(ANALYSIS)));
		addOrReplaceTab("Residuals", buildCsvPanel(getReportsPath().resolve(RESIDUALS)));
		addOrReplaceTab("Logs", buildLogsPanel());
		add(resultsTabs, "grow, push");

		add(statusLabel, "growx");

		wireEvents();
		refreshCurrentSimulationState();
		refreshResults();
	}

	private JPanel buildCurrentSimulationPanel() {
		JPanel panel = new JPanel(new MigLayout("fillx, insets 6, gapx 8, gapy 6", "[right][grow][right][grow]", ""));
		panel.setBorder(BorderFactory.createTitledBorder("Pathline ROM Analysis"));

		panel.add(new JLabel("Document:"));
		panel.add(documentValue);
		panel.add(new JLabel("Simulation:"));
		panel.add(simulationValue, "wrap");

		panel.add(new JLabel("ROM state:"));
		panel.add(romStateValue);
		panel.add(new JLabel("Mode:"));
		panel.add(romModeValue, "wrap");

		panel.add(new JLabel("Fallback:"));
		panel.add(fallbackValue);
		panel.add(new JLabel("Diagnostics:"));
		panel.add(diagnosticsValue, "wrap");

		panel.add(new JLabel("Seed plan:"));
		panel.add(seedPlanValue);
		panel.add(new JLabel("Trusted envelope:"));
		panel.add(trustedEnvelopeValue, "wrap");

		panel.add(new JLabel("Runtime:"));
		panel.add(runtimeValue);
		panel.add(new JLabel("Max Mach:"));
		panel.add(machValue, "wrap");

		panel.add(new JLabel("Dataset class:"));
		panel.add(datasetClassValue);
		panel.add(new JLabel("Aligned dt(apogee):"));
		panel.add(alignedTimingValue, "wrap");

		panel.add(new JLabel("Truth source:"));
		panel.add(truthSourceValue);
		panel.add(new JLabel("Alignment channel:"));
		panel.add(alignmentChannelValue, "wrap");

		panel.add(new JLabel("Alignment lag:"));
		panel.add(alignmentLagValue);
		panel.add(new JLabel("Alignment quality:"));
		panel.add(alignmentQualityValue, "wrap");

		JLabel note = new JLabel("<html>This analysis compares only the current simulation against one reference CSV "
				+ "while forcing the pathline ROM runtime for this test flow. "
				+ "It runs in-process inside OpenRocket, so it works in the packaged app without Gradle.</html>");
		panel.add(note, "span 4, growx");
		return panel;
	}

	private JPanel buildRunnerPanel() {
		JPanel outer = new JPanel(new BorderLayout(6, 6));
		outer.setBorder(BorderFactory.createTitledBorder("Pathline Batch Runner"));

		JPanel form = new JPanel(new MigLayout("fillx, insets 0, gapx 8, gapy 6", "[right][grow,fill][]", ""));
		form.add(new JLabel("Reference CSV:"));
		form.add(referenceField, "growx");
		JButton browseReferenceButton = new JButton("Browse");
		browseReferenceButton.addActionListener(e -> browseForFile(referenceField));
		form.add(browseReferenceButton, "wrap");

		form.add(new JLabel("Batch config:"));
		form.add(configField, "growx");
		JButton browseConfigButton = new JButton("Browse");
		browseConfigButton.addActionListener(e -> browseForFile(configField));
		form.add(browseConfigButton, "wrap");

		form.add(new JLabel("Reports dir:"));
		form.add(reportsField, "growx");
		JButton browseReportsButton = new JButton("Browse");
		browseReportsButton.addActionListener(e -> browseForDirectory(reportsField));
		form.add(browseReportsButton, "wrap");

		JPanel buttons = new JPanel(new MigLayout("insets 0, gapx 6", "[][][]", ""));
		buttons.add(runButton);
		buttons.add(runBatchButton);
		buttons.add(refreshButton);
		buttons.add(openFolderButton);

		outer.add(form, BorderLayout.CENTER);
		outer.add(buttons, BorderLayout.SOUTH);
		return outer;
	}

	private void wireEvents() {
		runButton.addActionListener(e -> runComparison());
		runBatchButton.addActionListener(e -> runBatchComparison());
		refreshButton.addActionListener(e -> refreshResults());
		openFolderButton.addActionListener(e -> openReportsFolder());
	}

	private void configureDefaults() {
		FileChooserSeed seed = defaultSeed();
		referenceField.setText(seed.reference().toString());
		reportsField.setText(seed.reports().toString());
		Path defaultConfig = PhaseThreeTuningPaths.findDefaultConfig();
		configField.setText(defaultConfig != null ? defaultConfig.toString() : seed.reference().toString());
		documentValue.setText(document != null && document.getFile() != null
				? document.getFile().getAbsolutePath()
				: "Unsaved document");
	}

	private FileChooserSeed defaultSeed() {
		Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
		Path reports = PhaseThreeTuningPaths.defaultReportsDirectory();
		Path reference = document != null && document.getFile() != null && document.getFile().getParentFile() != null
				? document.getFile().getParentFile().toPath().toAbsolutePath().normalize()
				: root;
		return new FileChooserSeed(reference, reports);
	}

	private void configureLogsView() {
		executionLogArea.setEditable(false);
		executionLogArea.setLineWrap(false);
		reportLogTextArea.setEditable(false);
		reportLogTextArea.setLineWrap(false);
		reportLogList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		reportLogList.addListSelectionListener(e -> {
			if (e.getValueIsAdjusting()) {
				return;
			}
			Path selected = reportLogList.getSelectedValue();
			if (selected != null) {
				reportLogTextArea.setText(readText(selected));
				reportLogTextArea.setCaretPosition(0);
			}
		});
	}

	private void refreshCurrentSimulationState() {
		simulationValue.setText(simulation.getName());
		RomSettings settings = simulation.getOptions().getRomSettings();
		romStateValue.setText(settings.isEnabled() ? "Enabled" : "Disabled");
		romModeValue.setText(formatRomMode(settings.getMode()));
		fallbackValue.setText(formatRomFallbackMode(settings.getFallbackMode()));
		diagnosticsValue.setText(settings.isDiagnosticsEnabled() ? "Snapshot logging enabled" : "Snapshot logging disabled");
		seedPlanValue.setText(settings.getBodyMeridianSeedCount() + " body meridian, "
				+ settings.getFinSurfaceSeedCount() + " per fin surface");
		trustedEnvelopeValue.setText(String.format(Locale.ROOT,
				"Mach 1 +/- %.2f, high-angle %.1f deg, separation %.0f%%",
				settings.getTransonicBandHalfWidth(),
				settings.getHighAngleDeg(),
				100.0 * settings.getMaxTrustedSeparationFraction()));
		runtimeValue.setText(buildRuntimeSummary());
	}

	private String buildRuntimeSummary() {
		boolean hasLegacySurface = simulation.getOptions().getRomDragSurface() != null
				|| simulation.getOptions().getRomAeroSurface4D() != null;
		if (hasLegacySurface) {
			return "Test runs force pathline ROM + FORCE_ROM fallback; legacy ROM surfaces ignored";
		}
		return "Test runs force pathline ROM + FORCE_ROM fallback";
	}

	private void runComparison() {
		Path referenceCsv = getReferencePath();
		Path reportsDir = getReportsPath();
		if (!Files.exists(referenceCsv)) {
			statusLabel.setText("Reference CSV not found: " + referenceCsv);
			return;
		}

		executionLogArea.setText("");
		setControlsEnabled(false);
		statusLabel.setText("Running pathline comparison...");
		runWorker = new SwingWorker<>() {
			@Override
			protected PhaseTwoBatchResult doInBackground() throws Exception {
				publish("Comparing current simulation against " + referenceCsv);
				return PhaseThreeSingleSimulationRunner.compareAndWrite(document, simulation, referenceCsv, reportsDir);
			}

			@Override
			protected void process(List<String> chunks) {
				for (String line : chunks) {
					executionLogArea.append(line);
					executionLogArea.append(System.lineSeparator());
				}
				executionLogArea.setCaretPosition(executionLogArea.getDocument().getLength());
			}

			@Override
			protected void done() {
				setControlsEnabled(true);
				try {
					get();
					publishLine("Pathline comparison completed successfully.");
					refreshResults();
					statusLabel.setText("Pathline comparison completed successfully.");
				} catch (Exception ex) {
					publishLine("Pathline comparison failed: " + ex.getMessage());
					statusLabel.setText("Pathline comparison failed: " + ex.getMessage());
					resultsTabs.setSelectedIndex(resultsTabs.indexOfTab("Logs"));
				} finally {
					runWorker = null;
				}
			}
		};
		runWorker.execute();
	}

	private void runBatchComparison() {
		Path configPath = getConfigPath();
		Path reportsDir = getReportsPath();
		if (!Files.exists(configPath)) {
			statusLabel.setText("Batch config not found: " + configPath);
			return;
		}

		executionLogArea.setText("");
		setControlsEnabled(false);
		statusLabel.setText("Running pathline batch...");
		runWorker = new SwingWorker<>() {
			@Override
			protected PhaseTwoBatchResult doInBackground() throws Exception {
				publish("Running Phase 3 batch config " + configPath);
				return PhaseThreeBatchRunner.runFromConfig(configPath, reportsDir);
			}

			@Override
			protected void process(List<String> chunks) {
				for (String line : chunks) {
					executionLogArea.append(line);
					executionLogArea.append(System.lineSeparator());
				}
				executionLogArea.setCaretPosition(executionLogArea.getDocument().getLength());
			}

			@Override
			protected void done() {
				setControlsEnabled(true);
				try {
					PhaseTwoBatchResult result = get();
					publishLine("Pathline batch completed successfully.");
					publishLine("Datasets processed: " + result.getDatasets().size());
					refreshResults();
					statusLabel.setText("Pathline batch completed successfully.");
				} catch (Exception ex) {
					publishLine("Pathline batch failed: " + ex.getMessage());
					statusLabel.setText("Pathline batch failed: " + ex.getMessage());
					resultsTabs.setSelectedIndex(resultsTabs.indexOfTab("Logs"));
				} finally {
					runWorker = null;
				}
			}
		};
		runWorker.execute();
	}

	private void publishLine(String line) {
		executionLogArea.append(line);
		executionLogArea.append(System.lineSeparator());
		executionLogArea.setCaretPosition(executionLogArea.getDocument().getLength());
	}

	private void refreshResults() {
		refreshCurrentSimulationState();
		Path reportsDir = getReportsPath();
		addOrReplaceTab("Summary", buildCsvPanel(reportsDir.resolve(SUMMARY)));
		addOrReplaceTab("Quantities", buildCsvPanel(reportsDir.resolve(QUANTITIES)));
		addOrReplaceTab("Improvements", buildCsvPanel(reportsDir.resolve(IMPROVEMENTS)));
		addOrReplaceTab("Analysis", buildCsvPanel(reportsDir.resolve(ANALYSIS)));
		addOrReplaceTab("Residuals", buildCsvPanel(reportsDir.resolve(RESIDUALS)));
		addOrReplaceTab("Logs", buildLogsPanel());
		refreshOverview(reportsDir.resolve(ANALYSIS));
		statusLabel.setText("Loaded reports from " + reportsDir);
	}

	private void refreshOverview(Path analysisPath) {
		datasetClassValue.setText("-");
		alignedTimingValue.setText("-");
		truthSourceValue.setText("-");
		alignmentChannelValue.setText("-");
		alignmentLagValue.setText("-");
		alignmentQualityValue.setText("-");
		machValue.setText("-");
		if (!Files.exists(analysisPath)) {
			return;
		}
		try {
			CsvTable analysis = readCsv(analysisPath);
			if (analysis.rows().isEmpty()) {
				return;
			}
			if (analysis.rows().size() > 1) {
				long brokenCount = analysis.rows().stream()
						.filter(row -> "BROKEN".equalsIgnoreCase(csvValue(analysis, row, "datasetClass")))
						.count();
				double maxMach = analysis.rows().stream()
						.map(row -> parseDouble(csvValue(analysis, row, "candidateMaxMach")))
						.filter(Double::isFinite)
						.max(Double::compareTo)
						.orElse(Double.NaN);
				datasetClassValue.setText(analysis.rows().size() + " datasets (" + brokenCount + " broken)");
				truthSourceValue.setText("Batch config");
				alignmentChannelValue.setText("Mixed");
				alignmentLagValue.setText("Mixed");
				alignmentQualityValue.setText("Mixed");
				alignedTimingValue.setText("Batch summary");
				machValue.setText(Double.isFinite(maxMach) ? String.format(Locale.ROOT, "%.3f", maxMach) : "-");
				return;
			}
			List<String> row = analysis.rows().get(0);
			datasetClassValue.setText(csvValue(analysis, row, "datasetClass"));
			String dt = csvValue(analysis, row, "alignedApogeeTimeDeltaSec");
			alignedTimingValue.setText(dt.isBlank() ? "-" : dt + " s");
			truthSourceValue.setText(blankDash(csvValue(analysis, row, "truthSource")));
			alignmentChannelValue.setText(blankDash(csvValue(analysis, row, "alignmentChannel")));
			String lag = csvValue(analysis, row, "alignmentLagSec");
			alignmentLagValue.setText(lag.isBlank() ? "-" : lag + " s");
			alignmentQualityValue.setText(blankDash(csvValue(analysis, row, "alignmentQuality")));
			String mach = csvValue(analysis, row, "candidateMaxMach");
			machValue.setText(mach.isBlank() ? "-" : mach);
		} catch (IOException ex) {
			datasetClassValue.setText("Failed to load");
		}
	}

	private void addOrReplaceTab(String title, JPanel panel) {
		int index = resultsTabs.indexOfTab(title);
		if (index >= 0) {
			resultsTabs.setComponentAt(index, panel);
		} else {
			resultsTabs.addTab(title, panel);
		}
	}

	private static String blankDash(String value) {
		return value == null || value.isBlank() ? "-" : value;
	}

	private JPanel buildCsvPanel(Path csvPath) {
		JPanel panel = new JPanel(new BorderLayout());
		if (!Files.exists(csvPath)) {
			panel.add(new JLabel("File not found: " + csvPath), BorderLayout.NORTH);
			return panel;
		}

		DefaultTableModel model = new DefaultTableModel() {
			private static final long serialVersionUID = 1L;

			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};

		try {
			CsvTable csv = readCsv(csvPath);
			for (String header : csv.headers()) {
				model.addColumn(header);
			}
			for (List<String> row : csv.rows()) {
				model.addRow(row.toArray());
			}
		} catch (IOException ex) {
			panel.add(new JLabel("Failed to load " + csvPath.getFileName() + ": " + ex.getMessage()), BorderLayout.NORTH);
			return panel;
		}

		JTable table = new JTable(model);
		table.setAutoCreateRowSorter(true);
		table.setFillsViewportHeight(true);
		panel.add(new JScrollPane(table), BorderLayout.CENTER);
		return panel;
	}

	private JPanel buildLogsPanel() {
		refreshReportLogs();
		JTabbedPane logTabs = new JTabbedPane();
		logTabs.addTab("Execution", new JScrollPane(executionLogArea));
		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(reportLogList),
				new JScrollPane(reportLogTextArea));
		split.setResizeWeight(0.25);
		logTabs.addTab("Report Logs", split);
		JPanel panel = new JPanel(new BorderLayout());
		panel.add(logTabs, BorderLayout.CENTER);
		return panel;
	}

	private void refreshReportLogs() {
		reportLogListModel.clear();
		List<Path> logs = findLogs(getReportsPath());
		for (Path log : logs) {
			reportLogListModel.addElement(log);
		}
		if (!logs.isEmpty()) {
			reportLogList.setSelectedIndex(0);
		} else {
			reportLogTextArea.setText("No report log files found in " + getReportsPath());
		}
	}

	private void openReportsFolder() {
		Path reportsDir = getReportsPath();
		try {
			Files.createDirectories(reportsDir);
			if (Desktop.isDesktopSupported()) {
				Desktop.getDesktop().open(reportsDir.toFile());
				statusLabel.setText("Opened reports folder.");
			} else {
				statusLabel.setText("Desktop integration is not supported on this environment.");
			}
		} catch (IOException ex) {
			statusLabel.setText("Failed to open reports folder: " + ex.getMessage());
		}
	}

	private void browseForFile(JTextField targetField) {
		JFileChooser chooser = new JFileChooser(targetField.getText());
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		if (chooser.showOpenDialog(SwingUtilities.getWindowAncestor(this)) == JFileChooser.APPROVE_OPTION) {
			targetField.setText(chooser.getSelectedFile().toPath().toAbsolutePath().normalize().toString());
		}
	}

	private void browseForDirectory(JTextField targetField) {
		JFileChooser chooser = new JFileChooser(targetField.getText());
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		if (chooser.showOpenDialog(SwingUtilities.getWindowAncestor(this)) == JFileChooser.APPROVE_OPTION) {
			targetField.setText(chooser.getSelectedFile().toPath().toAbsolutePath().normalize().toString());
		}
	}

	private void setControlsEnabled(boolean enabled) {
		runButton.setEnabled(enabled);
		runBatchButton.setEnabled(enabled);
		refreshButton.setEnabled(enabled);
		openFolderButton.setEnabled(enabled);
		referenceField.setEnabled(enabled);
		configField.setEnabled(enabled);
		reportsField.setEnabled(enabled);
	}

	private Path getReferencePath() {
		return Path.of(referenceField.getText()).toAbsolutePath().normalize();
	}

	private Path getReportsPath() {
		return Path.of(reportsField.getText()).toAbsolutePath().normalize();
	}

	private Path getConfigPath() {
		return Path.of(configField.getText()).toAbsolutePath().normalize();
	}

	private static List<Path> findLogs(Path reportsDir) {
		List<Path> logs = new ArrayList<>();
		if (!Files.isDirectory(reportsDir)) {
			return logs;
		}
		try {
			Files.list(reportsDir)
					.filter(path -> path.getFileName().toString().endsWith(".log"))
					.sorted(Comparator.comparing(path -> path.getFileName().toString()))
					.forEach(logs::add);
		} catch (IOException ignored) {
			// Best effort only.
		}
		return logs;
	}

	private static String readText(Path file) {
		try {
			return Files.readString(file, StandardCharsets.UTF_8);
		} catch (IOException ex) {
			return "Failed to read file: " + file + System.lineSeparator() + ex.getMessage();
		}
	}

	private static CsvTable readCsv(Path path) throws IOException {
		List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
		if (lines.isEmpty()) {
			return new CsvTable(List.of(), List.of());
		}
		List<String> headers = parseCsvLine(lines.get(0));
		List<List<String>> rows = new ArrayList<>();
		for (int i = 1; i < lines.size(); i++) {
			if (!lines.get(i).isEmpty()) {
				rows.add(parseCsvLine(lines.get(i)));
			}
		}
		return new CsvTable(headers, rows);
	}

	private static List<String> parseCsvLine(String line) {
		List<String> values = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean inQuotes = false;
		for (int i = 0; i < line.length(); i++) {
			char ch = line.charAt(i);
			if (ch == '"') {
				if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
					current.append('"');
					i++;
				} else {
					inQuotes = !inQuotes;
				}
			} else if (ch == ',' && !inQuotes) {
				values.add(current.toString());
				current.setLength(0);
			} else {
				current.append(ch);
			}
		}
		values.add(current.toString());
		return values;
	}

	private static String valueAt(List<String> row, int index) {
		if (index < 0 || index >= row.size()) {
			return "";
		}
		return row.get(index);
	}

	private static String csvValue(CsvTable table, List<String> row, String header) {
		int index = table.headers().indexOf(header);
		return valueAt(row, index);
	}

	private static double parseDouble(String value) {
		if (value == null || value.isBlank()) {
			return Double.NaN;
		}
		try {
			return Double.parseDouble(value);
		} catch (NumberFormatException ex) {
			return Double.NaN;
		}
	}

	private static String formatRomMode(RomMode mode) {
		if (mode == null) {
			return "Standard";
		}
		return switch (mode) {
			case STANDARD -> "Standard";
			case CONSERVATIVE -> "Conservative";
			case DIAGNOSTIC -> "Diagnostic";
		};
	}

	private static String formatRomFallbackMode(RomFallbackMode fallbackMode) {
		if (fallbackMode == null) {
			return "Blend to legacy";
		}
		return switch (fallbackMode) {
			case BLEND -> "Blend to legacy";
			case BARROWMAN_ONLY -> "Legacy only";
			case FORCE_ROM -> "Force ROM";
		};
	}

	private record CsvTable(List<String> headers, List<List<String>> rows) {
	}

	private record FileChooserSeed(Path reference, Path reports) {
	}
}

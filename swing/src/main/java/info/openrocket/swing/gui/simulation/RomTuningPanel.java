package info.openrocket.swing.gui.simulation;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.tuning.PhaseThreeSingleSimulationRunner;
import info.openrocket.core.tuning.PhaseTwoBatchResult;
import net.miginfocom.swing.MigLayout;

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
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

class RomTuningPanel extends SimulationScrollablePanel {
	private static final long serialVersionUID = -463962520799963637L;

	private static final String TUNING_OVERVIEW = "phase-three-tuning-overview.csv";
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
	private final JLabel romModeValue = new JLabel("-");
	private final JLabel surfaceValue = new JLabel("-");
	private final JLabel datasetClassValue = new JLabel("-");
	private final JLabel alignedTimingValue = new JLabel("-");
	private final JLabel truthSourceValue = new JLabel("-");
	private final JLabel alignmentChannelValue = new JLabel("-");
	private final JLabel alignmentLagValue = new JLabel("-");
	private final JLabel alignmentQualityValue = new JLabel("-");
	private final JLabel machValue = new JLabel("-");
	private final JLabel statusLabel = new JLabel("Ready");
	private final JTextField referenceField = new JTextField();
	private final JTextField reportsField = new JTextField();
	private final JButton runButton = new JButton("Run Test");
	private final JButton refreshButton = new JButton("Refresh");
	private final JButton exportCurrentViewButton = new JButton("Export Current View CSV");
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

		addOrReplaceTab("Tuning Overview", buildCsvPanel("Tuning Overview", getReportsPath().resolve(TUNING_OVERVIEW)));
		addOrReplaceTab("Summary", buildCsvPanel("Summary", getReportsPath().resolve(SUMMARY)));
		addOrReplaceTab("Quantities", buildCsvPanel("Quantities", getReportsPath().resolve(QUANTITIES)));
		addOrReplaceTab("Improvements", buildCsvPanel("Improvements", getReportsPath().resolve(IMPROVEMENTS)));
		addOrReplaceTab("Analysis", buildCsvPanel("Analysis", getReportsPath().resolve(ANALYSIS)));
		addOrReplaceTab("Residuals", buildCsvPanel("Residuals", getReportsPath().resolve(RESIDUALS)));
		addOrReplaceTab("Logs", buildLogsPanel());
		add(resultsTabs, "grow, push");

		add(statusLabel, "growx");

		wireEvents();
		refreshCurrentSimulationState();
		refreshResults();
	}

	private JPanel buildCurrentSimulationPanel() {
		JPanel panel = new JPanel(new MigLayout("fillx, insets 6, gapx 8, gapy 6", "[right][grow][right][grow]", ""));
		panel.setBorder(BorderFactory.createTitledBorder("Phase Three ROM Comparison"));

		panel.add(new JLabel("Document:"));
		panel.add(documentValue);
		panel.add(new JLabel("Simulation:"));
		panel.add(simulationValue, "wrap");

		panel.add(new JLabel("ROM mode:"));
		panel.add(romModeValue);
		panel.add(new JLabel("Built surface:"));
		panel.add(surfaceValue, "wrap");

		panel.add(new JLabel("Dataset class:"));
		panel.add(datasetClassValue);
		panel.add(new JLabel("Max Mach:"));
		panel.add(machValue, "wrap");

		panel.add(new JLabel("Aligned dt(apogee):"));
		panel.add(alignedTimingValue, "span 3, wrap");

		panel.add(new JLabel("Truth source:"));
		panel.add(truthSourceValue, "span 3, wrap");

		panel.add(new JLabel("Alignment channel:"));
		panel.add(alignmentChannelValue);
		panel.add(new JLabel("Alignment lag:"));
		panel.add(alignmentLagValue, "wrap");

		panel.add(new JLabel("Alignment quality:"));
		panel.add(alignmentQualityValue, "span 3, wrap");

		JLabel note = new JLabel("<html>Phase Three compares only the current simulation against one reference CSV. "
				+ "The analysis runs in-process inside OpenRocket, so it works in the packaged app without Gradle.</html>");
		panel.add(note, "span 4, growx");
		return panel;
	}

	private JPanel buildRunnerPanel() {
		JPanel outer = new JPanel(new BorderLayout(6, 6));
		outer.setBorder(BorderFactory.createTitledBorder("Runner"));

		JPanel form = new JPanel(new MigLayout("fillx, insets 0, gapx 8, gapy 6", "[right][grow,fill][]", ""));
		form.add(new JLabel("Reference CSV:"));
		form.add(referenceField, "growx");
		JButton browseReferenceButton = new JButton("Browse");
		browseReferenceButton.addActionListener(e -> browseForFile(referenceField));
		form.add(browseReferenceButton, "wrap");

		form.add(new JLabel("Reports dir:"));
		form.add(reportsField, "growx");
		JButton browseReportsButton = new JButton("Browse");
		browseReportsButton.addActionListener(e -> browseForDirectory(reportsField));
		form.add(browseReportsButton, "wrap");

		JPanel buttons = new JPanel(new MigLayout("insets 0, gapx 6", "[][][]", ""));
		buttons.add(runButton);
		buttons.add(refreshButton);
		buttons.add(exportCurrentViewButton);
		buttons.add(openFolderButton);

		outer.add(form, BorderLayout.CENTER);
		outer.add(buttons, BorderLayout.SOUTH);
		return outer;
	}

	private void wireEvents() {
		runButton.addActionListener(e -> runPhaseThree());
		refreshButton.addActionListener(e -> refreshResults());
		exportCurrentViewButton.addActionListener(e -> exportCurrentView());
		openFolderButton.addActionListener(e -> openReportsFolder());
	}

	private void configureDefaults() {
		FileChooserSeed seed = defaultSeed();
		referenceField.setText(seed.reference().toString());
		reportsField.setText(seed.reports().toString());
		documentValue.setText(document != null && document.getFile() != null
				? document.getFile().getAbsolutePath()
				: "Unsaved document");
	}

	private FileChooserSeed defaultSeed() {
		Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
		Path reports = root.resolve(DEFAULT_REPORTS).normalize();
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
		romModeValue.setText(simulation.getOptions().getRomSurfaceMode() == null
				? "Unknown"
				: simulation.getOptions().getRomSurfaceMode().name());
		boolean has3d = simulation.getOptions().getRomDragSurface() != null;
		boolean has4d = simulation.getOptions().getRomAeroSurface4D() != null;
		surfaceValue.setText(has4d ? "4D surface loaded" : has3d ? "3D surface loaded" : "No ROM surface loaded");
	}

	private void runPhaseThree() {
		if (runWorker != null) {
			statusLabel.setText("A test run is already in progress.");
			return;
		}
		Path referenceCsv = getReferencePath();
		Path reportsDir = getReportsPath();
		if (!Files.exists(referenceCsv)) {
			statusLabel.setText("Reference CSV not found: " + referenceCsv);
			return;
		}

		executionLogArea.setText("");
		setControlsEnabled(false);
		publishLine("Starting a fresh OpenRocket simulation rerun for the current ROM settings.");
		statusLabel.setText("Running fresh Phase Three comparison...");
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
					publishLine("Phase Three comparison completed successfully.");
					refreshResults();
					statusLabel.setText("Phase Three comparison completed successfully.");
				} catch (Exception ex) {
					publishLine("Phase Three comparison failed: " + ex.getMessage());
					statusLabel.setText("Phase Three comparison failed: " + ex.getMessage());
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
		addOrReplaceTab("Tuning Overview", buildCsvPanel("Tuning Overview", reportsDir.resolve(TUNING_OVERVIEW)));
		addOrReplaceTab("Summary", buildCsvPanel("Summary", reportsDir.resolve(SUMMARY)));
		addOrReplaceTab("Quantities", buildCsvPanel("Quantities", reportsDir.resolve(QUANTITIES)));
		addOrReplaceTab("Improvements", buildCsvPanel("Improvements", reportsDir.resolve(IMPROVEMENTS)));
		addOrReplaceTab("Analysis", buildCsvPanel("Analysis", reportsDir.resolve(ANALYSIS)));
		addOrReplaceTab("Residuals", buildCsvPanel("Residuals", reportsDir.resolve(RESIDUALS)));
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

	private JPanel buildCsvPanel(String tabTitle, Path csvPath) {
		JPanel panel = new JPanel(new BorderLayout());
		panel.putClientProperty("tabTitle", tabTitle);
		panel.putClientProperty("sourcePath", csvPath);
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
			List<String> visibleHeaders = selectHeaders(tabTitle, csv.headers());
			for (String header : visibleHeaders) {
				model.addColumn(header);
			}
			for (List<String> row : csv.rows()) {
				Object[] filteredRow = new Object[visibleHeaders.size()];
				for (int i = 0; i < visibleHeaders.size(); i++) {
					filteredRow[i] = csvValue(csv, row, visibleHeaders.get(i));
				}
				model.addRow(filteredRow);
			}
		} catch (IOException ex) {
			panel.add(new JLabel("Failed to load " + csvPath.getFileName() + ": " + ex.getMessage()), BorderLayout.NORTH);
			return panel;
		}

		JTable table = new JTable(model);
		table.setAutoCreateRowSorter(true);
		table.setFillsViewportHeight(true);
		panel.putClientProperty("tableModel", model);
		panel.add(new JScrollPane(table), BorderLayout.CENTER);
		return panel;
	}

	private JPanel buildLogsPanel() {
		refreshReportLogs();
		JTabbedPane logTabs = new JTabbedPane();
		logTabs.addTab("Execution", new JScrollPane(executionLogArea));
		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(reportLogList), new JScrollPane(reportLogTextArea));
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
		refreshButton.setEnabled(enabled);
		exportCurrentViewButton.setEnabled(enabled);
		openFolderButton.setEnabled(enabled);
		referenceField.setEnabled(enabled);
		reportsField.setEnabled(enabled);
	}

	private Path getReferencePath() {
		return Path.of(referenceField.getText()).toAbsolutePath().normalize();
	}

	private Path getReportsPath() {
		return Path.of(reportsField.getText()).toAbsolutePath().normalize();
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

	private void exportCurrentView() {
		java.awt.Component selected = resultsTabs.getSelectedComponent();
		if (!(selected instanceof JPanel panel)) {
			statusLabel.setText("No table view is selected for export.");
			return;
		}
		Object modelObject = panel.getClientProperty("tableModel");
		if (!(modelObject instanceof DefaultTableModel model)) {
			statusLabel.setText("The selected tab does not contain exportable table data.");
			return;
		}

		String tabTitle = String.valueOf(panel.getClientProperty("tabTitle"));
		String defaultName = sanitizeFilePart(tabTitle.isBlank() ? "rom-results" : tabTitle) + ".csv";

		JFileChooser chooser = new JFileChooser(getReportsPath().toFile());
		chooser.setDialogTitle("Export current ROM results view");
		chooser.setSelectedFile(getReportsPath().resolve(defaultName).toFile());
		if (chooser.showSaveDialog(SwingUtilities.getWindowAncestor(this)) != JFileChooser.APPROVE_OPTION) {
			return;
		}

		Path target = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
		try {
			writeModelCsv(model, target);
			statusLabel.setText("Exported current view to " + target.getFileName());
		} catch (IOException ex) {
			statusLabel.setText("Failed to export current view: " + ex.getMessage());
		}
	}

	private static void writeModelCsv(DefaultTableModel model, Path target) throws IOException {
		StringBuilder out = new StringBuilder();
		for (int column = 0; column < model.getColumnCount(); column++) {
			if (column > 0) {
				out.append(',');
			}
			out.append(escapeCsv(String.valueOf(model.getColumnName(column))));
		}
		out.append(System.lineSeparator());
		for (int row = 0; row < model.getRowCount(); row++) {
			for (int column = 0; column < model.getColumnCount(); column++) {
				if (column > 0) {
					out.append(',');
				}
				Object value = model.getValueAt(row, column);
				out.append(escapeCsv(value == null ? "" : String.valueOf(value)));
			}
			out.append(System.lineSeparator());
		}
		Files.writeString(target, out.toString(), StandardCharsets.UTF_8);
	}

	private static String escapeCsv(String value) {
		if (value == null) {
			return "";
		}
		boolean needsQuotes = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
		String escaped = value.replace("\"", "\"\"");
		return needsQuotes ? "\"" + escaped + "\"" : escaped;
	}

	private static String sanitizeFilePart(String value) {
		if (value == null || value.isBlank()) {
			return "rom-results";
		}
		String cleaned = value.replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("-{2,}", "-");
		return cleaned.isBlank() ? "rom-results" : cleaned;
	}

	private static List<String> selectHeaders(String tabTitle, List<String> headers) {
		List<String> preferred = switch (tabTitle) {
			case "Tuning Overview" -> List.of(
					"dataset", "datasetClass", "romMode", "romSurfaceSource", "truthSource",
					"candidateMaxMach", "fullScore", "fullSeverity", "boostScore", "coastScore",
					"ascentScore", "apogeeErrorPercent", "alignedApogeeTimeDeltaSec",
					"alignmentChannel", "alignmentLagSec", "alignmentQuality",
					"primaryWeakness", "primaryWeaknessScore", "recommendedFocus",
					"reliabilityLaneStatus", "reliabilityFailureReason", "topContributors", "flags");
			case "Summary" -> List.of(
					"dataset", "fullScore", "fullSeverity", "boostScore", "coastScore", "descentScore",
					"apogeeErrorPercent", "alignedApogeeTimeDeltaSec", "ascentScore", "ascentLaneStatus",
					"reliabilityLaneStatus", "candidateMaxMach", "alignmentChannel", "alignmentLagSec",
					"alignmentQuality", "topContributors", "romImprovementHints", "failureReason");
			case "Quantities" -> List.of(
					"dataset", "source", "velocityAbsPeak", "accelAbsPeak", "cdProxyMean",
					"launchTimeSec", "burnoutTimeSec", "apogeeAltitudeM", "apogeeTimeSec",
					"alignedApogeeTimeSec", "maxMach", "rowsAccepted", "droppedSamples");
			case "Improvements" -> List.of(
					"dataset", "window", "rowType", "channel", "channelScore", "severity",
					"equationGroup", "recommendation");
			case "Analysis" -> List.of(
					"dataset", "datasetClass", "romMode", "romSurfaceSource", "candidateMaxMach",
					"fullScore", "fullSeverity", "apogeeErrorPercent", "ascentScore", "ascentLaneStatus",
					"reliabilityLaneStatus", "primaryWeakness", "primaryWeaknessScore",
					"primaryStrength", "primaryStrengthScore", "recommendedFocus",
					"weakestResidualPhase", "weakestResidualChannel", "weakestResidualNrmse",
					"primaryDragResidualPhase", "primaryDragDeltaPercent");
			case "Residuals" -> List.of(
					"dataset", "phase", "residualType", "channel", "nrmse", "rmse", "mae",
					"percentDelta", "residualSummary");
			default -> headers;
		};

		Set<String> remaining = new LinkedHashSet<>(headers);
		List<String> selected = new ArrayList<>();
		for (String header : preferred) {
			if (remaining.remove(header)) {
				selected.add(header);
			}
		}
		if (selected.isEmpty()) {
			return headers;
		}
		if ("Logs".equals(tabTitle)) {
			return headers;
		}
		selected.addAll(remaining.stream().filter(header -> header.endsWith("Status")).toList());
		return selected;
	}

	private record CsvTable(List<String> headers, List<List<String>> rows) {
	}

	private record FileChooserSeed(Path reference, Path reports) {
	}
}

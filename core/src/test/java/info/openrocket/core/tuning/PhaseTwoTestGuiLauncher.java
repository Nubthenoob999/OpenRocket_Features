package info.openrocket.core.tuning;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PhaseTwoTestGuiLauncher {
	private static final String TUNING_OVERVIEW = "phase-three-tuning-overview.csv";
	private static final String SUMMARY = "phase-two-summary.csv";
	private static final String QUANTITIES = "phase-two-quantities.csv";
	private static final String IMPROVEMENTS = "phase-two-improvements.csv";
	private static final String ANALYSIS = "phase-three-analysis.csv";
	private static final String RESIDUALS = "phase-three-drag-residuals.csv";
	private static final String CONSOLE_LOG = "phase-two-console.log";
	private static final String DEFAULT_CONFIG = "src/test/java/info/openrocket/core/tuning/Phase3_tuning.json";

	private JFrame frame;
	private JLabel statusLabel;
	private JLabel reportsLabel;
	private JTextField configField;
	private JTextField reportsField;
	private JButton runTestsButton;
	private JButton refreshButton;
	private JButton exportCurrentViewButton;
	private JButton openFolderButton;
	private JTabbedPane tabs;

	private PhaseTwoTestGuiLauncher() {
	}

	public static void main(String[] args) {
		Path reportsDir = args.length > 0 ? Path.of(args[0]) : Path.of("build", "reports", "phase-three");
		Path resolvedReports = reportsDir.toAbsolutePath().normalize();
		Path resolvedConfig = Path.of(DEFAULT_CONFIG).toAbsolutePath().normalize();

		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception ignored) {
			// Keep default LAF when system LAF is unavailable.
		}

		SwingUtilities.invokeLater(() -> new PhaseTwoTestGuiLauncher().openUi(resolvedConfig, resolvedReports));
	}

	private void openUi(Path configPath, Path reportsDir) {
		frame = new JFrame("Phase-Three Test GUI");
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.setLayout(new BorderLayout(8, 8));

		frame.add(buildControlsPanel(configPath, reportsDir), BorderLayout.NORTH);

		tabs = new JTabbedPane();
		frame.add(tabs, BorderLayout.CENTER);
		statusLabel = new JLabel("Ready");
		statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 10, 8, 10));
		frame.add(statusLabel, BorderLayout.SOUTH);

		refreshTabs(getReportsPath());

		frame.setPreferredSize(new Dimension(1200, 760));
		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}

	private JPanel buildControlsPanel(Path configPath, Path reportsDir) {
		JPanel outer = new JPanel(new BorderLayout(6, 6));
		outer.setBorder(BorderFactory.createEmptyBorder(8, 10, 2, 10));

		reportsLabel = new JLabel("Reports: " + reportsDir);
		outer.add(reportsLabel, BorderLayout.NORTH);

		JPanel controls = new JPanel(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(3, 3, 3, 3);
		c.fill = GridBagConstraints.HORIZONTAL;

		c.gridx = 0;
		c.gridy = 0;
		c.weightx = 0;
		controls.add(new JLabel("Config JSON:"), c);

		configField = new JTextField(configPath.toString());
		c.gridx = 1;
		c.weightx = 1;
		controls.add(configField, c);

		JButton browseConfig = new JButton("Browse");
		browseConfig.addActionListener(e -> browseForFile(configField));
		c.gridx = 2;
		c.weightx = 0;
		controls.add(browseConfig, c);

		c.gridx = 0;
		c.gridy = 1;
		c.weightx = 0;
		controls.add(new JLabel("Reports Dir:"), c);

		reportsField = new JTextField(reportsDir.toString());
		c.gridx = 1;
		c.weightx = 1;
		controls.add(reportsField, c);

		JButton browseReports = new JButton("Browse");
		browseReports.addActionListener(e -> browseForDirectory(reportsField));
		c.gridx = 2;
		c.weightx = 0;
		controls.add(browseReports, c);

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		runTestsButton = new JButton("Run Test");
		runTestsButton.addActionListener(e -> runTests());
		actions.add(runTestsButton);

		refreshButton = new JButton("Refresh");
		refreshButton.addActionListener(e -> refreshTabs(getReportsPath()));
		actions.add(refreshButton);

		exportCurrentViewButton = new JButton("Export Current View CSV");
		exportCurrentViewButton.addActionListener(e -> exportCurrentView());
		actions.add(exportCurrentViewButton);

		openFolderButton = new JButton("Open Reports Folder");
		openFolderButton.addActionListener(e -> openReportsFolder());
		actions.add(openFolderButton);

		c.gridx = 1;
		c.gridy = 2;
		c.gridwidth = 2;
		c.weightx = 0;
		controls.add(actions, c);

		outer.add(controls, BorderLayout.CENTER);
		return outer;
	}

	private void browseForFile(JTextField targetField) {
		JFileChooser chooser = new JFileChooser(targetField.getText());
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
			targetField.setText(chooser.getSelectedFile().toPath().toAbsolutePath().toString());
		}
	}

	private void browseForDirectory(JTextField targetField) {
		JFileChooser chooser = new JFileChooser(targetField.getText());
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
			targetField.setText(chooser.getSelectedFile().toPath().toAbsolutePath().toString());
		}
	}

	private void setControlsEnabled(boolean enabled) {
		runTestsButton.setEnabled(enabled);
		refreshButton.setEnabled(enabled);
		exportCurrentViewButton.setEnabled(enabled);
		openFolderButton.setEnabled(enabled);
		configField.setEnabled(enabled);
		reportsField.setEnabled(enabled);
	}

	private Path getReportsPath() {
		return Path.of(reportsField.getText()).toAbsolutePath().normalize();
	}

	private Path getConfigPath() {
		return Path.of(configField.getText()).toAbsolutePath().normalize();
	}

	private void refreshTabs(Path reportsDir) {
		reportsLabel.setText("Reports: " + reportsDir);
		tabs.removeAll();
		tabs.addTab("Tuning Overview", buildCsvPanel("Tuning Overview", reportsDir.resolve(TUNING_OVERVIEW)));
		tabs.addTab("Summary", buildCsvPanel("Summary", reportsDir.resolve(SUMMARY)));
		tabs.addTab("Quantities", buildCsvPanel("Quantities", reportsDir.resolve(QUANTITIES)));
		tabs.addTab("Improvements", buildCsvPanel("Improvements", reportsDir.resolve(IMPROVEMENTS)));
		tabs.addTab("Analysis", buildCsvPanel("Analysis", reportsDir.resolve(ANALYSIS)));
		tabs.addTab("Residuals", buildCsvPanel("Residuals", reportsDir.resolve(RESIDUALS)));
		tabs.addTab("Logs", buildLogsPanel(reportsDir));
		statusLabel.setText("Loaded reports from " + reportsDir);
	}

	private void runTests() {
		Path configPath = getConfigPath();
		Path reportsDir = getReportsPath();
		if (!Files.exists(configPath)) {
			statusLabel.setText("Config file not found: " + configPath);
			return;
		}

		setControlsEnabled(false);
		statusLabel.setText("Running fresh Phase Three batch analysis...");

		SwingWorker<Void, Void> worker = new SwingWorker<>() {
			private Exception error;

			@Override
			protected Void doInBackground() {
				try {
					Files.createDirectories(reportsDir);
					PhaseTwoBatchRunner.runFromConfig(configPath, reportsDir);
				} catch (Exception e) {
					error = e;
				}
				return null;
			}

			@Override
			protected void done() {
				setControlsEnabled(true);
				if (error != null) {
					statusLabel.setText("Run failed: " + error.getMessage());
				} else {
					refreshTabs(reportsDir);
					statusLabel.setText("Phase Three batch analysis completed successfully.");
				}
			}
		};
		worker.execute();
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
		} catch (IOException e) {
			statusLabel.setText("Failed to open reports folder: " + e.getMessage());
		}
	}

	private JPanel buildCsvPanel(String tabTitle, Path csvPath) {
		JPanel panel = new JPanel(new BorderLayout());
		panel.putClientProperty("tabTitle", tabTitle);
		panel.putClientProperty("sourcePath", csvPath);
		if (!Files.exists(csvPath)) {
			panel.add(new JLabel("File not found: " + csvPath), BorderLayout.NORTH);
			return panel;
		}

		DefaultTableModel model = new DefaultTableModel();
		try (Reader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8);
				 CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)) {
			List<String> headers = parser.getHeaderNames();
			List<String> visibleHeaders = selectHeaders(tabTitle, headers);
			for (String h : visibleHeaders) {
				model.addColumn(h);
			}
			for (CSVRecord record : parser) {
				Object[] row = new Object[visibleHeaders.size()];
				for (int i = 0; i < visibleHeaders.size(); i++) {
					row[i] = record.isMapped(visibleHeaders.get(i)) ? record.get(visibleHeaders.get(i)) : "";
				}
				model.addRow(row);
			}
		} catch (IOException e) {
			panel.add(new JLabel("Failed to load " + csvPath.getFileName() + ": " + e.getMessage()), BorderLayout.NORTH);
			return panel;
		}

		JTable table = new JTable(model);
		table.setAutoCreateRowSorter(true);
		table.setFillsViewportHeight(true);
		panel.putClientProperty("tableModel", model);
		panel.add(new JScrollPane(table), BorderLayout.CENTER);
		return panel;
	}

	private void exportCurrentView() {
		java.awt.Component selected = tabs.getSelectedComponent();
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
		JFileChooser chooser = new JFileChooser(getReportsPath().toFile());
		chooser.setDialogTitle("Export current Phase Three view");
		chooser.setSelectedFile(getReportsPath().resolve(sanitizeFilePart(tabTitle) + ".csv").toFile());
		if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
			return;
		}

		Path target = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
		try {
			writeModelCsv(model, target);
			statusLabel.setText("Exported current view to " + target.getFileName());
		} catch (IOException e) {
			statusLabel.setText("Failed to export current view: " + e.getMessage());
		}
	}

	private static JPanel buildLogsPanel(Path reportsDir) {
		JPanel panel = new JPanel(new BorderLayout(8, 8));

		List<Path> logs = findLogs(reportsDir);
		JList<Path> list = new JList<>(logs.toArray(Path[]::new));
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		JTextArea textArea = new JTextArea();
		textArea.setEditable(false);
		textArea.setLineWrap(false);

		list.addListSelectionListener(e -> {
			if (e.getValueIsAdjusting()) {
				return;
			}
			Path selected = list.getSelectedValue();
			if (selected != null) {
				textArea.setText(readText(selected));
				textArea.setCaretPosition(0);
			}
		});

		if (!logs.isEmpty()) {
			list.setSelectedIndex(0);
		}

		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
				new JScrollPane(list), new JScrollPane(textArea));
		split.setResizeWeight(0.25);
		panel.add(split, BorderLayout.CENTER);

		return panel;
	}

	private static List<Path> findLogs(Path reportsDir) {
		List<Path> logs = new ArrayList<>();
		Path console = reportsDir.resolve(CONSOLE_LOG);
		if (Files.exists(console)) {
			logs.add(console);
		}
		try {
			Files.list(reportsDir)
					.filter(p -> {
						String name = p.getFileName().toString();
						return name.endsWith(".log");
					})
					.sorted(Comparator.comparing(p -> p.getFileName().toString()))
					.forEach(p -> {
						if (!logs.contains(p)) {
							logs.add(p);
						}
					});
		} catch (IOException ignored) {
			// Best effort listing.
		}
		return logs;
	}

	private static String readText(Path file) {
		try {
			return Files.readString(file, StandardCharsets.UTF_8);
		} catch (IOException e) {
			return "Failed to read file: " + file + System.lineSeparator() + e.getMessage();
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
			return "phase-three-results";
		}
		String cleaned = value.replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("-{2,}", "-");
		return cleaned.isBlank() ? "phase-three-results" : cleaned;
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
		selected.addAll(remaining.stream().filter(header -> header.endsWith("Status")).toList());
		return selected;
	}
}

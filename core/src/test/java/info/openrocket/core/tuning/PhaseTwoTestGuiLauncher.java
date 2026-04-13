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
import java.util.List;

public final class PhaseTwoTestGuiLauncher {
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
		runTestsButton = new JButton("Run Phase Three");
		runTestsButton.addActionListener(e -> runTests());
		actions.add(runTestsButton);

		refreshButton = new JButton("Refresh");
		refreshButton.addActionListener(e -> refreshTabs(getReportsPath()));
		actions.add(refreshButton);

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
		tabs.addTab("Summary", buildCsvPanel(reportsDir.resolve(SUMMARY)));
		tabs.addTab("Quantities", buildCsvPanel(reportsDir.resolve(QUANTITIES)));
		tabs.addTab("Improvements", buildCsvPanel(reportsDir.resolve(IMPROVEMENTS)));
		tabs.addTab("Analysis", buildCsvPanel(reportsDir.resolve(ANALYSIS)));
		tabs.addTab("Residuals", buildCsvPanel(reportsDir.resolve(RESIDUALS)));
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
		statusLabel.setText("Running Phase Three batch analysis...");

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

	private static JPanel buildCsvPanel(Path csvPath) {
		JPanel panel = new JPanel(new BorderLayout());
		if (!Files.exists(csvPath)) {
			panel.add(new JLabel("File not found: " + csvPath), BorderLayout.NORTH);
			return panel;
		}

		DefaultTableModel model = new DefaultTableModel();
		try (Reader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8);
				 CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)) {
			List<String> headers = parser.getHeaderNames();
			for (String h : headers) {
				model.addColumn(h);
			}
			for (CSVRecord record : parser) {
				Object[] row = new Object[headers.size()];
				for (int i = 0; i < headers.size(); i++) {
					row[i] = record.get(i);
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
		panel.add(new JScrollPane(table), BorderLayout.CENTER);
		return panel;
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
}

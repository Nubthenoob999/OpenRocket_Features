package info.openrocket.swing.gui.simulation;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroTableResolver;
import info.openrocket.core.aerodynamics.physicsaero.table.AerodynamicTable;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.l10n.Translator;
import info.openrocket.core.montecarlo.MonteCarloAnalysis;
import info.openrocket.core.montecarlo.MonteCarloBatchRunner;
import info.openrocket.core.montecarlo.MonteCarloExtension;
import info.openrocket.core.montecarlo.MonteCarloRunRecord;
import info.openrocket.core.simulation.SimulationOptions;
import info.openrocket.core.simulation.extension.SimulationExtension;
import info.openrocket.core.simulation.montecarlo.MonteCarloResult;
import info.openrocket.core.simulation.montecarlo.MonteCarloSettings;
import info.openrocket.core.startup.Application;
import info.openrocket.core.util.Config;
import info.openrocket.swing.gui.util.GUIUtil;
import net.miginfocom.swing.MigLayout;

/** Modeless, multi-target Monte Carlo workflow launched from the simulation toolbar. */
public final class MonteCarloDialog extends JDialog {
	private static final Translator trans = Application.getTranslator();

	private final OpenRocketDocument document;
	private final List<Simulation> availableTargets;
	private final MonteCarloExtension workingExtension = new MonteCarloExtension();
	private final MonteCarloSetupPanel setupPanel;
	private final DefaultTableModel targetModel;
	private final JTable targetTable;
	private final JComboBox<SimulationChoice> resultChoice = new JComboBox<>();
	private final JPanel resultHost = new JPanel(new BorderLayout());
	private final JTextArea resultState = wrappingText("");
	private final JTextArea sharedNotice = wrappingText("");
	private final JCheckBox useTable;
	private final JButton applyButton = new JButton(trans.get("MonteCarloDialog.apply"));
	private final ProgressButton runButton = new ProgressButton(trans.get("MonteCarloDialog.runAll"));
	private final JButton cancelButton = new JButton(trans.get("MonteCarloDialog.close"));
	private final JButton exportButton = new JButton(trans.get("MonteCarloDialog.export"));
	private SwingWorker<Void, TargetUpdate> worker;
	private boolean closeWhenWorkerStops;

	public MonteCarloDialog(Window owner, OpenRocketDocument document, Simulation[] selected) {
		super(owner, trans.get("MonteCarloDialog.title"), ModalityType.MODELESS);
		if (document.getSimulations().isEmpty()) {
			throw new IllegalArgumentException("At least one simulation must exist in the document");
		}
		this.document = document;
		this.availableTargets = List.copyOf(document.getSimulations());
		Set<Simulation> initialSelection = new HashSet<>();
		if (selected != null) {
			for (Simulation simulation : selected) {
				if (availableTargets.contains(simulation)) initialSelection.add(simulation);
			}
		}
		Simulation initialTarget = initialSelection.isEmpty()
				? availableTargets.get(0)
				: availableTargets.stream().filter(initialSelection::contains).findFirst().orElse(availableTargets.get(0));
		// Edit Simulation is document-modal.  Without an exclusion Java blocks this
		// modeless analysis window even where the two windows do not overlap.
		setModalExclusionType(java.awt.Dialog.ModalExclusionType.APPLICATION_EXCLUDE);

		MonteCarloExtension first = findExtension(initialTarget);
		if (first != null) workingExtension.copyPersistentSettingsFrom(first);
		setupPanel = new MonteCarloSetupPanel(workingExtension, initialTarget, false);
		useTable = new JCheckBox(trans.get("MonteCarloDialog.useTable"),
				workingExtension.isUsePhysicsAeroTable());
		useTable.setToolTipText(trans.get("MonteCarloDialog.useTable.ttip"));
		useTable.addActionListener(event -> workingExtension.setUsePhysicsAeroTable(useTable.isSelected()));

		targetModel = new DefaultTableModel(new Object[] {
				trans.get("MonteCarloDialog.select"), trans.get("MonteCarloDialog.target"),
				trans.get("MonteCarloDialog.status"), trans.get("MonteCarloDialog.progress") }, 0) {
			@Override public boolean isCellEditable(int row, int column) {
				return column == 0 && worker == null;
			}
			@Override public Class<?> getColumnClass(int column) {
				return column == 0 ? Boolean.class : String.class;
			}
		};
		for (Simulation simulation : availableTargets) {
			boolean selectedTarget = initialSelection.contains(simulation);
			targetModel.addRow(new Object[] { selectedTarget, simulation.getName(),
					selectedTarget ? trans.get("MonteCarloDialog.queued")
							: trans.get("MonteCarloDialog.notSelected"), "" });
			resultChoice.addItem(new SimulationChoice(simulation));
		}
		resultChoice.setSelectedItem(new SimulationChoice(initialTarget));
		targetTable = new JTable(targetModel);
		targetTable.setFillsViewportHeight(true);
		targetTable.setRowHeight(38);
		targetTable.getColumnModel().getColumn(0).setMaxWidth(72);
		targetTable.getColumnModel().getColumn(2).setCellRenderer(new WrappingRenderer());
		targetModel.addTableModelListener(event -> {
			if (event.getColumn() == 0) updateSelectionUi();
		});

		setLayout(new BorderLayout());
		add(buildHeader(), BorderLayout.NORTH);

		JPanel left = new JPanel(new BorderLayout(0, 6));
		JScrollPane targetScroll = new JScrollPane(targetTable);
		targetScroll.setPreferredSize(new Dimension(360, 135));
		left.add(targetScroll, BorderLayout.NORTH);
		JScrollPane settingsScroll = setupPanel.wrapInScrollPane();
		settingsScroll.setMinimumSize(new Dimension(0, 0));
		left.add(settingsScroll, BorderLayout.CENTER);
		left.setMinimumSize(new Dimension(350, 0));

		JPanel right = new JPanel(new BorderLayout(0, 6));
		JPanel resultHeader = new JPanel(new BorderLayout(6, 2));
		resultHeader.add(resultChoice, BorderLayout.NORTH);
		resultHeader.add(resultState, BorderLayout.CENTER);
		right.add(resultHeader, BorderLayout.NORTH);
		right.add(resultHost, BorderLayout.CENTER);
		right.setMinimumSize(new Dimension(430, 0));
		resultChoice.addActionListener(event -> showSelectedResults());
		showSelectedResults();

		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
		split.setResizeWeight(0.42);
		split.setContinuousLayout(true);
		split.setOneTouchExpandable(true);
		add(split, BorderLayout.CENTER);
		add(buildActions(), BorderLayout.SOUTH);

		applyButton.addActionListener(event -> applySettings());
		runButton.addActionListener(event -> runAll());
		cancelButton.addActionListener(event -> cancelOrClose(false));
		exportButton.addActionListener(event -> exportSelected());

		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			@Override public void windowClosing(WindowEvent event) { cancelOrClose(true); }
		});
		setMinimumSize(new Dimension(900, 620));
		setSize(new Dimension(1240, 800));
		setLocationRelativeTo(owner);
		updateSelectionUi();
		SwingUtilities.invokeLater(() -> {
			if (split.isDisplayable()) split.setDividerLocation(0.42);
		});
	}

	private Component buildHeader() {
		JPanel header = new JPanel(new MigLayout("fillx, ins 10 14 10 14", "[grow,fill]", "[]2[]"));
		Color background = javax.swing.UIManager.getColor("TableHeader.background");
		if (background != null) header.setBackground(background);
		JLabel title = new JLabel(trans.get("MonteCarloDialog.header"));
		title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 3f));
		header.add(title, "wrap");
		header.add(sharedNotice, "growx, wmin 0");
		return header;
	}

	private Component buildActions() {
		JPanel actions = new JPanel(new MigLayout("fillx, ins 8", "[grow,fill][]", "[]"));
		actions.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0,
				javax.swing.UIManager.getColor("Separator.foreground")));
		actions.add(useTable, "growx, wmin 0");
		JPanel buttons = new JPanel(new MigLayout("ins 0, gap 6", "[][][][]"));
		buttons.add(applyButton);
		buttons.add(runButton);
		buttons.add(cancelButton);
		buttons.add(exportButton);
		actions.add(buttons, "align right");
		return actions;
	}

	private boolean applySettings() {
		return applySettings(selectedTargets());
	}

	private boolean applySettings(List<Simulation> targets) {
		if (targets.isEmpty()) return false;
		if (hasOpenSimulationEditor()) {
			JOptionPane.showMessageDialog(this,
					trans.get("MonteCarloDialog.simulationEditorOpen"),
					trans.get("MonteCarloDialog.simulationEditorOpen.title"),
					JOptionPane.INFORMATION_MESSAGE);
			return false;
		}
		if (!GUIUtil.commitSpinnerEdits(setupPanel)) {
			JOptionPane.showMessageDialog(this, trans.get("SimulationConfigDialog.invalidSpinner"),
					trans.get("SimulationConfigDialog.invalidSpinner.title"), JOptionPane.ERROR_MESSAGE);
			return false;
		}
		workingExtension.setUsePhysicsAeroTable(useTable.isSelected());
		document.addUndoPosition(trans.get("MonteCarloDialog.undoApply"));
		for (Simulation target : targets) {
			MonteCarloExtension extension = findExtension(target);
			if (extension == null) {
				extension = new MonteCarloExtension();
				target.getSimulationExtensions().add(extension);
			}
				extension.copyPersistentSettingsFrom(workingExtension);
				target.notifySimulationExtensionsChanged();
		}
		showSelectedResults();
		return true;
	}

	private boolean hasOpenSimulationEditor() {
		for (Window window : Window.getWindows()) {
			if (window instanceof SimulationConfigDialog editor && window.isShowing()
					&& editor.editsDocument(document)) {
				return true;
			}
		}
		return false;
	}

	private void runAll() {
		List<QueuedTarget> queue = selectedTargetRows();
		if (worker != null || !applySettings(queue.stream().map(QueuedTarget::simulation).toList())) return;
		final long totalWork = queue.stream()
				.map(QueuedTarget::simulation)
				.map(MonteCarloDialog::findExtension)
				.mapToLong(extension -> (long) extension.getNumberOfSimulations() + 1L)
				.sum();
		setRunning(true);
		runButton.setProgress(0, totalWork);
		for (int row = 0; row < availableTargets.size(); row++) {
			boolean selectedTarget = Boolean.TRUE.equals(targetModel.getValueAt(row, 0));
			targetModel.setValueAt(selectedTarget ? trans.get("MonteCarloDialog.queued")
					: trans.get("MonteCarloDialog.notSelected"), row, 2);
			targetModel.setValueAt("", row, 3);
		}
		worker = new SwingWorker<>() {
			@Override protected Void doInBackground() {
				long completedBeforeTarget = 0L;
				try {
					for (QueuedTarget queuedTarget : queue) {
						if (isCancelled()) break;
						int row = queuedTarget.row();
						Simulation target = queuedTarget.simulation();
						MonteCarloExtension extension = findExtension(target);
						long targetWork = (long) extension.getNumberOfSimulations() + 1L;
						long completedBeforeThisTarget = completedBeforeTarget;
						publish(TargetUpdate.status(row, trans.get("MonteCarloDialog.running"), "0",
								completedBeforeThisTarget, totalWork));
						try {
						if (!document.getSimulations().contains(target) ||
								target.getStatus() == Simulation.Status.CANT_RUN ||
								target.getStatus() == Simulation.Status.EXTERNAL) {
							throw new IllegalStateException(trans.get("MonteCarloDialog.invalidTarget"));
						}
						MonteCarloSettings settings = MonteCarloBatchRunner.buildSettings(extension,
								extension.getNumberOfSimulations(), extension.getWorkerThreads());
						boolean tableEnabled = extension.isUsePhysicsAeroTable();
						AerodynamicTable table = tableEnabled ? new PhysicsAeroTableResolver().resolve(
								target.getActiveConfiguration(), target.getOptions().getPhysicsAeroSettings()) : null;
						SimulationOptions options = target.getOptions().clone();
						MonteCarloResult result = MonteCarloBatchRunner.runAnalysis(target, settings,
								tableEnabled, table, (completed, total) -> publish(TargetUpdate.status(row,
									trans.get("MonteCarloDialog.running"), completed + " / " + total,
									completedBeforeThisTarget + completed, totalWork)));
						List<MonteCarloRunRecord> records = MonteCarloBatchRunner.toLegacyRecords(target, result, options);
						MonteCarloAnalysis analysis = MonteCarloAnalysis.completed(target, extension, records, tableEnabled);
						publish(TargetUpdate.completed(row, analysis,
								completedBeforeThisTarget + targetWork, totalWork));
						} catch (CancellationException exception) {
							break;
						} catch (RuntimeException exception) {
							String message = exception.getMessage();
							if (message == null || message.isBlank()) message = exception.getClass().getSimpleName();
							publish(TargetUpdate.failed(row, message,
									completedBeforeThisTarget + targetWork, totalWork));
						}
						completedBeforeTarget += targetWork;
					}
				} finally {
					boolean cancelled = isCancelled();
					SwingUtilities.invokeLater(() -> finishBatch(cancelled));
				}
				return null;
			}

			@Override protected void process(List<TargetUpdate> updates) {
				for (TargetUpdate update : updates) {
					targetModel.setValueAt(update.status(), update.index(), 2);
					if (update.progress() != null) targetModel.setValueAt(update.progress(), update.index(), 3);
					runButton.setProgress(update.overallCompleted(), update.overallTotal());
					if (update.analysis() != null) {
						Simulation target = availableTargets.get(update.index());
						target.setMonteCarloAnalysis(update.analysis());
						if (((SimulationChoice) resultChoice.getSelectedItem()).simulation() == target) showSelectedResults();
					}
				}
			}

		};
		worker.execute();
	}

	private void finishBatch(boolean cancelled) {
		if (cancelled) {
			for (int row = 0; row < targetModel.getRowCount(); row++) {
				String status = String.valueOf(targetModel.getValueAt(row, 2));
				if (status.equals(trans.get("MonteCarloDialog.queued")) ||
						status.equals(trans.get("MonteCarloDialog.running"))) {
					targetModel.setValueAt(trans.get("MonteCarloDialog.cancelled"), row, 2);
				}
			}
		}
		worker = null;
		setRunning(false);
		if (!cancelled) runButton.setProgress(1, 1);
		runButton.setText(trans.get("MonteCarloDialog.runAll") + " — " +
				trans.get(cancelled ? "MonteCarloDialog.cancelled" : "MonteCarloDialog.complete"));
		if (closeWhenWorkerStops) dispose();
	}

	private void setRunning(boolean running) {
		// A batch runs from the settings copied into each target by runAll().  Keep the
		// editor live so it can be prepared for the next batch; recursively disabling
		// and restoring Swing children left some controls permanently disabled after a
		// run or a resize/reparent operation.
		targetTable.setEnabled(!running);
		applyButton.setEnabled(!running && !selectedTargets().isEmpty());
		runButton.setEnabled(!running && !selectedTargets().isEmpty());
		useTable.setEnabled(!running);
		cancelButton.setText(trans.get(running ? "MonteCarloDialog.cancel" : "MonteCarloDialog.close"));
		if (!running) updateSelectionUi();
	}

	private void cancelOrClose(boolean windowClosing) {
		if (worker != null) {
			closeWhenWorkerStops |= windowClosing;
			worker.cancel(true);
			runButton.setText(trans.get("MonteCarloDialog.runAll") + " — " +
					trans.get("MonteCarloDialog.cancelling"));
			return;
		}
		dispose();
	}

	private void exportSelected() {
		SimulationChoice choice = (SimulationChoice) resultChoice.getSelectedItem();
		if (choice == null || choice.simulation().getMonteCarloAnalysis() == null) {
			JOptionPane.showMessageDialog(this, trans.get("MonteCarloDialog.noResults"),
					trans.get("MonteCarloDialog.export"), JOptionPane.WARNING_MESSAGE);
			return;
		}
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle(trans.get("MonteCarloDialog.exportFolder"));
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setAcceptAllFileFilterUsed(false);
		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
		File directory = chooser.getSelectedFile();
		try {
			MonteCarloSetupPanel.exportBatchResults(choice.simulation(),
					choice.simulation().getMonteCarloAnalysis().getRecords(), Path.of(directory.getAbsolutePath()));
			JOptionPane.showMessageDialog(this, trans.get("MonteCarloDialog.exportComplete"),
					trans.get("MonteCarloDialog.export"), JOptionPane.INFORMATION_MESSAGE);
		} catch (Exception exception) {
			JOptionPane.showMessageDialog(this, exception.getMessage(), trans.get("MonteCarloDialog.export"),
					JOptionPane.ERROR_MESSAGE);
		}
	}

	private void showSelectedResults() {
		SimulationChoice choice = (SimulationChoice) resultChoice.getSelectedItem();
		resultHost.removeAll();
		if (choice != null) {
			MonteCarloAnalysis analysis = choice.simulation().getMonteCarloAnalysis();
			if (analysis == null) {
				resultState.setText(trans.get("MonteCarloDialog.noSavedResults"));
			} else if (analysis.isStale()) {
				resultState.setText(trans.get("MonteCarloDialog.stale") + ": " + analysis.getStaleReason());
			} else {
				resultState.setText(trans.get("MonteCarloDialog.current"));
			}
			resultHost.add(new MonteCarloVisualizationPanel(choice.simulation()), BorderLayout.CENTER);
		}
		resultHost.revalidate();
		resultHost.repaint();
	}

	private void updateSelectionUi() {
		List<Simulation> selected = selectedTargets();
		boolean canApply = worker == null && !selected.isEmpty();
		applyButton.setEnabled(canApply);
		runButton.setEnabled(canApply);
		sharedNotice.setText(selected.isEmpty()
				? trans.get("MonteCarloDialog.noTargetsSelected")
				: selectedSettingsDiffer(selected)
						? trans.get("MonteCarloDialog.mixedWarning")
						: trans.get("MonteCarloDialog.sharedSettings"));
		for (int row = 0; row < targetModel.getRowCount(); row++) {
			boolean checked = Boolean.TRUE.equals(targetModel.getValueAt(row, 0));
			String status = String.valueOf(targetModel.getValueAt(row, 2));
			if (!checked && (status.equals(trans.get("MonteCarloDialog.queued")) ||
					status.equals(trans.get("MonteCarloDialog.notSelected")))) {
				targetModel.setValueAt(trans.get("MonteCarloDialog.notSelected"), row, 2);
			} else if (checked && status.equals(trans.get("MonteCarloDialog.notSelected"))) {
				targetModel.setValueAt(trans.get("MonteCarloDialog.queued"), row, 2);
			}
		}
	}

	private List<Simulation> selectedTargets() {
		List<Simulation> selected = new ArrayList<>();
		for (int row = 0; row < availableTargets.size(); row++) {
			if (Boolean.TRUE.equals(targetModel.getValueAt(row, 0))) selected.add(availableTargets.get(row));
		}
		return selected;
	}

	private List<QueuedTarget> selectedTargetRows() {
		List<QueuedTarget> selected = new ArrayList<>();
		for (int row = 0; row < availableTargets.size(); row++) {
			if (Boolean.TRUE.equals(targetModel.getValueAt(row, 0))) {
				selected.add(new QueuedTarget(row, availableTargets.get(row)));
			}
		}
		return List.copyOf(selected);
	}

	private boolean selectedSettingsDiffer(List<Simulation> targets) {
		Config shared = workingExtension.getConfig();
		for (Simulation target : targets) {
			if (!configsEqual(shared, configOf(target))) return true;
		}
		return false;
	}

	private static Config configOf(Simulation simulation) {
		MonteCarloExtension extension = findExtension(simulation);
		return extension == null ? new Config() : extension.getConfig();
	}

	private static boolean configsEqual(Config first, Config second) {
		if (!first.keySet().equals(second.keySet())) return false;
		for (String key : first.keySet()) {
			Object a = first.get(key, null);
			Object b = second.get(key, null);
			if (!java.util.Objects.equals(a, b)) return false;
		}
		return true;
	}

	private static MonteCarloExtension findExtension(Simulation simulation) {
		for (SimulationExtension extension : simulation.getSimulationExtensions()) {
			if (extension instanceof MonteCarloExtension monteCarlo) return monteCarlo;
		}
		return null;
	}

	private static JTextArea wrappingText(String text) {
		JTextArea area = new JTextArea(text);
		area.setEditable(false);
		area.setOpaque(false);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.setFocusable(false);
		area.setBorder(null);
		return area;
	}

	private record SimulationChoice(Simulation simulation) {
		@Override public String toString() { return simulation.getName(); }
	}

	private record QueuedTarget(int row, Simulation simulation) { }

	private record TargetUpdate(int index, String status, String progress,
			MonteCarloAnalysis analysis, boolean failed, long overallCompleted, long overallTotal) {
		static TargetUpdate status(int index, String status, String progress,
				long overallCompleted, long overallTotal) {
			return new TargetUpdate(index, status, progress, null, false, overallCompleted, overallTotal);
		}
		static TargetUpdate completed(int index, MonteCarloAnalysis analysis,
				long overallCompleted, long overallTotal) {
			return new TargetUpdate(index, trans.get("MonteCarloDialog.done"), "100%", analysis, false,
					overallCompleted, overallTotal);
		}
		static TargetUpdate failed(int index, String message,
				long overallCompleted, long overallTotal) {
			return new TargetUpdate(index, trans.get("MonteCarloDialog.failed") + ": " + message, "", null, true,
					overallCompleted, overallTotal);
		}
	}

	/** A normal action button whose background visibly fills as aggregate work completes. */
	static final class ProgressButton extends JButton {
		private double progress;

		ProgressButton(String text) {
			super(text);
		}

		void setProgress(long completed, long total) {
			progress = total <= 0 ? 0.0 : Math.max(0.0, Math.min(1.0, (double) completed / total));
			setText(trans.get("MonteCarloDialog.runAll") + " — " + Math.round(progress * 100.0) + "%");
			getAccessibleContext().setAccessibleDescription(
					Math.round(progress * 100.0) + "% " + trans.get("MonteCarloDialog.complete"));
			repaint();
		}

		double getProgressFraction() {
			return progress;
		}

		@Override protected void paintComponent(Graphics graphics) {
			super.paintComponent(graphics);
			if (progress <= 0.0 || getWidth() <= 0 || getHeight() <= 0) return;

			Graphics2D fill = (Graphics2D) graphics.create();
			try {
				Color progressColor = UIManager.getColor("ProgressBar.foreground");
				if (progressColor == null) progressColor = new Color(0x3A, 0x8D, 0xDE);
				fill.setComposite(AlphaComposite.SrcOver.derive(0.62f));
				fill.setColor(progressColor);
				int inset = 2;
				int fillWidth = (int) Math.round((getWidth() - inset * 2) * progress);
				fill.fillRect(inset, inset, fillWidth, Math.max(0, getHeight() - inset * 2));
			} finally {
				fill.dispose();
			}
			// Repaint the label above the translucent fill so it remains crisp at every percentage.
			paintProgressLabel(graphics);
		}

		private void paintProgressLabel(Graphics graphics) {
			String text = getText();
			if (text == null || text.isEmpty()) return;
			Graphics2D label = (Graphics2D) graphics.create();
			try {
				label.setFont(getFont());
				label.setColor(getForeground());
				java.awt.FontMetrics metrics = label.getFontMetrics();
				int x = Math.max(0, (getWidth() - metrics.stringWidth(text)) / 2);
				int y = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
				label.drawString(text, x, y);
			} finally {
				label.dispose();
			}
		}
	}

	private static final class WrappingRenderer extends DefaultTableCellRenderer {
		@Override public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
				boolean focused, int row, int column) {
			JTextArea area = wrappingText(value == null ? "" : value.toString());
			area.setFont(table.getFont());
			if (selected) {
				area.setBackground(table.getSelectionBackground());
				area.setForeground(table.getSelectionForeground());
				area.setOpaque(true);
			}
			return area;
		}
	}
}

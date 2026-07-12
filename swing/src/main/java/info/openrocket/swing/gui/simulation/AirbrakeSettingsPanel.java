package info.openrocket.swing.gui.simulation;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.ItemEvent;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import info.openrocket.core.simulation.SimulationOptions;
import info.openrocket.core.unit.UnitGroup;
import info.openrocket.swing.gui.SpinnerEditor;
import info.openrocket.swing.gui.adaptors.DoubleModel;
import info.openrocket.swing.gui.components.UnitSelector;
import net.miginfocom.swing.MigLayout;

class AirbrakeSettingsPanel extends JPanel {
	private final SimulationOptions options;

	AirbrakeSettingsPanel(SimulationOptions options) {
		super(new MigLayout("fillx, wrap 3, ins 0", "[right,shrink 0]10[grow,fill,shrink 100]10[]"));
		this.options = options;
		buildUi();
	}

	private void buildUi() {
		add(new JLabel("CFD data CSV:"));
		final JTextField pathField = new JTextField(options.getCfdDataFilePath());
		configurePathField(pathField);
		// Keep the CFD data path field visually bounded so long paths don't blow out the column width.
		pathField.setMaximumSize(new java.awt.Dimension(400, pathField.getMaximumSize().height));
		pathField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				options.setCfdDataFilePath(pathField.getText());
				pathField.setToolTipText(pathField.getText());
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				options.setCfdDataFilePath(pathField.getText());
				pathField.setToolTipText(pathField.getText());
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				options.setCfdDataFilePath(pathField.getText());
				pathField.setToolTipText(pathField.getText());
			}
		});
		add(pathField, "growx, wmin 0, wmax 400");
		JButton browse = new JButton("Browse...");
		browse.addActionListener(e -> {
			JFileChooser chooser = new JFileChooser();
			chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
			if (chooser.showOpenDialog(AirbrakeSettingsPanel.this) == JFileChooser.APPROVE_OPTION) {
				String selectedPath = chooser.getSelectedFile().getAbsolutePath();
				pathField.setText(selectedPath);
				options.setCfdDataFilePath(selectedPath);
			}
		});
		add(browse, "wrap");

		addDoubleSpinner("Airbrake area:", "ReferenceArea", UnitGroup.UNITS_AREA);
		addDoubleSpinner("Reference length:", "ReferenceLength", UnitGroup.UNITS_DISTANCE);
		addDoubleSpinner("Target apogee:", "TargetApogee", UnitGroup.UNITS_DISTANCE);
		addDoubleSpinner("Max Mach for deployment:", "MaxMachForDeployment", null);
		addDoubleSpinner("Apogee tolerance (+/-):", "ApogeeToleranceMeters", UnitGroup.UNITS_DISTANCE);

		JPanel burn = new JPanel(new MigLayout("insets 8, fillx", "[right]10[grow,fill][right]10[120!]"));
		burn.setBorder(new TitledBorder("Burnout-only deployment"));

		JCheckBox cbBurnOnly = new JCheckBox("Deploy airbrakes only after motor burnout");
		cbBurnOnly.setToolTipText("When checked, ignores apogee prediction and deploys fully a set time after burnout.");
		cbBurnOnly.setSelected(options.isDeployAfterBurnoutOnly());

		JSpinner delaySpinner = new JSpinner(new javax.swing.SpinnerNumberModel(
				Math.max(0.0, options.getDeployAfterBurnoutDelayS()),
				0.0,
				60.0,
				0.1));
		((JSpinner.DefaultEditor) delaySpinner.getEditor()).getTextField().setColumns(6);
		JLabel delayLabel = new JLabel("Delay after burnout:");
		JLabel secondsLabel = new JLabel("s");

		Runnable syncBurnoutControls = () -> {
			boolean enabled = cbBurnOnly.isSelected();
			delaySpinner.setEnabled(enabled);
			delayLabel.setEnabled(enabled);
			secondsLabel.setEnabled(enabled);
		};
		syncBurnoutControls.run();

		cbBurnOnly.addActionListener(e -> {
			options.setDeployAfterBurnoutOnly(cbBurnOnly.isSelected());
			syncBurnoutControls.run();
		});

		delaySpinner.addChangeListener(e -> {
			double value = ((Number) delaySpinner.getValue()).doubleValue();
			options.setDeployAfterBurnoutDelayS(Math.max(0.0, value));
		});

		burn.add(cbBurnOnly, "span 3, wrap");
		burn.add(delayLabel);
		burn.add(delaySpinner, "growx");
		burn.add(secondsLabel, "wrap");
		add(burn, "span 3, growx, wmin 0, wrap");

		JPanel debug = new JPanel(new MigLayout("fillx, insets 8, wrap 2", "[right,shrink 0]10[grow,fill,shrink 100]"));
		debug.setBorder(new TitledBorder("Debug"));

		JCheckBox enableDebug = new JCheckBox("Enable debug mode");
		enableDebug.setSelected(options.isDebugEnabled());
		enableDebug.addItemListener(e -> options.setDebugEnabled(e.getStateChange() == ItemEvent.SELECTED));
		debug.add(enableDebug, "span 2");

		JCheckBox alwaysOpen = new JCheckBox("Always-open override for airbrakes");
		alwaysOpen.setSelected(options.isDbgAlwaysOpen());
		alwaysOpen.addItemListener(e -> options.setDbgAlwaysOpen(e.getStateChange() == ItemEvent.SELECTED));
		debug.add(alwaysOpen, "span 2");

		debug.add(new JLabel("Forced deploy fraction (0-1):"));
		JSpinner forcedFraction = new JSpinner(new javax.swing.SpinnerNumberModel(options.getDbgForcedDeployFrac(), 0.0, 1.0, 0.01));
		forcedFraction.addChangeListener(e -> {
			Object value = forcedFraction.getValue();
			double fraction = (value instanceof Number) ? ((Number) value).doubleValue() : 0.0;
			options.setDbgForcedDeployFrac(fraction);
		});
		debug.add(forcedFraction, "growx");

		JCheckBox tracePredictor = new JCheckBox("Trace apogee predictor");
		tracePredictor.setSelected(options.isDbgTracePredictor());
		tracePredictor.addItemListener(e -> options.setDbgTracePredictor(e.getStateChange() == ItemEvent.SELECTED));
		debug.add(tracePredictor, "span 2");

		JCheckBox traceController = new JCheckBox("Trace controller");
		traceController.setSelected(options.isDbgTraceController());
		traceController.addItemListener(e -> options.setDbgTraceController(e.getStateChange() == ItemEvent.SELECTED));
		debug.add(traceController, "span 2");

		JCheckBox writeCsv = new JCheckBox("Write CSV traces");
		writeCsv.setSelected(options.isDbgWriteCsv());
		writeCsv.addItemListener(e -> options.setDbgWriteCsv(e.getStateChange() == ItemEvent.SELECTED));
		debug.add(writeCsv, "span 2");

		debug.add(new JLabel("CSV directory (optional):"));
		JTextField csvDirField = new JTextField(options.getDbgCsvDir());
		configurePathField(csvDirField);
		csvDirField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				options.setDbgCsvDir(csvDirField.getText());
				csvDirField.setToolTipText(csvDirField.getText());
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				options.setDbgCsvDir(csvDirField.getText());
				csvDirField.setToolTipText(csvDirField.getText());
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				options.setDbgCsvDir(csvDirField.getText());
				csvDirField.setToolTipText(csvDirField.getText());
			}
		});
		debug.add(csvDirField, "growx, wmin 0");

		JCheckBox liveConsole = new JCheckBox("Show live debug console");
		liveConsole.setSelected(options.isDbgShowConsole());
		liveConsole.addItemListener(e -> options.setDbgShowConsole(e.getStateChange() == ItemEvent.SELECTED));
		debug.add(liveConsole, "span 2");

		add(debug, "span 3, growx, wmin 0");
	}

	private void addDoubleSpinner(String label, String property, UnitGroup unitGroup) {
		add(new JLabel(label));
		DoubleModel model = unitGroup != null
				? new DoubleModel(options, property, unitGroup, 0)
				: new DoubleModel(options, property, UnitGroup.UNITS_COEFFICIENT, 0);
		JSpinner spinner = new JSpinner(model.getSpinnerModel());
		spinner.setEditor(new SpinnerEditor(spinner));
		add(spinner, "growx, wmin 0");
		if (unitGroup != null) {
			add(new UnitSelector(model), "wrap");
		} else {
			add(new JLabel(), "wrap");
		}
	}

	void setControlsEnabled(boolean enabled) {
		setEnabledRecursive(this, enabled);
	}

	private static void setEnabledRecursive(Component component, boolean enabled) {
		component.setEnabled(enabled);
		if (component instanceof Container container) {
			for (Component child : container.getComponents()) {
				setEnabledRecursive(child, enabled);
			}
		}
		if (component instanceof JComponent jComponent) {
			jComponent.repaint();
		}
	}

	private static void configurePathField(JTextField field) {
		field.setColumns(1);
		field.setToolTipText(field.getText());
		SimulationTabLayoutUtils.forceViewportWidth(field);
	}
}

package info.openrocket.swing.gui.simulation;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog.ModalityType;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.MenuElement;
import javax.swing.SwingUtilities;

import com.google.inject.Key;

import info.openrocket.core.aerodynamics.rom.RomFallbackMode;
import info.openrocket.core.aerodynamics.rom.RomMode;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.l10n.Translator;
import info.openrocket.core.models.gravity.GravityModelType;
import info.openrocket.core.montecarlo.MonteCarloExtension;
import info.openrocket.core.preferences.ApplicationPreferences;
import info.openrocket.core.simulation.RK4SimulationStepper;
import info.openrocket.core.simulation.SimulationOptions;
import info.openrocket.core.simulation.SimulationStepperMethod;
import info.openrocket.core.simulation.extension.SimulationExtension;
import info.openrocket.core.simulation.extension.SimulationExtensionProvider;
import info.openrocket.core.startup.Application;
import info.openrocket.core.unit.UnitGroup;
import info.openrocket.core.util.GeodeticComputationStrategy;
import info.openrocket.swing.gui.SpinnerEditor;
import info.openrocket.swing.gui.adaptors.DoubleModel;
import info.openrocket.swing.gui.adaptors.EnumModel;
import info.openrocket.swing.gui.components.BasicSlider;
import info.openrocket.swing.gui.components.DescriptionArea;
import info.openrocket.swing.gui.components.StyledLabel;
import info.openrocket.swing.gui.components.StyledLabel.Style;
import info.openrocket.swing.gui.components.UnitSelector;
import info.openrocket.swing.gui.theme.UITheme;
import info.openrocket.swing.gui.util.GUIUtil;
import info.openrocket.swing.gui.util.Icons;
import info.openrocket.swing.simulation.extension.SwingSimulationExtensionConfigurator;
import net.miginfocom.swing.MigLayout;

class SimulationOptionsPanel extends SimulationScrollablePanel {

	private static final long serialVersionUID = -5251458539346201239L;
	private static final String LEGACY_AIRBRAKES_EXTENSION_ID = "com.airbrakesplugin.AirbrakeExtension";

	private static final Translator trans = Application.getTranslator();
	private static final String PANEL_LAYOUT = "fillx, insets 6, gap 8 8, wrap 2";
	private static final String PANEL_COLUMNS = "[grow 0.88,fill][grow 1.12,fill]";
	private static final String FORM_COLUMNS = "[right][grow,fill][pref!][grow,fill]";

	private final OpenRocketDocument document;
	final Simulation simulation;
	private final SimulationOptions options;

	private JLabel aerodynamicMethodValue;
	private JTextArea aerodynamicLookupSummaryArea;
	private JCheckBox romEnabledCheckBox;
	private JComboBox<RomMode> romModeCombo;
	private JComboBox<RomFallbackMode> romFallbackCombo;
	private JCheckBox romDiagnosticsCheckBox;
	private JTextArea romStatusArea;
	private boolean updatingRomControls;

	private JPanel currentExtensions;
	final JPopupMenu extensionMenu;
	JMenu extensionMenuCopyExtension;
	private JCheckBox monteCarloEnabledCheckBox;
	private JButton monteCarloConfigureButton;
	private JCheckBox airbrakesEnabledCheckBox;
	private AirbrakeSettingsPanel airbrakeSettingsPanel;

	private JSpinner gravitySpinner;
	private UnitSelector gravityUnit;
	private BasicSlider gravitySlider;
	private JLabel gravityLabel;

	private static Color textColor;
	private static Color dimTextColor;
	private static Color infoTextColor;

	static {
		initColors();
	}

	SimulationOptionsPanel(OpenRocketDocument document, final Simulation simulation) {
		super(new MigLayout(PANEL_LAYOUT, PANEL_COLUMNS, ""));
		this.document = document;
		this.simulation = simulation;
		this.options = simulation.getOptions();

		final SimulationOptions conditions = this.options;

		JPanel columnsPanel = new JPanel(new MigLayout("fillx, insets 0, gap 8 8, wrap 2",
				PANEL_COLUMNS, ""));
		add(columnsPanel, "span 2, growx, pushx, wrap");

		JPanel simulatorOptionsPanel = new JPanel(new MigLayout("fillx, insets 8, gapx 8, gapy 6, wrap 1",
				"[grow,fill]", ""));
		simulatorOptionsPanel.setBorder(BorderFactory.createTitledBorder(trans.get("simedtdlg.border.Simopt")));
		columnsPanel.add(simulatorOptionsPanel, "growx, top");

		JPanel optionsForm = new JPanel(new MigLayout("fillx, insets 0, gapx 8, gapy 6", FORM_COLUMNS, ""));
		simulatorOptionsPanel.add(optionsForm, "growx, wrap");

		String tip = trans.get("simedtdlg.lbl.ttip.Calcmethod");
		JLabel label = new JLabel(trans.get("simedtdlg.lbl.Calcmethod"));
		label.setToolTipText(tip);
		optionsForm.add(label, "gapright para");

		aerodynamicMethodValue = new JLabel();
		aerodynamicMethodValue.setToolTipText(tip);
		optionsForm.add(aerodynamicMethodValue, "span 3, growx, wrap");

		tip = trans.get("simedtdlg.lbl.ttip.Simmethod1") + trans.get("simedtdlg.lbl.ttip.Simmethod2");
		label = new JLabel(trans.get("simedtdlg.lbl.Simmethod"));
		label.setToolTipText(tip);
		optionsForm.add(label, "gapright para");

		EnumModel<SimulationStepperMethod> simulationStepperMethodChoice = new EnumModel<>(
				conditions, "SimulationStepperMethodChoice");
		final JComboBox<SimulationStepperMethod> simulationStepperMethodChoiceCombo =
				new JComboBox<>(simulationStepperMethodChoice);
		ActionListener simulationStepperMethodChoiceComboTTipListener = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				SimulationStepperMethod selected =
						(SimulationStepperMethod) simulationStepperMethodChoiceCombo.getSelectedItem();
				simulationStepperMethodChoiceCombo.setToolTipText(selected != null ? selected.getDescription() : null);
			}
		};
		simulationStepperMethodChoiceCombo.addActionListener(simulationStepperMethodChoiceComboTTipListener);
		simulationStepperMethodChoiceComboTTipListener.actionPerformed(null);
		optionsForm.add(simulationStepperMethodChoiceCombo, "span 3, growx, wrap");

		label = new JLabel("Pathline runtime");
		label.setToolTipText("Phase I tuning and test runs now use the pathline ROM only.");
		optionsForm.add(label, "gaptop para, gapright para");

		aerodynamicLookupSummaryArea = createWrappingTextArea(infoTextColor);
		optionsForm.add(aerodynamicLookupSummaryArea, "gapleft para, span 4, growx, wrap para");

		label = new JLabel("Phase I ROM");
		label.setToolTipText("Enable the Phase I pathline ROM, choose its operating preset, and select fallback behavior.");
		optionsForm.add(label, "gapright para");

		optionsForm.add(createRomControlPanel(), "span 3, growx, wrap");

		romStatusArea = createWrappingTextArea(infoTextColor);
		optionsForm.add(romStatusArea, "gapleft para, span 4, growx, wrap para");

		label = new JLabel(trans.get("simedtdlg.lbl.GeodeticMethod"));
		label.setToolTipText(trans.get("simedtdlg.lbl.ttip.GeodeticMethodTip"));
		optionsForm.add(label, "gapright para");

		EnumModel<GeodeticComputationStrategy> gcsModel = new EnumModel<>(conditions, "GeodeticComputation");
		final JComboBox<GeodeticComputationStrategy> gcsCombo = new JComboBox<>(gcsModel);
		ActionListener gcsTTipListener = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				GeodeticComputationStrategy gcs = (GeodeticComputationStrategy) gcsCombo.getSelectedItem();
				gcsCombo.setToolTipText(gcs != null ? gcs.getDescription() : null);
			}
		};
		gcsCombo.addActionListener(gcsTTipListener);
		gcsTTipListener.actionPerformed(null);
		optionsForm.add(gcsCombo, "span 3, growx, wrap");

		label = new JLabel(trans.get("simedtdlg.lbl.GravityModel"));
		label.setToolTipText(trans.get("simedtdlg.lbl.ttip.GravityModel"));
		optionsForm.add(label, "gapright para");

		EnumModel<GravityModelType> gravityModelTypeModel = new EnumModel<>(conditions, "GravityModelType");
		final JComboBox<GravityModelType> gravityModelCombo = new JComboBox<>(gravityModelTypeModel);
		ActionListener gravityModelTTipListener = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				GravityModelType selectedType = (GravityModelType) gravityModelCombo.getSelectedItem();
				if (selectedType == GravityModelType.WGS) {
					gravityModelCombo.setToolTipText(trans.get("simedtdlg.GravityModel.WGS84.ttip"));
				} else if (selectedType == GravityModelType.CONSTANT) {
					gravityModelCombo.setToolTipText(trans.get("simedtdlg.GravityModel.Constant.ttip"));
				}
			}
		};
		gravityModelCombo.addActionListener(gravityModelTTipListener);
		gravityModelTTipListener.actionPerformed(null);
		optionsForm.add(gravityModelCombo, "span 3, growx, wrap");

		gravityLabel = new JLabel(trans.get("simedtdlg.lbl.GravityValue"));
		tip = trans.get("simedtdlg.lbl.ttip.GravityValue");
		gravityLabel.setToolTipText(tip);
		optionsForm.add(gravityLabel, "gapright para, hidemode 3");

		DoubleModel gravityModel = new DoubleModel(conditions, "ConstantGravity", UnitGroup.UNITS_ACCELERATION, 0);
		gravitySpinner = new JSpinner(gravityModel.getSpinnerModel());
		gravitySpinner.setEditor(new SpinnerEditor(gravitySpinner));
		gravitySpinner.setToolTipText(tip);
		optionsForm.add(gravitySpinner, "growx, hidemode 3");

		gravityUnit = new UnitSelector(gravityModel);
		gravityUnit.setToolTipText(tip);
		optionsForm.add(gravityUnit, "hidemode 3");
		gravitySlider = new BasicSlider(gravityModel.getSliderModel(0, 20));
		gravitySlider.setToolTipText(tip);
		optionsForm.add(gravitySlider, "growx, hidemode 3, wrap");

		ActionListener gravityModelListener = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				GravityModelType selectedType = (GravityModelType) gravityModelCombo.getSelectedItem();
				boolean isConstant = selectedType == GravityModelType.CONSTANT;
				gravityLabel.setVisible(isConstant);
				gravitySpinner.setVisible(isConstant);
				gravityUnit.setVisible(isConstant);
				gravitySlider.setVisible(isConstant);
				optionsForm.revalidate();
				optionsForm.repaint();
			}
		};
		gravityModelCombo.addActionListener(gravityModelListener);
		gravityModelListener.actionPerformed(null);

		label = new JLabel(trans.get("simedtdlg.lbl.Timestep"));
		tip = trans.get("simedtdlg.lbl.ttip.Timestep1")
				+ trans.get("simedtdlg.lbl.ttip.Timestep2")
				+ " "
				+ UnitGroup.UNITS_TIME_STEP.toStringUnit(RK4SimulationStepper.RECOMMENDED_TIME_STEP)
				+ ".";
		label.setToolTipText(tip);
		optionsForm.add(label, "gaptop para, gapright para");

		DoubleModel timeStepModel = new DoubleModel(conditions, "TimeStep", UnitGroup.UNITS_TIME_STEP, 0.01, 1);
		JSpinner timeStepSpinner = new JSpinner(timeStepModel.getSpinnerModel());
		timeStepSpinner.setEditor(new SpinnerEditor(timeStepSpinner));
		timeStepSpinner.setToolTipText(tip);
		optionsForm.add(timeStepSpinner, "growx");

		UnitSelector timeStepUnit = new UnitSelector(timeStepModel);
		timeStepUnit.setToolTipText(tip);
		optionsForm.add(timeStepUnit);
		BasicSlider timeStepSlider = new BasicSlider(timeStepModel.getSliderModel(0.01, 0.2));
		timeStepSlider.setToolTipText(tip);
		optionsForm.add(timeStepSlider, "growx, wrap");

		label = new JLabel(trans.get("simedtdlg.lbl.MaxSimTime"));
		tip = trans.get("simedtdlg.lbl.ttip.MaxSimTime");
		label.setToolTipText(tip);
		optionsForm.add(label, "gapright para");

		DoubleModel maxSimulationTimeModel = new DoubleModel(conditions, "MaxSimulationTime",
				UnitGroup.UNITS_LONG_TIME, 1);
		JSpinner maxSimulationTimeSpinner = new JSpinner(maxSimulationTimeModel.getSpinnerModel());
		maxSimulationTimeSpinner.setEditor(new SpinnerEditor(maxSimulationTimeSpinner));
		maxSimulationTimeSpinner.setToolTipText(tip);
		optionsForm.add(maxSimulationTimeSpinner, "growx");

		UnitSelector maxSimulationTimeUnit = new UnitSelector(maxSimulationTimeModel);
		maxSimulationTimeUnit.setToolTipText(tip);
		optionsForm.add(maxSimulationTimeUnit);
		optionsForm.add(new JPanel(), "growx, wrap");

		monteCarloEnabledCheckBox = new JCheckBox("Enable Monte Carlo analysis");
		monteCarloEnabledCheckBox.setToolTipText("Turn Monte Carlo batch analysis on or off for this simulation.");
		monteCarloEnabledCheckBox.addActionListener(e -> {
			if (monteCarloEnabledCheckBox.isSelected()) {
				MonteCarloExtension ext = ensureMonteCarloExtension();
				ext.setEnabled(true);
			} else {
				removeMonteCarloExtensions();
			}
			updateCurrentExtensions();
			updateMonteCarloControls();
		});
		optionsForm.add(monteCarloEnabledCheckBox, "span 2, gaptop para, alignx left");

		monteCarloConfigureButton = new JButton("Configure Monte Carlo...");
		monteCarloConfigureButton.addActionListener(e -> {
			MonteCarloExtension ext = ensureMonteCarloExtension();
			ext.setEnabled(true);
			SwingSimulationExtensionConfigurator configurator = findConfigurator(ext);
			if (configurator != null) {
				configurator.configure(ext, simulation, SwingUtilities.windowForComponent(SimulationOptionsPanel.this));
				updateCurrentExtensions();
				updateMonteCarloControls();
			} else {
				JOptionPane.showMessageDialog(
						SwingUtilities.windowForComponent(SimulationOptionsPanel.this),
						"Monte Carlo configurator plugin was not found.",
						"Monte Carlo",
						JOptionPane.WARNING_MESSAGE);
			}
		});
		optionsForm.add(monteCarloConfigureButton, "span 2, alignx left, wrap");

		airbrakesEnabledCheckBox = new JCheckBox("Enable airbrakes");
		airbrakesEnabledCheckBox.setToolTipText("Enable native airbrakes for this simulation.");
		airbrakesEnabledCheckBox.addActionListener(e -> {
			options.setAirbrakesEnabled(airbrakesEnabledCheckBox.isSelected());
			updateAirbrakeControls();
		});
		optionsForm.add(airbrakesEnabledCheckBox, "span 4, gaptop para, alignx left, wrap");

		airbrakeSettingsPanel = new AirbrakeSettingsPanel(conditions);
		optionsForm.add(airbrakeSettingsPanel, "span 4, growx, gapleft para, wrap para");
		updateAirbrakeControls();

		JPanel extensionsPanel = new JPanel(new MigLayout("fillx, insets 8, gap 6 6, wrap 1", "[grow,fill]", ""));
		extensionsPanel.setBorder(BorderFactory.createTitledBorder(trans.get("simedtdlg.border.SimExt")));
		columnsPanel.add(extensionsPanel, "grow, top");

		DescriptionArea desc = new DescriptionArea(4);
		desc.setText(trans.get("simedtdlg.SimExt.desc"));
		extensionsPanel.add(desc, "growx");

		final JButton addExtension = new JButton(trans.get("simedtdlg.SimExt.add"));
		extensionMenu = getExtensionMenu();
		addExtension.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent ev) {
				extensionMenu.show(addExtension, 5, addExtension.getBounds().height);
			}
		});
		extensionsPanel.add(addExtension, "growx");

		currentExtensions = new JPanel(new MigLayout("fillx, gap 0 6, ins 0"));
		JScrollPane scroll = new JScrollPane(currentExtensions);
		scroll.setForeground(textColor);
		scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.getHorizontalScrollBar().setUnitIncrement(16);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		extensionsPanel.add(scroll, "growx, pushy, growy, hmin 180lp");

		JButton resetBtn = new JButton(trans.get("simedtdlg.but.resettodefault"));
		resetBtn.setToolTipText(trans.get("simedtdlg.but.ttip.resettodefault")
				+ UnitGroup.UNITS_SHORT_TIME.toStringUnit(RK4SimulationStepper.RECOMMENDED_TIME_STEP)
				+ ").");
		resetBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				ApplicationPreferences preferences = Application.getPreferences();
				conditions.setTimeStep(preferences.getDouble(
						ApplicationPreferences.SIMULATION_TIME_STEP,
						RK4SimulationStepper.RECOMMENDED_TIME_STEP));
				conditions.setMaxSimulationTime(preferences.getDouble(
						ApplicationPreferences.SIMULATION_MAX_TIME,
						RK4SimulationStepper.RECOMMENDED_MAX_TIME));
				conditions.setGeodeticComputation(preferences.getEnum(
						ApplicationPreferences.GEODETIC_COMPUTATION,
						GeodeticComputationStrategy.SPHERICAL));
			}
		});

		JButton saveBtn = new JButton(trans.get("simedtdlg.but.savedefault"));
		saveBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				ApplicationPreferences preferences = Application.getPreferences();
				preferences.setTimeStep(conditions.getTimeStep());
				preferences.setMaxSimulationTime(conditions.getMaxSimulationTime());
				preferences.setGeodeticComputation(conditions.getGeodeticComputation());
			}
		});

		JPanel defaultsPanel = new JPanel(new MigLayout("ins 0", "[][grow][]", ""));
		defaultsPanel.add(resetBtn);
		defaultsPanel.add(new JPanel(), "growx");
		defaultsPanel.add(saveBtn);
		add(defaultsPanel, "span 2, growx, wrap");

		updateCurrentExtensions();
		updateMonteCarloControls();

		options.addChangeListener(e -> SwingUtilities.invokeLater(this::refreshRomPresentation));
		refreshRomPresentation();
	}

	private static void initColors() {
		updateColors();
		UITheme.Theme.addUIThemeChangeListener(SimulationOptionsPanel::updateColors);
	}

	public static void updateColors() {
		textColor = UITheme.getColor(UITheme.Keys.TEXT);
		dimTextColor = UITheme.getColor(UITheme.Keys.TEXT_DIM);
		infoTextColor = UITheme.getColor(UITheme.Keys.INFO);
	}

	private JPopupMenu getExtensionMenu() {
		Set<SimulationExtensionProvider> extensions = Application.getInjector().getInstance(new Key<>() {
		});

		JPopupMenu basemenu = new JPopupMenu();

		for (final SimulationExtensionProvider provider : extensions) {
			List<String> ids = provider.getIds();
			for (final String id : ids) {
				if (LEGACY_AIRBRAKES_EXTENSION_ID.equals(id)) {
					continue;
				}
				SimulationExtension candidate = provider.getInstance(id);
				if (isManagedExtension(candidate)) {
					continue;
				}
				List<String> menuItems = provider.getName(id);
				if (menuItems != null) {
					JComponent menu = findMenu(basemenu, menuItems);
					JMenuItem item = new JMenuItem(menuItems.get(menuItems.size() - 1));
					item.addActionListener(new ActionListener() {
						@Override
						public void actionPerformed(ActionEvent arg0) {
							SimulationExtension extension = provider.getInstance(id);
							simulation.getSimulationExtensions().add(extension);
							updateCurrentExtensions();
							SwingSimulationExtensionConfigurator configurator = findConfigurator(extension);
							if (configurator != null) {
								configurator.configure(extension, simulation,
										SwingUtilities.windowForComponent(SimulationOptionsPanel.this));
								updateCurrentExtensions();
							}
						}
					});
					menu.add(item);
				}
			}
		}

		updateExtensionMenuCopyExtension(basemenu);
		return basemenu;
	}

	private void updateExtensionMenuCopyExtension(JPopupMenu extensionMenu) {
		if (extensionMenu == null) {
			return;
		}
		if (this.extensionMenuCopyExtension != null) {
			extensionMenu.remove(this.extensionMenuCopyExtension);
		}

		this.extensionMenuCopyExtension = null;
		for (Simulation sim : document.getSimulations()) {
			List<SimulationExtension> copyableExtensions = sim.getSimulationExtensions().stream()
					.filter(extension -> !isManagedExtension(extension))
					.toList();
			if (copyableExtensions.isEmpty()) {
				continue;
			}

			JMenu menu = new JMenu(sim.getName());
			for (final SimulationExtension ext : copyableExtensions) {
				JMenuItem item = new JMenuItem(ext.getName());
				item.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent arg0) {
						SimulationExtension extension = ext.clone();
						simulation.getSimulationExtensions().add(extension);
						updateCurrentExtensions();
						SwingSimulationExtensionConfigurator configurator = findConfigurator(extension);
						if (configurator != null) {
							configurator.configure(extension, simulation,
									SwingUtilities.windowForComponent(SimulationOptionsPanel.this));
							updateCurrentExtensions();
						}
					}
				});
				menu.add(item);
			}

			if (this.extensionMenuCopyExtension == null) {
				this.extensionMenuCopyExtension = new JMenu(trans.get("simedtdlg.SimExt.copyExtension"));
			}
			this.extensionMenuCopyExtension.add(menu);
		}
		if (this.extensionMenuCopyExtension != null) {
			extensionMenu.add(this.extensionMenuCopyExtension);
		}
	}

	private JComponent findMenu(MenuElement menu, List<String> menuItems) {
		for (int i = 0; i < menuItems.size() - 1; i++) {
			String menuItem = menuItems.get(i);

			MenuElement found = null;
			for (MenuElement e : menu.getSubElements()) {
				if (e instanceof JMenu && ((JMenu) e).getText().equals(menuItem)) {
					found = e;
					break;
				}
			}

			if (found != null) {
				menu = found;
			} else {
				JMenu m = new JMenu(menuItem);
				((JComponent) menu).add(m);
				menu = m;
			}
		}
		return (JComponent) menu;
	}

	private void updateLookupSummary() {
		if (aerodynamicLookupSummaryArea == null) {
			return;
		}
		String summary = "Phase I runtime is pathline-only."
				+ "\nLegacy lookup-table aerodynamic overrides do not participate in active ROM testing."
				+ "\nFallback calculator: pure Barrowman."
				+ "\nBatch and tuning runs force pathline ROM with FORCE_ROM fallback.";
		aerodynamicLookupSummaryArea.setText(summary);
		aerodynamicLookupSummaryArea.setCaretPosition(0);
	}

	private JPanel createRomControlPanel() {
		JPanel panel = new JPanel(new MigLayout("fillx, insets 0, gapx 8, gapy 6", "[grow,fill][grow,fill]", ""));

		romEnabledCheckBox = new JCheckBox("Enable Phase I pathline ROM");
		romEnabledCheckBox.setToolTipText("Use the Phase I pathline reduced-order model instead of plain legacy aerodynamics.");
		romEnabledCheckBox.addActionListener(e -> {
			if (updatingRomControls) {
				return;
			}
			options.setRomEnabled(romEnabledCheckBox.isSelected());
			refreshRomPresentation();
		});
		panel.add(romEnabledCheckBox, "span 2, wrap");

		panel.add(new JLabel("Operating preset:"));
		romModeCombo = new JComboBox<>(RomMode.values());
		romModeCombo.setToolTipText("Controls the Phase I seed density and normal-force gain.");
		configureEnumCombo(romModeCombo, SimulationOptionsPanel::formatRomMode);
		romModeCombo.addActionListener(e -> {
			if (updatingRomControls) {
				return;
			}
			options.setRomMode((RomMode) romModeCombo.getSelectedItem());
			refreshRomPresentation();
		});
		panel.add(romModeCombo, "growx, wrap");

		panel.add(new JLabel("Fallback behavior:"));
		romFallbackCombo = new JComboBox<>(RomFallbackMode.values());
		romFallbackCombo.setToolTipText("Select how the calculator behaves when Phase I confidence drops.");
		configureEnumCombo(romFallbackCombo, SimulationOptionsPanel::formatRomFallbackMode);
		romFallbackCombo.addActionListener(e -> {
			if (updatingRomControls) {
				return;
			}
			options.setRomFallbackMode((RomFallbackMode) romFallbackCombo.getSelectedItem());
			refreshRomPresentation();
		});
		panel.add(romFallbackCombo, "growx, wrap");

		romDiagnosticsCheckBox = new JCheckBox("Enable developer diagnostics");
		romDiagnosticsCheckBox.setToolTipText("Keep per-step Phase I computation snapshots for log export and debugging.");
		romDiagnosticsCheckBox.addActionListener(e -> {
			if (updatingRomControls) {
				return;
			}
			options.setRomDiagnosticsEnabled(romDiagnosticsCheckBox.isSelected());
			refreshRomPresentation();
		});
		panel.add(romDiagnosticsCheckBox, "span 2, wrap");

		return panel;
	}

	private void refreshRomPresentation() {
		updateLookupSummary();
		refreshRomControls();
	}

	private void refreshRomControls() {
		updatingRomControls = true;
		try {
			boolean enabled = options.isRomEnabled();
			romEnabledCheckBox.setSelected(enabled);
			romModeCombo.setSelectedItem(options.getRomMode());
			romFallbackCombo.setSelectedItem(options.getRomFallbackMode());
			romDiagnosticsCheckBox.setSelected(options.isRomDiagnosticsEnabled());

			romModeCombo.setEnabled(enabled);
			romFallbackCombo.setEnabled(enabled);
			romDiagnosticsCheckBox.setEnabled(enabled);

			aerodynamicMethodValue.setText(buildAerodynamicMethodLabel());
			romStatusArea.setText(buildRomStatusSummary());
			romStatusArea.setCaretPosition(0);
		} finally {
			updatingRomControls = false;
		}
	}

	private String buildAerodynamicMethodLabel() {
		if (!options.isRomEnabled()) {
			return trans.get("simedtdlg.lbl.ExtBarrowman") + " (Pathline ROM disabled)";
		}
		return "Pathline ROM - " + formatRomMode(options.getRomMode())
				+ ", " + formatRomFallbackMode(options.getRomFallbackMode());
	}

	private String buildRomStatusSummary() {
		if (!options.isRomEnabled()) {
			return "Pathline ROM is currently disabled. Simulations will use Barrowman fallback only.";
		}
		return "Pathline ROM is active."
				+ "\nMode: " + formatRomMode(options.getRomMode())
				+ " | Fallback: " + formatRomFallbackMode(options.getRomFallbackMode())
				+ " | Diagnostics: " + (options.isRomDiagnosticsEnabled() ? "enabled" : "disabled")
				+ "\nLegacy ROM surfaces and lookup overrides are ignored by the active test runtime."
				+ "\nSeed plan: " + options.getRomSettings().getBodyMeridianSeedCount()
				+ " body meridian seeds, " + options.getRomSettings().getFinSurfaceSeedCount()
				+ " seeds per fin surface."
				+ "\nConfidence gates: transonic band +/- "
				+ String.format("%.2f", options.getRomSettings().getTransonicBandHalfWidth())
				+ " Mach, high-angle threshold "
				+ String.format("%.1f", options.getRomSettings().getHighAngleDeg())
				+ " deg, trusted separation fraction "
				+ String.format("%.0f%%", 100.0 * options.getRomSettings().getMaxTrustedSeparationFraction()) + ".";
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

	private static <T> void configureEnumCombo(JComboBox<T> comboBox, Function<T, String> formatter) {
		comboBox.setRenderer(new DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;

			@Override
			public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
					boolean isSelected, boolean cellHasFocus) {
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				if (value != null) {
					@SuppressWarnings("unchecked")
					T typedValue = (T) value;
					setText(formatter.apply(typedValue));
				}
				return this;
			}
		});
	}

	private MonteCarloExtension findMonteCarloExtension() {
		for (SimulationExtension extension : simulation.getSimulationExtensions()) {
			if (extension instanceof MonteCarloExtension monteCarloExtension) {
				return monteCarloExtension;
			}
		}
		return null;
	}

	private MonteCarloExtension ensureMonteCarloExtension() {
		MonteCarloExtension extension = findMonteCarloExtension();
		if (extension != null) {
			return extension;
		}

		extension = new MonteCarloExtension();
		simulation.getSimulationExtensions().add(extension);
		return extension;
	}

	private void removeMonteCarloExtensions() {
		Iterator<SimulationExtension> iterator = simulation.getSimulationExtensions().iterator();
		while (iterator.hasNext()) {
			if (iterator.next() instanceof MonteCarloExtension) {
				iterator.remove();
			}
		}
	}

	private void updateMonteCarloControls() {
		if (monteCarloEnabledCheckBox == null || monteCarloConfigureButton == null) {
			return;
		}

		MonteCarloExtension extension = findMonteCarloExtension();
		boolean enabled = extension != null && extension.isEnabled();
		monteCarloEnabledCheckBox.setSelected(enabled);
		monteCarloConfigureButton.setEnabled(true);
	}

	private void updateAirbrakeControls() {
		if (airbrakesEnabledCheckBox == null || airbrakeSettingsPanel == null) {
			return;
		}

		boolean enabled = options.isAirbrakesEnabled();
		airbrakesEnabledCheckBox.setSelected(enabled);
		airbrakeSettingsPanel.setVisible(enabled);
		airbrakeSettingsPanel.setControlsEnabled(enabled);
		airbrakeSettingsPanel.revalidate();
		airbrakeSettingsPanel.repaint();
	}

	private void updateCurrentExtensions() {
		currentExtensions.removeAll();

		List<SimulationExtension> visibleExtensions = simulation.getSimulationExtensions().stream()
				.filter(extension -> !isManagedExtension(extension))
				.toList();

		if (visibleExtensions.isEmpty()) {
			StyledLabel l = new StyledLabel(trans.get("simedtdlg.SimExt.noExtensions"), Style.ITALIC);
			l.setForeground(dimTextColor);
			currentExtensions.add(l, "growx, pad 5 5 5 5, wrap");
		} else {
			for (SimulationExtension extension : visibleExtensions) {
				currentExtensions.add(new SimulationExtensionPanel(extension), "growx, wrap");
			}
		}

		updateExtensionMenuCopyExtension(this.extensionMenu);
		updateMonteCarloControls();

		revalidate();
		repaint();
	}

	private class SimulationExtensionPanel extends JPanel {

		private static final long serialVersionUID = -3296795614810745035L;

		SimulationExtensionPanel(final SimulationExtension extension) {
			super(new MigLayout("fillx, gapx 0"));

			setBorder(BorderFactory.createLineBorder(dimTextColor));
			add(new JLabel(extension.getName()), "spanx, growx, wrap");

			JButton button;

			add(new JPanel(), "spanx, split, growx, right");

			if (findConfigurator(extension) != null) {
				button = new JButton(Icons.CONFIGURE);
				button.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						findConfigurator(extension).configure(extension, simulation,
								SwingUtilities.windowForComponent(SimulationOptionsPanel.this));
						updateCurrentExtensions();
					}
				});
				add(button, "right");
			}

			if (extension.getDescription() != null) {
				button = new JButton(Icons.HELP);
				button.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						final JDialog dialog = new JDialog(SwingUtilities.windowForComponent(SimulationOptionsPanel.this),
								extension.getName(), ModalityType.APPLICATION_MODAL);
						JPanel panel = new JPanel(new MigLayout("fill"));
						DescriptionArea area = new DescriptionArea(extension.getDescription(), 10, 0);
						panel.add(area, "width 400lp, wrap para");
						JButton close = new JButton(trans.get("button.close"));
						close.addActionListener(new ActionListener() {
							@Override
							public void actionPerformed(ActionEvent e) {
								dialog.setVisible(false);
							}
						});
						panel.add(close, "right");
						dialog.add(panel);
						GUIUtil.setDisposableDialogOptions(dialog, close);
						dialog.setLocationRelativeTo(SwingUtilities.windowForComponent(SimulationOptionsPanel.this));
						dialog.setVisible(true);
					}
				});
				add(button, "right");
			}

			button = new JButton(Icons.EDIT_DELETE);
			button.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					Iterator<SimulationExtension> iter = simulation.getSimulationExtensions().iterator();
					while (iter.hasNext()) {
						if (iter.next() == extension) {
							iter.remove();
							break;
						}
					}
					updateCurrentExtensions();
				}
			});
			add(button, "right");
		}
	}

	private SwingSimulationExtensionConfigurator findConfigurator(SimulationExtension extension) {
		Set<SwingSimulationExtensionConfigurator> configurators = Application.getInjector().getInstance(new Key<>() {
		});
		for (SwingSimulationExtensionConfigurator configurator : configurators) {
			if (configurator.support(extension)) {
				return configurator;
			}
		}
		return null;
	}

	private static boolean isManagedExtension(SimulationExtension extension) {
		return extension instanceof MonteCarloExtension;
	}

	private static JTextArea createWrappingTextArea(Color foreground) {
		JTextArea area = new JTextArea();
		area.setEditable(false);
		area.setOpaque(false);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.setFocusable(false);
		area.setForeground(foreground);
		area.setBorder(BorderFactory.createEmptyBorder());
		return area;
	}
}

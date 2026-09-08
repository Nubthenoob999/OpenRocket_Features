package info.openrocket.swing.gui.simulation;

import static info.openrocket.core.util.StringUtils.escapeHtml;

import java.awt.Color;
import java.awt.Dialog.ModalityType;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import info.openrocket.core.rocketcomponent.RocketComponent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.MenuElement;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.text.View;
import javax.swing.JTextField;
import javax.swing.MenuElement;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.google.inject.Key;

import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroMode;
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
import info.openrocket.swing.gui.util.FlatLafOutlines;
import info.openrocket.swing.gui.util.GUIUtil;
import info.openrocket.swing.gui.util.Icons;
import info.openrocket.swing.simulation.extension.SwingSimulationExtensionConfigurator;
import net.miginfocom.swing.MigLayout;

class SimulationOptionsPanel extends SimulationScrollablePanel {

	private static final long serialVersionUID = -5251458539346201239L;
	private static final String LEGACY_AIRBRAKES_EXTENSION_ID = "com.airbrakesplugin.AirbrakeExtension";

	private static final Translator trans = Application.getTranslator();
	private static final String PANEL_LAYOUT = "fillx, insets 6, gap 8 8, wrap 1";
	private static final String PANEL_COLUMNS = "[grow,fill]";
	private static final String COLUMN_PAIR_COLUMNS = "[grow,fill,shrink 50][grow,fill,shrink 50]";
	private static final String SECTION_STACK_LAYOUT = "fillx, insets 0, gap 8 8, wrap 1";
	private static final String FORM_COLUMNS = "[right,shrink 0][grow,fill,shrink 100][pref!,shrink 0][grow,fill,shrink 100]";

	private final OpenRocketDocument document;
	final Simulation simulation;
	private final SimulationOptions options;

	private JLabel aerodynamicMethodValue;

	private JPanel currentExtensions;
	final JPopupMenu extensionMenu;
	JMenu extensionMenuCopyExtension;
	private JCheckBox weathercockingEnabledCheckBox;
	private JCheckBox airbrakesEnabledCheckBox;
	private AirbrakeSettingsPanel airbrakeSettingsPanel;

	private JSpinner gravitySpinner;
	private UnitSelector gravityUnit;
	private BasicSlider gravitySlider;
	private JLabel gravityLabel;
	private final JCheckBox fixedRandomSeedCheckBox;
	private final JTextField randomSeedField;
	private final FlatLafOutlines.Validator randomSeedValidator;
	private boolean updatingRandomSeedControls;

	private static Color textColor;
	private static Color dimTextColor;

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
				COLUMN_PAIR_COLUMNS, ""));
		JPanel leftColumn = new JPanel(new MigLayout(SECTION_STACK_LAYOUT, "[grow,fill]", ""));
		JPanel rightColumn = new JPanel(new MigLayout(SECTION_STACK_LAYOUT, "[grow,fill]", ""));
		columnsPanel.add(leftColumn, "growx, wmin 0, top");
		columnsPanel.add(rightColumn, "growx, wmin 0, top");
		add(columnsPanel, "growx, pushx, wmin 0, wrap");

		JPanel simulatorOptionsPanel = new JPanel(new MigLayout("fillx, insets 8, gapx 8, gapy 6, wrap 1",
				"[grow,fill]", ""));
		simulatorOptionsPanel.setBorder(BorderFactory.createTitledBorder(trans.get("simedtdlg.border.Simopt")));
		leftColumn.add(simulatorOptionsPanel, "growx, wmin 0, top");

		JPanel optionsForm = new JPanel(new MigLayout("fillx, insets 0, gapx 8, gapy 6", FORM_COLUMNS, ""));
		simulatorOptionsPanel.add(optionsForm, "growx, pushx, wmin 0, wrap");

		String tip = trans.get("simedtdlg.lbl.ttip.Calcmethod");
		JLabel label = new JLabel(trans.get("simedtdlg.lbl.Calcmethod"));
		label.setToolTipText(tip);
		optionsForm.add(label, "gapright para");

		aerodynamicMethodValue = new JLabel();
		aerodynamicMethodValue.setToolTipText(tip);
		optionsForm.add(aerodynamicMethodValue, "span 3, growx, wmin 0, wrap");

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
		optionsForm.add(simulationStepperMethodChoiceCombo, "span 3, growx, wmin 0, wrap");

		weathercockingEnabledCheckBox = new JCheckBox("Enable weathercocking compensation");
		weathercockingEnabledCheckBox.setToolTipText(
				"Apply signed rail-angle compensation and its short post-rod correction window.");
		weathercockingEnabledCheckBox.addActionListener(e -> {
			options.setWeathercockingCompensationEnabled(weathercockingEnabledCheckBox.isSelected());
			updateWeathercockingControls();
		});
		optionsForm.add(weathercockingEnabledCheckBox, "span 4, alignx left, wrap para");
		updateWeathercockingControls();

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
		optionsForm.add(gcsCombo, "span 3, growx, wmin 0, wrap");

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
		optionsForm.add(gravityModelCombo, "span 3, growx, wmin 0, wrap");

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
		optionsForm.add(gravitySlider, "growx, wmin 0, hidemode 3, wrap");

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
		optionsForm.add(timeStepSlider, "growx, wmin 0, wrap");

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

		fixedRandomSeedCheckBox = new JCheckBox(trans.get("simedtdlg.checkbox.FixedRandomSeed"),
				conditions.isRandomSeedFixed());
		fixedRandomSeedCheckBox.setToolTipText(trans.get("simedtdlg.checkbox.ttip.FixedRandomSeed"));
		optionsForm.add(fixedRandomSeedCheckBox, "span 2, gaptop para, alignx left");

		randomSeedField = new JTextField(12);
		randomSeedField.setToolTipText(trans.get("simedtdlg.lbl.ttip.RandomSeed"));
		optionsForm.add(randomSeedField, "span 2, growx, wrap");
		updateRandomSeedControlsFromOptions();

		randomSeedValidator = FlatLafOutlines.validator(randomSeedField)
				.errorIf(() -> fixedRandomSeedCheckBox.isSelected()
						&& parseRandomSeed(randomSeedField.getText()) == null,
						() -> trans.get("simedtdlg.error.RandomSeed"))
				.showMessagePopup(2500);
		randomSeedValidator.update();

		fixedRandomSeedCheckBox.addActionListener(e -> {
			if (updatingRandomSeedControls) return;
			updatingRandomSeedControls = true;
			if (fixedRandomSeedCheckBox.isSelected()) {
				conditions.randomizeSeed();
				randomSeedField.setText(Integer.toString(conditions.getRandomSeed()));
				randomSeedField.setEnabled(true);
				conditions.setRandomSeedFixed(true);
			} else {
				conditions.setRandomSeedFixed(false);
				randomSeedField.setText("");
				randomSeedField.setEnabled(false);
			}
			updatingRandomSeedControls = false;
			randomSeedValidator.update();
		});

		randomSeedField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent event) {
				updateRandomSeedFromField();
				randomSeedValidator.update();
			}

			@Override
			public void removeUpdate(DocumentEvent event) {
				updateRandomSeedFromField();
				randomSeedValidator.update();
			}

			@Override
			public void changedUpdate(DocumentEvent event) {
				updateRandomSeedFromField();
				randomSeedValidator.update();
			}
		});
		randomSeedField.addFocusListener(new FocusAdapter() {
			@Override
			public void focusLost(FocusEvent event) {
				if (!event.isTemporary()) replaceInvalidRandomSeed();
			}
		});

		airbrakesEnabledCheckBox = new JCheckBox("Enable airbrakes");
		airbrakesEnabledCheckBox.setToolTipText("Enable native airbrakes for this simulation.");
		airbrakesEnabledCheckBox.addActionListener(e -> {
			options.setAirbrakesEnabled(airbrakesEnabledCheckBox.isSelected());
			updateAirbrakeControls();
		});
		optionsForm.add(airbrakesEnabledCheckBox, "span 4, gaptop para, alignx left, wrap");

		airbrakeSettingsPanel = new AirbrakeSettingsPanel(conditions);
		optionsForm.add(airbrakeSettingsPanel, "span 4, growx, wmin 0, gapleft para, wrap para");
		updateAirbrakeControls();

		JPanel extensionsPanel = new JPanel(new MigLayout("fillx, insets 8, gap 6 6, wrap 1", "[grow,fill]", ""));
		extensionsPanel.setBorder(BorderFactory.createTitledBorder(trans.get("simedtdlg.border.SimExt")));
		rightColumn.add(extensionsPanel, "growx, wmin 0, top");

		JTextArea desc = SimulationTabLayoutUtils.createBoundedWrappingText(trans.get("simedtdlg.SimExt.desc"), textColor);
		extensionsPanel.add(desc, "growx, wmin 0, wrap");

		final JButton addExtension = new JButton(trans.get("simedtdlg.SimExt.add"));
		extensionMenu = getExtensionMenu();
		addExtension.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent ev) {
				extensionMenu.show(addExtension, 5, addExtension.getBounds().height);
			}
		});
		extensionsPanel.add(addExtension, "growx, wmin 0");

		currentExtensions = new JPanel(new MigLayout("fillx, gap 0 6, ins 0, wrap 1", "[grow,fill]", ""));
		JScrollPane scroll = SimulationTabLayoutUtils.createContainedScrollPane(currentExtensions);
		scroll.setForeground(textColor);
		scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.getHorizontalScrollBar().setUnitIncrement(16);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		extensionsPanel.add(scroll, "growx, wmin 0, hmin 140lp, hmax 320lp, top");

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
				conditions.setRecoverySpeedWarning(preferences.getRecoverySpeedWarning());
				//conditions.setDrogueLowSpeedWarning(preferences.getDrogueLowSpeedWarning());
				conditions.setRecoveryDrogueMainHighSpeedWarning(preferences.getRecoveryDrogueMainHighSpeedWarning());
				conditions.setRecoveryDrogueMainLowSpeedWarning(preferences.getRecoveryDrogueMainLowSpeedWarning());
				if (preferences.isRandomSeedFixed()) {
					conditions.setRandomSeed(preferences.getRandomSeed());
					conditions.setRandomSeedFixed(true);
				} else {
					conditions.setRandomSeedFixed(false);
				}
				updateRandomSeedControlsFromOptions();
				randomSeedValidator.update();
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
				preferences.setRecoverySpeedWarning(conditions.getRecoverySpeedWarning());
				//preferences.setDrogueLowSpeedWarning(conditions.getDrogueLowSpeedWarning());
				preferences.setRecoveryDrogueMainHighSpeedWarning(conditions.getRecoveryDrogueMainHighSpeedWarning());
				preferences.setRecoveryDrogueMainLowSpeedWarning(conditions.getRecoveryDrogueMainLowSpeedWarning());
				prepareForSimulation();
				if (conditions.isRandomSeedFixed()) {
					preferences.setRandomSeed(conditions.getRandomSeed());
				}
				preferences.setRandomSeedFixed(conditions.isRandomSeedFixed());
			}
		});

		JPanel defaultsPanel = new JPanel(new MigLayout("ins 0", "[][grow][]", ""));
		defaultsPanel.add(resetBtn);
		defaultsPanel.add(new JPanel(), "growx");
		defaultsPanel.add(saveBtn);
		add(defaultsPanel, "growx, wmin 0, wrap");

		updateCurrentExtensions();

		options.addChangeListener(e -> SwingUtilities.invokeLater(this::refreshManagedOptionPresentation));
		refreshManagedOptionPresentation();
	}

	void prepareForSimulation() {
		boolean useFixedSeed = applyRandomSeedInput(options,
				fixedRandomSeedCheckBox.isSelected(), randomSeedField.getText());
		updatingRandomSeedControls = true;
		fixedRandomSeedCheckBox.setSelected(useFixedSeed);
		randomSeedField.setEnabled(useFixedSeed);
		if (!useFixedSeed) randomSeedField.setText("");
		updatingRandomSeedControls = false;
		randomSeedValidator.update();
	}

	static boolean applyRandomSeedInput(SimulationOptions options,
			boolean fixedSeedRequested, String seedText) {
		Integer seed = fixedSeedRequested ? parseRandomSeed(seedText) : null;
		if (seed == null) {
			options.setRandomSeedFixed(false);
			return false;
		}
		options.setRandomSeed(seed);
		options.setRandomSeedFixed(true);
		return true;
	}

	private void updateRandomSeedFromField() {
		if (updatingRandomSeedControls || !fixedRandomSeedCheckBox.isSelected()) return;
		Integer seed = parseRandomSeed(randomSeedField.getText());
		if (seed != null) options.setRandomSeed(seed);
	}

	private void replaceInvalidRandomSeed() {
		if (!fixedRandomSeedCheckBox.isSelected()
				|| parseRandomSeed(randomSeedField.getText()) != null) return;
		options.randomizeSeed();
		updatingRandomSeedControls = true;
		randomSeedField.setText(Integer.toString(options.getRandomSeed()));
		updatingRandomSeedControls = false;
		randomSeedValidator.update();
	}

	private void updateRandomSeedControlsFromOptions() {
		updatingRandomSeedControls = true;
		fixedRandomSeedCheckBox.setSelected(options.isRandomSeedFixed());
		randomSeedField.setEnabled(options.isRandomSeedFixed());
		randomSeedField.setText(options.isRandomSeedFixed()
				? Integer.toString(options.getRandomSeed()) : "");
		updatingRandomSeedControls = false;
	}

	private static Integer parseRandomSeed(String seedText) {
		try {
			return Integer.valueOf(seedText.trim());
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	private static void initColors() {
		updateColors();
		UITheme.Theme.addUIThemeChangeListener(SimulationOptionsPanel::updateColors);
	}

	public static void updateColors() {
		textColor = UITheme.getColor(UITheme.Keys.TEXT);
		dimTextColor = UITheme.getColor(UITheme.Keys.TEXT_DIM);
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

	private String buildAerodynamicMethodLabel() {
		if (options.getPhysicsAeroMode() == PhysicsAeroMode.OFF) {
			return trans.get("simedtdlg.lbl.ExtBarrowman");
		}
		return "Physics-Based Aerodynamics (Experimental) - " + options.getPhysicsAeroMode();
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

	private void updateWeathercockingControls() {
		if (weathercockingEnabledCheckBox == null) {
			return;
		}

		weathercockingEnabledCheckBox.setSelected(options.isWeathercockingCompensationEnabled());
	}

	private void refreshManagedOptionPresentation() {
		aerodynamicMethodValue.setText(buildAerodynamicMethodLabel());
		updateWeathercockingControls();
		updateAirbrakeControls();
	}

	private void updateCurrentExtensions() {
		currentExtensions.removeAll();

		List<SimulationExtension> visibleExtensions = simulation.getSimulationExtensions().stream()
				.filter(extension -> !isManagedExtension(extension))
				.toList();

		if (visibleExtensions.isEmpty()) {
			StyledLabel l = new StyledLabel(trans.get("simedtdlg.SimExt.noExtensions"), Style.ITALIC);
			l.setForeground(dimTextColor);
			currentExtensions.add(l, "growx, wmin 0, pad 5 5 5 5, wrap");
		} else {
			for (SimulationExtension extension : visibleExtensions) {
				currentExtensions.add(new SimulationExtensionPanel(extension), "growx, wmin 0, wrap");
			}
		}

		updateExtensionMenuCopyExtension(this.extensionMenu);
		revalidate();
		repaint();
	}

	private class SimulationExtensionPanel extends JPanel {

		private static final long serialVersionUID = -3296795614810745035L;

		SimulationExtensionPanel(final SimulationExtension extension) {
			super(new MigLayout("fillx, insets 4 6 4 6, gapx 4", "[grow,fill][pref!]", ""));

			setBorder(BorderFactory.createLineBorder(dimTextColor));
			JLabel nameLabel = SimulationTabLayoutUtils.createCompactValueLabel(extension.getName());
			add(nameLabel, "growx, wmin 0");

			final JPanel actions = new JPanel(new MigLayout("insets 0, gapx 2", "", ""));
			add(actions, "right");

			JButton button;

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
				actions.add(button);
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
				actions.add(button);
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
			actions.add(button);
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

}

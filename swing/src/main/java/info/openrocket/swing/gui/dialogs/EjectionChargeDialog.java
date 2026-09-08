package info.openrocket.swing.gui.dialogs;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.material.Material;
import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.NoseCone;
import info.openrocket.core.rocketcomponent.Parachute;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.rocketcomponent.TubeCoupler;
import info.openrocket.core.util.ejection.AirframeMaterial;
import info.openrocket.core.util.ejection.EffectiveBayLengthCalculator;
import info.openrocket.core.util.ejection.EjectionChargeEngine;
import info.openrocket.core.util.ejection.EjectionChargeInputs;
import info.openrocket.core.util.ejection.EjectionChargeResult;
import info.openrocket.core.util.ejection.EjectionFiringDirection;
import info.openrocket.core.util.ejection.FrictionDerating;
import info.openrocket.core.util.ejection.PinMaterial;
import info.openrocket.core.util.ejection.ShearPinLookupTable;
import info.openrocket.core.util.ejection.ShearPinSpec;
import info.openrocket.core.util.ejection.StrengthSource;
import info.openrocket.swing.gui.util.GUIUtil;
import info.openrocket.swing.gui.util.Icons;

import net.miginfocom.swing.MigLayout;

/**
 * Black-powder ejection charge sizing dialog. Uses the new
 * {@link EjectionChargeEngine} (Lamé interference + shear-pin + ideal-gas-law)
 * and presents a baseline recommended pressure with a user-adjustable safety
 * factor and pressure override.
 */
public class EjectionChargeDialog extends JDialog {
	private static final long serialVersionUID = 2L;
	private static final int FORM_PREFERRED_WIDTH = 900;
	private static final int FORM_MAX_WIDTH = 1040;
	private static final Dimension DIALOG_DEFAULT_SIZE = new Dimension(920, 700);
	private static final Dimension DIALOG_MINIMUM_SIZE = new Dimension(820, 620);
	private static final int MAX_RESTORED_WIDTH = 1120;

	private static final double IN_PER_M = 39.3700787;
	private static final double M_PER_IN = 0.0254;
	private static final double PSI_PER_PA = 1.0 / 6894.757;
	private static final double PA_PER_PSI = 6894.757;

	private final OpenRocketDocument document;

	// --- Section A: component selector ---
	private JComboBox<ComponentItem> componentSelector;
	private JComboBox<ComponentItem> matingComponentSelector;
	private JComboBox<ParachuteItem> parachuteSelector;
	private JComboBox<EjectionFiringDirection> firingDirectionSelector;
	private JLabel computedOverlapLabel;
	private JLabel computedBayLengthLabel;
	private JLabel computedPackedVolumeLabel;
	private double computedPackedVolume_m3 = 0.0;

	// --- Section B: airframe ---
	private JSpinner bayInnerDiameterSpinner; // inches
	private JSpinner bayOuterDiameterSpinner; // inches
	private JSpinner bayLengthSpinner;        // inches
	private JComboBox<AirframeMaterial> bayMaterialSelector;

	// --- Section B: coupler ---
	private JSpinner couplerOuterDiameterSpinner; // inches
	private JSpinner couplerInnerDiameterSpinner; // inches
	private JSpinner couplerEngagementSpinner;    // inches
	private JComboBox<AirframeMaterial> couplerMaterialSelector;
	private JSpinner interferenceSpinner;         // inches diametral
	private JComboBox<FrictionDerating> frictionDeratingSelector;

	// --- Section B: shear pins ---
	private JComboBox<String> pinDesignationSelector;
	private JSpinner pinCountSpinner;
	private JComboBox<StrengthSource> strengthSourceSelector;

	// --- Section B: chute ---
	private JSpinner chuteVolumeFractionSpinner; // 0..0.95

	// --- Section C: pressure & SF ---
	private JLabel minPressureLabel;
	private JSlider safetyFactorSlider;
	private JLabel safetyFactorLabel;
	private JSpinner desiredPressureSpinner; // psi
	private JButton resetPressureButton;

	// --- Section D/E results ---
	private JLabel bpHeadlineLabel;
	private JLabel bpRangeLabel;
	private JLabel bayVolumeLabel;
	private JLabel effectiveVolumeLabel;
	private JCheckBox showDetailsCheckbox;
	private JPanel detailsPanel;
	private JLabel detailShearLabel;
	private JLabel detailFrictionLabel;
	private JLabel detailTotalForceLabel;
	private JLabel detailContactPressureLabel;
	private JLabel detailMinPressureLabel;
	private JLabel detailMolesLabel;
	private JLabel detailLameLabel;

	// --- Section F warnings ---
	private JPanel warningsPanel;

	// --- Section G actions ---
	private JButton calculateButton;
	private JButton closeButton;

	// State
	private double recommendedPressure_psi = 0.0;
	private boolean userOverrodePressure = false;
	private boolean updatingFromCalc = false;
	/** Suppresses the engagement-spinner change listener while the dialog is
	 *  programmatically updating the value from the auto-overlap calculation. */
	private boolean updatingEngagementFromAuto = false;
	/** Suppresses every input listener while the dialog is replaying values
	 *  captured from the last open session. Cleared after the restore completes. */
	private boolean restoringState = false;

	/**
	 * Per-JVM-session snapshot of every user choice in the dialog. Survives
	 * dialog close/reopen but is reset when the application exits, and is
	 * never written to the .ork file. {@code null} until the first dispose.
	 */
	private static SessionState savedState = null;

	public EjectionChargeDialog(Window owner, OpenRocketDocument document) {
		super(owner, "Ejection Charge Calculator", ModalityType.DOCUMENT_MODAL);
		this.document = document;

		setLayout(new BorderLayout());
		JTabbedPane tabs = new JTabbedPane();
		tabs.addTab("Calculator", buildMainPanel());
		add(tabs, BorderLayout.CENTER);
		add(buildButtonBar(), BorderLayout.SOUTH);

		populateComponentSelector();
		updateRecommendedFromInputs();
		runCalculation();

		// Replay any values captured the last time the dialog was open
		// in this JVM. The snapshot lives only in memory, so a fresh
		// application launch starts with defaults again.
		if (savedState != null) {
			restoreState(savedState);
		}

		validate();
		pack();
		setSize(DIALOG_DEFAULT_SIZE);

		setLocationByPlatform(true);

		GUIUtil.setDisposableDialogOptions(this, calculateButton);
		GUIUtil.rememberWindowPosition(this);
		GUIUtil.rememberWindowSize(this);
		if (getWidth() > MAX_RESTORED_WIDTH) {
			setSize(MAX_RESTORED_WIDTH, getHeight());
		}
		GUIUtil.constrainWindowToScreen(this, DIALOG_MINIMUM_SIZE);
	}

	// =====================================================================
	// Layout
	// =====================================================================

	private JPanel buildMainPanel() {
		JPanel root = new CalculatorFormPanel();
		root.setLayout(new MigLayout("fillx, insets 8, gapy 6, wrap 1", "[grow,fill]"));

		root.add(buildComponentSection(), "growx, wmin 0");
		root.add(buildInputsSection(), "growx, wmin 0");
		root.add(buildPressureSection(), "growx, wmin 0");
		root.add(buildResultsSection(), "growx, wmin 0");
		root.add(buildDetailsSection(), "growx, wmin 0");
		root.add(buildWarningsSection(), "growx, wmin 0");

		JScrollPane scroll = new JScrollPane(root);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(null);

		JPanel wrap = new JPanel(new BorderLayout());
		wrap.add(scroll, BorderLayout.CENTER);
		return wrap;
	}

	private JPanel buildComponentSection() {
		JPanel p = titled("1. Select components");
		p.setLayout(new MigLayout("fillx, insets 6, gapy 4", "[right][240:420:560,fill]"));
		p.add(wrappedNote("Choose components to fill the dimensions automatically. "
				+ "You can review or override every value below.", 650),
				"span 2, growx, wmin 0, wrap");

		p.add(new JLabel("Bay component:"));
		componentSelector = new JComboBox<>();
		componentSelector.addActionListener(e -> onComponentSelected());
		p.add(componentSelector, "wrap");

		computedBayLengthLabel = new JLabel("Effective bay length: \u2014");
		p.add(computedBayLengthLabel, "skip 1, growx, wrap");
		p.add(wrappedNote("\u26a0 Verify the detected bay length before flight; measure and override it below if needed.",
				650), "span 2, growx, wmin 0, wrap");

		p.add(new JLabel("Mating component:"));
		matingComponentSelector = new JComboBox<>();
		matingComponentSelector.addActionListener(e -> onMatingComponentSelected());
		p.add(matingComponentSelector, "wrap");

		computedOverlapLabel = new JLabel("Computed engagement (overlap): \u2014");
		p.add(computedOverlapLabel, "skip 1, growx, wrap");

		p.add(new JLabel("Charge direction:"));
		firingDirectionSelector = new JComboBox<>(EjectionFiringDirection.values());
		firingDirectionSelector.setSelectedItem(EjectionFiringDirection.FORWARD);
		firingDirectionSelector.setToolTipText(
				"<html>Direction the ejection charge pushes the chute / coupler.<br>"
						+ "Forward: charge sits at the aft end of the bay and pushes "
						+ "the nose / coupler off the forward end.<br>"
						+ "Aft: charge sits at the forward end of the bay and pushes "
						+ "the drogue / coupler off the aft end.<br>"
						+ "This selection picks the closed end of the bay (against "
						+ "the nearest centering ring, motor mount, or bulkhead "
						+ "opposite the coupler joint).</html>");
		firingDirectionSelector.addActionListener(e -> {
			if (restoringState) return;
			ComponentItem sel = (ComponentItem) componentSelector.getSelectedItem();
			BodyTube tube = resolveBayTube(sel == null ? null : sel.component);
			if (tube != null) {
				updatingEngagementFromAuto = true;
				try {
					applyEffectiveBayLength(tube);
				} finally {
					updatingEngagementFromAuto = false;
				}
			}
			userOverrodePressure = false;
			runCalculation();
		});
		p.add(firingDirectionSelector, "wrap");

		p.add(new JLabel("Parachute:"));
		parachuteSelector = new JComboBox<>();
		parachuteSelector.addActionListener(e -> onParachuteSelected());
		p.add(parachuteSelector, "wrap");

		computedPackedVolumeLabel = new JLabel("Computed packed chute volume: \u2014");
		p.add(computedPackedVolumeLabel, "skip 1, growx");
		return p;
	}

	private JPanel buildInputsSection() {
		JPanel p = titled("2. Review inputs");
		p.setLayout(new MigLayout("fillx, insets 6", "[grow,fill]15[grow,fill]"));

		p.add(buildAirframePanel(), "grow");
		p.add(buildCouplerPanel(),  "grow, wrap");

		p.add(buildPinsPanel(),     "grow");
		p.add(buildChutePanel(),    "grow, wrap");

		return p;
	}

	private JPanel buildAirframePanel() {
		JPanel p = titled("Airframe (outer tube)");
		p.setLayout(new MigLayout("fillx, insets 4", "[right][120:170:220,fill][]"));

		bayInnerDiameterSpinner  = inSpinner(4.00, 0.10, 24.0, 0.05);
		bayOuterDiameterSpinner  = inSpinner(4.10, 0.10, 25.0, 0.05);
		bayLengthSpinner         = inSpinner(8.00, 0.0, 96.0, 0.25);
		bayMaterialSelector      = new JComboBox<>(AirframeMaterial.values());
		bayMaterialSelector.setSelectedItem(AirframeMaterial.FIBERGLASS);
		bayMaterialSelector.setToolTipText(
				"Friction category; selected component stiffness and strength come from its material record.");

		row(p, "Inner diameter:",   bayInnerDiameterSpinner,  "in");
		row(p, "Outer diameter:",   bayOuterDiameterSpinner,  "in");
		row(p, "Bay length:",       bayLengthSpinner,         "in");
		row(p, "Friction category:", bayMaterialSelector,      "");
		return p;
	}

	private JPanel buildCouplerPanel() {
		JPanel p = titled("Coupler / nose-cone shoulder");
		p.setLayout(new MigLayout("fillx, insets 4", "[right][120:170:220,fill][]"));

		couplerOuterDiameterSpinner = inSpinner(3.99, 0.10, 24.0, 0.05);
		couplerInnerDiameterSpinner = inSpinner(3.85, 0.05, 24.0, 0.05);
		couplerEngagementSpinner    = inSpinner(3.00, 0.10, 24.0, 0.25);
		couplerEngagementSpinner.setToolTipText(
				"<html>Auto-computed from the bridging coupler's protrusion into "
						+ "the mating component when components are selected. "
						+ "You may edit this value to override the auto-computed engagement.</html>");
		couplerEngagementSpinner.addChangeListener(e -> {
			if (updatingEngagementFromAuto || restoringState) return;
			userOverrodePressure = false;
			runCalculation();
		});
		couplerMaterialSelector     = new JComboBox<>(AirframeMaterial.values());
		couplerMaterialSelector.setSelectedItem(AirframeMaterial.FIBERGLASS);
		couplerMaterialSelector.setToolTipText(
				"Friction category; selected component stiffness comes from its material record.");
		interferenceSpinner         = inSpinner(0.001, 0.0, 0.020, 0.0005);
		frictionDeratingSelector    = new JComboBox<>(FrictionDerating.values());
		frictionDeratingSelector.setSelectedItem(FrictionDerating.MEDIUM);
		frictionDeratingSelector.setToolTipText(
				"<html>Empirical de-rating applied to the Lam\u00e9 friction force.<br>"
						+ "Low (0.4): well-lubricated / loose fits.<br>"
						+ "Medium (0.6): default, typical bench-pull average.<br>"
						+ "High (0.8): dry / new / tight fits.</html>");
		frictionDeratingSelector.addActionListener(e -> {
			if (restoringState) return;
			userOverrodePressure = false;
			runCalculation();
		});

		row(p, "Outer diameter:",  couplerOuterDiameterSpinner, "in");
		row(p, "Inner diameter:",  couplerInnerDiameterSpinner, "in");
		row(p, "Engagement (auto):", couplerEngagementSpinner,  "in");
		row(p, "Friction category:", couplerMaterialSelector,     "");
		row(p, "Diametral δ:",     interferenceSpinner,         "in");
		row(p, "Friction de-rating:", frictionDeratingSelector,  "");
		return p;
	}

	private JPanel buildPinsPanel() {
		JPanel p = titled("Shear pins");
		p.setLayout(new MigLayout("fillx, insets 4", "[right][120:170:220,fill][]"));

		pinDesignationSelector = new JComboBox<>();
		for (String d : ShearPinLookupTable.getAllDesignations()) {
			pinDesignationSelector.addItem(d);
		}
		pinDesignationSelector.setRenderer(new PinRenderer());
		pinDesignationSelector.setSelectedItem("#4-40");

		pinCountSpinner = new JSpinner(new SpinnerNumberModel(3, 0, 32, 1));
		strengthSourceSelector = new JComboBox<>(StrengthSource.values());
		strengthSourceSelector.setSelectedItem(StrengthSource.TESTED_MIN);

		row(p, "Designation:", pinDesignationSelector, "");
		row(p, "Quantity:",    pinCountSpinner,        "");
		row(p, "Strength:",    strengthSourceSelector, "");
		return p;
	}

	private JPanel buildChutePanel() {
		JPanel p = titled("Packed chute");
		p.setLayout(new MigLayout("fillx, insets 4", "[right][120:170:220,fill][]"));

		chuteVolumeFractionSpinner = new JSpinner(
				new SpinnerNumberModel(0.10, 0.00, 0.95, 0.01));
		chuteVolumeFractionSpinner.setToolTipText(
				"Fallback estimate used only when no parachute is selected.");
		row(p, "Volume fraction:", chuteVolumeFractionSpinner, "of bay");
		p.add(wrappedNote("Used only when no parachute is selected above.", 300),
				"span 3, growx, wmin 0");
		return p;
	}

	private JPanel buildPressureSection() {
		JPanel p = titled("3. Set pressure & safety factor");
		p.setLayout(new MigLayout("fillx, insets 6", "[right][220:340:480,fill][]"));

		minPressureLabel = new JLabel("—");
		minPressureLabel.setFont(minPressureLabel.getFont().deriveFont(Font.BOLD));
		row(p, "Minimum required pressure:", minPressureLabel, "");

		safetyFactorSlider = new JSlider(10, 30, 15);
		safetyFactorSlider.setMajorTickSpacing(5);
		safetyFactorSlider.setMinorTickSpacing(1);
		safetyFactorSlider.setPaintTicks(true);
		Hashtable<Integer, JLabel> labels = new Hashtable<>();
		labels.put(10, new JLabel("1.0"));
		labels.put(15, new JLabel("1.5"));
		labels.put(20, new JLabel("2.0"));
		labels.put(25, new JLabel("2.5"));
		labels.put(30, new JLabel("3.0"));
		safetyFactorSlider.setLabelTable(labels);
		safetyFactorSlider.setPaintLabels(true);

		safetyFactorLabel = new JLabel("1.50");
		safetyFactorLabel.setFont(safetyFactorLabel.getFont().deriveFont(Font.BOLD));
		safetyFactorSlider.addChangeListener(e -> {
			if (restoringState) return;
			safetyFactorLabel.setText(String.format(Locale.ROOT, "%.2f", currentSafetyFactor()));
			if (!safetyFactorSlider.getValueIsAdjusting()) {
				userOverrodePressure = false;
				runCalculation();
			} else {
				safetyFactorLabel.setText(String.format(Locale.ROOT, "%.2f", currentSafetyFactor()));
			}
		});
		p.add(new JLabel("Safety factor:"));
		p.add(safetyFactorSlider, "growx");
		p.add(safetyFactorLabel, "wrap");

		desiredPressureSpinner = new JSpinner(new SpinnerNumberModel(15.0, 0.5, 200.0, 0.5));
		desiredPressureSpinner.addChangeListener(e -> {
			if (updatingFromCalc || restoringState) return;
			userOverrodePressure = true;
			runCalculation();
		});
		resetPressureButton = new JButton("Reset to recommended", Icons.RESET);
		resetPressureButton.addActionListener(e -> {
			userOverrodePressure = false;
			runCalculation();
		});
		p.add(new JLabel("Desired pressure:"));
		p.add(desiredPressureSpinner, "growx, split 2");
		p.add(new JLabel("psi"));
		p.add(resetPressureButton, "wrap");

		return p;
	}

	private JPanel buildResultsSection() {
		JPanel p = titled("4. Recommended BP charge");
		p.setLayout(new MigLayout("fillx, insets 6", "[grow,fill]"));

		bpHeadlineLabel = new JLabel("—");
		bpHeadlineLabel.setFont(bpHeadlineLabel.getFont().deriveFont(Font.BOLD, 18f));
		bpHeadlineLabel.setHorizontalAlignment(SwingConstants.CENTER);
		p.add(bpHeadlineLabel, "growx, wrap");

		bpRangeLabel = new JLabel("—");
		bpRangeLabel.setHorizontalAlignment(SwingConstants.CENTER);
		p.add(bpRangeLabel, "growx, wrap");

		p.add(new JSeparator(), "growx, gaptop 4, gapbottom 4, wrap");
		bayVolumeLabel = new JLabel("Bay volume: —");
		effectiveVolumeLabel = new JLabel("Effective volume (after chute): —");
		p.add(bayVolumeLabel, "wrap");
		p.add(effectiveVolumeLabel, "wrap");

		p.add(new JSeparator(), "growx, gaptop 6, gapbottom 4, wrap");
		p.add(wrappedNote("\u26a0 Estimate only. Ground-test the complete recovery system before flight.", 650),
				"growx, wmin 0, wrap");

		return p;
	}

	private JPanel buildDetailsSection() {
		JPanel container = new JPanel(new MigLayout("fillx, insets 0", "[grow,fill]"));

		showDetailsCheckbox = new JCheckBox("\u25B6 Show calculation details", false);
		showDetailsCheckbox.addActionListener(e -> {
			if (restoringState) return;
			boolean s = showDetailsCheckbox.isSelected();
			showDetailsCheckbox.setText(s
					? "\u25BC Hide calculation details"
					: "\u25B6 Show calculation details");
			detailsPanel.setVisible(s);
			revalidate();
			repaint();
		});
		container.add(showDetailsCheckbox, "growx, wrap");

		detailsPanel = titled("Breakdown");
		detailsPanel.setLayout(new MigLayout("fillx, insets 6", "[right][200:360:520,fill]"));
		detailsPanel.setVisible(false);

		detailShearLabel = new JLabel("—");
		detailFrictionLabel = new JLabel("—");
		detailTotalForceLabel = new JLabel("—");
		detailContactPressureLabel = new JLabel("—");
		detailMinPressureLabel = new JLabel("—");
		detailMolesLabel = new JLabel("—");
		detailLameLabel = new JLabel("—");

		row(detailsPanel, "Shear pin force:",          detailShearLabel,           "");
		row(detailsPanel, "Coupler friction force:",   detailFrictionLabel,        "");
		row(detailsPanel, "Total separation force:",   detailTotalForceLabel,      "");
		row(detailsPanel, "Lamé contact pressure:",    detailContactPressureLabel, "");
		row(detailsPanel, "Lamé factors (C_o / C_i):", detailLameLabel,            "");
		row(detailsPanel, "Min required pressure:",    detailMinPressureLabel,     "");
		row(detailsPanel, "Gas moles required:",       detailMolesLabel,           "");

		container.add(detailsPanel, "growx");
		return container;
	}

	private JPanel buildWarningsSection() {
		warningsPanel = titled("Warnings");
		warningsPanel.setLayout(new BoxLayout(warningsPanel, BoxLayout.Y_AXIS));
		return warningsPanel;
	}

	private JPanel buildButtonBar() {
		JPanel p = new JPanel(new MigLayout("fillx, insets 8", "[grow][][]"));
		calculateButton = new JButton("Calculate", Icons.SIM_RUN);
		calculateButton.addActionListener(e -> runCalculation());
		closeButton = new JButton("Close", Icons.FILE_CLOSE);
		closeButton.addActionListener(e -> dispose());
		p.add(Box.createHorizontalGlue(), "growx");
		p.add(calculateButton);
		p.add(closeButton);
		return p;
	}

	// =====================================================================
	// Component selection / autofill
	// =====================================================================

	private void populateComponentSelector() {
		componentSelector.addItem(new ComponentItem(null, "-- Manual entry --"));
		matingComponentSelector.addItem(new ComponentItem(null, "-- None --"));
		parachuteSelector.addItem(new ParachuteItem(null, "-- None (use fraction) --"));
		if (document == null) return;
		Rocket rocket = document.getRocket();
		for (RocketComponent c : rocket) {
			if (c instanceof BodyTube || c instanceof NoseCone) {
				String label = c.getComponentName() + " \u2014 " + c.getName();
				componentSelector.addItem(new ComponentItem(c, label));
			}
			if (c instanceof BodyTube || c instanceof TubeCoupler || c instanceof NoseCone) {
				String label = c.getComponentName() + " \u2014 " + c.getName();
				matingComponentSelector.addItem(new ComponentItem(c, label));
			}
			if (c instanceof Parachute) {
				Parachute pc = (Parachute) c;
				String label = pc.getComponentName() + " \u2014 " + pc.getName();
				parachuteSelector.addItem(new ParachuteItem(pc, label));
			}
		}
	}

	private void onComponentSelected() {
		if (restoringState) return;
		ComponentItem sel = (ComponentItem) componentSelector.getSelectedItem();
		if (sel == null || sel.component == null) return;

		updatingEngagementFromAuto = true;
		try {
			if (sel.component instanceof BodyTube) {
				BodyTube tube = (BodyTube) sel.component;
				setSpinnerIn(bayInnerDiameterSpinner, tube.getInnerRadius() * 2.0);
				setSpinnerIn(bayOuterDiameterSpinner, tube.getOuterRadius() * 2.0);
				applyEffectiveBayLength(tube);
				selectMaterialCategory(bayMaterialSelector, tube.getMaterial());
			} else if (sel.component instanceof NoseCone) {
				NoseCone nose = (NoseCone) sel.component;
				RocketComponent parent = nose.getParent();
				if (parent instanceof BodyTube) {
					BodyTube tube = (BodyTube) parent;
					setSpinnerIn(bayInnerDiameterSpinner, tube.getInnerRadius() * 2.0);
					setSpinnerIn(bayOuterDiameterSpinner, tube.getOuterRadius() * 2.0);
					applyEffectiveBayLength(tube);
					selectMaterialCategory(bayMaterialSelector, tube.getMaterial());
				}
			}
			populateCouplerFieldsFromSelection();
		} finally {
			updatingEngagementFromAuto = false;
		}
		updateComputedOverlap();
		userOverrodePressure = false;
		runCalculation();
	}

	private TubeCoupler findCouplerInside(BodyTube tube) {
		for (RocketComponent child : tube.getChildren()) {
			if (child instanceof TubeCoupler) {
				return (TubeCoupler) child;
			}
		}
		return null;
	}

	/**
	 * Resolves the body tube that hosts the pressurized bay for the given
	 * primary selection. Returns the tube itself when {@code c} is a body
	 * tube, the parent body tube when {@code c} is a nose cone, or null
	 * otherwise.
	 */
	private static BodyTube resolveBayTube(RocketComponent c) {
		if (c instanceof BodyTube) return (BodyTube) c;
		if (c instanceof NoseCone) {
			RocketComponent parent = c.getParent();
			if (parent instanceof BodyTube) return (BodyTube) parent;
		}
		return null;
	}

	private EjectionFiringDirection currentFiringDirection() {
		EjectionFiringDirection d = (firingDirectionSelector == null)
				? null
				: (EjectionFiringDirection) firingDirectionSelector.getSelectedItem();
		return (d == null) ? EjectionFiringDirection.FORWARD : d;
	}

	/**
	 * Pushes the effective bay length into the spinner and updates the
	 * "Effective bay length" label so the user can see the breakdown of
	 * tube length minus each interface inset.
	 */
	private void applyEffectiveBayLength(BodyTube tube) {
		EffectiveBayLengthCalculator.Result result =
				EffectiveBayLengthCalculator.calculate(tube, currentFiringDirection());
		double effective_m = result.getEffectiveLength();
		setSpinnerIn(bayLengthSpinner, effective_m);
		double tubeLen_in = tube.getLength() * IN_PER_M;
		double topInset_in = result.getForwardInset() * IN_PER_M;
		double botInset_in = result.getAftInset() * IN_PER_M;
		double effective_in = effective_m * IN_PER_M;
		if (computedBayLengthLabel != null) {
			computedBayLengthLabel.setText(String.format(Locale.ROOT,
					"Effective bay length: %.2f in  (tube %.2f \u2212 fwd %.2f \u2212 aft %.2f, %s firing)",
					effective_in, tubeLen_in, topInset_in, botInset_in,
					currentFiringDirection().getDisplayName()));
		}
	}

	/**
	 * Fills the Coupler / nose-cone-shoulder spinners (OD, ID, engagement)
	 * from whichever bridging element can be inferred from the current
	 * primary + mating selections. Mirrors {@link #findBridge} so the
	 * autopopulate path covers every joint shape the engagement math
	 * already handles (coupler-as-mating, mating-tube hosting the coupler,
	 * nose-cone shoulder mating to a body tube, etc.).
	 *
	 * <p>The engagement value is seeded with half the coupler / shoulder
	 * length; {@link #updateComputedOverlap()} refines it once both
	 * selections are present.
	 */
	private void populateCouplerFieldsFromSelection() {
		ComponentItem a = (ComponentItem) componentSelector.getSelectedItem();
		ComponentItem b = (ComponentItem) matingComponentSelector.getSelectedItem();
		RocketComponent ca = (a == null) ? null : a.component;
		RocketComponent cb = (b == null) ? null : b.component;

		TubeCoupler coupler = findBridgeCoupler(ca, cb);
		if (coupler != null) {
			setSpinnerIn(couplerOuterDiameterSpinner, coupler.getOuterRadius() * 2.0);
			setSpinnerIn(couplerInnerDiameterSpinner, coupler.getInnerRadius() * 2.0);
			setSpinnerIn(couplerEngagementSpinner,    coupler.getLength() * 0.5);
			selectMaterialCategory(couplerMaterialSelector, coupler.getMaterial());
			return;
		}
		NoseCone shoulder = findBridgeShoulder(ca, cb);
		if (shoulder != null) {
			double shOD = shoulder.getShoulderRadius() * 2.0;
			double shThk = shoulder.getShoulderThickness();
			double shID = Math.max(0.0, (shoulder.getShoulderRadius() - shThk) * 2.0);
			setSpinnerIn(couplerOuterDiameterSpinner, shOD);
			setSpinnerIn(couplerInnerDiameterSpinner, shID);
			setSpinnerIn(couplerEngagementSpinner,    shoulder.getShoulderLength());
			selectMaterialCategory(couplerMaterialSelector, shoulder.getMaterial());
		}
	}

	private static void selectMaterialCategory(JComboBox<AirframeMaterial> selector, Material material) {
		AirframeMaterial category = AirframeMaterial.fromMaterial(material);
		if (category != null) {
			selector.setSelectedItem(category);
		}
	}

	/** Returns the bridging {@link TubeCoupler} for the selection pair, or null. */
	private TubeCoupler findBridgeCoupler(RocketComponent a, RocketComponent b) {
		if (a instanceof TubeCoupler) return (TubeCoupler) a;
		if (b instanceof TubeCoupler) return (TubeCoupler) b;
		if (a instanceof BodyTube) {
			TubeCoupler c = findCouplerInside((BodyTube) a);
			if (c != null) return c;
		}
		if (b instanceof BodyTube) {
			TubeCoupler c = findCouplerInside((BodyTube) b);
			if (c != null) return c;
		}
		return null;
	}

	/** Returns the bridging nose-cone shoulder for the selection pair, or null. */
	private NoseCone findBridgeShoulder(RocketComponent a, RocketComponent b) {
		if (a instanceof NoseCone && ((NoseCone) a).getShoulderLength() > 0.0) {
			return (NoseCone) a;
		}
		if (b instanceof NoseCone && ((NoseCone) b).getShoulderLength() > 0.0) {
			return (NoseCone) b;
		}
		return null;
	}

	private void onMatingComponentSelected() {
		if (restoringState) return;
		updatingEngagementFromAuto = true;
		try {
			populateCouplerFieldsFromSelection();
		} finally {
			updatingEngagementFromAuto = false;
		}
		updateComputedOverlap();
		userOverrodePressure = false;
		runCalculation();
	}

	private void onParachuteSelected() {
		if (restoringState) return;
		updateComputedPackedVolume();
		userOverrodePressure = false;
		runCalculation();
	}

	/** Returns absolute axial start (m) of the component along the rocket X-axis. */
	private static double axialStart_m(RocketComponent c) {
		try {
			return c.getComponentLocations()[0].getX();
		} catch (RuntimeException ex) {
			return 0.0;
		}
	}

	/** Returns the tubular length (m) of a component used for axial-overlap math. */
	private static double tubularLength_m(RocketComponent c) {
		if (c instanceof BodyTube) {
			return ((BodyTube) c).getLength();
		}
		if (c instanceof TubeCoupler) {
			return ((TubeCoupler) c).getLength();
		}
		if (c instanceof NoseCone) {
			// For a nose cone, only the shoulder participates in any overlap.
			return ((NoseCone) c).getShoulderLength();
		}
		return c.getLength();
	}

	/**
	 * Returns the axial start (m) of the portion of the component that can
	 * overlap a mating tube. For a nose cone this is the start of its shoulder,
	 * which sits at the cone's aft end.
	 */
	private static double tubularAxialStart_m(RocketComponent c) {
		double x0 = axialStart_m(c);
		if (c instanceof NoseCone) {
			// Shoulder begins at the cone's aft end and extends further aft.
			return x0 + c.getLength();
		}
		return x0;
	}

	private void updateComputedOverlap() {
		ComponentItem a = (ComponentItem) componentSelector.getSelectedItem();
		ComponentItem b = (ComponentItem) matingComponentSelector.getSelectedItem();
		if (a == null || a.component == null || b == null || b.component == null) {
			computedOverlapLabel.setText("Computed engagement (overlap): \u2014");
			return;
		}

		// The engagement length that drives friction is the length of the
		// bridging coupler/shoulder that lies inside the *mating* component
		// — i.e. the portion that has actually protruded out of its parent
		// tube and into the other tube. Picking the wrong endpoints here
		// (e.g. intersecting two adjacent body tubes that themselves do not
		// overlap, or counting the entire coupler when both halves sit
		// inside the same tube) produces a hugely inflated number.
		double engagement_m = computeEngagementLength_m(a.component, b.component);
		if (engagement_m < 0.0) {
			computedOverlapLabel.setText(
					"Computed engagement (overlap): \u2014 (no bridging coupler/shoulder found)");
			return;
		}
		double engagement_in = engagement_m * IN_PER_M;
		computedOverlapLabel.setText(String.format(Locale.ROOT,
				"Computed engagement (overlap): %.3f in (%.1f mm) \u2014 editable",
				engagement_in, engagement_m * 1000.0));
		// Push into the engagement spinner programmatically without firing
		// the manual-override change listener.
		updatingEngagementFromAuto = true;
		try {
			setSpinnerIn(couplerEngagementSpinner, engagement_m);
		} finally {
			updatingEngagementFromAuto = false;
		}
	}

	/**
	 * Computes the engagement length (m) for the joint formed by the two
	 * selected components. Returns -1 when no bridging element can be
	 * identified.
	 *
	 * <p>The rule is: locate the coupler/shoulder that bridges the joint,
	 * then return the portion of that coupler whose axial extent lies
	 * inside the mating component (i.e. has protruded out of the coupler's
	 * own parent). For a nose cone the shoulder plays the role of the
	 * coupler. The result is clamped to the coupler's own length.
	 */
	private double computeEngagementLength_m(RocketComponent a, RocketComponent b) {
		Bridge bridge = findBridge(a, b);
		if (bridge == null) {
			return -1.0;
		}
		double cs = bridge.couplerStart_m;
		double ce = bridge.couplerEnd_m;
		double couplerLen = ce - cs;
		if (couplerLen <= 0.0) return 0.0;

		double ms = bridge.matingStart_m;
		double me = bridge.matingEnd_m;
		double engagement = Math.max(0.0, Math.min(ce, me) - Math.max(cs, ms));

		// If the user picked the coupler itself as one of the components and
		// the mating tube does not yet "see" the protruding portion (e.g. the
		// coupler is fully inside its own parent in the model), fall back to
		// half of the coupler length — the conventional rule of thumb — so we
		// never report an engagement larger than half of the coupler.
		if (engagement <= 0.0) {
			engagement = 0.5 * couplerLen;
		}
		// Clamp: engagement can never exceed the coupler's own length, and
		// in practice should not exceed half of the coupler since the other
		// half remains anchored in the bay tube.
		engagement = Math.min(engagement, couplerLen);
		engagement = Math.min(engagement, 0.5 * couplerLen);
		return engagement;
	}

	/**
	 * Identifies the bridging coupler (or nose-cone shoulder) for the joint
	 * between {@code a} and {@code b}, plus the axial extent of the mating
	 * component (the one that does NOT contain the coupler).
	 */
	private Bridge findBridge(RocketComponent a, RocketComponent b) {
		// Case 1: one of the selections is itself a coupler.
		if (a instanceof TubeCoupler) {
			return bridgeFromCoupler((TubeCoupler) a, b);
		}
		if (b instanceof TubeCoupler) {
			return bridgeFromCoupler((TubeCoupler) b, a);
		}
		// Case 2: a nose cone with a shoulder mates to a body tube.
		if (a instanceof NoseCone && b instanceof BodyTube) {
			return bridgeFromNoseCone((NoseCone) a, (BodyTube) b);
		}
		if (b instanceof NoseCone && a instanceof BodyTube) {
			return bridgeFromNoseCone((NoseCone) b, (BodyTube) a);
		}
		// Case 3: two body tubes — look for a coupler child in either.
		if (a instanceof BodyTube && b instanceof BodyTube) {
			BodyTube ta = (BodyTube) a;
			BodyTube tb = (BodyTube) b;
			TubeCoupler c = findCouplerInside(ta);
			if (c != null) {
				return bridgeFromCoupler(c, tb);
			}
			c = findCouplerInside(tb);
			if (c != null) {
				return bridgeFromCoupler(c, ta);
			}
		}
		return null;
	}

	private Bridge bridgeFromCoupler(TubeCoupler coupler, RocketComponent mating) {
		if (mating == null) return null;
		double cs = axialStart_m(coupler);
		double ce = cs + coupler.getLength();
		double ms = axialStart_m(mating);
		double me = ms + tubularLength_m(mating);
		return new Bridge(cs, ce, ms, me);
	}

	private Bridge bridgeFromNoseCone(NoseCone nose, BodyTube mating) {
		double shoulderStart = axialStart_m(nose) + nose.getLength();
		double shoulderEnd = shoulderStart + nose.getShoulderLength();
		double ms = axialStart_m(mating);
		double me = ms + mating.getLength();
		return new Bridge(shoulderStart, shoulderEnd, ms, me);
	}

	/** Axial extents of the bridging coupler/shoulder and the mating tube. */
	private static final class Bridge {
		final double couplerStart_m;
		final double couplerEnd_m;
		final double matingStart_m;
		final double matingEnd_m;
		Bridge(double cs, double ce, double ms, double me) {
			this.couplerStart_m = cs;
			this.couplerEnd_m = ce;
			this.matingStart_m = ms;
			this.matingEnd_m = me;
		}
	}

	private void updateComputedPackedVolume() {
		ParachuteItem sel = (ParachuteItem) parachuteSelector.getSelectedItem();
		if (sel == null || sel.parachute == null) {
			computedPackedVolume_m3 = 0.0;
			computedPackedVolumeLabel.setText(
					"Computed packed chute volume: \u2014 (using fraction)");
			return;
		}
		Parachute p = sel.parachute;
		double packedLen_m = p.getLength();
		double packedRad_m = p.getRadius();
		double vol_m3 = Math.PI * packedRad_m * packedRad_m * packedLen_m;
		computedPackedVolume_m3 = vol_m3;
		double in3 = vol_m3 / (M_PER_IN * M_PER_IN * M_PER_IN);
		computedPackedVolumeLabel.setText(String.format(Locale.ROOT,
				"Computed packed chute volume: %.2f in\u00b3 (%.0f cm\u00b3) "
						+ "\u2014 length %.2f in, dia %.2f in",
				in3, vol_m3 * 1e6,
				packedLen_m * IN_PER_M, 2.0 * packedRad_m * IN_PER_M));
	}

	// =====================================================================
	// Calculation orchestration
	// =====================================================================

	private void updateRecommendedFromInputs() {
		// Pre-populate the desired pressure spinner once by running a default calc.
	}

	private void runCalculation() {
		try {
			EjectionChargeInputs inputs = collectInputs();
			EjectionChargeResult result = EjectionChargeEngine.calculate(inputs);
			recommendedPressure_psi = result.getWorkingPressure_pa() * PSI_PER_PA;

			updatingFromCalc = true;
			try {
				if (!userOverrodePressure) {
					desiredPressureSpinner.setValue(round(recommendedPressure_psi, 2));
				}
			} finally {
				updatingFromCalc = false;
			}

			// If user has overridden, recompute BP at their pressure
			double desired_psi = ((Number) desiredPressureSpinner.getValue()).doubleValue();
			double desired_pa = desired_psi * PA_PER_PSI;
			double bpAtDesired_g = EjectionChargeEngine.bpMassGrams(
					desired_pa / Math.max(result.getMinRequiredPressure_pa(), 1e-9),
					result.getMinRequiredPressure_pa(),
					result.getEffectiveVolume_m3());
			result.setBpMassAtWorking_g(bpAtDesired_g);
			result.setWorkingPressure_pa(desired_pa);

			renderResult(result);
		} catch (RuntimeException ex) {
			bpHeadlineLabel.setText("—");
			bpRangeLabel.setText("Input error: " + ex.getMessage());
			bpRangeLabel.setForeground(new Color(0xCC0000));
		}
	}

	private EjectionChargeInputs collectInputs() {
		EjectionChargeInputs in = new EjectionChargeInputs();

		double bayID_in = (Double) bayInnerDiameterSpinner.getValue();
		double bayOD_in = (Double) bayOuterDiameterSpinner.getValue();
		double bayL_in  = (Double) bayLengthSpinner.getValue();
		in.setBayInnerDiameter_m(bayID_in * M_PER_IN);
		in.setBayOuterDiameter_m(bayOD_in * M_PER_IN);
		in.setBayLength_m(bayL_in * M_PER_IN);
		in.setBayMaterial((AirframeMaterial) bayMaterialSelector.getSelectedItem());
		ComponentItem selectedBay = (ComponentItem) componentSelector.getSelectedItem();
		BodyTube bayTube = resolveBayTube(selectedBay == null ? null : selectedBay.component);
		if (bayTube != null) {
			in.setBayComponentMaterial(bayTube.getMaterial());
		}

		in.setCouplerOuterDiameter_m(((Double) couplerOuterDiameterSpinner.getValue()) * M_PER_IN);
		in.setCouplerInnerDiameter_m(((Double) couplerInnerDiameterSpinner.getValue()) * M_PER_IN);
		in.setCouplerEngagementLength_m(((Double) couplerEngagementSpinner.getValue()) * M_PER_IN);
		in.setCouplerMaterial((AirframeMaterial) couplerMaterialSelector.getSelectedItem());
		ComponentItem selectedMate = (ComponentItem) matingComponentSelector.getSelectedItem();
		RocketComponent bayComponent = selectedBay == null ? null : selectedBay.component;
		RocketComponent matingComponent = selectedMate == null ? null : selectedMate.component;
		TubeCoupler coupler = findBridgeCoupler(bayComponent, matingComponent);
		if (coupler != null) {
			in.setCouplerComponentMaterial(coupler.getMaterial());
		} else {
			NoseCone shoulder = findBridgeShoulder(bayComponent, matingComponent);
			if (shoulder != null) {
				in.setCouplerComponentMaterial(shoulder.getMaterial());
			}
		}
		in.setDiametralInterference_m(((Double) interferenceSpinner.getValue()) * M_PER_IN);

		in.setShearPinDesignation((String) pinDesignationSelector.getSelectedItem());
		in.setNumShearPins(((Number) pinCountSpinner.getValue()).intValue());
		in.setStrengthSource((StrengthSource) strengthSourceSelector.getSelectedItem());

		in.setSafetyFactor(currentSafetyFactor());
		in.setChuteVolumeFraction(((Number) chuteVolumeFractionSpinner.getValue()).doubleValue());
		in.setChutePackedVolume_m3(computedPackedVolume_m3);
		FrictionDerating fd = (FrictionDerating) frictionDeratingSelector.getSelectedItem();
		in.setFrictionDerating(fd == null ? FrictionDerating.MEDIUM : fd);
		return in;
	}

	private double currentSafetyFactor() {
		return safetyFactorSlider.getValue() / 10.0;
	}

	private void renderResult(EjectionChargeResult r) {
		// Headline
		bpHeadlineLabel.setText(String.format(Locale.ROOT,
				"%.2f g BP   @ %.1f psi", r.getBpMassAtWorking_g(),
				r.getWorkingPressure_pa() * PSI_PER_PA));

		bpRangeLabel.setForeground(UIManager.getColor("Label.foreground"));
		bpRangeLabel.setText(String.format(Locale.ROOT,
				"Test range (SF 1.5 → 2.5):   %.2f g  →  %.2f g",
				r.getBpMassAtSF15_g(), r.getBpMassAtSF25_g()));

		double bayVol_in3 = r.getBayVolume_m3() / (M_PER_IN * M_PER_IN * M_PER_IN);
		double effVol_in3 = r.getEffectiveVolume_m3() / (M_PER_IN * M_PER_IN * M_PER_IN);
		bayVolumeLabel.setText(String.format(Locale.ROOT,
				"Bay volume: %.2f in³ (%.0f cm³)",
				bayVol_in3, r.getBayVolume_m3() * 1e6));
		effectiveVolumeLabel.setText(String.format(Locale.ROOT,
				"Effective volume (after chute): %.2f in³ (%.0f cm³)",
				effVol_in3, r.getEffectiveVolume_m3() * 1e6));

		// Pressure
		minPressureLabel.setText(String.format(Locale.ROOT,
				"%.2f psi (%.1f kPa)",
				r.getMinRequiredPressure_pa() * PSI_PER_PA,
				r.getMinRequiredPressure_pa() / 1000.0));

		// Details
		detailShearLabel.setText(String.format(Locale.ROOT,
				"%.2f N (%.1f lbs)",
				r.getShearPinForce_N(), r.getShearPinForce_N() / 4.44822));
		detailFrictionLabel.setText(String.format(Locale.ROOT,
				"%.2f N (%.1f lbs)",
				r.getCouplerFrictionForce_N(), r.getCouplerFrictionForce_N() / 4.44822));
		detailTotalForceLabel.setText(String.format(Locale.ROOT,
				"%.2f N (%.1f lbs)",
				r.getTotalSeparationForce_N(), r.getTotalSeparationForce_N() / 4.44822));
		detailContactPressureLabel.setText(String.format(Locale.ROOT,
				"%.2f psi (%.1f kPa)",
				r.getContactPressure_pa() * PSI_PER_PA,
				r.getContactPressure_pa() / 1000.0));
		detailMinPressureLabel.setText(String.format(Locale.ROOT,
				"%.2f psi (%.1f kPa)",
				r.getMinRequiredPressure_pa() * PSI_PER_PA,
				r.getMinRequiredPressure_pa() / 1000.0));
		detailMolesLabel.setText(String.format(Locale.ROOT,
				"%.4f mol gas at %.0f K", r.getMolesGasRequired_mol(),
				EjectionChargeEngine.T_COMBUSTION_K));
		detailLameLabel.setText(String.format(Locale.ROOT,
				"C_outer = %.2f / C_inner = %.2f",
				r.getLameFactorOuter(), r.getLameFactorInner()));

		// Warnings
		warningsPanel.removeAll();
		List<EjectionChargeResult.Warning> ws = new ArrayList<>(r.getWarnings());
		if (ws.isEmpty()) {
			JLabel ok = new JLabel("\u2713 No warnings — design parameters look reasonable.");
			ok.setForeground(new Color(0x196F2D));
			ok.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
			warningsPanel.add(ok);
		} else {
			for (EjectionChargeResult.Warning w : ws) {
				warningsPanel.add(buildWarningRow(w));
			}
		}
		warningsPanel.revalidate();
		warningsPanel.repaint();
	}

	private JPanel buildWarningRow(EjectionChargeResult.Warning w) {
		Color bg;
		String tag;
		if (w.getLevel() == EjectionChargeResult.WarningLevel.WARNING) {
			bg = new Color(0xFFE2E2);
			tag = "WARNING";
		} else {
			bg = new Color(0xFFF5D6);
			tag = "CAUTION";
		}
		JPanel row = new JPanel(new MigLayout("fillx, insets 4", "[][grow,fill]"));
		row.setBackground(bg);
		row.setBorder(BorderFactory.createLineBorder(bg.darker(), 1));
		JLabel tagLbl = new JLabel(tag);
		tagLbl.setFont(tagLbl.getFont().deriveFont(Font.BOLD));
		row.add(tagLbl);
		JLabel msg = wrappedLabel(escape(w.getMessage()), 650, false);
		row.add(msg, "growx, wmin 0");
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		return row;
	}

	// =====================================================================
	// Helpers
	// =====================================================================

	private static JLabel wrappedNote(String text, int width) {
		return wrappedLabel(text, width, true);
	}

	private static JLabel wrappedLabel(String text, int width, boolean italic) {
		String content = italic ? "<i>" + text + "</i>" : text;
		JLabel label = new JLabel("<html><table width='" + width + "'><tr><td>"
				+ content + "</td></tr></table></html>");
		label.setName("wrappedGuidance");
		label.setVerticalAlignment(SwingConstants.TOP);
		return label;
	}

	/**
	 * Tracks compact and normal dialog widths, but stops expanding once the
	 * calculator has enough room.  This keeps controls readable on a maximized
	 * window without introducing a horizontal scrollbar on smaller screens.
	 */
	private static final class CalculatorFormPanel extends JPanel implements Scrollable {
		private static final long serialVersionUID = 1L;

		@Override
		public Dimension getPreferredSize() {
			Dimension preferred = super.getPreferredSize();
			return new Dimension(FORM_MAX_WIDTH, preferred.height);
		}

		@Override
		public Dimension getPreferredScrollableViewportSize() {
			return new Dimension(FORM_PREFERRED_WIDTH, getPreferredSize().height);
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
			return Math.max(16, visibleRect.height - 16);
		}

		@Override
		public boolean getScrollableTracksViewportWidth() {
			return !(getParent() instanceof JViewport viewport) || viewport.getWidth() <= FORM_MAX_WIDTH;
		}

		@Override
		public boolean getScrollableTracksViewportHeight() {
			return false;
		}
	}

	private static JPanel titled(String title) {
		JPanel p = new JPanel();
		p.setBorder(BorderFactory.createTitledBorder(title));
		return p;
	}

	private static JSpinner inSpinner(double initial, double min, double max, double step) {
		return new JSpinner(new SpinnerNumberModel(initial, min, max, step));
	}

	private static void row(JPanel p, String label, Component editor, String suffix) {
		p.add(new JLabel(label));
		if (suffix != null && !suffix.isEmpty()) {
			p.add(editor, "growx, split 2");
			p.add(new JLabel(suffix), "wrap");
		} else {
			p.add(editor, "growx, span 2, wrap");
		}
	}

	private static void setSpinnerIn(JSpinner spinner, double meters) {
		double in = meters * IN_PER_M;
		SpinnerNumberModel m = (SpinnerNumberModel) spinner.getModel();
		double min = ((Number) m.getMinimum()).doubleValue();
		double max = ((Number) m.getMaximum()).doubleValue();
		spinner.setValue(Math.max(min, Math.min(max, round(in, 4))));
	}

	private static double round(double v, int decimals) {
		double f = Math.pow(10, decimals);
		return Math.round(v * f) / f;
	}

	private static String escape(String s) {
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	// =====================================================================
	// In-memory session persistence
	// =====================================================================

	@Override
	public void dispose() {
		// Snapshot every user-controlled field before tearing the dialog
		// down, so a subsequent open in the same JVM session restores them.
		// The snapshot lives only in memory — application exit clears it,
		// and nothing here is written to the .ork file.
		try {
			savedState = captureState();
		} catch (RuntimeException ex) {
			// Never let a snapshot failure block dialog closure.
			savedState = null;
		}
		super.dispose();
	}

	private SessionState captureState() {
		SessionState s = new SessionState();

		ComponentItem comp = (ComponentItem) componentSelector.getSelectedItem();
		s.bayComponentName = (comp == null || comp.component == null)
				? null : comp.component.getName();

		ComponentItem mate = (ComponentItem) matingComponentSelector.getSelectedItem();
		s.matingComponentName = (mate == null || mate.component == null)
				? null : mate.component.getName();

		ParachuteItem par = (ParachuteItem) parachuteSelector.getSelectedItem();
		s.parachuteName = (par == null || par.parachute == null)
				? null : par.parachute.getName();

		s.firingDirection = (EjectionFiringDirection) firingDirectionSelector.getSelectedItem();

		s.bayInnerD_in    = ((Number) bayInnerDiameterSpinner.getValue()).doubleValue();
		s.bayOuterD_in    = ((Number) bayOuterDiameterSpinner.getValue()).doubleValue();
		s.bayLength_in    = ((Number) bayLengthSpinner.getValue()).doubleValue();
		s.bayMaterial     = (AirframeMaterial) bayMaterialSelector.getSelectedItem();

		s.couplerOuterD_in    = ((Number) couplerOuterDiameterSpinner.getValue()).doubleValue();
		s.couplerInnerD_in    = ((Number) couplerInnerDiameterSpinner.getValue()).doubleValue();
		s.couplerEngagement_in = ((Number) couplerEngagementSpinner.getValue()).doubleValue();
		s.couplerMaterial     = (AirframeMaterial) couplerMaterialSelector.getSelectedItem();
		s.interference_in     = ((Number) interferenceSpinner.getValue()).doubleValue();
		s.frictionDerating    = (FrictionDerating) frictionDeratingSelector.getSelectedItem();

		s.pinDesignation = (String) pinDesignationSelector.getSelectedItem();
		s.pinCount       = ((Number) pinCountSpinner.getValue()).intValue();
		s.strengthSource = (StrengthSource) strengthSourceSelector.getSelectedItem();

		s.chuteVolumeFraction = ((Number) chuteVolumeFractionSpinner.getValue()).doubleValue();

		s.safetyFactorSliderValue = safetyFactorSlider.getValue();
		s.desiredPressure_psi     = ((Number) desiredPressureSpinner.getValue()).doubleValue();
		s.userOverrodePressure    = userOverrodePressure;

		s.showDetails = showDetailsCheckbox.isSelected();
		return s;
	}

	private void restoreState(SessionState s) {
		if (s == null) return;
		restoringState = true;
		try {
			// --- Component selectors (match by component name) ---
			selectComponentByName(componentSelector, s.bayComponentName);
			selectComponentByName(matingComponentSelector, s.matingComponentName);
			selectParachuteByName(parachuteSelector, s.parachuteName);

			if (s.firingDirection != null) {
				firingDirectionSelector.setSelectedItem(s.firingDirection);
			}

			// --- Numeric inputs ---
			setSpinnerSafe(bayInnerDiameterSpinner,    s.bayInnerD_in);
			setSpinnerSafe(bayOuterDiameterSpinner,    s.bayOuterD_in);
			setSpinnerSafe(bayLengthSpinner,           s.bayLength_in);
			if (s.bayMaterial != null) bayMaterialSelector.setSelectedItem(s.bayMaterial);

			setSpinnerSafe(couplerOuterDiameterSpinner, s.couplerOuterD_in);
			setSpinnerSafe(couplerInnerDiameterSpinner, s.couplerInnerD_in);
			setSpinnerSafe(couplerEngagementSpinner,    s.couplerEngagement_in);
			if (s.couplerMaterial != null) couplerMaterialSelector.setSelectedItem(s.couplerMaterial);
			setSpinnerSafe(interferenceSpinner,         s.interference_in);
			if (s.frictionDerating != null) frictionDeratingSelector.setSelectedItem(s.frictionDerating);

			if (s.pinDesignation != null) pinDesignationSelector.setSelectedItem(s.pinDesignation);
			setSpinnerSafe(pinCountSpinner, s.pinCount);
			if (s.strengthSource != null) strengthSourceSelector.setSelectedItem(s.strengthSource);

			setSpinnerSafe(chuteVolumeFractionSpinner, s.chuteVolumeFraction);

			// --- Pressure / SF block ---
			int sliderMin = safetyFactorSlider.getMinimum();
			int sliderMax = safetyFactorSlider.getMaximum();
			safetyFactorSlider.setValue(
					Math.max(sliderMin, Math.min(sliderMax, s.safetyFactorSliderValue)));
			safetyFactorLabel.setText(String.format(Locale.ROOT, "%.2f", currentSafetyFactor()));
			setSpinnerSafe(desiredPressureSpinner, s.desiredPressure_psi);
			userOverrodePressure = s.userOverrodePressure;

			// --- Details checkbox ---
			showDetailsCheckbox.setSelected(s.showDetails);
			showDetailsCheckbox.setText(s.showDetails
					? "\u25BC Hide calculation details"
					: "\u25B6 Show calculation details");
			detailsPanel.setVisible(s.showDetails);
		} finally {
			restoringState = false;
		}

		// Refresh all derived labels and the calculation now that the
		// listeners are live again. The calculation will respect the
		// restored userOverrodePressure flag.
		updateComputedOverlap();
		updateComputedPackedVolume();
		runCalculation();
	}

	private static void selectComponentByName(JComboBox<ComponentItem> combo, String name) {
		if (name == null) {
			if (combo.getItemCount() > 0) combo.setSelectedIndex(0);
			return;
		}
		for (int i = 0; i < combo.getItemCount(); i++) {
			ComponentItem item = combo.getItemAt(i);
			if (item != null && item.component != null
					&& name.equals(item.component.getName())) {
				combo.setSelectedIndex(i);
				return;
			}
		}
		// No match — leave at default (index 0 / "-- Manual entry --" / "-- None --").
	}

	private static void selectParachuteByName(JComboBox<ParachuteItem> combo, String name) {
		if (name == null) {
			if (combo.getItemCount() > 0) combo.setSelectedIndex(0);
			return;
		}
		for (int i = 0; i < combo.getItemCount(); i++) {
			ParachuteItem item = combo.getItemAt(i);
			if (item != null && item.parachute != null
					&& name.equals(item.parachute.getName())) {
				combo.setSelectedIndex(i);
				return;
			}
		}
	}

	private static void setSpinnerSafe(JSpinner spinner, double value) {
		SpinnerNumberModel m = (SpinnerNumberModel) spinner.getModel();
		double min = ((Number) m.getMinimum()).doubleValue();
		double max = ((Number) m.getMaximum()).doubleValue();
		spinner.setValue(Math.max(min, Math.min(max, value)));
	}

	private static void setSpinnerSafe(JSpinner spinner, int value) {
		SpinnerNumberModel m = (SpinnerNumberModel) spinner.getModel();
		int min = ((Number) m.getMinimum()).intValue();
		int max = ((Number) m.getMaximum()).intValue();
		spinner.setValue(Math.max(min, Math.min(max, value)));
	}

	/** In-memory snapshot of every user choice in the dialog. */
	private static final class SessionState {
		String bayComponentName;
		String matingComponentName;
		String parachuteName;
		EjectionFiringDirection firingDirection;

		double bayInnerD_in;
		double bayOuterD_in;
		double bayLength_in;
		AirframeMaterial bayMaterial;

		double couplerOuterD_in;
		double couplerInnerD_in;
		double couplerEngagement_in;
		AirframeMaterial couplerMaterial;
		double interference_in;
		FrictionDerating frictionDerating;

		String pinDesignation;
		int pinCount;
		StrengthSource strengthSource;

		double chuteVolumeFraction;

		int safetyFactorSliderValue;
		double desiredPressure_psi;
		boolean userOverrodePressure;

		boolean showDetails;
	}

	// --- Inner classes ---

	private static final class ComponentItem {
		final RocketComponent component;
		final String label;
		ComponentItem(RocketComponent c, String label) {
			this.component = c;
			this.label = label;
		}
		@Override public String toString() { return label; }
	}

	private static final class ParachuteItem {
		final Parachute parachute;
		final String label;
		ParachuteItem(Parachute p, String label) {
			this.parachute = p;
			this.label = label;
		}
		@Override public String toString() { return label; }
	}

	private static final class PinRenderer extends DefaultListCellRenderer {
		private static final long serialVersionUID = 1L;
		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value,
				int index, boolean isSelected, boolean cellHasFocus) {
			Component c = super.getListCellRendererComponent(list, value, index,
					isSelected, cellHasFocus);
			if (value instanceof String) {
				ShearPinSpec spec = ShearPinLookupTable.getSpec((String) value);
				PinMaterial mat = spec.getMaterial();
				String matTag;
				switch (mat) {
					case NYLON_6_6_IMPERIAL: matTag = "Nylon (imp)"; break;
					case NYLON_6_6_METRIC:   matTag = "Nylon (metric)"; break;
					case STYRENE_ROD:        matTag = "Styrene"; break;
					case ABS_ROD:            matTag = "ABS"; break;
					default:                 matTag = "";
				}
				setText(value + "   —   " + matTag
						+ (spec.hasTestedData() ? "  [tested]" : "  [theoretical]"));
			}
			return c;
		}
	}

	// Suppress unused warning (kept for future autofill enhancements)
	@SuppressWarnings("unused")
	private void noop() { SwingUtilities.invokeLater(() -> {}); }
}

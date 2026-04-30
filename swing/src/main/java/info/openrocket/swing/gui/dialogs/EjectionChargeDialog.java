package info.openrocket.swing.gui.dialogs;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
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
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.NoseCone;
import info.openrocket.core.rocketcomponent.Parachute;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.rocketcomponent.TubeCoupler;
import info.openrocket.core.util.ejection.AirframeMaterial;
import info.openrocket.core.util.ejection.EjectionChargeEngine;
import info.openrocket.core.util.ejection.EjectionChargeInputs;
import info.openrocket.core.util.ejection.EjectionChargeResult;
import info.openrocket.core.util.ejection.PinMaterial;
import info.openrocket.core.util.ejection.ShearPinLookupTable;
import info.openrocket.core.util.ejection.ShearPinSpec;
import info.openrocket.core.util.ejection.StrengthSource;
import info.openrocket.swing.gui.util.GUIUtil;

import net.miginfocom.swing.MigLayout;

/**
 * Black-powder ejection charge sizing dialog. Uses the new
 * {@link EjectionChargeEngine} (Lamé interference + shear-pin + ideal-gas-law)
 * and presents a baseline recommended pressure with a user-adjustable safety
 * factor and pressure override.
 */
public class EjectionChargeDialog extends JDialog {
	private static final long serialVersionUID = 2L;

	private static final double IN_PER_M = 39.3700787;
	private static final double M_PER_IN = 0.0254;
	private static final double PSI_PER_PA = 1.0 / 6894.757;
	private static final double PA_PER_PSI = 6894.757;

	private final OpenRocketDocument document;

	// --- Section A: component selector ---
	private JComboBox<ComponentItem> componentSelector;
	private JComboBox<ComponentItem> matingComponentSelector;
	private JComboBox<ParachuteItem> parachuteSelector;
	private JLabel computedOverlapLabel;
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

	public EjectionChargeDialog(Window owner, OpenRocketDocument document) {
		super(owner, "Ejection Charge Calculator", ModalityType.APPLICATION_MODAL);
		this.document = document;

		setLayout(new BorderLayout());
		JTabbedPane tabs = new JTabbedPane();
		tabs.addTab("Calculator", buildMainPanel());
		add(tabs, BorderLayout.CENTER);
		add(buildButtonBar(), BorderLayout.SOUTH);

		populateComponentSelector();
		updateRecommendedFromInputs();
		runCalculation();

		pack();
		// Constrain initial size — keep width but cap height so the dialog
		// fits on smaller laptop displays. The interior is wrapped in a
		// scroll pane, so the user can still reach every section.
		Dimension preferred = getPreferredSize();
		int width = Math.max(720, preferred.width);
		int height = Math.min(620, preferred.height);
		setMinimumSize(new Dimension(720, 480));
		setSize(new Dimension(width, height));
		setLocationRelativeTo(owner);
		GUIUtil.setDisposableDialogOptions(this, calculateButton);
	}

	// =====================================================================
	// Layout
	// =====================================================================

	private JPanel buildMainPanel() {
		JPanel root = new JPanel(new MigLayout("fillx, insets 10, wrap 1", "[grow,fill]"));

		root.add(buildComponentSection(),    "growx");
		root.add(buildInputsSection(),       "growx");
		root.add(buildPressureSection(),     "growx");
		root.add(buildResultsSection(),      "growx");
		root.add(buildDetailsSection(),      "growx");
		root.add(buildWarningsSection(),     "growx");

		JScrollPane scroll = new JScrollPane(root);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.setBorder(null);

		JPanel wrap = new JPanel(new BorderLayout());
		wrap.add(scroll, BorderLayout.CENTER);
		return wrap;
	}

	private JPanel buildComponentSection() {
		JPanel p = titled("Component selection");
		p.setLayout(new MigLayout("fillx, insets 6", "[][grow,fill]"));
		p.add(new JLabel("Bay component:"));
		componentSelector = new JComboBox<>();
		componentSelector.addActionListener(e -> onComponentSelected());
		p.add(componentSelector, "growx, wrap");

		p.add(new JLabel("Mating tube/coupler:"));
		matingComponentSelector = new JComboBox<>();
		matingComponentSelector.addActionListener(e -> onMatingComponentSelected());
		p.add(matingComponentSelector, "growx, wrap");

		computedOverlapLabel = new JLabel("Computed engagement (overlap): \u2014");
		p.add(computedOverlapLabel, "span 2, growx, wrap");

		p.add(new JLabel("Parachute:"));
		parachuteSelector = new JComboBox<>();
		parachuteSelector.addActionListener(e -> onParachuteSelected());
		p.add(parachuteSelector, "growx, wrap");

		computedPackedVolumeLabel = new JLabel("Computed packed chute volume: \u2014");
		p.add(computedPackedVolumeLabel, "span 2, growx, wrap");

		p.add(new JLabel("<html><i>Selecting a body tube auto-fills airframe geometry. "
				+ "Selecting a mating tube/coupler computes the engagement length "
				+ "from the axial overlap. Selecting a parachute uses its packed "
				+ "length and diameter for the packed volume.</i></html>"),
				"span 2, growx");
		return p;
	}

	private JPanel buildInputsSection() {
		JPanel p = titled("Inputs");
		p.setLayout(new MigLayout("fillx, insets 6", "[grow,fill]15[grow,fill]"));

		p.add(buildAirframePanel(), "grow");
		p.add(buildCouplerPanel(),  "grow, wrap");

		p.add(buildPinsPanel(),     "grow");
		p.add(buildChutePanel(),    "grow, wrap");

		return p;
	}

	private JPanel buildAirframePanel() {
		JPanel p = titled("Airframe (outer tube)");
		p.setLayout(new MigLayout("fillx, insets 4", "[][grow,fill][]"));

		bayInnerDiameterSpinner  = inSpinner(4.00, 0.10, 24.0, 0.05);
		bayOuterDiameterSpinner  = inSpinner(4.10, 0.10, 25.0, 0.05);
		bayLengthSpinner         = inSpinner(8.00, 0.50, 96.0, 0.25);
		bayMaterialSelector      = new JComboBox<>(AirframeMaterial.values());
		bayMaterialSelector.setSelectedItem(AirframeMaterial.FIBERGLASS);

		row(p, "Inner diameter:",   bayInnerDiameterSpinner,  "in");
		row(p, "Outer diameter:",   bayOuterDiameterSpinner,  "in");
		row(p, "Bay length:",       bayLengthSpinner,         "in");
		row(p, "Material:",         bayMaterialSelector,      "");
		return p;
	}

	private JPanel buildCouplerPanel() {
		JPanel p = titled("Coupler / nose-cone shoulder");
		p.setLayout(new MigLayout("fillx, insets 4", "[][grow,fill][]"));

		couplerOuterDiameterSpinner = inSpinner(3.99, 0.10, 24.0, 0.05);
		couplerInnerDiameterSpinner = inSpinner(3.85, 0.05, 24.0, 0.05);
		couplerEngagementSpinner    = inSpinner(3.00, 0.10, 24.0, 0.25);
		couplerEngagementSpinner.setEnabled(false);
		couplerEngagementSpinner.setToolTipText(
				"Computed automatically from the axial overlap of the two "
						+ "selected components.");
		couplerMaterialSelector     = new JComboBox<>(AirframeMaterial.values());
		couplerMaterialSelector.setSelectedItem(AirframeMaterial.FIBERGLASS);
		interferenceSpinner         = inSpinner(0.001, 0.0, 0.020, 0.0005);

		row(p, "Outer diameter:",  couplerOuterDiameterSpinner, "in");
		row(p, "Inner diameter:",  couplerInnerDiameterSpinner, "in");
		row(p, "Engagement (auto):", couplerEngagementSpinner,  "in");
		row(p, "Material:",        couplerMaterialSelector,     "");
		row(p, "Diametral δ:",     interferenceSpinner,         "in");
		return p;
	}

	private JPanel buildPinsPanel() {
		JPanel p = titled("Shear pins");
		p.setLayout(new MigLayout("fillx, insets 4", "[][grow,fill][]"));

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
		p.setLayout(new MigLayout("fillx, insets 4", "[][grow,fill][]"));

		chuteVolumeFractionSpinner = new JSpinner(
				new SpinnerNumberModel(0.10, 0.00, 0.95, 0.01));
		chuteVolumeFractionSpinner.setToolTipText(
				"Fallback estimate used only when no parachute is selected.");
		row(p, "Volume fraction:", chuteVolumeFractionSpinner, "of bay");
		p.add(new JLabel("<html><i>When a parachute is selected above, its packed "
				+ "length and diameter are used directly. Otherwise this fraction "
				+ "of the bay volume is reserved for the chute.</i></html>"),
				"span 3, growx");
		return p;
	}

	private JPanel buildPressureSection() {
		JPanel p = titled("Pressure & safety factor");
		p.setLayout(new MigLayout("fillx, insets 6", "[][grow,fill][]"));

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
			if (updatingFromCalc) return;
			userOverrodePressure = true;
			runCalculation();
		});
		resetPressureButton = new JButton("Reset to recommended");
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
		JPanel p = titled("Recommended BP charge");
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

		return p;
	}

	private JPanel buildDetailsSection() {
		JPanel container = new JPanel(new MigLayout("fillx, insets 0", "[grow,fill]"));

		showDetailsCheckbox = new JCheckBox("\u25B6 Show calculation details", false);
		showDetailsCheckbox.addActionListener(e -> {
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
		detailsPanel.setLayout(new MigLayout("fillx, insets 6", "[][grow,fill]"));
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
		calculateButton = new JButton("Calculate");
		calculateButton.addActionListener(e -> runCalculation());
		closeButton = new JButton("Close");
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
		ComponentItem sel = (ComponentItem) componentSelector.getSelectedItem();
		if (sel == null || sel.component == null) return;

		if (sel.component instanceof BodyTube) {
			BodyTube tube = (BodyTube) sel.component;
			setSpinnerIn(bayInnerDiameterSpinner, tube.getInnerRadius() * 2.0);
			setSpinnerIn(bayOuterDiameterSpinner, tube.getOuterRadius() * 2.0);
			setSpinnerIn(bayLengthSpinner,        tube.getLength());

			TubeCoupler coupler = findCouplerInside(tube);
			if (coupler != null) {
				setSpinnerIn(couplerOuterDiameterSpinner, coupler.getOuterRadius() * 2.0);
				setSpinnerIn(couplerInnerDiameterSpinner, coupler.getInnerRadius() * 2.0);
				setSpinnerIn(couplerEngagementSpinner,    coupler.getLength());
			}
		} else if (sel.component instanceof NoseCone) {
			NoseCone nose = (NoseCone) sel.component;
			double shOD = nose.getShoulderRadius() * 2.0;
			double shThk = nose.getShoulderThickness();
			double shID = Math.max(0.0, (nose.getShoulderRadius() - shThk) * 2.0);
			setSpinnerIn(couplerOuterDiameterSpinner, shOD);
			setSpinnerIn(couplerInnerDiameterSpinner, shID);
			setSpinnerIn(couplerEngagementSpinner,    nose.getShoulderLength());

			RocketComponent parent = nose.getParent();
			if (parent instanceof BodyTube) {
				BodyTube tube = (BodyTube) parent;
				setSpinnerIn(bayInnerDiameterSpinner, tube.getInnerRadius() * 2.0);
				setSpinnerIn(bayOuterDiameterSpinner, tube.getOuterRadius() * 2.0);
				setSpinnerIn(bayLengthSpinner,        tube.getLength());
			}
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

	private void onMatingComponentSelected() {
		updateComputedOverlap();
		userOverrodePressure = false;
		runCalculation();
	}

	private void onParachuteSelected() {
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
		double s1 = tubularAxialStart_m(a.component);
		double e1 = s1 + tubularLength_m(a.component);
		double s2 = tubularAxialStart_m(b.component);
		double e2 = s2 + tubularLength_m(b.component);
		double overlap_m = Math.max(0.0, Math.min(e1, e2) - Math.max(s1, s2));
		double overlap_in = overlap_m * IN_PER_M;
		computedOverlapLabel.setText(String.format(Locale.ROOT,
				"Computed engagement (overlap): %.3f in (%.1f mm)",
				overlap_in, overlap_m * 1000.0));
		// Push into the (disabled) engagement spinner so it's visible and used.
		setSpinnerIn(couplerEngagementSpinner, overlap_m);
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

		in.setCouplerOuterDiameter_m(((Double) couplerOuterDiameterSpinner.getValue()) * M_PER_IN);
		in.setCouplerInnerDiameter_m(((Double) couplerInnerDiameterSpinner.getValue()) * M_PER_IN);
		in.setCouplerEngagementLength_m(((Double) couplerEngagementSpinner.getValue()) * M_PER_IN);
		in.setCouplerMaterial((AirframeMaterial) couplerMaterialSelector.getSelectedItem());
		in.setDiametralInterference_m(((Double) interferenceSpinner.getValue()) * M_PER_IN);

		in.setShearPinDesignation((String) pinDesignationSelector.getSelectedItem());
		in.setNumShearPins(((Number) pinCountSpinner.getValue()).intValue());
		in.setStrengthSource((StrengthSource) strengthSourceSelector.getSelectedItem());

		in.setSafetyFactor(currentSafetyFactor());
		in.setChuteVolumeFraction(((Number) chuteVolumeFractionSpinner.getValue()).doubleValue());
		in.setChutePackedVolume_m3(computedPackedVolume_m3);
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
		JLabel msg = new JLabel("<html>" + escape(w.getMessage()) + "</html>");
		row.add(msg, "growx");
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		return row;
	}

	// =====================================================================
	// Helpers
	// =====================================================================

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

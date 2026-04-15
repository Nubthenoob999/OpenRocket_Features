package info.openrocket.swing.gui.dialogs;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.unit.Unit;
import info.openrocket.core.unit.UnitGroup;
import info.openrocket.core.util.EjectionChargeCalculator;
import info.openrocket.swing.gui.util.GUIUtil;

import net.miginfocom.swing.MigLayout;

/**
 * Dialog for calculating black powder ejection charges for dual-deployment
 * recovery systems. Allows the user to specify airframe dimensions and target
 * pressure, then computes the recommended BP charge mass.
 *
 * <p>Accessible from the Tools menu in the main application frame.
 *
 * @author Alex Zoghlin
 */
public class EjectionChargeDialog extends JDialog {
	private static final long serialVersionUID = 1L;
	private static final double DEFAULT_DIAMETER_METERS = 0.0508; // 2 in
	private static final double DEFAULT_LENGTH_METERS = 0.2032;   // 8 in

	private static final Unit[] LENGTH_UNITS = buildLengthUnits();

	private final OpenRocketDocument document;

	// Input components
	private JComboBox<BodyTubeItem> tubeSelector;
	private JSpinner diameterSpinner;
	private JComboBox<Unit> diameterUnitSelector;
	private JSpinner lengthSpinner;
	private JComboBox<Unit> lengthUnitSelector;
	private JSlider pressureSlider;
	private JLabel pressureValueLabel;
	private JButton calculateButton;

	// Output labels
	private JLabel volumeLabel;
	private JLabel bpMassLabel;
	private JLabel bpRangeLabel;
	private JLabel bpOuncesLabel;
	private JLabel bpTeaspoonsLabel;

	private Unit diameterUnit;
	private Unit lengthUnit;

	// Body tubes found in the design
	private final List<BodyTubeItem> bodyTubes = new ArrayList<>();

	/**
	 * Wrapper for BodyTube to display in combo box.
	 */
	private static class BodyTubeItem {
		final BodyTube tube;
		final String label;

		BodyTubeItem(BodyTube tube, String label) {
			this.tube = tube;
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	public EjectionChargeDialog(Window owner, OpenRocketDocument document) {
		super(owner, "Ejection Charge Calculator", ModalityType.MODELESS);
		this.document = document;

		initSelectedUnits();

		scanBodyTubes();
		buildUI();
		updatePressureLabel();

		pack();
		setLocationRelativeTo(owner);
		GUIUtil.setDisposableDialogOptions(this, null);
	}

	private void initSelectedUnits() {
		Unit preferred = UnitGroup.UNITS_LENGTH.getDefaultUnit();
		diameterUnit = pickLengthUnit(preferred);
		lengthUnit = diameterUnit;
	}

	private static Unit[] buildLengthUnits() {
		try {
			return new Unit[] {
					UnitGroup.UNITS_LENGTH.getUnit("mm"),
					UnitGroup.UNITS_LENGTH.getUnit("cm"),
					UnitGroup.UNITS_LENGTH.getUnit("m"),
					UnitGroup.UNITS_LENGTH.getUnit("in"),
					UnitGroup.UNITS_LENGTH.getUnit("ft")
			};
		} catch (IllegalArgumentException ex) {
			return UnitGroup.UNITS_LENGTH.getUnits();
		}
	}

	private static Unit pickLengthUnit(Unit preferred) {
		for (Unit unit : LENGTH_UNITS) {
			if (unit.getUnit().equals(preferred.getUnit())) {
				return unit;
			}
		}
		return LENGTH_UNITS[0];
	}

	/**
	 * Scan the rocket design for BodyTube components.
	 */
	private void scanBodyTubes() {
		if (document == null) return;
		Rocket rocket = document.getRocket();
		if (rocket == null) return;

		for (RocketComponent comp : rocket) {
			if (comp instanceof BodyTube) {
				BodyTube bt = (BodyTube) comp;
				String label = String.format(Locale.ROOT,
						"%s (\u00d8%.2f in x %.2f in)",
						comp.getName(),
						(bt.getInnerRadius() * 2.0) / 0.0254,
						bt.getLength() / 0.0254);
				bodyTubes.add(new BodyTubeItem(bt, label));
			}
		}
	}

	/**
	 * Build the dialog UI.
	 */
	private void buildUI() {
		JPanel panel = new JPanel(new MigLayout("fill, ins 10", "[right][grow,fill][right][grow,fill]"));

		// --- Tube selector ---
		if (!bodyTubes.isEmpty()) {
			panel.add(new JLabel("Select body tube:"), "span 1");
			tubeSelector = new JComboBox<>(bodyTubes.toArray(new BodyTubeItem[0]));
			tubeSelector.insertItemAt(new BodyTubeItem(null, "-- Manual entry --"), 0);
			tubeSelector.setSelectedIndex(1); // select first tube
			tubeSelector.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					onTubeSelected();
				}
			});
			panel.add(tubeSelector, "span 3, wrap");
		}

		// --- Diameter ---
		panel.add(new JLabel("Inner diameter:"));
		diameterSpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 100000.0, 0.1));
		diameterSpinner.setPreferredSize(new Dimension(120, diameterSpinner.getPreferredSize().height));
		panel.add(diameterSpinner, "split 2");
		diameterUnitSelector = new JComboBox<>(LENGTH_UNITS);
		diameterUnitSelector.setSelectedItem(diameterUnit);
		diameterUnitSelector.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				onLengthUnitChanged(true);
			}
		});
		panel.add(diameterUnitSelector, "growx, wrap");
		updateSpinnerStep(diameterSpinner, diameterUnit);
		setSpinnerValueFromMeters(diameterSpinner, diameterUnit, DEFAULT_DIAMETER_METERS);

		// --- Length ---
		panel.add(new JLabel("Compartment length:"));
		lengthSpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 100000.0, 0.1));
		lengthSpinner.setPreferredSize(new Dimension(120, lengthSpinner.getPreferredSize().height));
		panel.add(lengthSpinner, "split 2");
		lengthUnitSelector = new JComboBox<>(LENGTH_UNITS);
		lengthUnitSelector.setSelectedItem(lengthUnit);
		lengthUnitSelector.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				onLengthUnitChanged(false);
			}
		});
		panel.add(lengthUnitSelector, "growx, wrap");
		updateSpinnerStep(lengthSpinner, lengthUnit);
		setSpinnerValueFromMeters(lengthSpinner, lengthUnit, DEFAULT_LENGTH_METERS);

		// --- Pressure slider ---
		panel.add(new JLabel("Target pressure (PSI):"));
		pressureSlider = new JSlider(SwingConstants.HORIZONTAL,
				(int) EjectionChargeCalculator.MIN_PRESSURE_PSI,
				(int) EjectionChargeCalculator.MAX_PRESSURE_PSI,
				(int) EjectionChargeCalculator.DEFAULT_PRESSURE_PSI);
		pressureSlider.setMajorTickSpacing(5);
		pressureSlider.setMinorTickSpacing(1);
		pressureSlider.setPaintTicks(true);
		pressureSlider.setPaintLabels(true);
		Hashtable<Integer, JLabel> sliderLabels = new Hashtable<>();
		sliderLabels.put(5, new JLabel("5"));
		sliderLabels.put(10, new JLabel("10"));
		sliderLabels.put(15, new JLabel("15"));
		sliderLabels.put(20, new JLabel("20"));
		pressureSlider.setLabelTable(sliderLabels);
		pressureSlider.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				updatePressureLabel();
			}
		});
		panel.add(pressureSlider);

		pressureValueLabel = new JLabel("-");
		panel.add(pressureValueLabel, "wrap para");

		// --- Results section ---
		JPanel resultsPanel = new JPanel(new MigLayout("fill, ins 5", "[right][grow,fill]"));
		resultsPanel.setBorder(BorderFactory.createTitledBorder("Results"));

		volumeLabel = new JLabel("-");
		resultsPanel.add(new JLabel("Compartment volume:"));
		resultsPanel.add(volumeLabel, "wrap");

		bpMassLabel = new JLabel("-");
		resultsPanel.add(new JLabel("BP charge (at target pressure):"));
		resultsPanel.add(bpMassLabel, "wrap");

		bpRangeLabel = new JLabel("-");
		resultsPanel.add(new JLabel("Recommended range (10-15 PSI):"));
		resultsPanel.add(bpRangeLabel, "wrap");

		bpOuncesLabel = new JLabel("-");
		resultsPanel.add(new JLabel("Equivalent in ounces:"));
		resultsPanel.add(bpOuncesLabel, "wrap");

		bpTeaspoonsLabel = new JLabel("-");
		resultsPanel.add(new JLabel("Approx. teaspoons FFFFg BP:"));
		resultsPanel.add(bpTeaspoonsLabel, "wrap");

		panel.add(resultsPanel, "span, growx, wrap para");

		// --- Safety warning ---
		JTextArea warningArea = new JTextArea(
				"WARNING: Always ground test your ejection charges before flight. "
						+ "Start with the minimum recommended charge and increase as needed. "
						+ "These calculations are approximate and do not account for air leaks, "
						+ "coupler fit, shear pins, or other factors. "
						+ "The user is solely responsible for safe testing and use."
		);
		warningArea.setLineWrap(true);
		warningArea.setWrapStyleWord(true);
		warningArea.setEditable(false);
		warningArea.setBackground(new Color(255, 255, 200));
		warningArea.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(new Color(200, 180, 0)),
				BorderFactory.createEmptyBorder(5, 5, 5, 5)
		));
		panel.add(warningArea, "span, growx, wrap para");

		// --- Calculate / Close buttons ---
		calculateButton = new JButton("Calculate");
		calculateButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				recalculate();
			}
		});
		panel.add(calculateButton, "span, split 2, left");

		// --- Close button ---
		JButton closeButton = new JButton("Close");
		closeButton.addActionListener(e -> dispose());
		panel.add(closeButton, "right, wrap");

		getContentPane().add(panel);

		// Pre-populate from first tube if available
		if (!bodyTubes.isEmpty()) {
			onTubeSelected();
		}
	}

	private void onLengthUnitChanged(boolean isDiameter) {
		JSpinner spinner = isDiameter ? diameterSpinner : lengthSpinner;
		Unit oldUnit = isDiameter ? diameterUnit : lengthUnit;
		Unit newUnit = (Unit) (isDiameter ? diameterUnitSelector.getSelectedItem() : lengthUnitSelector.getSelectedItem());
		if (spinner == null || oldUnit == null || newUnit == null || oldUnit.equals(newUnit)) {
			return;
		}

		double valueInSi = oldUnit.fromUnit(((Number) spinner.getValue()).doubleValue());
		if (isDiameter) {
			diameterUnit = newUnit;
		} else {
			lengthUnit = newUnit;
		}
		updateSpinnerStep(spinner, newUnit);
		spinner.setValue(newUnit.toUnit(valueInSi));
	}

	private void updateSpinnerStep(JSpinner spinner, Unit unit) {
		double step;
		String symbol = unit.getUnit();
		if ("mm".equals(symbol)) {
			step = 1.0;
		} else if ("cm".equals(symbol)) {
			step = 0.1;
		} else if ("m".equals(symbol)) {
			step = 0.01;
		} else if ("in".equals(symbol)) {
			step = 0.1;
		} else if ("ft".equals(symbol)) {
			step = 0.1;
		} else {
			step = 0.1;
		}
		SpinnerNumberModel model = (SpinnerNumberModel) spinner.getModel();
		model.setStepSize(step);
	}

	private void setSpinnerValueFromMeters(JSpinner spinner, Unit unit, double meters) {
		spinner.setValue(unit.toUnit(meters));
	}

	private void updatePressureLabel() {
		int pressurePSI = pressureSlider.getValue();
		double pressurePa = EjectionChargeCalculator.psiToPascals(pressurePSI);
		pressureValueLabel.setText(String.format(Locale.ROOT, "%d PSI (%.0f kPa)", pressurePSI, pressurePa / 1000.0));
	}

	/**
	 * Handle selection of a body tube from the combo box.
	 */
	private void onTubeSelected() {
		if (tubeSelector == null) return;
		BodyTubeItem item = (BodyTubeItem) tubeSelector.getSelectedItem();
		if (item == null || item.tube == null) return;

		BodyTube bt = item.tube;
		double innerDiameter = bt.getInnerRadius() * 2.0;
		double length = bt.getLength();

		setSpinnerValueFromMeters(diameterSpinner, diameterUnit, innerDiameter);
		setSpinnerValueFromMeters(lengthSpinner, lengthUnit, length);
	}

	/**
	 * Recalculate and update all output fields.
	 */
	private void recalculate() {
		double diamValue = diameterUnit.fromUnit(((Number) diameterSpinner.getValue()).doubleValue());
		double lenValue = lengthUnit.fromUnit(((Number) lengthSpinner.getValue()).doubleValue());
		int pressurePSI = pressureSlider.getValue();

		if (diamValue <= 0 || lenValue <= 0) {
			volumeLabel.setText("-");
			bpMassLabel.setText("-");
			bpRangeLabel.setText("-");
			bpOuncesLabel.setText("-");
			bpTeaspoonsLabel.setText("-");
			return;
		}

		double volumeM3 = EjectionChargeCalculator.calculateVolume(diamValue, lenValue);
		double pressurePa = EjectionChargeCalculator.psiToPascals(pressurePSI);

		double bpGrams = EjectionChargeCalculator.calculateBPMassGrams(pressurePa, volumeM3);
		double bpLow = EjectionChargeCalculator.calculateBPMassGrams(
				EjectionChargeCalculator.psiToPascals(EjectionChargeCalculator.LOW_PRESSURE_PSI), volumeM3);
		double bpHigh = EjectionChargeCalculator.calculateBPMassGrams(
				EjectionChargeCalculator.psiToPascals(EjectionChargeCalculator.HIGH_PRESSURE_PSI), volumeM3);

		double volumeCubicIn = EjectionChargeCalculator.cubicMetersToInches(volumeM3);
		double bpOunces = EjectionChargeCalculator.gramsToOunces(bpGrams);
		double bpTeaspoons = EjectionChargeCalculator.gramsToTeaspoons(bpGrams);

		// Update pressure label
		updatePressureLabel();

		// Update volume display
		double volumeCm3 = volumeM3 * 1e6;
		volumeLabel.setText(String.format(Locale.ROOT, "%.2f in\u00b3 (%.1f cm\u00b3)", volumeCubicIn, volumeCm3));

		// Update BP mass
		bpMassLabel.setText(String.format(Locale.ROOT, "%.2f g", bpGrams));
		bpRangeLabel.setText(String.format(Locale.ROOT, "%.2f g - %.2f g", bpLow, bpHigh));
		bpOuncesLabel.setText(String.format(Locale.ROOT, "%.3f oz", bpOunces));
		bpTeaspoonsLabel.setText(String.format(Locale.ROOT, "%.2f tsp", bpTeaspoons));
	}
}

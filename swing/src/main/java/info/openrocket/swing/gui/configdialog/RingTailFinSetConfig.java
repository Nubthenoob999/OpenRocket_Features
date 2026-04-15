package info.openrocket.swing.gui.configdialog;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;

import net.miginfocom.swing.MigLayout;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.l10n.Translator;
import info.openrocket.core.material.Material;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.startup.Application;
import info.openrocket.core.unit.UnitGroup;

import info.openrocket.swing.gui.SpinnerEditor;
import info.openrocket.swing.gui.adaptors.CustomFocusTraversalPolicy;
import info.openrocket.swing.gui.adaptors.DoubleModel;
import info.openrocket.swing.gui.components.BasicSlider;
import info.openrocket.swing.gui.components.UnitSelector;

/**
 * Configuration dialog for a RingTailFinSet component.
 *
 * @author Alex Zoghlin
 */
public class RingTailFinSetConfig extends RocketComponentConfig {
	private static final long serialVersionUID = 1L;
	private static final Translator trans = Application.getTranslator();

	public RingTailFinSetConfig(OpenRocketDocument d, RocketComponent c, JDialog parent) {
		super(d, c, parent);

		JPanel primary = new JPanel(new MigLayout());

		JPanel panel = new JPanel(new MigLayout("gap rel unrel, ins 0", "[][65lp::][30lp::][]", ""));

		//// Ring radius:
		panel.add(new JLabel(trans.get("RingTailFinSetCfg.lbl.RingRadius")));

		DoubleModel m = new DoubleModel(component, "RingRadius", UnitGroup.UNITS_LENGTH, 0);
		register(m);

		JSpinner spin = new JSpinner(m.getSpinnerModel());
		spin.setEditor(new SpinnerEditor(spin));
		focusElement = spin;
		panel.add(spin, "growx");
		order.add(((SpinnerEditor) spin.getEditor()).getTextField());

		panel.add(new UnitSelector(m), "growx");
		panel.add(new BasicSlider(m.getSliderModel(0, 0.04, 0.2)), "w 100lp, wrap para");

		//// Ring chord (axial length):
		panel.add(new JLabel(trans.get("RingTailFinSetCfg.lbl.RingChord")));

		m = new DoubleModel(component, "RingChord", UnitGroup.UNITS_LENGTH, 0);
		register(m);

		spin = new JSpinner(m.getSpinnerModel());
		spin.setEditor(new SpinnerEditor(spin));
		panel.add(spin, "growx");
		order.add(((SpinnerEditor) spin.getEditor()).getTextField());

		panel.add(new UnitSelector(m), "growx");
		panel.add(new BasicSlider(m.getSliderModel(0, 0.02, 0.1)), "w 100lp, wrap para");

		//// Ring thickness:
		panel.add(new JLabel(trans.get("RingTailFinSetCfg.lbl.RingThickness")));

		m = new DoubleModel(component, "RingThickness", UnitGroup.UNITS_LENGTH, 0);
		register(m);

		spin = new JSpinner(m.getSpinnerModel());
		spin.setEditor(new SpinnerEditor(spin));
		panel.add(spin, "growx");
		order.add(((SpinnerEditor) spin.getEditor()).getTextField());

		panel.add(new UnitSelector(m), "growx");
		panel.add(new BasicSlider(m.getSliderModel(0, 0.01)), "w 100lp, wrap 20lp");

		primary.add(panel, "grow, gapright 40lp");

		// Right side panel
		panel = new JPanel(new MigLayout("gap rel unrel, ins 0", "[][65lp::][30lp::][]", ""));

		{ //// Placement
			PlacementPanel placementPanel = new PlacementPanel(component, order);
			register(placementPanel);
			panel.add(placementPanel, "span, grow, wrap");
		}

		{ //// Material
			MaterialPanel materialPanel = new MaterialPanel(component, document, Material.Type.BULK, order);
			register(materialPanel);
			panel.add(materialPanel, "span, grow, wrap");
		}

		primary.add(panel, "grow");

		//// General and General properties
		tabbedPane.insertTab(trans.get("LaunchLugCfg.tab.General"), null, primary,
				trans.get("LaunchLugCfg.tab.Generalprop"), 0);
		tabbedPane.setSelectedIndex(0);

		// Apply the custom focus travel policy to this config dialog
		order.add(cancelButton);
		order.add(okButton);
		CustomFocusTraversalPolicy policy = new CustomFocusTraversalPolicy(order);
		parent.setFocusTraversalPolicy(policy);
	}

	@Override
	public void updateFields() {
		super.updateFields();
	}
}

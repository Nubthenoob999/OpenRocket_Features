package info.openrocket.swing.gui.choosers;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.file.stl.STLExportOptions;
import info.openrocket.core.file.wavefrontobj.DefaultCoordTransform;
import info.openrocket.core.file.wavefrontobj.ObjUtils;
import info.openrocket.core.l10n.Translator;
import info.openrocket.core.preferences.ApplicationPreferences;
import info.openrocket.core.rocketcomponent.ComponentAssembly;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.startup.Application;
import info.openrocket.core.unit.UnitGroup;
import info.openrocket.swing.gui.SpinnerEditor;
import info.openrocket.swing.gui.adaptors.DoubleModel;
import info.openrocket.swing.gui.util.GUIUtil;
import net.miginfocom.swing.MigLayout;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import java.awt.Window;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Option chooser panel for STL export settings.
 * Provides a simplified interface compared to the OBJ exporter since STL files
 * are primarily used for 3D printing (no appearance, always triangulated).
 *
 * @author Alex Zoghlin
 */
public class STLOptionChooser extends JPanel implements OptionChooser {
    private static final Translator trans = Application.getTranslator();

    private final JComponent parent;
    private final JLabel componentsLabel;
    private final JCheckBox exportChildren;
    private final JCheckBox exportAllInstances;
    private final JCheckBox exportMotors;
    private final JCheckBox removeOffset;
    private final JComboBox<ObjUtils.LevelOfDetail> LOD;
    private final DoubleModel scalingModel;

    private final List<RocketComponent> selectedComponents;
    private final Rocket rocket;

    public STLOptionChooser(JComponent parent, STLExportOptions opts,
                            List<RocketComponent> selectedComponents, Rocket rocket) {
        super(new MigLayout("hidemode 3"));

        this.parent = parent;
        this.selectedComponents = selectedComponents;
        this.rocket = rocket;

        // Component label
        componentsLabel = new JLabel();
        updateComponentsLabel(selectedComponents);
        this.add(componentsLabel, "spanx, wrap unrel");

        // Export children
        this.exportChildren = new JCheckBox(trans.get("OBJOptionChooser.checkbox.exportChildren"));
        this.exportChildren.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                List<RocketComponent> components;
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    final Set<RocketComponent> allComponents = new HashSet<>();
                    for (RocketComponent component : selectedComponents) {
                        allComponents.add(component);
                        allComponents.addAll(component.getAllChildren());
                    }
                    components = new ArrayList<>(allComponents);
                } else {
                    components = selectedComponents;
                }
                updateComponentsLabel(components);
            }
        });
        this.add(exportChildren, "spanx, wrap");

        // Export all instances
        this.exportAllInstances = new JCheckBox(trans.get("OBJOptionChooser.checkbox.exportAllInstances"));
        this.exportAllInstances.setToolTipText(trans.get("OBJOptionChooser.checkbox.exportAllInstances.ttip"));
        this.add(exportAllInstances, "spanx, wrap");

        // Export motors
        this.exportMotors = new JCheckBox(trans.get("OBJOptionChooser.checkbox.exportMotors"));
        this.exportMotors.setToolTipText(trans.get("OBJOptionChooser.checkbox.exportMotors.ttip"));
        this.add(exportMotors, "spanx, wrap");

        // Remove offset
        this.removeOffset = new JCheckBox(trans.get("OBJOptionChooser.checkbox.removeOffset"));
        this.removeOffset.setToolTipText(trans.get("OBJOptionChooser.checkbox.removeOffset.ttip"));
        this.add(removeOffset, "spanx, wrap unrel");

        // Scaling
        JLabel scalingLabel = new JLabel(trans.get("OBJOptionChooser.lbl.Scaling"));
        scalingLabel.setToolTipText(trans.get("OBJOptionChooser.lbl.Scaling.ttip"));
        this.add(scalingLabel, "spanx, split 2");
        this.scalingModel = new DoubleModel(opts, "ScalingDouble", UnitGroup.UNITS_SCALING, 0, 10000);
        JSpinner spin = new JSpinner(scalingModel.getSpinnerModel());
        spin.setToolTipText(trans.get("OBJOptionChooser.lbl.Scaling.ttip"));
        spin.setEditor(new SpinnerEditor(spin, 5));
        this.add(spin, "wrap");

        // Level of detail
        JLabel LODLabel = new JLabel(trans.get("OBJOptionChooser.lbl.LevelOfDetail"));
        LODLabel.setToolTipText(trans.get("OBJOptionChooser.lbl.LevelOfDetail.ttip"));
        this.add(LODLabel, "spanx, split 2");
        this.LOD = new JComboBox<>(ObjUtils.LevelOfDetail.values());
        this.LOD.setToolTipText(trans.get("OBJOptionChooser.lbl.LevelOfDetail.ttip"));
        this.add(LOD, "growx, wrap unrel");

        // Info label about STL format
        JLabel infoLabel = new JLabel(trans.get("STLOptionChooser.lbl.info"));
        this.add(infoLabel, "spanx, wrap");

        loadOptions(opts);
    }

    private void updateComponentsLabel(List<RocketComponent> components) {
        final String labelText;
        final String tooltip;

        final boolean isSingleComponent = components.size() == 1;
        final String componentName = isSingleComponent ? "<b>" + components.get(0).getName() + "</b>" :
                trans.get("OBJOptionChooser.lbl.multipleComponents");
        labelText = String.format(trans.get("OBJOptionChooser.lbl.component"), componentName);
        tooltip = createComponentsTooltip(components);
        componentsLabel.setText(labelText);
        componentsLabel.setToolTipText(tooltip);
    }

    private static String createComponentsTooltip(List<RocketComponent> selectedComponents) {
        if (selectedComponents.size() <= 1) {
            return "";
        }

        StringBuilder tooltipBuilder = new StringBuilder("<html>");
        int counter = 0;
        for (int i = 0; i < selectedComponents.size() - 1; i++) {
            tooltipBuilder.append(selectedComponents.get(i).getName()).append(", ");
            if (counter == 4) {
                tooltipBuilder.append("<br>");
                counter = 0;
            } else {
                counter++;
            }
        }
        tooltipBuilder.append(selectedComponents.get(selectedComponents.size() - 1).getComponentName());
        tooltipBuilder.append("</html>");
        return tooltipBuilder.toString();
    }

    public void loadOptions(STLExportOptions opts) {
        boolean onlyComponentAssemblies = isOnlyComponentAssembliesSelected(selectedComponents);
        boolean hasChildren = isComponentsHaveChildren(selectedComponents);
        if (onlyComponentAssemblies || !hasChildren) {
            exportChildren.setSelected(true);
            exportChildren.setEnabled(false);
            if (onlyComponentAssemblies) {
                exportChildren.setToolTipText(trans.get("OBJOptionChooser.checkbox.exportChildren.assemblies.ttip"));
            } else {
                exportChildren.setToolTipText(trans.get("OBJOptionChooser.checkbox.exportChildren.noChildren.ttip"));
            }
        } else {
            exportChildren.setEnabled(true);
            exportChildren.setSelected(opts.isExportChildren());
            exportChildren.setToolTipText(trans.get("OBJOptionChooser.checkbox.exportChildren.ttip"));
        }

        this.exportAllInstances.setSelected(opts.isExportAllInstances());
        this.exportMotors.setSelected(opts.isExportMotors());
        this.removeOffset.setSelected(opts.isRemoveOffset());
        this.scalingModel.setValue(opts.getScaling());
        this.LOD.setSelectedItem(opts.getLOD());
    }

    public void storeOptions(STLExportOptions opts) {
        opts.setExportChildren(exportChildren.isSelected());
        opts.setExportAllInstances(exportAllInstances.isSelected());
        opts.setExportMotors(exportMotors.isSelected());
        opts.setRemoveOffset(removeOffset.isSelected());
        opts.setScaling((float) scalingModel.getValue());
        opts.setLOD((ObjUtils.LevelOfDetail) LOD.getSelectedItem());
        opts.setTransformer(new DefaultCoordTransform(rocket.getLength()));
    }

    private static boolean isOnlyComponentAssembliesSelected(List<RocketComponent> selectedComponents) {
        for (RocketComponent component : selectedComponents) {
            if (!(component instanceof ComponentAssembly)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isComponentsHaveChildren(List<RocketComponent> selectedComponents) {
        for (RocketComponent component : selectedComponents) {
            if (component.getChildCount() > 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void storeOptions(OpenRocketDocument document, ApplicationPreferences preferences) {
        storeOptions(document.getDefaultSTLOptions());
    }
}

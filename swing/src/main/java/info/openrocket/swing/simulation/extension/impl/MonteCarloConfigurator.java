package info.openrocket.swing.simulation.extension.impl;

import info.openrocket.core.document.Simulation;
import info.openrocket.core.montecarlo.MonteCarloExtension;
import info.openrocket.swing.gui.simulation.MonteCarloSetupPanel;
import info.openrocket.swing.simulation.extension.AbstractSwingSimulationExtensionConfigurator;
import net.miginfocom.swing.MigLayout;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/**
 * Compatibility configurator retained for source compatibility with older integrations.
 *
 * Monte Carlo is now configured from its native tab in the simulation edit dialog.  This class
 * deliberately is not registered as a plugin, preventing the legacy extension configurator from
 * becoming a second, conflicting UI entry point.
 *
 * @deprecated use {@link info.openrocket.swing.gui.simulation.MonteCarloSimulationPanel}
 */
@Deprecated
public class MonteCarloConfigurator
        extends AbstractSwingSimulationExtensionConfigurator<MonteCarloExtension> {

    public MonteCarloConfigurator() {
        super(MonteCarloExtension.class);
    }

    @Override
    protected JComponent getConfigurationComponent(MonteCarloExtension ext, Simulation sim, JPanel panel) {
        panel.setLayout(new MigLayout("fill, ins 0", "[grow, fill]", "[grow, fill]"));
        panel.add(new MonteCarloSetupPanel(ext, sim), "grow, push");

        JScrollPane scroll = new JScrollPane(panel);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(14);
        return scroll;
    }
}

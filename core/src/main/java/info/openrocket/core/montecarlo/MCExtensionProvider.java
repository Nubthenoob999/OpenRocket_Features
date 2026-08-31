package info.openrocket.core.montecarlo;

import info.openrocket.core.plugin.Plugin;
import info.openrocket.core.simulation.extension.AbstractSimulationExtensionProvider;

@Plugin 
public class MCExtensionProvider extends AbstractSimulationExtensionProvider {

    public MCExtensionProvider() {
        // Keep the historical extension ID registered so Monte Carlo settings in existing .ork
        // files can still be loaded.  Swing treats this as native managed state and does not
        // expose the provider in the Simulation Extensions menu.
        super(MonteCarloExtension.class, "Simulation Analysis", "Monte Carlo");
    }
}

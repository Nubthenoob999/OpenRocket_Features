package info.openrocket.core.montecarlo;

import info.openrocket.core.plugin.Plugin;
import info.openrocket.core.simulation.extension.AbstractSimulationExtensionProvider;

@Plugin 
public class MCExtensionProvider extends AbstractSimulationExtensionProvider {

    public MCExtensionProvider() {
        super(MonteCarloExtension.class, "Simulation Analysis", "Monte Carlo");
    }
}
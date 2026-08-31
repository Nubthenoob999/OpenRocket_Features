package info.openrocket.swing.simulation.extension.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import org.junit.jupiter.api.Test;

import info.openrocket.core.document.Simulation;
import info.openrocket.core.montecarlo.MonteCarloExtension;
import info.openrocket.core.plugin.Plugin;
import info.openrocket.core.util.TestRockets;
import info.openrocket.swing.util.BaseTestCase;

@SuppressWarnings("deprecation")
public class MonteCarloConfiguratorTest extends BaseTestCase {
	@Test
	public void testLegacyConfiguratorIsNotPluginRegistered() {
		assertNull(MonteCarloConfigurator.class.getAnnotation(Plugin.class),
				"the native Monte Carlo tab must be the only registered configuration UI");
	}

	@Test
	public void testDisturbanceControlsAreDisplayedAndPreserveTheirSettings() {
        Simulation simulation = new Simulation(TestRockets.makeEstesAlphaIII());
        simulation.setFlightConfigurationId(TestRockets.TEST_FCID_0);
        MonteCarloExtension extension = new MonteCarloExtension();
        extension.setGustEventsEnabled(true);
        extension.setShearLayerEnabled(true);

        JPanel root = new JPanel();
        JComponent component = new MonteCarloConfigurator()
                .getConfigurationComponent(extension, simulation, root);
        assertNotNull(component);

        JTabbedPane tabs = findFirst(root, JTabbedPane.class);
        assertNotNull(tabs);
		assertEquals(List.of("Launch", "Atmosphere", "Disturbances", "Vehicle"),
                tabTitles(tabs));
		assertTrue(extension.isGustEventsEnabled(), "opening the UI must preserve the gust setting");
		assertTrue(extension.isShearLayerEnabled(), "opening the UI must preserve the shear setting");

        Component disturbances = tabs.getComponentAt(tabs.indexOfTab("Disturbances"));
		List<String> checkBoxLabels = findAll(disturbances, JCheckBox.class).stream()
				.map(JCheckBox::getText)
				.toList();
		assertEquals(List.of("Enable gust events", "Enable shear layer"), checkBoxLabels);
    }

    private static List<String> tabTitles(JTabbedPane tabs) {
        List<String> titles = new ArrayList<>();
        for (int index = 0; index < tabs.getTabCount(); index++) {
            titles.add(tabs.getTitleAt(index));
        }
        return titles;
    }

    private static <T extends Component> T findFirst(Component root, Class<T> type) {
        List<T> matches = findAll(root, type);
        return matches.isEmpty() ? null : matches.get(0);
    }

    private static <T extends Component> List<T> findAll(Component root, Class<T> type) {
        List<T> matches = new ArrayList<>();
        if (type.isInstance(root)) matches.add(type.cast(root));
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                matches.addAll(findAll(child, type));
            }
        }
        return matches;
    }
}

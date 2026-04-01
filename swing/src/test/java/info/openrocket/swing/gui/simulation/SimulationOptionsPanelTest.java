package info.openrocket.swing.gui.simulation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayDeque;
import java.util.Deque;

import javax.swing.JCheckBox;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.util.Modules;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.OpenRocketDocumentFactory;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.formatting.RocketDescriptor;
import info.openrocket.core.formatting.RocketDescriptorImpl;
import info.openrocket.core.l10n.DebugTranslator;
import info.openrocket.core.l10n.Translator;
import info.openrocket.core.plugin.PluginModule;
import info.openrocket.core.preferences.ApplicationPreferences;
import info.openrocket.core.startup.Application;
import info.openrocket.core.util.TestRockets;
import info.openrocket.swing.ServicesForTesting;

public class SimulationOptionsPanelTest {

	@BeforeAll
	public static void setUp() {
		Module applicationModule = new AbstractModule() {
			@Override
			protected void configure() {
				bind(ApplicationPreferences.class).to(ServicesForTesting.PreferencesForTesting.class);
				bind(Translator.class).toProvider(ServicesForTesting.TranslatorProviderForTesting.class);
				bind(RocketDescriptor.class).to(RocketDescriptorImpl.class);
			}
		};
		Module debugTranslator = new AbstractModule() {
			@Override
			protected void configure() {
				bind(Translator.class).toInstance(new DebugTranslator(null));
			}
		};
		Module pluginModule = new PluginModule();
		Injector injector = Guice.createInjector(Modules.override(applicationModule).with(debugTranslator), pluginModule);
		Application.setInjector(injector);
	}

	@Test
	public void testAirbrakeControlsAppearAndLegacyExtensionMenuEntryHidden() throws Exception {
		OpenRocketDocument document = OpenRocketDocumentFactory.createDocumentFromRocket(TestRockets.makeEstesAlphaIII());
		Simulation simulation = new Simulation(document.getRocket());
		document.addSimulation(simulation);

		final SimulationOptionsPanel[] holder = new SimulationOptionsPanel[1];
		SwingUtilities.invokeAndWait(() -> holder[0] = new SimulationOptionsPanel(document, simulation));
		SimulationOptionsPanel panel = holder[0];
		assertNotNull(panel);

		JCheckBox checkbox = findCheckBox(panel, "Enable airbrakes");
		assertNotNull(checkbox);
		assertFalse(simulation.getOptions().isAirbrakesEnabled());

		AirbrakeSettingsPanel airbrakePanel = findComponent(panel, AirbrakeSettingsPanel.class);
		assertNotNull(airbrakePanel);
		assertFalse(airbrakePanel.isVisible());

		SwingUtilities.invokeAndWait(checkbox::doClick);
		assertTrue(simulation.getOptions().isAirbrakesEnabled());
		assertTrue(airbrakePanel.isVisible());

		assertFalse(menuContainsText(panel.extensionMenu, "AirBrakes Simulation"));
	}

	private static JCheckBox findCheckBox(Container root, String text) {
		return findComponent(root, JCheckBox.class, component -> text.equals(component.getText()));
	}

	private static boolean menuContainsText(JPopupMenu popupMenu, String text) {
		for (Component component : popupMenu.getComponents()) {
			if (component instanceof JMenu menu) {
				if (text.equals(menu.getText()) || menuContainsText(menu.getPopupMenu(), text)) {
					return true;
				}
			}
		}
		return false;
	}

	private static <T extends Component> T findComponent(Container root, Class<T> type) {
		return findComponent(root, type, component -> true);
	}

	private static <T extends Component> T findComponent(Container root, Class<T> type,
			java.util.function.Predicate<T> predicate) {
		Deque<Component> queue = new ArrayDeque<>();
		queue.add(root);
		while (!queue.isEmpty()) {
			Component component = queue.removeFirst();
			if (type.isInstance(component)) {
				T cast = type.cast(component);
				if (predicate.test(cast)) {
					return cast;
				}
			}
			if (component instanceof Container container) {
				for (Component child : container.getComponents()) {
					queue.addLast(child);
				}
			}
		}
		return null;
	}
}

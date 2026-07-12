package info.openrocket.swing.gui.simulation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.util.ArrayDeque;
import java.util.Deque;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;

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
import info.openrocket.core.aerodynamics.rom.RomFallbackMode;
import info.openrocket.core.aerodynamics.rom.RomMode;
import info.openrocket.core.startup.Application;
import info.openrocket.core.util.TestRockets;
import info.openrocket.swing.ServicesForTesting;
import info.openrocket.swing.gui.components.DescriptionArea;

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

	@Test
	public void testPhaseIRomControlsUpdateOptionsAndMethodLabel() throws Exception {
		OpenRocketDocument document = OpenRocketDocumentFactory.createDocumentFromRocket(TestRockets.makeEstesAlphaIII());
		Simulation simulation = new Simulation(document.getRocket());
		document.addSimulation(simulation);

		final SimulationOptionsPanel[] holder = new SimulationOptionsPanel[1];
		SwingUtilities.invokeAndWait(() -> holder[0] = new SimulationOptionsPanel(document, simulation));
		SimulationOptionsPanel panel = holder[0];
		assertNotNull(panel);

		JCheckBox enableRom = findCheckBox(panel, "Enable Phase I pathline ROM");
		JCheckBox diagnostics = field(panel, "romDiagnosticsCheckBox", JCheckBox.class);
		@SuppressWarnings("unchecked")
		JComboBox<RomMode> modeCombo = field(panel, "romModeCombo", JComboBox.class);
		@SuppressWarnings("unchecked")
		JComboBox<RomFallbackMode> fallbackCombo = field(panel, "romFallbackCombo", JComboBox.class);
		JLabel methodLabel = field(panel, "aerodynamicMethodValue", JLabel.class);
		JTextArea romSummary = field(panel, "romStatusArea", JTextArea.class);

		assertTrue(methodLabel.getText().contains("Pathline ROM disabled"));
		assertFalse(modeCombo.isEnabled());

		SwingUtilities.invokeAndWait(enableRom::doClick);
		assertTrue(simulation.getOptions().isRomEnabled());
		assertTrue(modeCombo.isEnabled());

		SwingUtilities.invokeAndWait(() -> {
			modeCombo.setSelectedItem(RomMode.DIAGNOSTIC);
			fallbackCombo.setSelectedItem(RomFallbackMode.FORCE_ROM);
			diagnostics.doClick();
		});

		assertEquals(RomMode.DIAGNOSTIC, simulation.getOptions().getRomMode());
		assertEquals(RomFallbackMode.FORCE_ROM, simulation.getOptions().getRomFallbackMode());
		assertFalse(simulation.getOptions().isRomDiagnosticsEnabled());
		assertTrue(methodLabel.getText().contains("Pathline ROM"));
		assertTrue(methodLabel.getText().contains("Diagnostic"));
		assertTrue(romSummary.getText().contains("Force ROM"));
		assertTrue(romSummary.getText().contains("ignored"));
		assertTrue(romSummary.getText().contains("Seed plan"));
	}

	@Test
	public void testWeathercockingCheckboxUpdatesOptions() throws Exception {
		OpenRocketDocument document = OpenRocketDocumentFactory.createDocumentFromRocket(TestRockets.makeEstesAlphaIII());
		Simulation simulation = new Simulation(document.getRocket());
		document.addSimulation(simulation);

		final SimulationOptionsPanel[] holder = new SimulationOptionsPanel[1];
		SwingUtilities.invokeAndWait(() -> holder[0] = new SimulationOptionsPanel(document, simulation));
		SimulationOptionsPanel panel = holder[0];
		assertNotNull(panel);

		JCheckBox checkbox = findCheckBox(panel, "Enable weathercocking compensation");
		assertNotNull(checkbox);
		assertFalse(simulation.getOptions().isWeathercockingCompensationEnabled());

		SwingUtilities.invokeAndWait(checkbox::doClick);
		assertTrue(simulation.getOptions().isWeathercockingCompensationEnabled());

		SwingUtilities.invokeAndWait(checkbox::doClick);
		assertFalse(simulation.getOptions().isWeathercockingCompensationEnabled());
	}

	@Test
	public void testSimulationOptionsPanelTracksNarrowViewportWidth() throws Exception {
		OpenRocketDocument document = OpenRocketDocumentFactory.createDocumentFromRocket(TestRockets.makeEstesAlphaIII());
		Simulation simulation = new Simulation(document.getRocket());
		document.addSimulation(simulation);

		final SimulationOptionsPanel[] panelHolder = new SimulationOptionsPanel[1];
		final JScrollPane[] scrollHolder = new JScrollPane[1];
		SwingUtilities.invokeAndWait(() -> {
			panelHolder[0] = new SimulationOptionsPanel(document, simulation);
			scrollHolder[0] = SimulationTabLayoutUtils.wrapFormScrollable(panelHolder[0]);
			scrollHolder[0].setSize(new Dimension(800, 600));
			layoutTree(scrollHolder[0]);
		});

		SimulationOptionsPanel panel = panelHolder[0];
		JScrollPane scroll = scrollHolder[0];
		assertEquals(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER, scroll.getHorizontalScrollBarPolicy());
		assertTrue(panel instanceof Scrollable);
		assertTrue(panel.getScrollableTracksViewportWidth());
		assertTrue(panel.getWidth() <= scroll.getViewport().getExtentSize().width,
				"Simulation options panel should track the viewport width");
	}

	@Test
	public void testExtensionDescriptionDoesNotForceWidePreferredSize() throws Exception {
		OpenRocketDocument document = OpenRocketDocumentFactory.createDocumentFromRocket(TestRockets.makeEstesAlphaIII());
		Simulation simulation = new Simulation(document.getRocket());
		document.addSimulation(simulation);

		final SimulationOptionsPanel[] panelHolder = new SimulationOptionsPanel[1];
		SwingUtilities.invokeAndWait(() -> {
			panelHolder[0] = new SimulationOptionsPanel(document, simulation);
			panelHolder[0].setSize(new Dimension(320, 600));
			layoutTree(panelHolder[0]);
		});

		SimulationOptionsPanel panel = panelHolder[0];
		JPanel extensionsPanel = findTitledPanel(panel, "SimExt");
		assertNotNull(extensionsPanel);
		assertTrue(extensionsPanel.getPreferredSize().width <= 360,
				"Simulation extensions tile should not publish a wide preferred width");
		assertFalse(containsDirectChild(panel, DescriptionArea.class),
				"Static Simulation extensions copy should not use DescriptionArea");
	}

	@Test
	public void testCompactValueLabelDoesNotExposeLongPathAsPreferredText() {
		String longPath = "/Users/opteron92/Projects/OpenRocket_Features/build/reports/phase-three/phase-three-analysis.csv";
		JLabel label = SimulationTabLayoutUtils.createCompactValueLabel(longPath);

		assertTrue(label.getText().length() < longPath.length());
		assertEquals(longPath, label.getToolTipText());
	}

	@Test
	public void testAirbrakePathFieldDoesNotForceWidePreferredSize() throws Exception {
		String longPath = "H:\\Shared drives\\HPRC\\1_NASA SL 2025-2026\\Senior Design\\Subteams\\Aerodynamics\\CDR\\Thingstodo-2\\Openrocket\\Drag Curve CDR.csv";
		OpenRocketDocument document = OpenRocketDocumentFactory.createDocumentFromRocket(TestRockets.makeEstesAlphaIII());
		Simulation simulation = new Simulation(document.getRocket());
		simulation.getOptions().setAirbrakesEnabled(true);
		simulation.getOptions().setCfdDataFilePath(longPath);
		document.addSimulation(simulation);

		final SimulationOptionsPanel[] holder = new SimulationOptionsPanel[1];
		SwingUtilities.invokeAndWait(() -> holder[0] = new SimulationOptionsPanel(document, simulation));
		AirbrakeSettingsPanel airbrakePanel = findComponent(holder[0], AirbrakeSettingsPanel.class);
		assertNotNull(airbrakePanel);

		JTextField pathField = findComponent(airbrakePanel, JTextField.class, field -> longPath.equals(field.getText()));
		assertNotNull(pathField);
		assertEquals(1, pathField.getColumns());
		assertTrue(pathField.getPreferredSize().width < 260,
				"Airbrake CSV path field should not publish the full path as preferred width");
		assertEquals(longPath, pathField.getToolTipText());
	}

	private static JCheckBox findCheckBox(Container root, String text) {
		return findComponent(root, JCheckBox.class, component -> text.equals(component.getText()));
	}

	private static <T> T field(Object target, String fieldName, Class<T> type) {
		try {
			java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
			field.setAccessible(true);
			return type.cast(field.get(target));
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("Unable to read field " + fieldName, e);
		}
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

	private static JPanel findTitledPanel(Container root, String titleFragment) {
		return findComponent(root, JPanel.class, panel -> {
			Border border = panel.getBorder();
			if (border instanceof TitledBorder titledBorder) {
				String title = titledBorder.getTitle();
				return title != null && title.contains(titleFragment);
			}
			return false;
		});
	}

	private static boolean containsDirectChild(Container root, Class<? extends Component> type) {
		for (Component child : root.getComponents()) {
			if (type.isInstance(child)) {
				return true;
			}
		}
		return false;
	}

	private static void layoutTree(Container container) {
		container.doLayout();
		for (Component child : container.getComponents()) {
			if (child instanceof Container childContainer) {
				layoutTree(childContainer);
			}
		}
	}
}

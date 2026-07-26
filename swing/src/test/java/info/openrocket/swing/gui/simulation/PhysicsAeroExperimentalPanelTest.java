package info.openrocket.swing.gui.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Predicate;

import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
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

import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroMode;
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
import info.openrocket.swing.gui.simulation.PhysicsAeroExperimentalPanel.Fidelity;

public class PhysicsAeroExperimentalPanelTest {

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
		Injector injector = Guice.createInjector(
				Modules.override(applicationModule).with(debugTranslator), new PluginModule());
		Application.setInjector(injector);
	}

	@Test
	public void testTabExplainsTheBuildPipelineInOrderedSteps() throws Exception {
		PhysicsAeroExperimentalPanel panel = createPanel(new Simulation(rocketDocument().getRocket()));

		for (String section : new String[] {"How the simulation uses the table", "What gets solved",
				"Build", "Stored table", "Inspect the stored table", "Activity log"}) {
			assertNotNull(findTitledPanel(panel, section), "Missing section: " + section);
		}

		JProgressBar progress = find(panel, JProgressBar.class);
		assertNotNull(progress);
		assertTrue(progress.isStringPainted(), "Progress should name the stage it is in, not just a percentage");
		assertEquals("Idle", progress.getString());

		assertNotNull(findLabelContaining(panel, "Read the airframe geometry"),
				"The build pipeline should list its geometry stage");
		assertNotNull(findLabelContaining(panel, "Pre-solve supersonic"),
				"The build pipeline should list its supersonic pre-solve stage");
		assertNotNull(findLabelContaining(panel, "Solve every Mach"),
				"The build pipeline should list its cell sweep stage");
		assertNotNull(findLabelContaining(panel, "write it to the cache"),
				"The build pipeline should list its validate-and-cache stage");
	}

	@Test
	public void testDomainSummaryReportsTheSolverWorkForTheSelectedGrid() throws Exception {
		PhysicsAeroExperimentalPanel panel = createPanel(new Simulation(rocketDocument().getRocket()));

		JTextArea solveWork = findTextContaining(panel, "cells, plus");
		assertNotNull(solveWork, "The tab should say how many cells the selected grid solves");
		assertTrue(solveWork.getText().startsWith("462 cells"),
				"Standard should describe the 22x7x3 flight-domain grid, but said: " + solveWork.getText());

		JComboBox<?> grid = findComboBoxOf(panel, Fidelity.class);
		assertNotNull(grid);
		SwingUtilities.invokeAndWait(() -> grid.setSelectedItem(Fidelity.PREVIEW));
		assertTrue(solveWork.getText().startsWith("255 cells"),
				"Switching to Preview should update the reported work, but said: " + solveWork.getText());
	}

	@Test
	public void testModeSelectionUpdatesTheSimulationAndItsExplanation() throws Exception {
		OpenRocketDocument document = rocketDocument();
		Simulation simulation = new Simulation(document.getRocket());
		PhysicsAeroExperimentalPanel panel = createPanel(simulation);

		JComboBox<?> mode = findComboBoxOf(panel, PhysicsAeroMode.class);
		assertNotNull(mode);
		assertEquals(PhysicsAeroMode.OFF, simulation.getOptions().getPhysicsAeroMode());
		assertNotNull(findTextContaining(panel, "built-in Barrowman method"),
				"The Off mode should be explained in plain language");

		SwingUtilities.invokeAndWait(() -> mode.setSelectedItem(PhysicsAeroMode.STRICT));
		assertEquals(PhysicsAeroMode.STRICT, simulation.getOptions().getPhysicsAeroMode());
		assertNotNull(findTextContaining(panel, "the simulation stops instead"),
				"Strict mode should explain that a missing lookup aborts the run");
	}

	@Test
	public void testTurbulentBoundaryLayerToggleIsExposedAndPersisted() throws Exception {
		Simulation simulation = new Simulation(rocketDocument().getRocket());
		PhysicsAeroExperimentalPanel panel = createPanel(simulation);

		javax.swing.JCheckBox turbulent = find(panel, javax.swing.JCheckBox.class);
		assertNotNull(turbulent);
		assertFalse(simulation.getOptions().isForceTurbulentBoundaryLayer());
		SwingUtilities.invokeAndWait(turbulent::doClick);
		assertTrue(simulation.getOptions().isForceTurbulentBoundaryLayer());
	}

	@Test
	public void testTabTracksTheViewportWidthWithoutHorizontalScrolling() throws Exception {
		Simulation simulation = new Simulation(rocketDocument().getRocket());
		final PhysicsAeroExperimentalPanel[] holder = new PhysicsAeroExperimentalPanel[1];
		final JScrollPane[] scroll = new JScrollPane[1];
		SwingUtilities.invokeAndWait(() -> {
			holder[0] = new PhysicsAeroExperimentalPanel(simulation);
			scroll[0] = SimulationTabLayoutUtils.wrapFormScrollable(holder[0]);
			scroll[0].setSize(new Dimension(900, 700));
			layoutTree(scroll[0]);
			layoutTree(scroll[0]);
		});

		assertEquals(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER, scroll[0].getHorizontalScrollBarPolicy());
		assertTrue(holder[0].getScrollableTracksViewportWidth());
		assertTrue(holder[0].getWidth() <= scroll[0].getViewport().getExtentSize().width,
				"The Aerodynamics tab should never be wider than its viewport");
	}

	private static OpenRocketDocument rocketDocument() {
		return OpenRocketDocumentFactory.createDocumentFromRocket(TestRockets.makeEstesAlphaIII());
	}

	private static PhysicsAeroExperimentalPanel createPanel(Simulation simulation) throws Exception {
		final PhysicsAeroExperimentalPanel[] holder = new PhysicsAeroExperimentalPanel[1];
		SwingUtilities.invokeAndWait(() -> {
			holder[0] = new PhysicsAeroExperimentalPanel(simulation);
			holder[0].setSize(new Dimension(900, 1400));
			layoutTree(holder[0]);
			layoutTree(holder[0]);
		});
		return holder[0];
	}

	private static <T extends Component> T find(Container root, Class<T> type) {
		return find(root, type, component -> true);
	}

	private static <T extends Component> T find(Container root, Class<T> type, Predicate<T> predicate) {
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

	private static JTextArea findTextContaining(Container root, String fragment) {
		return find(root, JTextArea.class, area -> area.getText() != null && area.getText().contains(fragment));
	}

	private static javax.swing.JLabel findLabelContaining(Container root, String fragment) {
		return find(root, javax.swing.JLabel.class,
				label -> label.getText() != null && label.getText().contains(fragment));
	}

	private static JComboBox<?> findComboBoxOf(Container root, Class<?> itemType) {
		return find(root, JComboBox.class, combo -> combo.getItemCount() > 0
				&& itemType.isInstance(combo.getItemAt(0)));
	}

	private static JPanel findTitledPanel(Container root, String titleFragment) {
		return find(root, JPanel.class, panel -> {
			Border border = panel.getBorder();
			if (border instanceof TitledBorder titled) {
				String title = titled.getTitle();
				return title != null && title.contains(titleFragment);
			}
			return false;
		});
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

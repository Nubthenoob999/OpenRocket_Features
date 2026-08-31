package info.openrocket.swing.gui.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JTabbedPane;

import org.junit.jupiter.api.Test;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.OpenRocketDocumentFactory;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.util.TestRockets;
import info.openrocket.swing.util.BaseTestCase;

public class SimulationConfigDialogTabsTest extends BaseTestCase {

	@Test
	public void testMonteCarloTabSitsBetweenSimulationOptionsAndAerodynamics() {
		if (GraphicsEnvironment.isHeadless()) {
			return;
		}

		OpenRocketDocument document =
				OpenRocketDocumentFactory.createDocumentFromRocket(TestRockets.makeEstesAlphaIII());
		Simulation simulation = new Simulation(document.getRocket());
		simulation.setFlightConfigurationId(TestRockets.TEST_FCID_0);
		document.addSimulation(simulation);

		SimulationConfigDialog dialog =
				new SimulationConfigDialog(null, document, true, simulation);
		try {
			JTabbedPane tabs = findFirst(dialog.getContentPane(), JTabbedPane.class);
			assertNotNull(tabs);

			List<String> titles = tabTitles(tabs);
			int monteCarlo = titles.indexOf("Monte Carlo");
			int aerodynamics = titles.indexOf("Aerodynamics");
			assertTrue(monteCarlo > 0, "Monte Carlo must follow the simulation settings tabs");
			assertEquals(monteCarlo + 1, aerodynamics,
					"Monte Carlo must sit directly before Aerodynamics");

			Component tab = tabs.getComponentAt(monteCarlo);
			assertTrue(tab instanceof MonteCarloSimulationPanel);

			writeSnapshotWhenRequested(dialog);
		} finally {
			dialog.dispose();
		}
	}

	private static void writeSnapshotWhenRequested(SimulationConfigDialog dialog) {
		String snapshotPath = System.getenv("OPENROCKET_UI_SNAPSHOT");
		if (snapshotPath == null || snapshotPath.isBlank()) {
			return;
		}

		BufferedImage image = new BufferedImage(dialog.getWidth(), dialog.getHeight(),
				BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try {
			dialog.printAll(graphics);
		} finally {
			graphics.dispose();
		}

		File snapshot = new File(snapshotPath);
		File parent = snapshot.getParentFile();
		if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
			throw new AssertionError("Unable to create snapshot directory " + parent);
		}
		try {
			ImageIO.write(image, "png", snapshot);
		} catch (IOException exception) {
			throw new AssertionError("Unable to write UI snapshot " + snapshot, exception);
		}
	}

	private static List<String> tabTitles(JTabbedPane tabs) {
		List<String> titles = new ArrayList<>();
		for (int index = 0; index < tabs.getTabCount(); index++) {
			titles.add(tabs.getTitleAt(index));
		}
		return titles;
	}

	private static <T extends Component> T findFirst(Component root, Class<T> type) {
		if (type.isInstance(root)) {
			return type.cast(root);
		}
		if (root instanceof Container container) {
			for (Component child : container.getComponents()) {
				T match = findFirst(child, type);
				if (match != null) {
					return match;
				}
			}
		}
		return null;
	}
}

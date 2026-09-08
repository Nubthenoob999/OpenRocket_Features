package info.openrocket.swing.gui.dialogs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import info.openrocket.core.document.OpenRocketDocumentFactory;
import info.openrocket.swing.util.BaseTestCase;

class EjectionChargeDialogLayoutTest extends BaseTestCase {

	@Test
	void keepsTheFormAndEditorsCompactInAWideWindow() throws Exception {
		Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(),
				"Headless environment cannot construct Swing dialogs");

		SwingUtilities.invokeAndWait(() -> {
			EjectionChargeDialog dialog = new EjectionChargeDialog(
					null, OpenRocketDocumentFactory.createEmptyRocket());
			try {
				dialog.doLayout();
				layoutTree(dialog.getContentPane());

				JScrollPane scroll = descendants(dialog, JScrollPane.class).get(0);
				scroll.setSize(1400, 700);
				layoutTree(scroll);
				assertEquals(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER,
						scroll.getHorizontalScrollBarPolicy());
				assertTrue(scroll.getViewport().getWidth() > 1040,
						"the test must exercise a viewport wider than the form cap");
				assertTrue(scroll.getViewport().getView().getWidth() <= 1040,
						"the calculator form must not stretch across a wide window");

				for (JComboBox<?> selector : descendants(dialog, JComboBox.class)) {
					assertTrue(selector.getWidth() <= 600,
							"selector grew beyond the compact form width: " + selector.getBounds());
				}
				for (JSpinner spinner : descendants(dialog, JSpinner.class)) {
					assertTrue(spinner.getWidth() <= 480,
							"numeric editor grew beyond the compact input width: " + spinner.getBounds());
				}
				for (JLabel label : descendants(dialog, JLabel.class)) {
					if ("wrappedGuidance".equals(label.getName())) {
						assertTrue(label.getPreferredSize().width <= 680,
								"guidance should wrap at a bounded width: " + label.getPreferredSize());
					}
				}

				writeSnapshotWhenRequested(dialog);
			} finally {
				dialog.dispose();
			}
		});
	}

	private static void layoutTree(Container container) {
		container.doLayout();
		for (Component component : container.getComponents()) {
			if (component instanceof Container child) {
				layoutTree(child);
			}
		}
	}

	private static <T extends Component> List<T> descendants(Container parent, Class<T> type) {
		List<T> matches = new ArrayList<>();
		for (Component child : parent.getComponents()) {
			if (type.isInstance(child)) matches.add(type.cast(child));
			if (child instanceof Container container) matches.addAll(descendants(container, type));
		}
		return matches;
	}

	private static void writeSnapshotWhenRequested(EjectionChargeDialog dialog) {
		String snapshotPath = System.getenv("OPENROCKET_UI_SNAPSHOT");
		if (snapshotPath == null || snapshotPath.isBlank()) return;

		Container content = dialog.getContentPane();
		BufferedImage image = new BufferedImage(content.getWidth(), content.getHeight(),
				BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try {
			content.printAll(graphics);
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
}


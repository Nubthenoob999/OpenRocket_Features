package info.openrocket.swing.gui.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import info.openrocket.swing.gui.SpinnerEditor;

class GUIUtilSpinnerCommitTest {
	@Test
	void commitsPendingTextBeforeAnActionReadsTheModel() throws Exception {
		JSpinner spinner = new JSpinner(new SpinnerNumberModel(2.0, 0.0, 20.0, 0.1));
		spinner.setEditor(new SpinnerEditor(spinner));
		JPanel root = new JPanel();
		root.add(spinner);
		AtomicBoolean committed = new AtomicBoolean();

		SwingUtilities.invokeAndWait(() -> {
			((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setText("7.25");
			committed.set(GUIUtil.commitSpinnerEdits(root));
		});

		assertTrue(committed.get());
		assertEquals(7.25, ((Number) spinner.getValue()).doubleValue(), 1.0e-9);
	}

	@Test
	void rejectsInvalidPendingText() throws Exception {
		JSpinner spinner = new JSpinner(new SpinnerNumberModel(2.0, 0.0, 20.0, 0.1));
		spinner.setEditor(new SpinnerEditor(spinner));
		AtomicBoolean committed = new AtomicBoolean(true);

		SwingUtilities.invokeAndWait(() -> {
			((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setText("not a number");
			committed.set(GUIUtil.commitSpinnerEdits(spinner));
		});

		assertFalse(committed.get());
		assertEquals(2.0, ((Number) spinner.getValue()).doubleValue(), 1.0e-9);
	}
}

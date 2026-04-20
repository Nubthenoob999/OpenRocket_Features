package info.openrocket.swing.gui.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.rom.RomFallbackMode;
import info.openrocket.core.aerodynamics.rom.RomMode;
import info.openrocket.core.aerodynamics.rom.RomSettings;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.util.TestRockets;
import info.openrocket.swing.util.BaseTestCase;

public class RomPrestepPanelTest extends BaseTestCase {

	@Test
	public void testPanelReflectsRomSettingsAndGeometry() throws Exception {
		OpenRocketDocument doc = TestRockets.makeTestRocket_v104_withSimulationData();
		Simulation simulation = doc.getSimulations().get(0);
		RomSettings settings = simulation.getOptions().getRomSettings();
		settings.setEnabled(true);
		settings.setMode(RomMode.DIAGNOSTIC);
		settings.setFallbackMode(RomFallbackMode.FORCE_ROM);
		settings.setBodyMeridianSeedCount(6);
		settings.setFinSurfaceSeedCount(2);
		settings.setPrestepMach(0.875);
		settings.setPrestepAngleOfAttackDeg(7.5);
		settings.setPrestepThetaDeg(15.0);
		settings.setPrestepPlumeState(0.25);
		simulation.getOptions().setRomSettings(settings);

		RomPrestepPanel panel = onEdt(() -> new RomPrestepPanel(simulation));

		JLabel romStatus = field(panel, "romStatusValue", JLabel.class);
		JLabel mode = field(panel, "modeValue", JLabel.class);
		JLabel fallback = field(panel, "fallbackValue", JLabel.class);
		JLabel geometryHash = field(panel, "geometryHashValue", JLabel.class);
		JLabel finCount = field(panel, "finCountValue", JLabel.class);
		JLabel seedCount = field(panel, "seedCountLabel", JLabel.class);
		DefaultTableModel model = field(panel, "seedTableModel", DefaultTableModel.class);
		JSpinner machSpinner = field(panel, "machSpinner", JSpinner.class);
		JSpinner aoaSpinner = field(panel, "aoaSpinner", JSpinner.class);
		JSpinner thetaSpinner = field(panel, "thetaSpinner", JSpinner.class);
		JSpinner plumeSpinner = field(panel, "plumeSpinner", JSpinner.class);

		assertEquals("Enabled", romStatus.getText());
		assertEquals("Diagnostic", mode.getText());
		assertEquals("Force ROM", fallback.getText());
		assertTrue(geometryHash.getText().length() >= 8);
		assertTrue(finCount.getText().length() > 0);
		assertTrue(seedCount.getText().contains("total seeds"));
		assertTrue(model.getRowCount() > 0);
		assertEquals(0.875, spinnerDoubleValue(machSpinner), 0.0);
		assertEquals(7.5, spinnerDoubleValue(aoaSpinner), 0.0);
		assertEquals(15.0, spinnerDoubleValue(thetaSpinner), 0.0);
		assertEquals(0.25, spinnerDoubleValue(plumeSpinner), 0.0);
	}

	@Test
	public void testPathlineSpinnersWriteThroughToRomSettings() throws Exception {
		OpenRocketDocument doc = TestRockets.makeTestRocket_v104_withSimulationData();
		Simulation simulation = doc.getSimulations().get(0);
		RomPrestepPanel panel = onEdt(() -> new RomPrestepPanel(simulation));

		JSpinner bodySpinner = field(panel, "bodyPathlineSpinner", JSpinner.class);
		JSpinner finSpinner = field(panel, "finPathlineSpinner", JSpinner.class);

		onEdtRun(() -> {
			bodySpinner.setValue(9);
			finSpinner.setValue(4);
		});

		assertEquals(9, simulation.getOptions().getRomSettings().getBodyMeridianSeedCount());
		assertEquals(4, simulation.getOptions().getRomSettings().getFinSurfaceSeedCount());
	}

	@Test
	public void testPrestepConditionSpinnersWriteThroughToRomSettings() throws Exception {
		OpenRocketDocument doc = TestRockets.makeTestRocket_v104_withSimulationData();
		Simulation simulation = doc.getSimulations().get(0);
		RomPrestepPanel panel = onEdt(() -> new RomPrestepPanel(simulation));

		JSpinner machSpinner = field(panel, "machSpinner", JSpinner.class);
		JSpinner aoaSpinner = field(panel, "aoaSpinner", JSpinner.class);
		JSpinner thetaSpinner = field(panel, "thetaSpinner", JSpinner.class);
		JSpinner plumeSpinner = field(panel, "plumeSpinner", JSpinner.class);

		onEdtRun(() -> {
			machSpinner.setValue(1.25);
			aoaSpinner.setValue(9.0);
			thetaSpinner.setValue(25.0);
			plumeSpinner.setValue(0.5);
		});

		RomSettings settings = simulation.getOptions().getRomSettings();
		assertEquals(1.25, settings.getPrestepMach(), 0.0);
		assertEquals(9.0, settings.getPrestepAngleOfAttackDeg(), 0.0);
		assertEquals(25.0, settings.getPrestepThetaDeg(), 0.0);
		assertEquals(0.5, settings.getPrestepPlumeState(), 0.0);
	}

	@Test
	public void testComputeProducesReadableResultSummary() throws Exception {
		OpenRocketDocument doc = TestRockets.makeTestRocket_v104_withSimulationData();
		Simulation simulation = doc.getSimulations().get(0);
		RomSettings settings = simulation.getOptions().getRomSettings();
		settings.setEnabled(true);
		simulation.getOptions().setRomSettings(settings);

		RomPrestepPanel panel = onEdt(() -> new RomPrestepPanel(simulation));
		JSpinner machSpinner = field(panel, "machSpinner", JSpinner.class);
		JSpinner aoaSpinner = field(panel, "aoaSpinner", JSpinner.class);
		JTextArea resultArea = field(panel, "resultArea", JTextArea.class);
		JLabel regime = field(panel, "regimeLabel", JLabel.class);
		JLabel confidence = field(panel, "confidenceLabel", JLabel.class);

		onEdtRun(() -> {
			machSpinner.setValue(1.0);
			aoaSpinner.setValue(5.0);
		});
		onEdtRun(() -> invokeNoArg(panel, "runCompute"));

		assertNotNull(resultArea.getText());
		assertTrue(resultArea.getText().length() > 40);
		assertTrue(regime.getText().length() > 0);
		assertTrue(confidence.getText().contains("%"));
	}

	private static void invokeNoArg(Object target, String methodName) {
		try {
			var method = target.getClass().getDeclaredMethod(methodName);
			method.setAccessible(true);
			method.invoke(target);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("Unable to invoke method " + methodName, e);
		}
	}

	private static <T> T field(Object target, String fieldName, Class<T> type) throws Exception {
		Field field = target.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		Object value = field.get(target);
		assertNotNull(value);
		return type.cast(value);
	}

	private static <T> T onEdt(EdtSupplier<T> supplier) throws Exception {
		AtomicReference<T> ref = new AtomicReference<>();
		AtomicReference<Throwable> error = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> {
			try {
				ref.set(supplier.get());
			} catch (Throwable t) {
				error.set(t);
			}
		});
		if (error.get() != null) {
			throw new RuntimeException(error.get());
		}
		return ref.get();
	}

	private static void onEdtRun(Runnable runnable) throws Exception {
		AtomicReference<Throwable> error = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> {
			try {
				runnable.run();
			} catch (Throwable t) {
				error.set(t);
			}
		});
		if (error.get() != null) {
			throw new RuntimeException(error.get());
		}
	}

	private static double spinnerDoubleValue(JSpinner spinner) {
		return ((Number) spinner.getValue()).doubleValue();
	}

	@FunctionalInterface
	private interface EdtSupplier<T> {
		T get() throws Exception;
	}
}

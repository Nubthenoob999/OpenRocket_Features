package info.openrocket.swing.gui.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JSpinner;
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
	public void panelReflectsRomSettingsAndGeometry() throws Exception {
		OpenRocketDocument doc = TestRockets.makeTestRocket_v104_withSimulationData();
		Simulation simulation = doc.getSimulations().get(0);
		RomSettings settings = simulation.getOptions().getRomSettings();
		settings.setEnabled(true);
		settings.setMode(RomMode.DIAGNOSTIC);
		settings.setFallbackMode(RomFallbackMode.FORCE_ROM);
		settings.setBodyMeridianSeedCount(6);
		settings.setFinSurfaceSeedCount(2);
		settings.setPreviewMachMin(0.30);
		settings.setPreviewMachMax(0.50);
		settings.setPreviewMachStep(0.10);
		settings.setPreviewAoADegMin(0.0);
		settings.setPreviewAoADegMax(2.0);
		settings.setPreviewAoADegStep(2.0);
		settings.setPreviewThetaDeg(15.0);
		settings.setPreviewPlumeState(0.25);
		simulation.getOptions().setRomSettings(settings);

		RomPrestepPanel panel = onEdt(() -> new RomPrestepPanel(simulation));

		JLabel romStatus = field(panel, "romStatusValue", JLabel.class);
		JLabel mode = field(panel, "modeValue", JLabel.class);
		JLabel fallback = field(panel, "fallbackValue", JLabel.class);
		JLabel geometryHash = field(panel, "geometryHashValue", JLabel.class);
		JLabel finCount = field(panel, "finCountValue", JLabel.class);
		JLabel seedCount = field(panel, "seedCountLabel", JLabel.class);
		DefaultTableModel seedModel = field(panel, "seedTableModel", DefaultTableModel.class);
		JSpinner machMin = field(panel, "machMinSpinner", JSpinner.class);
		JSpinner machMax = field(panel, "machMaxSpinner", JSpinner.class);
		JSpinner aoaMin = field(panel, "aoaMinSpinner", JSpinner.class);
		JSpinner aoaMax = field(panel, "aoaMaxSpinner", JSpinner.class);
		JSpinner theta = field(panel, "thetaSpinner", JSpinner.class);
		JSpinner plume = field(panel, "plumeSpinner", JSpinner.class);

		assertEquals("Enabled", romStatus.getText());
		assertEquals("Diagnostic", mode.getText());
		assertEquals("Force ROM", fallback.getText());
		assertTrue(geometryHash.getText().length() >= 8);
		assertTrue(finCount.getText().length() > 0);
		assertTrue(seedCount.getText().contains("total seeds"));
		assertTrue(seedModel.getRowCount() > 0);
		assertEquals(0.30, spinnerDoubleValue(machMin), 1e-9);
		assertEquals(0.50, spinnerDoubleValue(machMax), 1e-9);
		assertEquals(0.0, spinnerDoubleValue(aoaMin), 1e-9);
		assertEquals(2.0, spinnerDoubleValue(aoaMax), 1e-9);
		assertEquals(15.0, spinnerDoubleValue(theta), 1e-9);
		assertEquals(0.25, spinnerDoubleValue(plume), 1e-9);
	}

	@Test
	public void pathlineSpinnersWriteThroughToRomSettings() throws Exception {
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
	public void sweepRangeSpinnersWriteThroughToRomSettings() throws Exception {
		OpenRocketDocument doc = TestRockets.makeTestRocket_v104_withSimulationData();
		Simulation simulation = doc.getSimulations().get(0);
		RomPrestepPanel panel = onEdt(() -> new RomPrestepPanel(simulation));

		JSpinner machMin = field(panel, "machMinSpinner", JSpinner.class);
		JSpinner machMax = field(panel, "machMaxSpinner", JSpinner.class);
		JSpinner machStep = field(panel, "machStepSpinner", JSpinner.class);
		JSpinner aoaMin = field(panel, "aoaMinSpinner", JSpinner.class);
		JSpinner aoaMax = field(panel, "aoaMaxSpinner", JSpinner.class);
		JSpinner aoaStep = field(panel, "aoaStepSpinner", JSpinner.class);
		JSpinner theta = field(panel, "thetaSpinner", JSpinner.class);
		JSpinner plume = field(panel, "plumeSpinner", JSpinner.class);

		onEdtRun(() -> {
			machMin.setValue(0.5);
			machMax.setValue(1.2);
			machStep.setValue(0.05);
			aoaMin.setValue(1.0);
			aoaMax.setValue(8.0);
			aoaStep.setValue(1.0);
			theta.setValue(25.0);
			plume.setValue(0.5);
		});

		RomSettings s = simulation.getOptions().getRomSettings();
		assertEquals(0.5, s.getPreviewMachMin(), 1e-9);
		assertEquals(1.2, s.getPreviewMachMax(), 1e-9);
		assertEquals(0.05, s.getPreviewMachStep(), 1e-9);
		assertEquals(1.0, s.getPreviewAoADegMin(), 1e-9);
		assertEquals(8.0, s.getPreviewAoADegMax(), 1e-9);
		assertEquals(1.0, s.getPreviewAoADegStep(), 1e-9);
		assertEquals(25.0, s.getPreviewThetaDeg(), 1e-9);
		assertEquals(0.5, s.getPreviewPlumeState(), 1e-9);
	}

	@Test
	public void rowCountEstimateUpdatesFromRanges() throws Exception {
		OpenRocketDocument doc = TestRockets.makeTestRocket_v104_withSimulationData();
		Simulation simulation = doc.getSimulations().get(0);
		RomPrestepPanel panel = onEdt(() -> new RomPrestepPanel(simulation));

		JSpinner machMin = field(panel, "machMinSpinner", JSpinner.class);
		JSpinner machMax = field(panel, "machMaxSpinner", JSpinner.class);
		JSpinner machStep = field(panel, "machStepSpinner", JSpinner.class);
		JSpinner aoaMin = field(panel, "aoaMinSpinner", JSpinner.class);
		JSpinner aoaMax = field(panel, "aoaMaxSpinner", JSpinner.class);
		JSpinner aoaStep = field(panel, "aoaStepSpinner", JSpinner.class);
		JLabel rowCount = field(panel, "rowCountLabel", JLabel.class);

		onEdtRun(() -> {
			machMin.setValue(0.30);
			machMax.setValue(0.60);
			machStep.setValue(0.10);
			aoaMin.setValue(0.0);
			aoaMax.setValue(4.0);
			aoaStep.setValue(2.0);
		});

		assertTrue(rowCount.getText().startsWith("12"),
				"expected 12 rows, got: " + rowCount.getText());
	}

	@Test
	public void generatePreviewDiagnosticsPopulatesTable() throws Exception {
		OpenRocketDocument doc = TestRockets.makeTestRocket_v104_withSimulationData();
		Simulation simulation = doc.getSimulations().get(0);
		RomSettings settings = simulation.getOptions().getRomSettings();
		settings.setEnabled(true);
		settings.setPreviewMachMin(0.30);
		settings.setPreviewMachMax(0.50);
		settings.setPreviewMachStep(0.10);
		settings.setPreviewAoADegMin(0.0);
		settings.setPreviewAoADegMax(2.0);
		settings.setPreviewAoADegStep(2.0);
		simulation.getOptions().setRomSettings(settings);

		RomPrestepPanel panel = onEdt(() -> new RomPrestepPanel(simulation));
		DefaultTableModel diagModel = field(panel, "diagTableModel", DefaultTableModel.class);

		// Synchronous invocation of the runner path that the panel uses.
		onEdtRun(() -> invokeNoArg(panel, "startSweep"));
		waitForWorkerToFinish(panel);

		assertTrue(diagModel.getRowCount() >= 4,
				"expected at least 4 rows (Mach grid 3 x AoA grid 2), got " + diagModel.getRowCount());
	}

	@Test
	public void diagnosticsTableHasExpectedColumns() throws Exception {
		String[] expected = invokeColumns();
		Set<String> expectedSet = new HashSet<>();
		for (String c : expected) {
			expectedSet.add(c);
		}
		assertTrue(expectedSet.contains("Mach"));
		assertTrue(expectedSet.contains("AoA_deg"));
		assertTrue(expectedSet.contains("CD_final"));
		assertTrue(expectedSet.contains("Confidence"));
		assertTrue(expectedSet.contains("Fallback_weight"));
		assertTrue(expectedSet.contains("Separation_fraction"));
		assertTrue(expectedSet.contains("Mean_stiffness"));
		assertTrue(expectedSet.contains("Status"));
		assertTrue(expectedSet.contains("Warnings"));
	}

	@Test
	public void csvExportWritesHeadersAndRows() throws Exception {
		DefaultTableModel model = new DefaultTableModel(new Object[]{"Mach", "AoA_deg", "Notes"}, 0);
		model.addRow(new Object[]{"0.300", "0.00", "ok"});
		model.addRow(new Object[]{"0.300", "2.00", "fallback, blended"});

		StringWriter writer = new StringWriter();
		Method m = RomPrestepPanel.class.getDeclaredMethod("writeCsv",
				javax.swing.table.TableModel.class, java.io.Writer.class);
		m.setAccessible(true);
		m.invoke(null, model, writer);

		String csv = writer.toString();
		assertTrue(csv.startsWith("Mach,AoA_deg,Notes\n"), "missing header: " + csv);
		assertTrue(csv.contains("0.300,0.00,ok\n"));
		assertTrue(csv.contains("\"fallback, blended\""));
	}

	@Test
	public void oversizedSweepIsBlockedByRowCap() throws Exception {
		OpenRocketDocument doc = TestRockets.makeTestRocket_v104_withSimulationData();
		Simulation simulation = doc.getSimulations().get(0);
		RomSettings settings = simulation.getOptions().getRomSettings();
		settings.setEnabled(true);
		settings.setPreviewMachMin(0.0);
		settings.setPreviewMachMax(8.0);
		settings.setPreviewMachStep(0.01);
		settings.setPreviewAoADegMin(0.0);
		settings.setPreviewAoADegMax(45.0);
		settings.setPreviewAoADegStep(0.1);
		settings.setPreviewMaxRows(50);
		simulation.getOptions().setRomSettings(settings);

		RomPrestepPanel panel = onEdt(() -> new RomPrestepPanel(simulation));
		DefaultTableModel diagModel = field(panel, "diagTableModel", DefaultTableModel.class);
		JLabel status = field(panel, "tableStatusLabel", JLabel.class);
		JButton exportCsv = field(panel, "exportCsvButton", JButton.class);

		onEdtRun(() -> invokeNoArg(panel, "startSweep"));

		assertEquals(0, diagModel.getRowCount());
		assertTrue(status.getText().toLowerCase().contains("row cap")
				|| status.getText().toLowerCase().contains("exceeds"),
				"status should mention the row cap: " + status.getText());
		assertFalse(exportCsv.isEnabled());
	}

	@Test
	public void cancelLeavesPartialRowsWithoutThrowing() throws Exception {
		OpenRocketDocument doc = TestRockets.makeTestRocket_v104_withSimulationData();
		Simulation simulation = doc.getSimulations().get(0);
		RomSettings settings = simulation.getOptions().getRomSettings();
		settings.setEnabled(true);
		settings.setPreviewMachMin(0.30);
		settings.setPreviewMachMax(0.50);
		settings.setPreviewMachStep(0.10);
		settings.setPreviewAoADegMin(0.0);
		settings.setPreviewAoADegMax(2.0);
		settings.setPreviewAoADegStep(2.0);
		simulation.getOptions().setRomSettings(settings);

		RomPrestepPanel panel = onEdt(() -> new RomPrestepPanel(simulation));

		onEdtRun(() -> invokeNoArg(panel, "startSweep"));
		// Immediately cancel.
		onEdtRun(() -> invokeNoArg(panel, "cancelSweep"));
		waitForWorkerToFinish(panel);

		// No exception thrown is enough to validate the contract; ensure the panel is idle.
		assertNotNull(field(panel, "diagTableModel", DefaultTableModel.class));
	}

	@Test
	public void romDisabledShowsBarrowmanOnlyPreviewRows() throws Exception {
		OpenRocketDocument doc = TestRockets.makeTestRocket_v104_withSimulationData();
		Simulation simulation = doc.getSimulations().get(0);
		RomSettings settings = simulation.getOptions().getRomSettings();
		settings.setEnabled(false);
		settings.setPreviewMachMin(0.30);
		settings.setPreviewMachMax(0.50);
		settings.setPreviewMachStep(0.10);
		settings.setPreviewAoADegMin(0.0);
		settings.setPreviewAoADegMax(2.0);
		settings.setPreviewAoADegStep(2.0);
		simulation.getOptions().setRomSettings(settings);

		RomPrestepPanel panel = onEdt(() -> new RomPrestepPanel(simulation));
		DefaultTableModel diagModel = field(panel, "diagTableModel", DefaultTableModel.class);
		onEdtRun(() -> invokeNoArg(panel, "startSweep"));
		waitForWorkerToFinish(panel);

		assertTrue(diagModel.getRowCount() > 0);
		String[] columns = invokeColumns();
		int statusCol = -1;
		for (int i = 0; i < columns.length; i++) {
			if ("Status".equals(columns[i])) {
				statusCol = i;
				break;
			}
		}
		assertTrue(statusCol >= 0);
		boolean sawDisabled = false;
		for (int r = 0; r < diagModel.getRowCount(); r++) {
			if ("DISABLED".equals(diagModel.getValueAt(r, statusCol))) {
				sawDisabled = true;
				break;
			}
		}
		assertTrue(sawDisabled, "expected at least one DISABLED status row");
	}

	private static void waitForWorkerToFinish(RomPrestepPanel panel) throws Exception {
		long deadline = System.currentTimeMillis() + 30_000L;
		while (System.currentTimeMillis() < deadline) {
			Object worker = peekField(panel, "activeWorker");
			if (worker == null) {
				// Drain any pending EDT events so done() side-effects settle.
				onEdtRun(() -> { });
				return;
			}
			Thread.sleep(50);
		}
		throw new AssertionError("Preview worker did not finish in time");
	}

	private static String[] invokeColumns() throws Exception {
		Method m = RomPrestepPanel.class.getDeclaredMethod("diagnosticsColumns");
		m.setAccessible(true);
		return (String[]) m.invoke(null);
	}

	private static void invokeNoArg(Object target, String methodName) {
		try {
			Method method = target.getClass().getDeclaredMethod(methodName);
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

	private static Object peekField(Object target, String fieldName) throws Exception {
		Field field = target.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		return field.get(target);
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

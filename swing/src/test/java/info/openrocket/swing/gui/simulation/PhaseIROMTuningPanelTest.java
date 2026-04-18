package info.openrocket.swing.gui.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.rom.RomFallbackMode;
import info.openrocket.core.aerodynamics.rom.RomMode;
import info.openrocket.core.aerodynamics.rom.RomSettings;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.OpenRocketDocumentFactory;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.tuning.PhaseThreeTuningPaths;
import info.openrocket.core.util.TestRockets;
import info.openrocket.swing.util.BaseTestCase;

public class PhaseIROMTuningPanelTest extends BaseTestCase {

	@Test
	public void testPanelShowsPhaseISettingsInsteadOfSurfaceStatus() throws Exception {
		OpenRocketDocument document = OpenRocketDocumentFactory.createDocumentFromRocket(TestRockets.makeEstesAlphaIII());
		Simulation simulation = new Simulation(document.getRocket());
		document.addSimulation(simulation);

		RomSettings settings = simulation.getOptions().getRomSettings();
		settings.setEnabled(true);
		settings.setMode(RomMode.CONSERVATIVE);
		settings.setFallbackMode(RomFallbackMode.BARROWMAN_ONLY);
		settings.setDiagnosticsEnabled(false);
		settings.setBodyMeridianSeedCount(7);
		settings.setFinSurfaceSeedCount(2);
		simulation.getOptions().setRomSettings(settings);

		RomTuningPanel panel = onEdt(() -> new RomTuningPanel(document, simulation));

		JLabel romState = field(panel, "romStateValue", JLabel.class);
		JLabel mode = field(panel, "romModeValue", JLabel.class);
		JLabel fallback = field(panel, "fallbackValue", JLabel.class);
		JLabel diagnostics = field(panel, "diagnosticsValue", JLabel.class);
		JLabel seedPlan = field(panel, "seedPlanValue", JLabel.class);
		JLabel trustedEnvelope = field(panel, "trustedEnvelopeValue", JLabel.class);
		JTextField configField = field(panel, "configField", JTextField.class);

		assertEquals("Enabled", romState.getText());
		assertEquals("Conservative", mode.getText());
		assertEquals("Legacy only", fallback.getText());
		assertEquals("Snapshot logging disabled", diagnostics.getText());
		assertTrue(seedPlan.getText().contains("7 body meridian"));
		assertTrue(trustedEnvelope.getText().contains("high-angle"));
		Path defaultConfig = PhaseThreeTuningPaths.findDefaultConfig();
		if (defaultConfig != null) {
			assertEquals(defaultConfig.toString(), configField.getText());
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

	@FunctionalInterface
	private interface EdtSupplier<T> {
		T get() throws Exception;
	}
}

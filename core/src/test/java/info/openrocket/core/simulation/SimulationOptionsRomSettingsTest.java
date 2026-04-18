package info.openrocket.core.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import info.openrocket.core.aerodynamics.BarrowmanCalculator;
import info.openrocket.core.aerodynamics.RomAerodynamicCalculator;
import info.openrocket.core.aerodynamics.rom.RomFallbackMode;
import info.openrocket.core.aerodynamics.rom.RomMode;
import info.openrocket.core.util.BaseTestCase;

import org.junit.jupiter.api.Test;

public class SimulationOptionsRomSettingsTest extends BaseTestCase {

	@Test
	public void cloneAndCopyPreservePhaseOneRomSettings() {
		SimulationOptions source = new SimulationOptions();
		source.setRomEnabled(true);
		source.setRomMode(RomMode.DIAGNOSTIC);
		source.setRomFallbackMode(RomFallbackMode.BARROWMAN_ONLY);
		source.setRomDiagnosticsEnabled(false);

		SimulationOptions clone = source.clone();
		SimulationOptions copy = new SimulationOptions();
		copy.copyConditionsFrom(source);

		assertTrue(clone.isRomEnabled());
		assertEquals(RomMode.DIAGNOSTIC, clone.getRomMode());
		assertEquals(RomFallbackMode.BARROWMAN_ONLY, clone.getRomFallbackMode());
		assertFalse(clone.isRomDiagnosticsEnabled());

		assertTrue(copy.isRomEnabled());
		assertEquals(RomMode.DIAGNOSTIC, copy.getRomMode());
		assertEquals(RomFallbackMode.BARROWMAN_ONLY, copy.getRomFallbackMode());
		assertFalse(copy.isRomDiagnosticsEnabled());
	}

	@Test
	public void simulationConditionsSelectRomOnlyWhenEnabled() {
		SimulationOptions disabled = new SimulationOptions();
		SimulationConditions disabledConditions = disabled.toSimulationConditions();

		assertTrue(disabledConditions.getAerodynamicCalculator() instanceof BarrowmanCalculator);
		assertFalse(disabledConditions.getRomAerodynamicCalculator().isEnabled());

		SimulationOptions enabled = new SimulationOptions();
		enabled.setRomEnabled(true);
		enabled.setRomMode(RomMode.STANDARD);
		SimulationConditions enabledConditions = enabled.toSimulationConditions();

		assertTrue(enabledConditions.getAerodynamicCalculator() instanceof RomAerodynamicCalculator);
		assertTrue(enabledConditions.getRomAerodynamicCalculator().isEnabled());
		assertEquals(RomMode.STANDARD, enabledConditions.getRomAerodynamicCalculator().getSettings().getMode());
	}
}

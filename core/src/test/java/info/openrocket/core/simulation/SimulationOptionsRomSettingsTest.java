package info.openrocket.core.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import info.openrocket.core.aerodynamics.BarrowmanCalculator;
import info.openrocket.core.aerodynamics.RomAerodynamicCalculator;
import info.openrocket.core.aerodynamics.rom.RomFallbackMode;
import info.openrocket.core.aerodynamics.rom.RomMode;
import info.openrocket.core.aerodynamics.rom.RomSettings;
import info.openrocket.core.util.BaseTestCase;

import org.junit.jupiter.api.Test;

public class SimulationOptionsRomSettingsTest extends BaseTestCase {

	@Test
	public void cloneAndCopyPreserveRomSettings() {
		SimulationOptions source = new SimulationOptions();
		RomSettings sourceSettings = source.getRomSettings();
		sourceSettings.setEnabled(true);
		sourceSettings.setMode(RomMode.DIAGNOSTIC);
		sourceSettings.setFallbackMode(RomFallbackMode.BARROWMAN_ONLY);
		sourceSettings.setDiagnosticsEnabled(false);
		sourceSettings.setBodyMeridianSeedCount(16);
		sourceSettings.setFinSurfaceSeedCount(5);
		sourceSettings.setTransonicBandHalfWidth(0.25);
		sourceSettings.setHighAngleDeg(18.5);
		sourceSettings.setMaxTrustedSeparationFraction(0.375);
		sourceSettings.setPrestepMach(1.25);
		sourceSettings.setPrestepAngleOfAttackDeg(7.5);
		sourceSettings.setPrestepThetaDeg(20.0);
		sourceSettings.setPrestepPlumeState(0.5);
		source.setRomSettings(sourceSettings);

		SimulationOptions clone = source.clone();
		SimulationOptions copy = new SimulationOptions();
		copy.copyConditionsFrom(source);

		assertEquals(source.getRomSettings(), clone.getRomSettings());
		assertEquals(source.getRomSettings(), copy.getRomSettings());
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

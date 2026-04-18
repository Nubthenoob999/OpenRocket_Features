package info.openrocket.core.aerodynamics.rom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import info.openrocket.core.aerodynamics.AerodynamicForces;
import info.openrocket.core.aerodynamics.BarrowmanCalculator;
import info.openrocket.core.aerodynamics.FlightConditions;
import info.openrocket.core.aerodynamics.RomAerodynamicCalculator;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.models.atmosphere.AtmosphericConditions;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.util.BaseTestCase;
import info.openrocket.core.util.TestRockets;

import org.junit.jupiter.api.Test;

public class PathlineROMCalculatorTest extends BaseTestCase {

	@Test
	public void disabledRomDelegatesExactlyToLegacyCalculator() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		FlightConfiguration configuration = rocket.getSelectedConfiguration();
		FlightConditions conditions = createConditions(configuration, 0.45, 2.0, 0.0);

		BarrowmanCalculator legacy = new BarrowmanCalculator();
		RomSettings settings = RomSettings.defaults();
		settings.setEnabled(false);

		RomAerodynamicCalculator rom = new RomAerodynamicCalculator(legacy.newInstance(), settings);
		AerodynamicForces legacyForces = legacy.getAerodynamicForces(configuration, conditions, new WarningSet());
		AerodynamicForces romForces = rom.getAerodynamicForces(configuration, conditions, new WarningSet());

		assertEquals(legacyForces.getCD(), romForces.getCD(), 1e-12);
		assertEquals(legacyForces.getCN(), romForces.getCN(), 1e-12);
		assertEquals(legacyForces.getCm(), romForces.getCm(), 1e-12);
		assertTrue(rom.getComputationSnapshots().isEmpty());
		assertEquals(0.0, rom.getPlumeState(), 1e-12);
	}

	@Test
	public void enabledRomProducesDiagnosticsAndStaysNearLegacyAtLowAngle() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		FlightConfiguration configuration = rocket.getSelectedConfiguration();
		FlightConditions conditions = createConditions(configuration, 0.35, 3.0, 0.0);

		BarrowmanCalculator legacy = new BarrowmanCalculator();
		RomSettings settings = RomSettings.defaults();
		settings.setEnabled(true);
		settings.setDiagnosticsEnabled(true);
		settings.setMode(RomMode.STANDARD);

		RomAerodynamicCalculator rom = new RomAerodynamicCalculator(legacy.newInstance(), settings);
		AerodynamicForces legacyForces = legacy.getAerodynamicForces(configuration, conditions, new WarningSet());
		AerodynamicForces romForces = rom.getAerodynamicForces(configuration, conditions, new WarningSet());
		RomResult result = rom.getLastResult();

		assertNotNull(result);
		assertFalse(result.getSeeds().isEmpty());
		assertFalse(rom.getComputationSnapshots().isEmpty());
		assertTrue(result.getConfidence().getOverallScore() > 0.35);
		assertTrue(Math.abs(romForces.getCN() - legacyForces.getCN()) < 0.35);
		assertTrue(Double.isFinite(romForces.getCm()));
		assertTrue(romForces.getCD() > 0.0);
	}

	@Test
	public void transonicCaseDropsConfidenceAndBlendsTowardFallback() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		FlightConfiguration configuration = rocket.getSelectedConfiguration();
		FlightConditions conditions = createConditions(configuration, 1.00, 8.0, 0.0);

		RomSettings settings = RomSettings.defaults();
		settings.setEnabled(true);
		settings.setMode(RomMode.DIAGNOSTIC);

		RomAerodynamicCalculator rom = new RomAerodynamicCalculator(new BarrowmanCalculator(), settings);
		rom.getAerodynamicForces(configuration, conditions, new WarningSet());
		RomResult result = rom.getLastResult();

		assertNotNull(result);
		assertTrue(result.getConfidence().getTransonicPenalty() > 0.9);
		assertTrue(result.getConfidence().getOverallScore() < 0.7);
		assertTrue(result.getFallbackWeight() > 0.25);
		assertTrue(result.getNotes().contains("regime=transonic"));
	}

	private static FlightConditions createConditions(FlightConfiguration configuration, double mach,
			double aoaDeg, double thetaDeg) {
		FlightConditions conditions = new FlightConditions(configuration);
		conditions.setMach(mach);
		conditions.setAOA(Math.toRadians(aoaDeg));
		conditions.setTheta(Math.toRadians(thetaDeg));
		conditions.setAtmosphericConditions(new AtmosphericConditions(288.15, 101325.0));
		return conditions;
	}
}

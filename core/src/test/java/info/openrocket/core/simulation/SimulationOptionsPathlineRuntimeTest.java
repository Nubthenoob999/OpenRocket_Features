package info.openrocket.core.simulation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.RomAerodynamicCalculator;
import info.openrocket.core.util.BaseTestCase;

public class SimulationOptionsPathlineRuntimeTest extends BaseTestCase {

	@Test
	public void romPrerequisiteIsAlwaysReadyForPathlineRuntime() {
		SimulationOptions options = new SimulationOptions();

		assertTrue(options.isRomDragPrerequisiteReady());
		assertDoesNotThrow(options::verifyRomDragPrerequisite);
	}

	@Test
	public void pathlineRuntimeBuildsWithoutLookupTables() {
		SimulationOptions options = new SimulationOptions();
		options.clearDragLookup();
		options.clearStabilityLookup();
		options.setRomEnabled(true);

		SimulationConditions conditions = options.toSimulationConditions();

		assertNotNull(conditions.getAerodynamicCalculator());
		assertNotNull(conditions.getRomAerodynamicCalculator());
		assertTrue(conditions.getRomAerodynamicCalculator() instanceof RomAerodynamicCalculator);
		assertFalse(options.hasDragLookup());
		assertFalse(options.hasStabilityLookup());
	}
}

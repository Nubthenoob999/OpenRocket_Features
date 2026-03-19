package info.openrocket.core.simulation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.lookup.MachAoALookup;
import info.openrocket.core.util.BaseTestCase;

public class SimulationOptionsLookupPrerequisiteTest extends BaseTestCase {

	@Test
	public void romPrerequisiteFailsWithoutDragLookup() {
		SimulationOptions options = new SimulationOptions();

		assertFalse(options.hasDragLookupWithAoA());
		assertFalse(options.isRomDragPrerequisiteReady());
		assertThrows(IllegalStateException.class, options::verifyRomDragPrerequisite);
	}

	@Test
	public void romPrerequisiteFailsWithMachOnlyDragLookup() {
		SimulationOptions options = new SimulationOptions();
		MachAoALookup machOnly = MachAoALookup.builder(java.util.List.of("cd"))
				.addData(0.0, Map.of("cd", 0.25))
				.addData(1.0, Map.of("cd", 0.55))
				.build();

		options.setDragLookup(null, machOnly);

		assertFalse(options.hasDragLookupWithAoA());
		assertFalse(options.isRomDragPrerequisiteReady());
		assertThrows(IllegalStateException.class, options::verifyRomDragPrerequisite);
	}

	@Test
	public void romPrerequisitePassesWithMachAoADragLookup() {
		SimulationOptions options = new SimulationOptions();
		MachAoALookup machAoa = MachAoALookup.builder(java.util.List.of("cd"))
				.addData(0.0, 0.0, Map.of("cd", 0.25))
				.addData(0.0, 5.0, Map.of("cd", 0.27))
				.addData(1.0, 0.0, Map.of("cd", 0.55))
				.addData(1.0, 5.0, Map.of("cd", 0.60))
				.build();

		options.setDragLookup(null, machAoa);

		assertTrue(options.hasDragLookupWithAoA());
		assertTrue(options.isRomDragPrerequisiteReady());
		assertDoesNotThrow(options::verifyRomDragPrerequisite);
	}
}

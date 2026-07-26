package info.openrocket.core.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import info.openrocket.core.util.BaseTestCase;
import org.junit.jupiter.api.Test;

class NozzleExitDiameterPropagationTest extends BaseTestCase {
	@Test
	void nozzleDiametersPropagateFromOptionsToRuntimeConditions() {
		SimulationOptions options = new SimulationOptions();
		options.setNozzleExitDiameterForStage(0, 0.10);
		options.setNozzleExitDiameterForStage(1, 0.08);
		options.setNozzleExitDiameterForStage(2, 0.06);

		SimulationConditions conditions = options.toSimulationConditions();
		assertEquals(0.10, conditions.getNozzleExitDiameterForStage(0));
		assertEquals(0.08, conditions.getNozzleExitDiameterForStage(1));
		assertEquals(0.06, conditions.getNozzleExitDiameterForStage(2));
		assertThrows(IllegalArgumentException.class,
				() -> options.setNozzleExitDiameterForStage(0, -0.01));
	}
}

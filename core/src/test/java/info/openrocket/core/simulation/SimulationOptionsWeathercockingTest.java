package info.openrocket.core.simulation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import info.openrocket.core.util.BaseTestCase;

public class SimulationOptionsWeathercockingTest extends BaseTestCase {

	@Test
	public void testCloneCopyEqualsAndRuntimePreserveWeathercockingState() {
		SimulationOptions source = new SimulationOptions();
		source.setWeathercockingCompensationEnabled(true);

		SimulationOptions clone = source.clone();
		assertTrue(clone.isWeathercockingCompensationEnabled());
		assertTrue(source.equals(clone));

		SimulationOptions target = new SimulationOptions();
		target.copyConditionsFrom(source);
		assertTrue(target.isWeathercockingCompensationEnabled());

		SimulationConditions runtime = source.toSimulationConditions();
		assertTrue(runtime.isWeathercockingCompensationEnabled());
	}
}
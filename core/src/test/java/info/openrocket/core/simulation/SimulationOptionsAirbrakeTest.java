package info.openrocket.core.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import info.openrocket.core.util.BaseTestCase;

public class SimulationOptionsAirbrakeTest extends BaseTestCase {

	@Test
	public void testCloneCopyAndEqualsPreserveAirbrakeState() {
		SimulationOptions source = new SimulationOptions();
		source.setAirbrakesEnabled(true);
		source.setCfdDataFilePath("C:/tmp/airbrakes.csv");
		source.setReferenceArea(0.015);
		source.setReferenceLength(0.18);
		source.setMaxDeploymentRate(12.5);
		source.setTargetApogee(1450.0);
		source.setMaxMachForDeployment(0.82);
		source.setAlwaysOpenMode(true);
		source.setAlwaysOpenPercentage(0.65);
		source.setApogeeToleranceMeters(4.0);
		source.setDeployAfterBurnoutOnly(true);
		source.setDeployAfterBurnoutDelayS(1.4);
		source.setDebugEnabled(true);
		source.setDbgAlwaysOpen(true);
		source.setDbgForcedDeployFrac(0.72);
		source.setDbgTracePredictor(false);
		source.setDbgTraceController(false);
		source.setDbgWriteCsv(false);
		source.setDbgCsvDir("C:/tmp/airbrake-debug");
		source.setDbgShowConsole(true);

		SimulationOptions clone = source.clone();
		assertTrue(clone.isAirbrakesEnabled());
		assertEquals("C:/tmp/airbrakes.csv", clone.getCfdDataFilePath());
		assertEquals(1450.0, clone.getTargetApogee(), 1e-12);
		assertEquals(0.72, clone.getDbgForcedDeployFrac(), 1e-12);
		assertTrue(source.equals(clone));

		SimulationOptions target = new SimulationOptions();
		target.copyConditionsFrom(source);
		assertTrue(target.isAirbrakesEnabled());
		assertEquals(source.getCfdDataFilePath(), target.getCfdDataFilePath());
		assertEquals(source.getReferenceArea(), target.getReferenceArea(), 1e-12);
		assertEquals(source.getTargetApogee(), target.getTargetApogee(), 1e-12);
		assertEquals(source.getDbgCsvDir(), target.getDbgCsvDir());
		assertTrue(target.isDbgShowConsole());

		assertNotSame(source.createAirbrakeConfig(), clone.createAirbrakeConfig());
	}
}

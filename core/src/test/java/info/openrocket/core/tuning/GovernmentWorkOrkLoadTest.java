package info.openrocket.core.tuning;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

public class GovernmentWorkOrkLoadTest {

	@Test
	public void testGovernmentWorkLaunch1OrkLoadsAndSimulates() throws Exception {
		TelemetrySeries series = HeadlessOrkSimulationRunner.runFirstSimulation(resolve(
				"Govenmnet_Work_launch_1",
				"NASA_26_Subscale_1.ork"));
		assertSeriesLoaded(series);
	}

	@Test
	public void testGovernmentWorkLaunch2OrkLoadsAndSimulates() throws Exception {
		TelemetrySeries series = HeadlessOrkSimulationRunner.runFirstSimulation(resolve(
				"Government_Work_Launch_2",
				"NASA_26_Subscale_2.ork"));
		assertSeriesLoaded(series);
	}

	private static File resolve(String folder, String file) {
		return Path.of("src", "test", "java", "info", "openrocket", "core", "tuning", folder, file)
				.toFile();
	}

	private static void assertSeriesLoaded(TelemetrySeries series) {
		assertNotNull(series);
		assertFalse(series.getTimeSec().isEmpty());
		assertFalse(series.getAltitudeMetersAgl().isEmpty());
	}
}

package info.openrocket.core.aerodynamics.rom;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.rom.adapter.SurfaceAdapter;
import info.openrocket.core.aerodynamics.rom.core.surface.AeroSurface4D;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.simulation.FlightData;
import info.openrocket.core.util.BaseTestCase;
import info.openrocket.core.util.TestRockets;

public class Rom4DIntegrationSmokeTest extends BaseTestCase {

	@Test
	public void testSimulationRunsWithInstalledRom4DSurface() throws Exception {
		OpenRocketDocument rocketDoc = TestRockets.makeTestRocket_v104_withSimulationData();
		Simulation simulation = rocketDoc.getSimulations().get(0);
		String geometryHash = RomGeometryParameters.fromRocket(
				simulation.getRocket().getFlightConfiguration(simulation.getFlightConfigurationId())).geometryHash();

		AeroSurface4D surface4D = createSurface4D(geometryHash);
		simulation.getOptions().setRomAeroSurface4D(surface4D);
		simulation.getOptions().setRomDragSurface(SurfaceAdapter.toBetaZeroDragSurface(surface4D));

		simulation.simulate();
		FlightData data = simulation.getSimulatedData();
		assertNotNull(data);
		assertTrue(data.getBranchCount() > 0);
		assertTrue(Double.isFinite(data.getMaxAltitude()));
	}

	private static AeroSurface4D createSurface4D(String hash) {
		double[] mach = new double[] { 0.0, 2.0 };
		double[] logRe = new double[] { 4.0, 8.0 };
		double[] alpha = new double[] { 0.0, 10.0 };
		double[] beta = new double[] { 0.0, 15.0 };
		double[][][][] off = new double[mach.length][logRe.length][alpha.length][beta.length];
		double[][][][] on = new double[mach.length][logRe.length][alpha.length][beta.length];
		double[][][][] body = new double[mach.length][logRe.length][alpha.length][beta.length];
		double[][][][] cn = new double[mach.length][logRe.length][alpha.length][beta.length];
		double[][][][] cm = new double[mach.length][logRe.length][alpha.length][beta.length];

		for (int i = 0; i < mach.length; i++) {
			for (int j = 0; j < logRe.length; j++) {
				for (int k = 0; k < alpha.length; k++) {
					off[i][j][k][0] = 0.40;
					off[i][j][k][1] = 0.45;
					on[i][j][k][0] = 0.30;
					on[i][j][k][1] = 0.35;
					body[i][j][k][0] = 0.37;
					body[i][j][k][1] = 0.42;
				}
			}
		}
		return new AeroSurface4D(mach, logRe, alpha, beta, off, on, body, cn, cm, hash, 4);
	}
}

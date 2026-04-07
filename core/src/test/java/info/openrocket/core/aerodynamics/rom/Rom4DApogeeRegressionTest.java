package info.openrocket.core.aerodynamics.rom;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.rom.core.eval.AeroGridEvaluator4D;
import info.openrocket.core.aerodynamics.rom.core.surface.AeroSurface4D;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.simulation.FlightData;
import info.openrocket.core.simulation.SimulationStepperMethod;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.util.BaseTestCase;
import info.openrocket.core.util.TestRockets;

public class Rom4DApogeeRegressionTest extends BaseTestCase {

	@Test
	public void testFourDRomDoesNotCatastrophicallyReduceApogeeVersusThreeD() throws Exception {
		Rocket rocket3D = TestRockets.makeEstesAlphaIII();
		Simulation simulation3D = new Simulation(rocket3D);
		simulation3D.setFlightConfigurationId(TestRockets.TEST_FCID_0);
		configureSimulationForComparison(simulation3D);

		FlightConfiguration configuration = rocket3D.getFlightConfiguration(TestRockets.TEST_FCID_0);
		RomGeometryParameters geometry = RomGeometryParameters.fromRocket(configuration);
		DragSurface surface3D = DragGridEvaluator.evaluate(geometry, null);
		AeroSurface4D surface4D = AeroGridEvaluator4D.evaluate(
				geometry.toRomGeometryInput(),
				geometry.geometryHash(),
				null);

		simulation3D.getOptions().setRomSurfaceMode(RomSurfaceMode.THREE_D);
		simulation3D.getOptions().setRomDragSurface(surface3D);
		simulation3D.getOptions().setRomAeroSurface4D(null);
		simulation3D.simulate();
		FlightData data3D = simulation3D.getSimulatedData();

		Rocket rocket4D = TestRockets.makeEstesAlphaIII();
		Simulation simulation4D = new Simulation(rocket4D);
		simulation4D.setFlightConfigurationId(TestRockets.TEST_FCID_0);
		configureSimulationForComparison(simulation4D);
		simulation4D.getOptions().setRomSurfaceMode(RomSurfaceMode.FOUR_D);
		simulation4D.getOptions().setRomDragSurface(surface3D);
		simulation4D.getOptions().setRomAeroSurface4D(surface4D);
		simulation4D.simulate();
		FlightData data4D = simulation4D.getSimulatedData();

		double apogee3D = data3D.getMaxAltitude();
		double apogee4D = data4D.getMaxAltitude();
		assertTrue(apogee3D > 0.0);
		assertTrue(apogee4D > 0.0);
		assertTrue(apogee4D >= 0.85 * apogee3D,
				"4D ROM apogee regressed too far below 3D baseline: 3D=" + apogee3D + " 4D=" + apogee4D);
	}

	private static void configureSimulationForComparison(Simulation simulation) {
		simulation.getOptions().setISAAtmosphere(true);
		simulation.getOptions().setSimulationStepperMethodChoice(SimulationStepperMethod.RK4);
		simulation.getOptions().setTimeStep(0.02);
		simulation.getOptions().setRandomSeed(12345);
	}
}

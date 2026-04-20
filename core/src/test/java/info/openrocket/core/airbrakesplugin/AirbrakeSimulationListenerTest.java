package info.openrocket.core.airbrakesplugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import info.openrocket.core.airbrakesplugin.util.AirDensity;
import info.openrocket.core.airbrakesplugin.util.ApogeePredictor;
import info.openrocket.core.airbrakesplugin.util.ExtrapolationType;
import info.openrocket.core.aerodynamics.AerodynamicForces;
import info.openrocket.core.aerodynamics.FlightConditions;
import info.openrocket.core.simulation.FlightDataBranch;
import info.openrocket.core.simulation.FlightDataType;
import info.openrocket.core.simulation.SimulationConditions;
import info.openrocket.core.simulation.SimulationStatus;
import info.openrocket.core.util.Coordinate;
import info.openrocket.core.util.WorldCoordinate;

public class AirbrakeSimulationListenerTest {
	private static final double EPSILON = 1e-12;

	@TempDir
	Path tempDir;

	@Test
	public void apogeeNotAboveTargetLeavesBaselineForcesUnchanged() throws Exception {
		AirbrakeAerodynamics airbrakes = new AirbrakeAerodynamics(writeDragSurface(), ExtrapolationType.CONSTANT);
		ApogeePredictor predictor = mock(ApogeePredictor.class);
		when(predictor.getPredictionIfReady()).thenReturn(50.0);

		AirbrakeConfig config = new AirbrakeConfig();
		config.setTargetApogee(100.0);

		AirbrakeSimulationListener listener = new AirbrakeSimulationListener(airbrakes, null, predictor, 0.0, config);
		SimulationStatusHarness harness = new SimulationStatusHarness();
		drivePastBurnout(listener, harness);

		double speed = harness.velocity.length();
		double altitudeMSL = harness.position.getZ();
		double mach = AirDensity.machFromV(speed, altitudeMSL);

		FlightConditions flightConditions = new FlightConditions(null);
		flightConditions.setMach(mach);
		flightConditions.setRefArea(0.02);
		listener.postFlightConditions(harness.status, flightConditions);

		AerodynamicForces forces = new AerodynamicForces();
		forces.setCDaxial(0.70);
		forces.setCD(0.80);

		listener.postAerodynamicCalculation(harness.status, forces);

		assertEquals(0.70, forces.getCDaxial(), EPSILON);
		assertEquals(0.80, forces.getCD(), EPSILON);
	}

	@Test
	public void extendedAirbrakesRescaleCdAxialByCombinedReferenceArea() throws Exception {
		AirbrakeAerodynamics airbrakes = new AirbrakeAerodynamics(writeDragSurface(), ExtrapolationType.CONSTANT);
		ApogeePredictor predictor = mock(ApogeePredictor.class);
		when(predictor.getPredictionIfReady()).thenReturn(200.0);

		AirbrakeConfig config = new AirbrakeConfig();
		config.setTargetApogee(100.0);

		double rocketArea = 0.02;
		double airbrakesAreaFallback = 0.03;

		AirbrakeSimulationListener listener = new AirbrakeSimulationListener(airbrakes, null, predictor, airbrakesAreaFallback, config);
		SimulationStatusHarness harness = new SimulationStatusHarness();
		drivePastBurnout(listener, harness);

		double speed = harness.velocity.length();
		double altitudeMSL = harness.position.getZ();
		double mach = AirDensity.machFromV(speed, altitudeMSL);

		FlightConditions flightConditions = new FlightConditions(null);
		flightConditions.setMach(mach);
		flightConditions.setRefArea(rocketArea);
		listener.postFlightConditions(harness.status, flightConditions);

		AerodynamicForces forces = new AerodynamicForces();
		forces.setCDaxial(0.70);
		forces.setCD(0.80);

		double rhoDyn = AirDensity.rhoForDynamicPressure(altitudeMSL, mach);
		double dynP = 0.5 * rhoDyn * harness.velocity.length2();
		double airbrakeDrag = airbrakes.calculateDragForce(1.0, speed, altitudeMSL);
		double rocketDragAxial = 0.70 * dynP * rocketArea;
		double combinedArea = rocketArea + airbrakesAreaFallback;
		double expectedCdAxial = (rocketDragAxial + airbrakeDrag) / (dynP * combinedArea);

		listener.postAerodynamicCalculation(harness.status, forces);

		assertEquals(expectedCdAxial, forces.getCDaxial(), EPSILON);
		assertEquals(0.80, forces.getCD(), EPSILON);  // CD unchanged
	}

	private void drivePastBurnout(AirbrakeSimulationListener listener,
			SimulationStatusHarness harness) throws Exception {
		listener.startSimulation(harness.status);

		harness.currentTime[0] = 1.0;
		harness.flightDataBranch.setValue(FlightDataType.TYPE_THRUST_FORCE, 20.0);
		listener.preStep(harness.status);

		harness.currentTime[0] = 2.0;
		harness.flightDataBranch.setValue(FlightDataType.TYPE_THRUST_FORCE, 0.0);
		listener.preStep(harness.status);
	}

	private Path writeDragSurface() throws IOException {
		Path csvPath = tempDir.resolve("airbrake-drag.csv");
		Files.writeString(csvPath,
				"Mach,Deployment,Drag\n"
						+ "0.20,0.00,30.0\n"
						+ "0.20,1.00,70.0\n"
						+ "0.80,0.00,50.0\n"
						+ "0.80,1.00,110.0\n");
		return csvPath;
	}

	private static final class SimulationStatusHarness {
		private final double[] currentTime = {0.0};
		private final FlightDataBranch flightDataBranch = new FlightDataBranch("test", FlightDataType.TYPE_TIME, FlightDataType.TYPE_THRUST_FORCE);
		private final SimulationConditions simulationConditions = new SimulationConditions();
		private final Coordinate velocity = new Coordinate(0.0, 0.0, 170.0);
		private final Coordinate position = new Coordinate(0.0, 0.0, 1000.0);
		private final SimulationStatus status = mock(SimulationStatus.class);

		private SimulationStatusHarness() {
			flightDataBranch.addPoint();
			flightDataBranch.setValue(FlightDataType.TYPE_TIME, 0.0);
			flightDataBranch.setValue(FlightDataType.TYPE_THRUST_FORCE, 20.0);
			simulationConditions.setLaunchSite(new WorldCoordinate(0.0, 0.0, 0.0));

			when(status.getSimulationTime()).thenAnswer(invocation -> currentTime[0]);
			when(status.getFlightDataBranch()).thenReturn(flightDataBranch);
			when(status.getSimulationConditions()).thenReturn(simulationConditions);
			when(status.getRocketVelocity()).thenReturn(velocity);
			when(status.getRocketPosition()).thenReturn(position);
		}
	}
}

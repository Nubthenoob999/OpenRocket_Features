package info.openrocket.core.simulation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.PhysicsAeroAerodynamicCalculator;
import info.openrocket.core.aerodynamics.physicsaero.diagnostics.CellDiagnostics;
import info.openrocket.core.aerodynamics.physicsaero.force.AerodynamicCoefficients;
import info.openrocket.core.aerodynamics.physicsaero.force.AerodynamicDerivatives;
import info.openrocket.core.aerodynamics.physicsaero.force.ReferenceState;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroMode;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroPreflightValidator;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroRuntimeFlag;
import info.openrocket.core.aerodynamics.physicsaero.table.AerodynamicTable;
import info.openrocket.core.aerodynamics.physicsaero.table.CertificationState;
import info.openrocket.core.aerodynamics.physicsaero.table.RuntimeCorrectionData;
import info.openrocket.core.aerodynamics.physicsaero.table.TableAxes;
import info.openrocket.core.aerodynamics.physicsaero.table.TableCell;
import info.openrocket.core.aerodynamics.physicsaero.table.TableMetadata;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.simulation.exception.SimulationException;
import info.openrocket.core.startup.OpenRocketCore;
import info.openrocket.core.util.Coordinate;
import info.openrocket.core.util.TestRockets;

class PhysicsAeroPoweredLaunchIntegrationTest {
	private static final String GEOMETRY = "powered-launch-geometry";
	private static final String SETTINGS = "powered-launch-settings";

	@BeforeAll
	static void initializeCore() {
		OpenRocketCore.initialize();
	}

	@Test
	void strictPreflightRejectsCoastOnlyTableForMotorConfiguration() {
		FlightConfiguration configuration = poweredConfiguration();
		SimulationException failure = assertThrows(SimulationException.class,
				() -> PhysicsAeroPreflightValidator.validate(
						table(configuration, new double[] {0}), configuration, PhysicsAeroMode.STRICT));

		assertTrue(failure.getMessage().contains("poweredFraction=1.0"));
		assertTrue(failure.getMessage().contains("allowed=[0.00000, 0.00000]"));
	}

	@Test
	void strictPoweredTableRunsWithNonzeroWindAndIgnitedMotor() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		Simulation simulation = new Simulation(rocket);
		simulation.setFlightConfigurationId(TestRockets.TEST_FCID_0);
		FlightConfiguration configuration = simulation.getActiveConfiguration();
		AerodynamicTable table = table(configuration, new double[] {0, 1});

		simulation.getOptions().setPhysicsAeroTableIdentity(table, "test-content");
		simulation.getOptions().setPhysicsAeroMode(PhysicsAeroMode.STRICT);
		simulation.getOptions().setISAAtmosphere(true);
		simulation.getOptions().setWindSpeedAverage(0.1);
		simulation.getOptions().setWindSpeedDeviation(0);
		simulation.getOptions().setLaunchRodLength(0.2);
		simulation.getOptions().setTimeStep(0.02);
		simulation.getOptions().setMaxSimulationTime(5);
		simulation.getOptions().setRandomSeed(42);

		assertDoesNotThrow(() -> PhysicsAeroPreflightValidator.validate(
				table, configuration, PhysicsAeroMode.STRICT));
		SimulationConditions conditions = simulation.getOptions().toSimulationConditions(table);
		conditions.setSimulation(simulation);
		BasicEventSimulationEngine engine = new BasicEventSimulationEngine();

		assertDoesNotThrow(() -> engine.simulate(conditions));

		List<FlightEvent.Type> events = engine.getFlightData().getBranch(0).getEvents().stream()
				.map(FlightEvent::getType).toList();
		assertTrue(events.contains(FlightEvent.Type.IGNITION));
		assertTrue(events.contains(FlightEvent.Type.LAUNCHROD));
		PhysicsAeroAerodynamicCalculator calculator =
				(PhysicsAeroAerodynamicCalculator) conditions.getAerodynamicCalculator();
		assertTrue(calculator.getRuntimeReport().runtimeFlags()
				.contains(PhysicsAeroRuntimeFlag.DIAGNOSTIC_FALLBACK_USED));
		assertTrue(calculator.getRuntimeReport().runtimeFlags()
				.contains(PhysicsAeroRuntimeFlag.TABLE_QUERY_USED));
		assertTrue(calculator.getRuntimeReport().runtimeFlags()
				.contains(PhysicsAeroRuntimeFlag.POWERED_INCREMENT_UNMODELED),
				calculator.getRuntimeReport().toString());
	}

	private static FlightConfiguration poweredConfiguration() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		return rocket.getFlightConfiguration(TestRockets.TEST_FCID_0);
	}

	private static AerodynamicTable table(FlightConfiguration configuration, double[] poweredAxis) {
		double[] mach = {0, 0.1, 0.3, 1.0};
		double[] alpha = degrees(-15, 0, 15);
		double[] beta = degrees(-5, 0, 5);
		TableAxes axes = new TableAxes(mach, alpha, beta, poweredAxis);
		double referenceLength = configuration.getReferenceLength();
		double referenceArea = configuration.getReferenceArea();
		List<TableCell> cells = new ArrayList<>(axes.cellCount());
		for (double currentMach : mach) {
			double referenceReynolds = currentMach * 340 * referenceLength / 1.5e-5;
			for (double currentAlpha : alpha) {
				for (double currentBeta : beta) {
					for (double powered : poweredAxis) {
						AerodynamicCoefficients coefficients = new AerodynamicCoefficients(
								0.25, 2 * currentAlpha, 2 * currentBeta, 0,
								-20 * currentAlpha, -20 * currentBeta);
						List<String> validity = powered == 0
								? List.of("COAST_STATE")
								: List.of("POWERED_STATE", "POWERED_INCREMENT_UNMODELED");
						cells.add(new TableCell(coefficients, Map.of("vehicle", coefficients),
								Map.of("TEST_TOTAL", coefficients), List.of("TEST_POWERED_LAUNCH"),
								new double[] {.9, .9, .9, .9, .9, .9},
								new double[] {.1, .1, .1, .1, .1, .1}, validity,
								new ReferenceState(1000, referenceArea, referenceLength, new Coordinate()),
								CellDiagnostics.direct(), true,
								new AerodynamicDerivatives(-0.1, -0.1, -0.1),
								new RuntimeCorrectionData(referenceReynolds, 1.0e-4, 1.0e4,
										new double[6], false, "TEST_POWERED_LAUNCH_RE")));
					}
				}
			}
		}
		TableMetadata metadata = new TableMetadata(TableMetadata.CURRENT_SCHEMA, GEOMETRY, SETTINGS,
				"code", "registry", TableMetadata.REQUIRED_UNITS,
				TableMetadata.REQUIRED_AXIS_CONVENTION, Map.of(), Map.of(),
				CertificationState.EXPERIMENTAL_FLIGHT_PENDING);
		return new AerodynamicTable(axes, cells, metadata);
	}

	private static double[] degrees(double... values) {
		return java.util.Arrays.stream(values).map(Math::toRadians).toArray();
	}
}

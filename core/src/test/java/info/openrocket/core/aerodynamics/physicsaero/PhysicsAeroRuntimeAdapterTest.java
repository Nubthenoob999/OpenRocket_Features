package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.AerodynamicForces;
import info.openrocket.core.aerodynamics.BarrowmanCalculator;
import info.openrocket.core.aerodynamics.FlightConditions;
import info.openrocket.core.aerodynamics.PhysicsAeroAerodynamicCalculator;
import info.openrocket.core.aerodynamics.SimulationAerodynamicsContext;
import info.openrocket.core.aerodynamics.physicsaero.diagnostics.CellDiagnostics;
import info.openrocket.core.aerodynamics.physicsaero.force.AerodynamicCoefficients;
import info.openrocket.core.aerodynamics.physicsaero.force.AerodynamicDerivatives;
import info.openrocket.core.aerodynamics.physicsaero.force.ReferenceState;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroMode;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroQueryException;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroRuntimeFlag;
import info.openrocket.core.aerodynamics.physicsaero.table.AerodynamicTable;
import info.openrocket.core.aerodynamics.physicsaero.table.CertificationState;
import info.openrocket.core.aerodynamics.physicsaero.table.RuntimeCorrectionData;
import info.openrocket.core.aerodynamics.physicsaero.table.TableAxes;
import info.openrocket.core.aerodynamics.physicsaero.table.TableCell;
import info.openrocket.core.aerodynamics.physicsaero.table.TableMetadata;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.startup.OpenRocketCore;
import info.openrocket.core.util.Coordinate;
import info.openrocket.core.util.TestRockets;

class PhysicsAeroRuntimeAdapterTest {
	private static final double ALPHA = 0.1;
	private static final double BETA = 0.1;

	@BeforeAll
	static void initializeCore() {
		OpenRocketCore.initialize();
	}

	@Test
	void mapsSixAxesRatesWindDragAndLocalCpSlopeExactlyOnce() {
		FlightConfiguration configuration = TestRockets.makeEstesAlphaIII().getSelectedConfiguration();
		FlightConditions conditions = queryConditions(configuration);
		double referenceReynolds = reynolds(conditions);
		PhysicsAeroAerodynamicCalculator calculator = new PhysicsAeroAerodynamicCalculator(
				table(referenceReynolds), "geometry", "settings", "content", PhysicsAeroMode.STRICT, null);

		AerodynamicForces forces = calculator.getAerodynamicForces(configuration, conditions, new WarningSet());
		double rateScale = conditions.getRefLength() / (2 * conditions.getVelocity());
		assertEquals(0.2, forces.getCDaxial(), 1e-12);
		assertEquals(0.2 * Math.cos(ALPHA) * Math.cos(BETA)
				+ 0.2 * Math.sin(ALPHA) * Math.cos(BETA) + 0.3 * Math.sin(BETA),
				forces.getCD(), 1e-12);
		assertEquals(0.2, forces.getCN(), 1e-12);
		assertEquals(0.3, forces.getCside(), 1e-12);
		assertEquals(0.01 - 0.2 * conditions.getRollRate() * rateScale, forces.getCroll(), 1e-12);
		assertEquals(-0.1 - 0.3 * conditions.getPitchRate() * rateScale, forces.getCm(), 1e-12);
		assertEquals(-0.05 - 0.4 * conditions.getYawRate() * rateScale, forces.getCyaw(), 1e-12);
		assertEquals(0.5, forces.getCP().getX(), 1e-12);
		assertEquals(2.0, forces.getCP().getWeight(), 1e-12);
		assertEquals(1, calculator.getRuntimeReport().successfulTableQueries());
		assertEquals(0, calculator.getRuntimeReport().fallbackCount());
	}

	@Test
	void strictFailsAndDiagnosticHybridRecordsOnlyRecognizedFallbacks() {
		FlightConfiguration configuration = TestRockets.makeEstesAlphaIII().getSelectedConfiguration();
		FlightConditions conditions = queryConditions(configuration);
		double referenceReynolds = reynolds(conditions);
		conditions.setMach(2);
		PhysicsAeroAerodynamicCalculator strict = new PhysicsAeroAerodynamicCalculator(
				table(referenceReynolds), "geometry", "settings", "content", PhysicsAeroMode.STRICT, null);
		PhysicsAeroQueryException failure = assertThrows(PhysicsAeroQueryException.class,
				() -> strict.getAerodynamicForces(configuration, conditions, new WarningSet()));
		assertEquals(info.openrocket.core.aerodynamics.physicsaero.diagnostics.FailureReason.OUT_OF_DOMAIN,
				failure.reason());

		PhysicsAeroAerodynamicCalculator hybrid = new PhysicsAeroAerodynamicCalculator(
				table(referenceReynolds), "geometry", "settings", "content",
				PhysicsAeroMode.DIAGNOSTIC_HYBRID, new BarrowmanCalculator());
		AerodynamicForces fallback = hybrid.getAerodynamicForces(configuration, conditions, new WarningSet());
		assertTrue(Double.isFinite(fallback.getCDaxial()));
		assertEquals(1, hybrid.getRuntimeReport().fallbackCount());
		assertTrue(hybrid.getRuntimeReport().runtimeFlags()
				.contains(PhysicsAeroRuntimeFlag.DIAGNOSTIC_FALLBACK_USED));
	}

	@Test
	void poweredCoastMismatchAndNamedReynoldsCorrectionAreReported() {
		FlightConfiguration configuration = TestRockets.makeEstesAlphaIII().getSelectedConfiguration();
		FlightConditions conditions = queryConditions(configuration);
		double referenceReynolds = reynolds(conditions);
		PhysicsAeroAerodynamicCalculator hybrid = new PhysicsAeroAerodynamicCalculator(
				table(referenceReynolds), "geometry", "settings", "content",
				PhysicsAeroMode.DIAGNOSTIC_HYBRID, new BarrowmanCalculator());
		hybrid.setSimulationAerodynamicsContext(new SimulationAerodynamicsContext(1, true, 100, 1, 101325));
		hybrid.getAerodynamicForces(configuration, conditions, new WarningSet());
		assertTrue(hybrid.getRuntimeReport().runtimeFlags()
				.contains(PhysicsAeroRuntimeFlag.POWERED_STATE_FALLBACK));

		PhysicsAeroAerodynamicCalculator corrected = new PhysicsAeroAerodynamicCalculator(
				table(referenceReynolds), "geometry", "settings", "content", PhysicsAeroMode.STRICT, null);
		conditions.getAtmosphericConditions().setPressure(
				conditions.getAtmosphericConditions().getPressure() * 1.02);
		corrected.getAerodynamicForces(configuration, conditions, new WarningSet());
		assertEquals(1, corrected.getRuntimeReport().reynoldsCorrectedQueries());
		assertTrue(corrected.getRuntimeReport().runtimeFlags()
				.contains(PhysicsAeroRuntimeFlag.RUNTIME_REYNOLDS_CORRECTION_USED));
	}

	@Test
	void axialHybridUsesTableDragAndEstablishedLateralStability() {
		FlightConfiguration configuration = TestRockets.makeEstesAlphaIII().getSelectedConfiguration();
		FlightConditions conditions = queryConditions(configuration);
		double referenceReynolds = reynolds(conditions);
		BarrowmanCalculator establishedCalculator = new BarrowmanCalculator();
		AerodynamicForces established = establishedCalculator.getAerodynamicForces(
				configuration, conditions, new WarningSet());
		PhysicsAeroAerodynamicCalculator hybrid = new PhysicsAeroAerodynamicCalculator(
				table(referenceReynolds), "geometry", "settings", "content",
				PhysicsAeroMode.DIAGNOSTIC_AXIAL_HYBRID, new BarrowmanCalculator());

		AerodynamicForces actual = hybrid.getAerodynamicForces(
				configuration, conditions, new WarningSet());

		assertEquals(0.2, actual.getCDaxial(), 1e-12);
		assertEquals(0.2 * Math.cos(ALPHA) * Math.cos(BETA)
				+ 0.2 * Math.sin(ALPHA) * Math.cos(BETA) + 0.3 * Math.sin(BETA),
				actual.getCD(), 1e-12);
		assertEquals(established.getCN(), actual.getCN(), 1e-12);
		assertEquals(established.getCm(), actual.getCm(), 1e-12);
		assertEquals(established.getCP().getX(), actual.getCP().getX(), 1e-12);
		assertTrue(hybrid.getRuntimeReport().runtimeFlags()
				.contains(PhysicsAeroRuntimeFlag.AXIAL_ONLY_HYBRID_USED));
	}

	@Test
	void axialHybridProjectsOutOfRangeAzimuthOntoValidatedTotalIncidence() {
		FlightConfiguration configuration = TestRockets.makeEstesAlphaIII().getSelectedConfiguration();
		FlightConditions conditions = queryConditions(configuration);
		double referenceReynolds = reynolds(conditions);
		AerodynamicTable narrowBeta = table(referenceReynolds,
				new double[] {-0.2, 0.2}, new double[] {-0.05, 0.05});
		PhysicsAeroAerodynamicCalculator hybrid = new PhysicsAeroAerodynamicCalculator(
				narrowBeta, "geometry", "settings", "content",
				PhysicsAeroMode.DIAGNOSTIC_AXIAL_HYBRID, new BarrowmanCalculator());

		AerodynamicForces forces = hybrid.getAerodynamicForces(
				configuration, conditions, new WarningSet());

		assertEquals(0.2, forces.getCDaxial(), 1e-12);
		assertEquals(0, hybrid.getRuntimeReport().fallbackCount());
		assertTrue(hybrid.getRuntimeReport().runtimeFlags()
				.contains(PhysicsAeroRuntimeFlag.AXIAL_INCIDENCE_SYMMETRY_PROJECTION));
	}

	private static FlightConditions queryConditions(FlightConfiguration configuration) {
		FlightConditions conditions = new FlightConditions(configuration);
		conditions.setRefLength(1);
		conditions.setMach(1);
		conditions.setAOA(Math.hypot(ALPHA, BETA));
		conditions.setTheta(Math.PI / 4);
		conditions.setRollRate(3);
		conditions.setPitchRate(4);
		conditions.setYawRate(5);
		return conditions;
	}

	private static double reynolds(FlightConditions conditions) {
		return conditions.getVelocity() * conditions.getRefLength()
				/ conditions.getAtmosphericConditions().getKinematicViscosity();
	}

	private static AerodynamicTable table(double referenceReynolds) {
		return table(referenceReynolds, new double[] {-ALPHA, ALPHA},
				new double[] {-BETA, BETA});
	}

	private static AerodynamicTable table(double referenceReynolds, double[] alphaAxis,
			double[] betaAxis) {
		TableAxes axes = new TableAxes(new double[] {1}, alphaAxis, betaAxis, new double[] {0});
		List<TableCell> cells = new ArrayList<>();
		for (double alpha : axes.alphaRad()) {
			for (double beta : axes.betaRad()) cells.add(cell(alpha, beta, referenceReynolds));
		}
		TableMetadata metadata = new TableMetadata(TableMetadata.CURRENT_SCHEMA, "geometry", "settings",
				"code", "registry", TableMetadata.REQUIRED_UNITS,
				TableMetadata.REQUIRED_AXIS_CONVENTION, Map.of(), Map.of(),
				CertificationState.EXPERIMENTAL_FLIGHT_PENDING);
		return new AerodynamicTable(axes, cells, metadata);
	}

	private static TableCell cell(double alpha, double beta, double referenceReynolds) {
		AerodynamicCoefficients coefficients = new AerodynamicCoefficients(
				0.2, 2 * alpha, 3 * beta, 0.01, -alpha, -0.5 * beta);
		return new TableCell(coefficients, Map.of("vehicle", coefficients), Map.of("ALL", coefficients),
				List.of("TEST_SIX_AXIS"), new double[] {.9, .9, .9, .9, .9, .9},
				new double[] {.1, .1, .1, .1, .1, .1}, List.of("COAST_STATE"),
				new ReferenceState(1000, 1, 1, new Coordinate()), CellDiagnostics.direct(), true,
				new AerodynamicDerivatives(-0.2, -0.3, -0.4),
				new RuntimeCorrectionData(referenceReynolds, .5, 2,
						new double[] {.01, 0, 0, 0, 0, 0}, false, "TEST_LOG_RE"));
	}
}

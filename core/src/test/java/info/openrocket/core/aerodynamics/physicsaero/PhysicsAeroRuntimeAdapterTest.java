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
		assertEquals(0.2 * Math.cos(Math.PI / 4) + 0.3 * Math.sin(Math.PI / 4),
				forces.getCN(), 1e-12);
		assertEquals(0.3 * Math.cos(Math.PI / 4) - 0.2 * Math.sin(Math.PI / 4),
				forces.getCside(), 1e-12);
		assertEquals(0.01 - 0.2 * conditions.getRollRate() * rateScale, forces.getCroll(), 1e-12);
		assertEquals(0.15 * Math.sin(Math.PI / 4)
				- 0.3 * conditions.getPitchRate() * rateScale, forces.getCm(), 1e-12);
		assertEquals(-0.05 * Math.sin(Math.PI / 4)
				- 0.4 * conditions.getYawRate() * rateScale, forces.getCyaw(), 1e-12);
		assertEquals(0.5, forces.getCP().getX(), 1e-12);
		assertEquals(2.0, forces.getCP().getWeight(), 1e-12);
		assertEquals(1, calculator.getRuntimeReport().successfulTableQueries());
		assertEquals(0, calculator.getRuntimeReport().fallbackCount());
	}

	@Test
	void convertsPhysicalAftCpPitchTorqueToOpenRocketRestoringMoment() {
		FlightConfiguration configuration = TestRockets.makeEstesAlphaIII().getSelectedConfiguration();
		FlightConditions conditions = queryConditions(configuration);
		conditions.setAOA(ALPHA);
		conditions.setTheta(0);
		conditions.setPitchRate(0);
		PhysicsAeroAerodynamicCalculator calculator = new PhysicsAeroAerodynamicCalculator(
				table(reynolds(conditions)), "geometry", "settings", "content", PhysicsAeroMode.STRICT, null);

		AerodynamicForces forces = calculator.getAerodynamicForces(configuration, conditions, new WarningSet());
		double centerOfGravityM = 0.25;
		double momentAboutCg = forces.getCm()
				- forces.getCN() * centerOfGravityM / conditions.getRefLength();

		// The source table has CN = 2 alpha and physical Cm = -alpha, i.e. xCP = 0.5 m.
		assertEquals(0.5, forces.getCP().getX(), 1e-12);
		assertEquals(0.1, forces.getCm(), 1e-12);
		assertEquals(0.05, momentAboutCg, 1e-12);
		assertTrue(momentAboutCg > 0, "an aft CP must produce OpenRocket's restoring pitch moment");
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
		hybrid.setSimulationAerodynamicsContext(new SimulationAerodynamicsContext(1, true, 100, 1, 101325, true));
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
	void lowDensityRuntimeHoldsTheLastDirectlyValidatedReynoldsBoundary() {
		FlightConfiguration configuration = TestRockets.makeEstesAlphaIII().getSelectedConfiguration();
		FlightConditions conditions = queryConditions(configuration);
		double runtimeReynolds = reynolds(conditions);
		double runtimeRatio = 7.0e-6;
		PhysicsAeroAerodynamicCalculator calculator = new PhysicsAeroAerodynamicCalculator(
				table(runtimeReynolds / runtimeRatio, 0.01), "geometry", "settings", "content",
				PhysicsAeroMode.STRICT, null);

		AerodynamicForces forces = calculator.getAerodynamicForces(
				configuration, conditions, new WarningSet());

		assertEquals(0.2 + 0.01 * Math.log(0.01), forces.getCDaxial(), 1e-12,
				"the correction must be held at the validated 0.01 anchor, not extrapolated");
		assertEquals(0, calculator.getRuntimeReport().fallbackCount());
		assertTrue(calculator.getRuntimeReport().runtimeFlags().contains(
				PhysicsAeroRuntimeFlag.LOW_DENSITY_REYNOLDS_SOURCE_BOUNDARY_HOLD));
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

	@Test
	void launchGuideUsesOnlyTableAxialLoadAtAxialDynamicPressure() {
		FlightConfiguration configuration = TestRockets.makeEstesAlphaIII().getSelectedConfiguration();
		FlightConditions conditions = queryConditions(configuration);
		conditions.setAOA(Math.toRadians(60));
		conditions.setTheta(0);
		conditions.setRollRate(0);
		conditions.setPitchRate(0);
		conditions.setYawRate(0);
		PhysicsAeroAerodynamicCalculator strict = new PhysicsAeroAerodynamicCalculator(
				table(reynolds(conditions)), "geometry", "settings", "content",
				PhysicsAeroMode.STRICT, null);
		strict.setSimulationAerodynamicsContext(new SimulationAerodynamicsContext(
				0, false, 0, 0, 101325, false));

		AerodynamicForces forces = strict.getAerodynamicForces(
				configuration, conditions, new WarningSet());

		assertEquals(0.2 * Math.pow(Math.cos(Math.toRadians(60)), 2),
				forces.getCDaxial(), 1e-12);
		assertEquals(0, forces.getCN(), 1e-12);
		assertEquals(0, forces.getCside(), 1e-12);
		assertEquals(0, forces.getCm(), 1e-12);
		assertEquals(0, forces.getCyaw(), 1e-12);
		assertEquals(1, strict.getRuntimeReport().successfulTableQueries());
		assertEquals(0, strict.getRuntimeReport().fallbackCount());
		assertTrue(strict.getRuntimeReport().runtimeFlags().contains(
				PhysicsAeroRuntimeFlag.LAUNCH_GUIDE_AXIAL_TABLE_PROJECTION));
	}

	@Test
	void strictRuntimeReconstructsOutOfRangeBetaFromTotalIncidenceAtAllMach() {
		FlightConfiguration configuration = TestRockets.makeEstesAlphaIII().getSelectedConfiguration();
		FlightConditions conditions = queryConditions(configuration);
		double rawAlpha = Math.toRadians(1.2);
		double rawBeta = Math.toRadians(5.2);
		conditions.setMach(1.1);
		conditions.setAOA(Math.hypot(rawAlpha, rawBeta));
		conditions.setTheta(Math.atan2(rawBeta, rawAlpha));
		conditions.setPitchRate(0);
		conditions.setYawRate(0);
		double referenceReynolds = reynolds(conditions);
		AerodynamicTable narrowBeta = table(referenceReynolds, 1.1,
				new double[] {-Math.toRadians(15), 0, Math.toRadians(15)},
				new double[] {-Math.toRadians(5), 0, Math.toRadians(5)});
		PhysicsAeroAerodynamicCalculator strict = new PhysicsAeroAerodynamicCalculator(
				narrowBeta, "geometry", "settings", "content", PhysicsAeroMode.STRICT, null);

		AerodynamicForces forces = strict.getAerodynamicForces(
				configuration, conditions, new WarningSet());

		assertEquals(2 * Math.hypot(rawAlpha, rawBeta), forces.getCN(), 2e-4);
		assertEquals(0, forces.getCside(), 2e-4);
		assertEquals(Math.hypot(rawAlpha, rawBeta), forces.getCm(), 1e-4);
		assertEquals(0, forces.getCyaw(), 1e-4);
		assertEquals(0.5, forces.getCP().getX(), 1e-12);
		assertEquals(0, strict.getRuntimeReport().fallbackCount());
		assertTrue(strict.getRuntimeReport().runtimeFlags().contains(
				PhysicsAeroRuntimeFlag.INCIDENCE_AZIMUTH_RECONSTRUCTION));
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
		return table(referenceReynolds, .5, 1, new double[] {-ALPHA, ALPHA},
				new double[] {-BETA, BETA});
	}

	private static AerodynamicTable table(double referenceReynolds, double minimumRatio) {
		return table(referenceReynolds, minimumRatio, 1, new double[] {-ALPHA, ALPHA},
				new double[] {-BETA, BETA});
	}

	private static AerodynamicTable table(double referenceReynolds, double[] alphaAxis,
			double[] betaAxis) {
		return table(referenceReynolds, .5, 1, alphaAxis, betaAxis);
	}

	private static AerodynamicTable table(double referenceReynolds, double mach,
			double[] alphaAxis, double[] betaAxis) {
		return table(referenceReynolds, .5, mach, alphaAxis, betaAxis);
	}

	private static AerodynamicTable table(double referenceReynolds, double minimumRatio,
			double mach, double[] alphaAxis, double[] betaAxis) {
		TableAxes axes = new TableAxes(new double[] {mach}, alphaAxis, betaAxis, new double[] {0});
		List<TableCell> cells = new ArrayList<>();
		for (double alpha : axes.alphaRad()) {
			for (double beta : axes.betaRad()) {
				cells.add(cell(alpha, beta, referenceReynolds, minimumRatio));
			}
		}
		TableMetadata metadata = new TableMetadata(TableMetadata.CURRENT_SCHEMA, "geometry", "settings",
				"code", "registry", TableMetadata.REQUIRED_UNITS,
				TableMetadata.REQUIRED_AXIS_CONVENTION, Map.of(), Map.of(),
				CertificationState.EXPERIMENTAL_FLIGHT_PENDING);
		return new AerodynamicTable(axes, cells, metadata);
	}

	private static TableCell cell(double alpha, double beta, double referenceReynolds) {
		return cell(alpha, beta, referenceReynolds, .5);
	}

	private static TableCell cell(double alpha, double beta, double referenceReynolds,
			double minimumRatio) {
		AerodynamicCoefficients coefficients = new AerodynamicCoefficients(
				0.2, 2 * alpha, 3 * beta, 0.01, -alpha, 0.5 * beta);
		return new TableCell(coefficients, Map.of("vehicle", coefficients), Map.of("ALL", coefficients),
				List.of("TEST_SIX_AXIS"), new double[] {.9, .9, .9, .9, .9, .9},
				new double[] {.1, .1, .1, .1, .1, .1}, List.of("COAST_STATE"),
				new ReferenceState(1000, 1, 1, new Coordinate()), CellDiagnostics.direct(), true,
				new AerodynamicDerivatives(-0.2, -0.3, -0.4),
				new RuntimeCorrectionData(referenceReynolds, minimumRatio, 2,
						new double[] {.01, 0, 0, 0, 0, 0}, false, "TEST_LOG_RE"));
	}
}

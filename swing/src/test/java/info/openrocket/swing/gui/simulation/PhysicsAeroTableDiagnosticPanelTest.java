package info.openrocket.swing.gui.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.physicsaero.diagnostics.CellDiagnostics;
import info.openrocket.core.aerodynamics.physicsaero.force.AerodynamicCoefficients;
import info.openrocket.core.aerodynamics.physicsaero.force.AerodynamicDerivatives;
import info.openrocket.core.aerodynamics.physicsaero.force.ReferenceState;
import info.openrocket.core.aerodynamics.physicsaero.table.AerodynamicTable;
import info.openrocket.core.aerodynamics.physicsaero.table.CertificationState;
import info.openrocket.core.aerodynamics.physicsaero.table.RuntimeCorrectionData;
import info.openrocket.core.aerodynamics.physicsaero.table.TableAxes;
import info.openrocket.core.aerodynamics.physicsaero.table.TableCell;
import info.openrocket.core.aerodynamics.physicsaero.table.TableMetadata;
import info.openrocket.core.util.Coordinate;

class PhysicsAeroTableDiagnosticPanelTest {
	private static final double EPSILON = 1.0e-12;

	@Test
	void exposesSixAxisDragBreakdownDerivativesAndReynoldsCorrection() {
		AerodynamicTable table = table();
		double runtimeReynolds = 2_000_000.0;

		var snapshot = PhysicsAeroTableDiagnosticPanel.evaluate(
				table, 0.5, 0, 0, 0, runtimeReynolds);

		assertEquals(0.20, snapshot.query().coefficients().ca(), EPSILON);
		assertEquals(0.10, snapshot.query().coefficients().cn(), EPSILON);
		assertEquals(-0.03, snapshot.query().coefficients().cy(), EPSILON);
		assertEquals(0.01, snapshot.query().coefficients().cl(), EPSILON);
		assertEquals(-0.04, snapshot.query().coefficients().cm(), EPSILON);
		assertEquals(0.02, snapshot.query().coefficients().cYaw(), EPSILON);
		assertEquals(-0.2, snapshot.query().derivatives().clp(), EPSILON);
		assertEquals(-0.3, snapshot.query().derivatives().cmq(), EPSILON);
		assertEquals(-0.4, snapshot.query().derivatives().cnr(), EPSILON);

		assertEquals(0.20, snapshot.rawDrag().total(), EPSILON);
		assertEquals(0.15, snapshot.rawDrag().pressure(), EPSILON);
		assertEquals(0.03, snapshot.rawDrag().base(), EPSILON);
		assertEquals(0.02, snapshot.rawDrag().friction(), EPSILON);
		assertEquals(0.20 + 0.01 * Math.log(2), snapshot.correctedCoefficients().ca(), EPSILON);
		assertEquals(0.10 + 0.02 * Math.log(2), snapshot.correctedCoefficients().cn(), EPSILON);
		assertEquals(0.20 + 0.01 * Math.log(2), snapshot.correctedDrag().total(), EPSILON);
		assertTrue(snapshot.reynoldsStatus().contains("TEST_LOG_RE"));
	}

	@Test
	void reportsNamedRebuildRequirementWithoutDiscardingRawResults() {
		var snapshot = PhysicsAeroTableDiagnosticPanel.evaluate(
				table(), 0.5, 0, 0, 0, 3_000_000.0);

		assertEquals(0.20, snapshot.query().coefficients().ca(), EPSILON);
		assertNull(snapshot.correctedCoefficients());
		assertTrue(snapshot.reynoldsStatus().contains("REYNOLDS_REBUILD_REQUIRED"));
	}

	@Test
	void preservesTypedOutOfDomainQueryFailure() {
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> PhysicsAeroTableDiagnosticPanel.evaluate(table(), 7.5, 0, 0, 0, null));
		assertTrue(exception.getMessage().contains("OUT_OF_DOMAIN"));
	}

	private static AerodynamicTable table() {
		AerodynamicCoefficients coefficients = new AerodynamicCoefficients(
				0.20, 0.10, -0.03, 0.01, -0.04, 0.02);
		Map<String, AerodynamicCoefficients> owners = Map.of(
				"BODY_PRESSURE_LOAD", new AerodynamicCoefficients(0.15, 0.10, -0.03, 0.01, -0.04, 0.02),
				"BODY_BASE_PRESSURE_DRAG", new AerodynamicCoefficients(0.03, 0, 0, 0, 0, 0),
				"BODY_SKIN_FRICTION", new AerodynamicCoefficients(0.02, 0, 0, 0, 0, 0));
		TableCell cell = new TableCell(coefficients, Map.of("vehicle", coefficients), owners,
				List.of("TEST_SIX_AXIS"), new double[] {.9, .8, .8, .7, .7, .7},
				new double[] {.1, .1, .1, .1, .1, .1}, List.of("COAST_STATE"),
				new ReferenceState(1000, 1, 1, new Coordinate()), CellDiagnostics.direct(), true,
				new AerodynamicDerivatives(-0.2, -0.3, -0.4),
				new RuntimeCorrectionData(1_000_000, 0.5, 2.0,
						new double[] {.01, .02, 0, 0, 0, 0}, false, "TEST_LOG_RE"));
		TableMetadata metadata = new TableMetadata(TableMetadata.CURRENT_SCHEMA, "geometry", "settings",
				"code", "registry", TableMetadata.REQUIRED_UNITS, TableMetadata.REQUIRED_AXIS_CONVENTION,
				Map.of(), Map.of(), CertificationState.EXPERIMENTAL_FLIGHT_PENDING);
		return new AerodynamicTable(new TableAxes(new double[] {0.5}, new double[] {0},
				new double[] {0}, new double[] {0}), List.of(cell), metadata);
	}
}

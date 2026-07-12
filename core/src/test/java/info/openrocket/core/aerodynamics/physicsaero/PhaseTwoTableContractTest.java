package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import info.openrocket.core.aerodynamics.physicsaero.config.OutputConfiguration;
import info.openrocket.core.aerodynamics.physicsaero.diagnostics.CellDiagnostics;
import info.openrocket.core.aerodynamics.physicsaero.force.AerodynamicCoefficients;
import info.openrocket.core.aerodynamics.physicsaero.force.ReferenceState;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroTableCalculator;
import info.openrocket.core.aerodynamics.physicsaero.table.AerodynamicTable;
import info.openrocket.core.aerodynamics.physicsaero.table.PhysicsAeroTableGenerator;
import info.openrocket.core.aerodynamics.physicsaero.table.TableAxes;
import info.openrocket.core.aerodynamics.physicsaero.table.TableCell;
import info.openrocket.core.aerodynamics.physicsaero.table.TableMetadata;
import info.openrocket.core.util.Coordinate;

class PhaseTwoTableContractTest {
	@TempDir
	Path temporary;

	@Test
	void zeroIncidenceMachSliceExecutesAndNonzeroIncidenceIsRejected() {
		TableMetadata metadata = metadata();
		TableAxes axes = new TableAxes(new double[] { 1.2, 2.0, 3.0, 5.0 }, new double[] { 0.0 },
				new double[] { 0.0 });
		List<TableCell> cells = new ArrayList<>();
		for (double mach : axes.mach()) {
			cells.add(cell(0.08 + 0.01 * mach));
		}
		PhysicsAeroTableCalculator calculator = new PhysicsAeroTableCalculator(
				new AerodynamicTable(axes, cells, metadata), metadata.geometryHash(), metadata.settingsHash());

		assertEquals(0.10, calculator.query(2.0, 0.0, 0.0).coefficients().ca(), 1.0e-12);
		assertTrue(calculator.query(2.5, 0.0, 0.0).interpolated());
		assertThrows(IllegalArgumentException.class, () -> calculator.query(2.0, 1.0e-9, 0.0));
		assertThrows(IllegalArgumentException.class, () -> calculator.query(2.0, 0.0, -1.0e-9));
	}

	@Test
	void failedStateWritesExactPhaseTwoCoordinatesToReproductionCase() throws Exception {
		TableMetadata metadata = metadata();
		TableAxes axes = new TableAxes(new double[] { 2.25 }, new double[] { 0.0 }, new double[] { 0.0 });
		Path output = temporary.resolve("body.aero");
		Path checkpoints = temporary.resolve("checkpoints");

		var report = new PhysicsAeroTableGenerator().generate(axes, metadata,
				new OutputConfiguration(output, temporary.resolve("body.json"), false), checkpoints, 1,
				new AtomicBoolean(), (index, mach, alpha, beta) -> {
					throw new IllegalStateException("BODY_ATTACHED_FLOW_SOLUTION_FAILED");
				});

		assertEquals(1, report.failedCells());
		assertFalse(Files.exists(output));
		String reproduction = Files.readString(checkpoints.resolve("failure-00000000.json"));
		assertTrue(reproduction.contains("BODY_ATTACHED_FLOW_SOLUTION_FAILED"));
		assertTrue(reproduction.contains("\"mach\": 2.25"));
		assertTrue(reproduction.contains("\"alphaRad\": 0.0"));
		assertTrue(reproduction.contains("\"betaRad\": 0.0"));
	}

	private static TableCell cell(double ca) {
		ReferenceState reference = new ReferenceState(100_000.0, 0.01, 1.0, new Coordinate());
		AerodynamicCoefficients coefficients = new AerodynamicCoefficients(ca, 0.0, 0.0, 0.0, 0.0, 0.0);
		return new TableCell(coefficients, Map.of("body", coefficients),
				Map.of("BODY_PRESSURE", coefficients), List.of("PHASE2_BODY"),
				new double[] { 1, 1, 1, 1, 1, 1 }, new double[6], List.of(), reference,
				CellDiagnostics.direct(), true);
	}

	private static TableMetadata metadata() {
		return new TableMetadata(TableMetadata.CURRENT_SCHEMA, "phase-2-geometry", "phase-2-settings",
				"phase-2-test", "phase-2-registry", "SI;radians", "OPENROCKET_BODY_AXES_V1",
				Instant.parse("2026-01-01T00:00:00Z"), Map.of("areaM2", 0.01, "lengthM", 1.0),
				Map.of("cornerAngleRad", 1.0e-4), "PHASE2_ZERO_INCIDENCE");
	}
}

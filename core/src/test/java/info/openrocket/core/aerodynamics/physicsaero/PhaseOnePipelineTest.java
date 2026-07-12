package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import info.openrocket.core.aerodynamics.physicsaero.api.*;
import info.openrocket.core.aerodynamics.physicsaero.config.OutputConfiguration;
import info.openrocket.core.aerodynamics.physicsaero.diagnostics.CellDiagnostics;
import info.openrocket.core.aerodynamics.physicsaero.flow.*;
import info.openrocket.core.aerodynamics.physicsaero.force.*;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.PerfectGasAir;
import info.openrocket.core.aerodynamics.physicsaero.geometry.*;
import info.openrocket.core.aerodynamics.physicsaero.interpolation.QueryResult;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroTableCalculator;
import info.openrocket.core.aerodynamics.physicsaero.table.*;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.util.Coordinate;
import info.openrocket.core.util.TestRockets;
import info.openrocket.core.util.BaseTestCase;

class PhaseOnePipelineTest extends BaseTestCase {
	@TempDir Path temporary;
	@Test void conePipelineSerializesReloadsAndQueriesExactly() throws Exception {
		Fixture f = fixture(); Path one = temporary.resolve("one.aero"), manifest = temporary.resolve("one.json");
		new TableWriter().write(f.table, one, manifest); AerodynamicTable loaded = new TableReader().read(one);
		QueryResult queried = new PhysicsAeroTableCalculator(loaded, f.metadata.geometryHash(), f.metadata.settingsHash()).query(2, 0, 0);
		assertEquals(f.cell.coefficients(), queried.coefficients()); assertEquals(f.cell.methodIds(), queried.methodIds()); assertFalse(queried.interpolated());
		Path two = temporary.resolve("two.aero"); new TableWriter().write(f.table, two, temporary.resolve("two.json"));
		assertArrayEquals(Files.readAllBytes(one), Files.readAllBytes(two)); assertTrue(Files.readString(manifest).contains(TableMetadata.CURRENT_SCHEMA));
	}
	@Test void cancellationCannotReplaceExistingValidTable() throws Exception {
		Fixture f = fixture(); Path output = temporary.resolve("existing.aero"), manifest = temporary.resolve("existing.json");
		new TableWriter().write(f.table, output, manifest); byte[] before = Files.readAllBytes(output);
		PhysicsAeroTableGenerator generator = new PhysicsAeroTableGenerator(); AtomicBoolean cancelled = new AtomicBoolean(true);
		var report = generator.generate(f.table.axes(), f.metadata, new OutputConfiguration(output, manifest, false), temporary.resolve("checkpoints"), 2, cancelled,
				(index, mach, alpha, beta) -> f.cell);
		assertTrue(report.cancelled()); assertArrayEquals(before, Files.readAllBytes(output));
	}
	@Test void smallGridInterpolatesAndRejectsExtrapolation() {
		Fixture f = fixture(); TableAxes axes = new TableAxes(new double[] {2, 3}, new double[] {0, 0.1}, new double[] {0, 0.05});
		List<TableCell> cells = new ArrayList<>(); for (int i = 0; i < 8; i++) cells.add(cell(new AerodynamicCoefficients(0.1 + i * 0.01, 0, 0, 0, 0, 0), f.cell.referenceState()));
		AerodynamicTable grid = new AerodynamicTable(axes, cells, f.metadata); PhysicsAeroTableCalculator calculator = new PhysicsAeroTableCalculator(grid, f.metadata.geometryHash(), f.metadata.settingsHash());
		assertEquals(0.1, calculator.query(2, 0, 0).coefficients().ca(), 0); assertTrue(calculator.query(2.5, 0.05, 0.025).interpolated());
		assertThrows(IllegalArgumentException.class, () -> calculator.query(4, 0, 0));
	}
	@Test void parallelBuildOrderAndCheckpointResumeAreDeterministic() throws Exception {
		Fixture f = fixture(); TableAxes axes = new TableAxes(new double[] {2, 3}, new double[] {0, 0.1}, new double[] {0, 0.05});
		PhysicsAeroTableGenerator generator = new PhysicsAeroTableGenerator();
		Path out1 = temporary.resolve("parallel-1.aero"), out2 = temporary.resolve("parallel-2.aero"), checkpoint = temporary.resolve("parallel-checkpoint");
		var report = generator.generate(axes, f.metadata, new OutputConfiguration(out1, temporary.resolve("parallel-1.json"), false), checkpoint, 3,
				new AtomicBoolean(), (index, mach, alpha, beta) -> cell(new AerodynamicCoefficients(0.1 + index.flatIndex(), 0, 0, 0, 0, 0), f.cell.referenceState()));
		assertEquals(axes.cellCount(), report.completedCells());
		var resumed = generator.generate(axes, f.metadata, new OutputConfiguration(out2, temporary.resolve("parallel-2.json"), false), checkpoint, 1,
				new AtomicBoolean(), (index, mach, alpha, beta) -> { throw new AssertionError("compatible checkpoint was not resumed"); });
		assertEquals(axes.cellCount(), resumed.completedCells()); assertArrayEquals(Files.readAllBytes(out1), Files.readAllBytes(out2));
	}
	@Test void failedCellWritesReproductionJsonAndDoesNotPublishTable() throws Exception {
		Fixture f = fixture(); Path output = temporary.resolve("failed.aero"), checkpoint = temporary.resolve("failed-checkpoint");
		var report = new PhysicsAeroTableGenerator().generate(f.table.axes(), f.metadata,
				new OutputConfiguration(output, temporary.resolve("failed.json"), false), checkpoint, 1, new AtomicBoolean(),
				(index, mach, alpha, beta) -> { throw new IllegalStateException("sentinel failure"); });
		assertEquals(1, report.failedCells()); assertFalse(Files.exists(output));
		assertTrue(Files.readString(checkpoint.resolve("failure-00000000.json")).contains("sentinel failure"));
	}
	private Fixture fixture() {
		Rocket rocket = TestRockets.makeEstesAlphaIII(); AeroGeometry geometry = new GeometryExtractor().extract(rocket, 1e-6, "ADIABATIC", "settings");
		AeroComponent body = geometry.components().stream().filter(c -> Set.of("NoseCone", "BodyTube", "Transition").contains(c.type())).findFirst().orElseThrow();
		PerfectGasAir air = new PerfectGasAir(); AtmosphereState atmosphere = new AtmosphereState(101325, 288.15, 101325 / (air.gasConstant() * 288.15), air.viscosity(288.15));
		FlowCondition flow = FlowCondition.fromAngles(2, 0, 0, atmosphere, air, false, "fixture");
		ReferenceState reference = new ReferenceState(flow.dynamicPressurePa(), geometry.references().referenceAreaM2(), geometry.references().referenceLengthM(), new Coordinate());
		ComponentEvaluator.Evaluation evaluation = new ComponentEvaluator().evaluate(new DeterministicTestCorrelation(), new CorrelationInput(body, flow, reference));
		TableCell cell = new TableCell(evaluation.coefficients(), Map.of(body.id(), evaluation.coefficients()), Map.of("BODY_PRESSURE", evaluation.coefficients()),
				List.of(evaluation.metadata().methodId().value()), ones(), new double[6], List.of(), reference, CellDiagnostics.direct(), true);
		TableMetadata metadata = new TableMetadata(TableMetadata.CURRENT_SCHEMA, geometry.geometryHash(), "settings-hash", "test-code", "registry-v1", "SI;radians",
				"OPENROCKET_BODY_AXES_V1", Instant.parse("2026-01-01T00:00:00Z"), Map.of("areaM2", reference.referenceAreaM2(), "lengthM", reference.referenceLengthM()), Map.of("root", 1e-10), "PHASE1_TESTED");
		AerodynamicTable table = new AerodynamicTable(new TableAxes(new double[] {2}, new double[] {0}, new double[] {0}), List.of(cell), metadata);
		return new Fixture(cell, metadata, table);
	}
	private static TableCell cell(AerodynamicCoefficients c, ReferenceState r) { return new TableCell(c, Map.of(), Map.of(), List.of("method"), ones(), new double[6], List.of(), r, CellDiagnostics.direct(), true); }
	private static double[] ones() { return new double[] {1, 1, 1, 1, 1, 1}; }
	private record Fixture(TableCell cell, TableMetadata metadata, AerodynamicTable table) {}
}

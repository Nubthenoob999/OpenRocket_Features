package info.openrocket.core.correlation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.time.Instant;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.physicsaero.flow.PoweredFlowState;
import info.openrocket.core.aerodynamics.physicsaero.force.AerodynamicCoefficients;
import info.openrocket.core.aerodynamics.physicsaero.integration.FullRegimeTableBuilder;
import info.openrocket.core.aerodynamics.physicsaero.interpolation.TableQueryEngine;
import info.openrocket.core.aerodynamics.physicsaero.table.TableMetadata;
import info.openrocket.core.correlation.RasaeroBenchmarkData.BenchmarkCase;

/** Explicit powered-flow capability probes for the four NACA RM L54D27 increments. */
@Tag("benchmark")
class RasaeroL54D27PoweredBenchmarkTest {
	private final RasaeroBenchmarkSolverAdapter solver = new RasaeroBenchmarkSolverAdapter();

	@TestFactory
	Stream<DynamicTest> poweredDragLedger(TestReporter reporter) {
		List<BenchmarkCase> cases = RasaeroBenchmarkData.casesForDataset("L54D27");
		assertEquals(4, cases.size());
		return cases.stream().map(benchmark -> DynamicTest.dynamicTest(benchmark.caseId(), () -> {
			double mach = benchmark.inputs().get("mach").getAsDouble();
			var prediction = solver.probePowered(benchmark, RasaeroBenchmarkFixtures.l54d27(), mach,
					RasaeroBenchmarkFixtures.standardAtmosphere());
			RasaeroBenchmarkAssertions.assertBlocking(benchmark, prediction, reporter);
		}));
	}

	@Test
	void poweredAndCoastVariantsAreNumericalTableCells() {
		var geometry = RasaeroBenchmarkFixtures.l54d27();
		var atmosphere = RasaeroBenchmarkFixtures.standardAtmosphere();
		PoweredFlowState coast = PoweredFlowState.coast(atmosphere.pressurePa());
		PoweredFlowState powered = new PoweredFlowState(1, 1, 2.91,
				geometry.references().exposedBaseAreaM2(), atmosphere.pressurePa(), 1.25,
				atmosphere.pressurePa());
		TableMetadata metadata = new TableMetadata(TableMetadata.CURRENT_SCHEMA,
				geometry.geometryHash(), "powered-table-test", "test", "registry", "SI;radians",
				"OPENROCKET_BODY_AXES_V1", Instant.parse("2026-01-01T00:00:00Z"),
				Map.of(), Map.of(), "TEST");
		var table = new FullRegimeTableBuilder().build(geometry, new double[] {1.075},
				new double[] {0}, new double[] {0}, new PoweredFlowState[] {coast, powered},
				atmosphere, RasaeroBenchmarkFixtures.AIR, metadata);
		double increment = table.cell(0, 0, 0, 1).coefficients().ca()
				- table.cell(0, 0, 0, 0).coefficients().ca();
		assertEquals(0.107, increment, 2e-6);
		assertEquals(0.073, table.cell(0, 0, 0, 1).ownerTotals()
				.get("POWERED_PLUME_INSTALLATION_DRAG").ca(), 2e-6);
		assertEquals(table.cell(0, 0, 0, 1).coefficients().ca(),
				table.cell(0, 0, 0, 1).componentTotals().values().stream()
						.mapToDouble(AerodynamicCoefficients::ca).sum(), 1e-12);
		assertEquals(table.cell(0, 0, 0, 1).coefficients().ca(),
				table.cell(0, 0, 0, 1).ownerTotals().values().stream()
						.mapToDouble(AerodynamicCoefficients::ca).sum(), 1e-12);
		assertThrows(IllegalStateException.class,
				() -> new TableQueryEngine().query(table, 1.075, 0, 0, 0.5));
	}

	@Test
	void nozzleGeometryOnlyStateScalesSourceIncrementByExitToBaseArea() {
		var geometry = RasaeroBenchmarkFixtures.l54d27();
		var atmosphere = RasaeroBenchmarkFixtures.standardAtmosphere();
		double quarterBaseArea = 0.25 * geometry.references().exposedBaseAreaM2();
		PoweredFlowState inferred = PoweredFlowState.nozzleGeometryOnly(
				1, quarterBaseArea, atmosphere.pressurePa());
		var result = new info.openrocket.core.aerodynamics.physicsaero.powered.PoweredBaseFlowModel()
				.evaluate(geometry, 1.075, inferred);
		assertEquals(0.25 * 0.107, result.totalDeltaCd(), 2e-6);
		org.junit.jupiter.api.Assertions.assertTrue(result.validityFlags()
				.contains("NOZZLE_GEOMETRY_ONLY_REFERENCE_EXIT_MACH"));
	}

	@Test
	void poweredIncrementClosesContinuouslyAtSourceRangeEndpoints() {
		var geometry = RasaeroBenchmarkFixtures.l54d27();
		var atmosphere = RasaeroBenchmarkFixtures.standardAtmosphere();
		PoweredFlowState powered = new PoweredFlowState(1, 1, 2.91,
				geometry.references().exposedBaseAreaM2(), atmosphere.pressurePa(),
				1.25, atmosphere.pressurePa());
		var model = new info.openrocket.core.aerodynamics.physicsaero.powered.PoweredBaseFlowModel();
		assertEquals(0, model.evaluate(geometry, 0.8, powered).totalDeltaCd(), 1e-12);
		assertEquals(0, model.evaluate(geometry, 1.2, powered).totalDeltaCd(), 1e-12);
	}
}

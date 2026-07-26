package info.openrocket.core.correlation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestReporter;

import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;
import info.openrocket.core.correlation.RasaeroBenchmarkData.BenchmarkCase;

/** Non-blocking residual diagnostics against the 14 RASAero II vendor CP predictions. */
@Tag("benchmark")
class RasaeroSecondaryReferenceDiagnosticTest {
	private final RasaeroBenchmarkSolverAdapter solver = new RasaeroBenchmarkSolverAdapter();

	@TestFactory
	Stream<DynamicTest> rasaeroTwoArcasCp(TestReporter reporter) {
		List<BenchmarkCase> cases = RasaeroBenchmarkData.casesForDataset("RASAERO-ARCAS-COMPARISON");
		assertEquals(14, cases.size());
		return cases.stream().map(canonical -> DynamicTest.dynamicTest(canonical.caseId(), () -> {
			BenchmarkCase benchmark = RasaeroBenchmarkData.solverCase(canonical);
			double mach = benchmark.inputs().get("mach").getAsDouble();
			boolean longModel = "ARCAS Long".equals(benchmark.inputs().get("configuration").getAsString());
			AeroGeometry geometry = longModel
					? RasaeroBenchmarkFixtures.arcasLong(0, true, true)
					: RasaeroBenchmarkFixtures.arcasShort(0, true, true);
			var atmosphere = RasaeroBenchmarkFixtures.reynoldsMatchedPerMeter(mach,
					RasaeroBenchmarkFixtures.ARCAS_REYNOLDS_PER_M);
			var prediction = solver.predictCpPercent(geometry, mach, Math.toRadians(1), atmosphere,
					List.of(RasaeroBenchmarkFixtures.ARCAS_GEOMETRY_LIMITATION));
			RasaeroBenchmarkAssertions.assertSecondaryDiagnostic(benchmark, prediction, reporter);
		}));
	}
}

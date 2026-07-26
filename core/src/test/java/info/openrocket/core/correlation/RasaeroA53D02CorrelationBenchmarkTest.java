package info.openrocket.core.correlation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestReporter;

import info.openrocket.core.aerodynamics.physicsaero.flow.AtmosphereState;
import info.openrocket.core.correlation.RasaeroBenchmarkData.BenchmarkCase;

/** Blocking zero-lift drag correlation coverage for all 22 NACA RM A53D02 cases. */
@Tag("benchmark")
class RasaeroA53D02CorrelationBenchmarkTest {
	private final RasaeroBenchmarkSolverAdapter solver = new RasaeroBenchmarkSolverAdapter();

	@TestFactory
	Stream<DynamicTest> zeroLiftDrag(TestReporter reporter) {
		List<BenchmarkCase> cases = RasaeroBenchmarkData.casesForDataset("A53D02");
		assertEquals(22, cases.size());
		return cases.stream().map(canonical -> DynamicTest.dynamicTest(canonical.caseId(), () -> {
			BenchmarkCase benchmark = RasaeroBenchmarkData.solverCase(canonical);
			double mach = benchmark.inputs().get("mach").getAsDouble();
			AtmosphereState atmosphere = benchmark.inputs().has("Re")
					? RasaeroBenchmarkFixtures.reynoldsMatchedTotal(mach,
							benchmark.inputs().get("Re").getAsDouble(), RasaeroBenchmarkFixtures.a53LengthM())
					: RasaeroBenchmarkFixtures.standardAtmosphere();
			var prediction = solver.predictCa(RasaeroBenchmarkFixtures.a53d02(), mach, 0,
					atmosphere, List.of());
			RasaeroBenchmarkAssertions.assertBlocking(benchmark, prediction, reporter);
		}));
	}
}

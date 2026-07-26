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

/** Blocking ARCAS supersonic CP and primary wind-tunnel power-off drag benchmarks. */
@Tag("benchmark")
class RasaeroD4014CorrelationBenchmarkTest {
	private final RasaeroBenchmarkSolverAdapter solver = new RasaeroBenchmarkSolverAdapter();

	@TestFactory
	Stream<DynamicTest> centerOfPressureAtOneDegree(TestReporter reporter) {
		List<BenchmarkCase> cases = cases("x_cp_percent_body_length");
		assertEquals(12, cases.size());
		return cases.stream().map(benchmark -> dynamic(benchmark, Math.toRadians(1), true, reporter));
	}

	@TestFactory
	Stream<DynamicTest> powerOffDrag(TestReporter reporter) {
		List<BenchmarkCase> cases = cases("CD_power_off");
		assertEquals(11, cases.size());
		return cases.stream().map(benchmark -> dynamic(benchmark, 0, false, reporter));
	}

	private DynamicTest dynamic(BenchmarkCase canonical, double alpha, boolean cp, TestReporter reporter) {
		return DynamicTest.dynamicTest(canonical.caseId(), () -> {
			BenchmarkCase benchmark = RasaeroBenchmarkData.solverCase(canonical);
			double mach = benchmark.inputs().get("mach").getAsDouble();
			boolean longModel = benchmark.inputs().get("configuration").getAsInt() == 2;
			AeroGeometry geometry = longModel
					? RasaeroBenchmarkFixtures.arcasLong(0, true, true)
					: RasaeroBenchmarkFixtures.arcasShort(0, true, true);
			var atmosphere = RasaeroBenchmarkFixtures.reynoldsMatchedPerMeter(mach,
					RasaeroBenchmarkFixtures.ARCAS_REYNOLDS_PER_M);
			List<String> limitations = List.of(RasaeroBenchmarkFixtures.ARCAS_GEOMETRY_LIMITATION);
			var prediction = cp ? solver.predictCpPercent(geometry, mach, alpha, atmosphere, limitations)
					: solver.predictCa(geometry, mach, alpha, atmosphere, limitations);
			RasaeroBenchmarkAssertions.assertBlocking(benchmark, prediction, reporter);
		});
	}

	private static List<BenchmarkCase> cases(String expectedName) {
		return RasaeroBenchmarkData.casesForDataset("D4014").stream()
				.filter(benchmark -> expectedName.equals(benchmark.expectedName())).toList();
	}
}

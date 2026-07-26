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

/** Blocking ARCAS transonic axial-force and center-of-pressure benchmarks. */
@Tag("benchmark")
class RasaeroD4013CorrelationBenchmarkTest {
	private final RasaeroBenchmarkSolverAdapter solver = new RasaeroBenchmarkSolverAdapter();

	@TestFactory
	Stream<DynamicTest> correctedAxialForce(TestReporter reporter) {
		List<BenchmarkCase> cases = cases("CA_corr");
		assertEquals(48, cases.size());
		return cases.stream().map(benchmark -> dynamic(benchmark, 0, false, reporter));
	}

	@TestFactory
	Stream<DynamicTest> centerOfPressureAtOneDegree(TestReporter reporter) {
		List<BenchmarkCase> cases = cases("x_cp_percent_body_length");
		assertEquals(24, cases.size());
		return cases.stream().map(benchmark -> dynamic(benchmark, Math.toRadians(1), true, reporter));
	}

	private DynamicTest dynamic(BenchmarkCase canonical, double alpha, boolean cp, TestReporter reporter) {
		return DynamicTest.dynamicTest(canonical.caseId(), () -> {
			BenchmarkCase benchmark = RasaeroBenchmarkData.solverCase(canonical);
			double mach = benchmark.inputs().get("mach").getAsDouble();
			boolean fins = !benchmark.inputs().has("fins")
					|| !"off".equals(benchmark.inputs().get("fins").getAsString());
			double cant = benchmark.inputs().has("fin_cant_deg")
					? benchmark.inputs().get("fin_cant_deg").getAsDouble() : 0;
			boolean longModel = benchmark.seriesId().startsWith("long_");
			AeroGeometry geometry = longModel
					? RasaeroBenchmarkFixtures.arcasLong(cant, fins, false)
					: RasaeroBenchmarkFixtures.arcasShort(cant, fins, false);
			var atmosphere = RasaeroBenchmarkFixtures.reynoldsMatchedPerMeter(mach,
					RasaeroBenchmarkFixtures.ARCAS_REYNOLDS_PER_M);
			var prediction = cp
					? solver.predictCpPercent(geometry, mach, alpha, atmosphere,
							List.of(RasaeroBenchmarkFixtures.ARCAS_GEOMETRY_LIMITATION))
					: solver.predictCa(geometry, mach, alpha, atmosphere,
							List.of(RasaeroBenchmarkFixtures.ARCAS_GEOMETRY_LIMITATION));
			RasaeroBenchmarkAssertions.assertBlocking(benchmark, prediction, reporter);
		});
	}

	private static List<BenchmarkCase> cases(String expectedName) {
		return RasaeroBenchmarkData.casesForDataset("D4013").stream()
				.filter(benchmark -> expectedName.equals(benchmark.expectedName())).toList();
	}
}

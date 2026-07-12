package info.openrocket.core.correlation;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import info.openrocket.core.aerodynamics.physicsaero.flow.AtmosphereState;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.PerfectGasAir;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroComponent;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AxisymmetricProfile;
import info.openrocket.core.aerodynamics.physicsaero.geometry.FinGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.GeometryStation;
import info.openrocket.core.aerodynamics.physicsaero.geometry.ReferenceGeometry;
import info.openrocket.core.aerodynamics.physicsaero.integration.FullRegimeTableBuilder;
import info.openrocket.core.aerodynamics.physicsaero.table.AerodynamicTable;
import info.openrocket.core.aerodynamics.physicsaero.table.TableCell;
import info.openrocket.core.aerodynamics.physicsaero.table.TableMetadata;
import info.openrocket.core.util.Coordinate;

/**
 * Pointwise acceptance coverage for the first RASAero benchmark batch.
 *
 * <p>The first eight A53D02 Figure 11 cases are the cases published in the
 * suite's sample solver output.  They exercise the production subsonic,
 * transonic, and initial-supersonic branches through one public entry point.</p>
 */
class RasaeroA53D02CorrelationBenchmarkTest {
	private static final String DATASET_ID = "A53D02";
	private static final String SERIES_ID = "fig11_faired_zero_lift_cd";
	private static final int FIRST_BATCH_SIZE = 8;
	private static final double FIRST_BATCH_MAX_MACH = 2.3;
	private static final double INCH = 0.0254;

	private static final double BODY_DIAMETER_M = 0.250 * INCH;
	private static final double BODY_RADIUS_M = BODY_DIAMETER_M / 2;
	private static final double BODY_LENGTH_M = 2.50 * INCH;
	private static final double NOSE_LENGTH_M = 1.00 * INCH;
	private static final double FIN_ROOT_CHORD_M = 0.75 * INCH;
	private static final double FIN_TIP_CHORD_M = 0.375 * INCH;
	private static final double FIN_SPAN_M = (0.500 * INCH - BODY_DIAMETER_M) / 2;
	private static final double FIN_SWEEP_M = FIN_ROOT_CHORD_M - FIN_TIP_CHORD_M;
	private static final double FIN_THICKNESS_RATIO = 0.04;

	@Test
	void firstBatchIsPresentAndUsesPublishedTolerances() throws IOException {
		List<BenchmarkCase> cases = firstBatch();

		assertEquals(FIRST_BATCH_SIZE, cases.size());
		assertEquals(FIRST_BATCH_SIZE, cases.stream().map(BenchmarkCase::caseId).distinct().count());
		assertEquals(List.of(0.6, 0.8, 0.95, 1.02, 1.2, 1.5, 2.0, 2.3),
				cases.stream().map(BenchmarkCase::mach).toList());
		assertTrue(cases.stream().allMatch(c -> c.absoluteTolerance() > 0));
		assertTrue(cases.stream().allMatch(c -> Double.isFinite(c.expectedCd())));
	}

	@TestFactory
	Stream<DynamicTest> firstBatchMatchesA53D02ZeroLiftDrag() throws IOException {
		List<BenchmarkCase> cases = firstBatch();
		AerodynamicTable predictions = predict(cases);

		return IntStream.range(0, cases.size()).mapToObj(index -> {
			BenchmarkCase benchmark = cases.get(index);
			TableCell prediction = predictions.cell(index, 0, 0);
			return DynamicTest.dynamicTest(benchmark.caseId(), () -> assertPrediction(benchmark, prediction));
		});
	}

	private static void assertPrediction(BenchmarkCase benchmark, TableCell prediction) {
		double actual = prediction.coefficients().ca();
		String context = benchmark.caseId() + " methods=" + prediction.methodIds()
				+ " validity=" + prediction.validityFlags();

		assertAll(context,
				() -> assertTrue(prediction.directlyGenerated(), "benchmark must use a directly generated cell"),
				() -> assertFalse(prediction.methodIds().isEmpty(), "production method IDs must be reported"),
				() -> assertTrue(prediction.methodIds().stream().noneMatch(id -> id.contains("TEST")),
						"test-only correlations must not satisfy a benchmark"),
				() -> assertTrue(Arrays.stream(prediction.coefficients().toArray()).allMatch(Double::isFinite),
						"all predicted coefficients must be finite"),
				() -> assertEquals(0, prediction.coefficients().cn(), 1e-12),
				() -> assertEquals(0, prediction.coefficients().cy(), 1e-12),
				() -> assertEquals(0, prediction.coefficients().cl(), 1e-12),
				() -> assertEquals(0, prediction.coefficients().cm(), 1e-12),
				() -> assertEquals(0, prediction.coefficients().cYaw(), 1e-12),
				() -> assertExpectedBranch(benchmark.mach(), prediction),
				() -> assertEquals(benchmark.expectedCd(), actual, benchmark.absoluteTolerance(),
						() -> "zero-lift CA/CD outside source tolerance: " + context));
	}

	private static void assertExpectedBranch(double mach, TableCell prediction) {
		if (mach < 0.9) {
			assertTrue(prediction.methodIds().contains("SUBSONIC_SOURCE_DISTRIBUTION_V1"));
			assertTrue(prediction.methodIds().contains("SUBSONIC_BASE_V1"));
			return;
		}
		if (mach < 1.2) {
			assertEquals(List.of("DEDICATED_TRANSONIC_ROCKET_PEAK_V1"), prediction.methodIds());
			assertTrue(prediction.validityFlags().contains("NEAR_SONIC"));
			assertTrue(prediction.validityFlags().contains("TRANSONIC_CORRELATION_DOMINANT"));
			return;
		}
		assertTrue(prediction.methodIds().contains("OPENROCKET_SUPERSONIC_BASE_CP_V1"));
		assertTrue(prediction.validityFlags().contains("INDIVIDUAL_FIN_3D"));
		assertTrue(prediction.validityFlags().contains("PNK_DISABLED_ISOLATED_VALIDATION_GATE"));
		assertEquals(1, prediction.validityFlags().stream().filter(flag -> flag.startsWith("PHASE5_")).count(),
				"supersonic cells must report one explicit viscous-coupling status");
	}

	private static AerodynamicTable predict(List<BenchmarkCase> cases) {
		AeroGeometry geometry = a53d02Geometry();
		PerfectGasAir air = new PerfectGasAir();
		double pressurePa = 101325;
		double temperatureK = 288.15;
		AtmosphereState atmosphere = new AtmosphereState(pressurePa, temperatureK,
				pressurePa / (air.gasConstant() * temperatureK), air.viscosity(temperatureK));
		double[] mach = cases.stream().mapToDouble(BenchmarkCase::mach).toArray();
		TableMetadata metadata = new TableMetadata(TableMetadata.CURRENT_SCHEMA, geometry.geometryHash(),
				"a53d02-standard-atmosphere", "benchmark-test", "physics-aero-production",
				"SI;radians", "OPENROCKET_BODY_AXES_V1", Instant.parse("2026-01-01T00:00:00Z"),
				Map.of("sourceBodyLengthM", BODY_LENGTH_M, "sourceBodyDiameterM", BODY_DIAMETER_M),
				Map.of("sourceDigitizationCd", 0.025), "SOURCE_ACCEPTANCE");

		return new FullRegimeTableBuilder().build(geometry, mach, new double[] {0}, new double[] {0},
				atmosphere, air, metadata);
	}

	private static List<BenchmarkCase> firstBatch() throws IOException {
		Path caseFile = suiteRoot().resolve("data/pointwise_test_cases.json");
		try (Reader reader = Files.newBufferedReader(caseFile, StandardCharsets.UTF_8)) {
			PointwiseCaseFile file = new Gson().fromJson(reader, PointwiseCaseFile.class);
			if (file == null || file.cases() == null) {
				throw new IllegalStateException("missing cases in " + caseFile);
			}
			return file.cases().stream()
					.filter(c -> DATASET_ID.equals(c.datasetId()))
					.filter(c -> SERIES_ID.equals(c.seriesId()))
					.filter(c -> c.mach() <= FIRST_BATCH_MAX_MACH)
					.sorted(Comparator.comparingDouble(BenchmarkCase::mach))
					.toList();
		}
	}

	private static Path suiteRoot() {
		Path relativeToCore = Path.of("src/test/java/info/openrocket/core/correlation/rasaero_benchmark_suite");
		for (Path directory = Path.of("").toAbsolutePath(); directory != null; directory = directory.getParent()) {
			Path fromCoreProject = directory.resolve(relativeToCore);
			if (Files.isDirectory(fromCoreProject)) {
				return fromCoreProject;
			}
			Path fromRepositoryRoot = directory.resolve("core").resolve(relativeToCore);
			if (Files.isDirectory(fromRepositoryRoot)) {
				return fromRepositoryRoot;
			}
		}
		throw new IllegalStateException("cannot locate rasaero_benchmark_suite from "
				+ Path.of("").toAbsolutePath());
	}

	/**
	 * Figure 1 of NACA RM A53D02 supplies the dimensional fixture omitted by
	 * the normalized pointwise records.  The fin API accepts one thickness per
	 * set, so the source's constant t/c taper is represented at mean chord.
	 */
	private static AeroGeometry a53d02Geometry() {
		AxisymmetricProfile noseProfile = tangentOgiveProfile(64);
		AxisymmetricProfile tubeProfile = new AxisymmetricProfile(List.of(
				new GeometryStation(NOSE_LENGTH_M, BODY_RADIUS_M, 0, 0),
				new GeometryStation(BODY_LENGTH_M, BODY_RADIUS_M, 0, 0)),
				List.of(), "SOURCE_EXACT_CYLINDER", 1e-12);

		AeroComponent nose = bodyComponent("nose", "NOSE_OGIVE", 0, NOSE_LENGTH_M,
				0, BODY_RADIUS_M, noseProfile);
		AeroComponent tube = bodyComponent("tube", "CYLINDER", NOSE_LENGTH_M, BODY_LENGTH_M,
				BODY_RADIUS_M, BODY_RADIUS_M, tubeProfile);

		double finAreaM2 = 0.5 * (FIN_ROOT_CHORD_M + FIN_TIP_CHORD_M) * FIN_SPAN_M;
		List<GeometryStation> outline = List.of(
				new GeometryStation(0, 0, 0, 0),
				new GeometryStation(FIN_SWEEP_M, FIN_SPAN_M, 0, 0),
				new GeometryStation(FIN_ROOT_CHORD_M, FIN_SPAN_M, 0, 0),
				new GeometryStation(FIN_ROOT_CHORD_M, 0, 0, 0));
		FinGeometry finGeometry = new FinGeometry("TRAPEZOIDAL", "SINGLE_WEDGE", 4,
				FIN_ROOT_CHORD_M, FIN_SPAN_M, finAreaM2, 0, outline);
		double finStartM = BODY_LENGTH_M - FIN_ROOT_CHORD_M;
		double meanThicknessM = FIN_THICKNESS_RATIO * 0.5 * (FIN_ROOT_CHORD_M + FIN_TIP_CHORD_M);
		AeroComponent fins = new AeroComponent("fins", "/fins", "benchmark", "FIN_TRAPEZOIDAL",
				"stage", 2, new Coordinate(finStartM, 0, 0), finStartM, BODY_LENGTH_M, BODY_RADIUS_M,
				2 * finAreaM2 * finGeometry.count(), finAreaM2 * finGeometry.count(), 0, 0,
				"ADIABATIC", Map.of("thicknessM", meanThicknessM, "baseRotationRad", 0.0),
				List.of(), null, finGeometry, null);

		double referenceAreaM2 = Math.PI * BODY_RADIUS_M * BODY_RADIUS_M;
		Map<String, Double> wettedAreas = Map.of(
				"nose", noseProfile.wettedAreaM2(),
				"tube", tubeProfile.wettedAreaM2(),
				"fins", fins.wettedAreaM2());
		ReferenceGeometry references = new ReferenceGeometry(referenceAreaM2, referenceAreaM2,
				BODY_LENGTH_M, BODY_DIAMETER_M, wettedAreas, new Coordinate(), BODY_DIAMETER_M);
		return new AeroGeometry(List.of(nose, tube, fins), references, "naca-rm-a53d02-figure-1");
	}

	private static AxisymmetricProfile tangentOgiveProfile(int intervals) {
		double rho = (NOSE_LENGTH_M * NOSE_LENGTH_M + BODY_RADIUS_M * BODY_RADIUS_M)
				/ (2 * BODY_RADIUS_M);
		List<GeometryStation> stations = new ArrayList<>(intervals + 1);
		for (int i = 0; i <= intervals; i++) {
			double x = NOSE_LENGTH_M * i / intervals;
			double u = NOSE_LENGTH_M - x;
			double root = Math.sqrt(Math.max(0, rho * rho - u * u));
			double radius = Math.max(0, root + BODY_RADIUS_M - rho);
			double slope = u / root;
			double secondDerivative = -rho * rho / (root * root * root);
			stations.add(new GeometryStation(x, radius, slope, secondDerivative));
		}
		return new AxisymmetricProfile(stations, List.of(), "SOURCE_TANGENT_OGIVE", 1e-12);
	}

	private static AeroComponent bodyComponent(String id, String classification, double startM, double endM,
			double startRadiusM, double endRadiusM, AxisymmetricProfile profile) {
		double maxRadiusM = Math.max(startRadiusM, endRadiusM);
		return new AeroComponent(id, "/" + id, "benchmark", classification, "stage", 0,
				new Coordinate(startM, 0, 0), startM, endM, maxRadiusM, profile.wettedAreaM2(),
				2 * maxRadiusM * (endM - startM), Math.PI * endRadiusM * endRadiusM, 0,
				"ADIABATIC", Map.of(), List.of(), profile, null, null);
	}

	private record PointwiseCaseFile(List<BenchmarkCase> cases) { }

	private record BenchmarkCase(
			@SerializedName("dataset_id") String datasetId,
			@SerializedName("series_id") String seriesId,
			@SerializedName("case_id") String caseId,
			JsonObject inputs,
			JsonObject expected,
			JsonObject tolerance) {
		double mach() {
			return inputs.get("mach").getAsDouble();
		}

		double expectedCd() {
			return expected.get("CD_zero_lift").getAsDouble();
		}

		double absoluteTolerance() {
			return tolerance.get("coefficient_abs").getAsDouble();
		}
	}
}

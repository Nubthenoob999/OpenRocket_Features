package info.openrocket.core.tuning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import info.openrocket.core.aerodynamics.physicsaero.measurement.StandardAtmosphereBarometricAltimeter;
import info.openrocket.core.simulation.FlightData;
import info.openrocket.core.simulation.FlightDataBranch;
import info.openrocket.core.simulation.FlightDataType;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

class OfflineTableFlightComparisonHarnessTest {
	@Test
	void writesDeterministicPerFlightErrorsAndPassBands() throws Exception {
		OfflineTableFlightComparisonHarness.RunOutcome baseline =
				OfflineTableFlightComparisonHarness.RunOutcome.success(105.0, "BARROWMAN_ONLY", "TABLE_DISABLED", 0.8);
		OfflineTableFlightComparisonHarness.RunOutcome table =
				new OfflineTableFlightComparisonHarness.RunOutcome("TABLE_STRICT_SUCCESS", 94.0, 95.0,
						"TEST_SENSOR", "PHYSICS_AERO_STRICT", "TABLE_CACHE_IDENTITY", 0.8,
						120.0, 12.0, true, 100, 100, 0, "", "",
						OfflineTableFlightComparisonHarness.FailureSnapshot.EMPTY, "");
		OfflineTableFlightComparisonHarness.FlightComparison flight =
				OfflineTableFlightComparisonHarness.FlightComparison.complete(
						"example-flight", "truth.csv", "flight.ork", TelemetrySchema.EASYMINI_ALTIMETER,
						100.0, new OfflineTableFlightComparisonHarness.TruthTrajectoryMetrics(110.0, 13.0),
						baseline, table);
		OfflineTableFlightComparisonHarness.ComparisonReport report =
				new OfflineTableFlightComparisonHarness.ComparisonReport(List.of(flight));

		Path output = Files.createTempDirectory("offline-table-comparison");
		OfflineTableFlightComparisonHarness.writeReport(report, output);

		String csv = Files.readString(output.resolve(OfflineTableFlightComparisonHarness.CSV_FILE_NAME));
		String summary = Files.readString(output.resolve(OfflineTableFlightComparisonHarness.SUMMARY_FILE_NAME));
		assertTrue(csv.contains("5.000000"));
		assertTrue(csv.contains("-6.000000"));
		assertTrue(csv.contains("true"));
		assertTrue(csv.contains("table_first_failure_alpha_deg"));
		assertTrue(csv.contains("table_geometric_apogee_m"));
		assertTrue(csv.contains("table_measurement_method"));
		assertTrue(csv.contains("truth_peak_ascent_velocity_z_m_s"));
		assertTrue(csv.contains("table_time_to_apogee_s"));
		assertTrue(csv.contains("table_within_3_percent"));
		assertTrue(summary.contains("offline_table_within_3_percent=0/1"));
		assertTrue(summary.contains("offline_table_within_10_percent=1/1"));
		assertEquals(report.toCsv(), Files.readString(output.resolve(OfflineTableFlightComparisonHarness.CSV_FILE_NAME)));
	}

	@Test
	void hybridOrFallbackTableRunCannotBeReportedAsAStrictTableSuccess() {
		OfflineTableFlightComparisonHarness.RunOutcome baseline =
				OfflineTableFlightComparisonHarness.RunOutcome.success(100.0, "BARROWMAN_ONLY", "TABLE_DISABLED", 0.8);
		OfflineTableFlightComparisonHarness.RunOutcome hybrid =
				OfflineTableFlightComparisonHarness.RunOutcome.success(100.0,
						"PHYSICS_AERO_DIAGNOSTIC_HYBRID", "TABLE_CACHE_IDENTITY", 0.8);
		OfflineTableFlightComparisonHarness.FlightComparison comparison =
				OfflineTableFlightComparisonHarness.FlightComparison.complete(
						"hybrid-is-not-strict", "truth.csv", "flight.ork", TelemetrySchema.EASYMINI_ALTIMETER,
						100.0, new OfflineTableFlightComparisonHarness.TruthTrajectoryMetrics(Double.NaN, Double.NaN),
						baseline, hybrid);

		assertFalse(hybrid.isStrictTableSuccess());
		assertEquals("SIMULATION_FAILED", comparison.status());
		assertTrue(comparison.toString().contains("PHYSICS_AERO_DIAGNOSTIC_HYBRID"));
	}

	@Test
	void convertsSimulatedPressureHistoryToAltimeterIndicatedApogee() {
		FlightDataBranch branch = new FlightDataBranch("test",
				FlightDataType.TYPE_ALTITUDE, FlightDataType.TYPE_AIR_PRESSURE);
		double[] altitudeM = { 0.0, 1000.0, 900.0 };
		double[] pressurePa = { 101_325.0, 89_874.0, 91_000.0 };
		for (int index = 0; index < altitudeM.length; index++) {
			branch.addPoint();
			branch.setValue(FlightDataType.TYPE_ALTITUDE, altitudeM[index]);
			branch.setValue(FlightDataType.TYPE_AIR_PRESSURE, pressurePa[index]);
		}
		OfflineTableFlightComparisonHarness.MeasurementEstimate estimate =
				OfflineTableFlightComparisonHarness.measurementEstimate(
						TelemetrySchema.EASYMINI_ALTIMETER, new FlightData(branch));
		double expected = new StandardAtmosphereBarometricAltimeter()
				.heightAbovePadM(pressurePa[0], pressurePa[1]);

		assertEquals(expected, estimate.indicatedApogeeMeters(), 1.0e-9);
		assertEquals(1000.0, estimate.geometricApogeeMeters(), 0.0);
		assertEquals(StandardAtmosphereBarometricAltimeter.METHOD_ID, estimate.methodId());
	}

	@Test
	void comparesFluctusDeadReckoningAltitudeDirectlyToGeometricDisplacement() {
		FlightDataBranch branch = new FlightDataBranch("test",
				FlightDataType.TYPE_ALTITUDE, FlightDataType.TYPE_AIR_PRESSURE);
		for (double[] sample : new double[][] { { 0.0, 101_325.0 }, { 1000.0, 89_874.0 } }) {
			branch.addPoint();
			branch.setValue(FlightDataType.TYPE_ALTITUDE, sample[0]);
			branch.setValue(FlightDataType.TYPE_AIR_PRESSURE, sample[1]);
		}
		OfflineTableFlightComparisonHarness.MeasurementEstimate estimate =
				OfflineTableFlightComparisonHarness.measurementEstimate(
						TelemetrySchema.FLUCTUS_SEMICOLON, new FlightData(branch));

		assertEquals(1000.0, estimate.indicatedApogeeMeters(), 0.0);
		assertEquals(1000.0, estimate.geometricApogeeMeters(), 0.0);
		assertEquals("DIRECT_GEOMETRIC_COMPARISON_FLUCTUS_DEAD_RECKONING_ALTITUDE_V1",
				estimate.methodId());
	}

	@Test
	void bundledEasyMiniHeightMatchesItsRecordedPressureAltitudeChange() throws Exception {
		Path config = PhaseThreeTuningPaths.findDefaultConfig();
		assertTrue(config != null);
		TelemetrySeries series = TelemetryParsers.parse(config.getParent()
				.resolve("DOL/2025-05-04-serial-13376-flight-0016.csv"));
		int apogeeIndex = 0;
		for (int index = 1; index < series.size(); index++) {
			Double altitude = series.getAltitudeMetersAgl().get(index);
			if (altitude != null && altitude > series.getAltitudeMetersAgl().get(apogeeIndex)) {
				apogeeIndex = index;
			}
		}
		double recordedHeightChange = series.getAltitudeMetersAgl().get(apogeeIndex)
				- series.getAltitudeMetersAgl().get(0);
		double pressureAltitudeChange = new StandardAtmosphereBarometricAltimeter().heightAbovePadM(
				series.getPressurePa().get(0), series.getPressurePa().get(apogeeIndex));

		assertEquals(recordedHeightChange, pressureAltitudeChange, 0.75,
				"EasyMini pressure and exported height should share the standard-atmosphere transfer");
	}

	@Test
	void missingRequiredAirbrakeSurfaceCannotBeSilentlyScored() throws Exception {
		Path config = PhaseThreeTuningPaths.findDefaultConfig();
		assertTrue(config != null);
		PhaseThreeBatchRunConfig runConfig = new com.google.gson.Gson().fromJson(
				Files.readString(config), PhaseThreeBatchRunConfig.class);
		PhaseThreeBatchDatasetConfig dataset = runConfig.getDatasets().stream()
				.filter(value -> "dol_serial_vs_dolconfig_sim".equals(value.getName()))
				.findFirst().orElseThrow();
		Path configDirectory = config.getParent();
		Path tempDirectory = Files.createTempDirectory("offline-table-missing-airbrake-surface");
		String sourceCsv = dataset.getTruthCsv() == null
				? dataset.getReferenceCsv() : dataset.getTruthCsv();
		Path isolatedTruth = tempDirectory.resolve("truth.csv");
		Path isolatedOrk = tempDirectory.resolve("flight.ork");
		Files.copy(PhaseThreeNativeAirbrakesConfigurer.resolvePath(configDirectory, sourceCsv), isolatedTruth);
		Files.copy(PhaseThreeNativeAirbrakesConfigurer.resolvePath(configDirectory, dataset.getOrkPath()), isolatedOrk);
		Path argumentsFile = tempDirectory.resolve("airbrakes-plugin-args.json");
		Files.writeString(argumentsFile, "{\n"
				+ "  \"arguments\": [\n"
				+ "    \"--dataset\", \"{datasetName}\",\n"
				+ "    \"--telemetry\", \"{referenceCsv}\",\n"
				+ "    \"--candidate\", \"{candidateCsv}\",\n"
				+ "    \"--cfd-csv\", \"missing-pelicantor-force-surface.csv\",\n"
				+ "    \"--airbrake-area-in2\", \"22.3\",\n"
				+ "    \"--reference-length-ft\", \"890\",\n"
				+ "    \"--target-apogee-ft\", \"4600\",\n"
				+ "    \"--max-mach-for-deployment\", \"1.0\",\n"
				+ "    \"--apogee-tolerance-ft\", \"16.4\"\n"
				+ "  ]\n"
				+ "}\n");
		PhaseThreeBatchDatasetConfig missingSurfaceDataset =
				new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(Map.of(
						"name", dataset.getName(),
						"truthCsv", isolatedTruth.getFileName().toString(),
						"referenceCsv", isolatedTruth.getFileName().toString(),
						"candidateCsv", isolatedTruth.getFileName().toString(),
						"orkPath", isolatedOrk.getFileName().toString(),
						"airbrakeEnabled", true,
						"plugin", Map.of("enabled", true,
								"argumentsFile", argumentsFile.getFileName().toString(),
								"arguments", List.of()))), PhaseThreeBatchDatasetConfig.class);

		OfflineTableFlightComparisonHarness.FlightComparison comparison =
				OfflineTableFlightComparisonHarness.compareDataset(missingSurfaceDataset, tempDirectory);

		assertEquals("SIMULATION_FAILED", comparison.status());
		assertTrue(comparison.baseline().failure().contains("AIRBRAKE_CONFIGURATION_REQUIRED"));
		assertTrue(comparison.table().failure().contains("AIRBRAKE_CONFIGURATION_REQUIRED"));
	}

	@Test
	@Tag("offline-table-flight-comparison")
	void runsTheBundledFlightSuiteWhenExplicitlyRequested() throws Exception {
		Assumptions.assumeTrue(Boolean.getBoolean("offlineTableComparison.runFlightSuite"),
				"Run :core:offlineTableFlightComparison to execute every configured recorded flight");
		String configuredOutput = System.getProperty("offlineTableComparison.outputDir", "");
		Path output = configuredOutput.isBlank()
				? Files.createTempDirectory("offline-table-flight-suite")
				: Path.of(configuredOutput);
		OfflineTableFlightComparisonHarness.ComparisonReport report =
				OfflineTableFlightComparisonHarness.runDefault(output);
		assertTrue(!report.flights().isEmpty(), "Expected at least one configured recorded flight");
		assertTrue(Files.exists(output.resolve(OfflineTableFlightComparisonHarness.CSV_FILE_NAME)));
		assertTrue(Files.exists(output.resolve(OfflineTableFlightComparisonHarness.SUMMARY_FILE_NAME)));
	}
}

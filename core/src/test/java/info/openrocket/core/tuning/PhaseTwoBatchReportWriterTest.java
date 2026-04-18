package info.openrocket.core.tuning;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PhaseTwoBatchReportWriterTest {

	@Test
	void writesCompactTuningOverviewCsv(@TempDir Path tempDir) throws IOException {
		Map<FlightPhaseWindow, PhaseTwoScoreResult> scores = new EnumMap<>(FlightPhaseWindow.class);
		scores.put(FlightPhaseWindow.FULL, new PhaseTwoScoreResult(
				96.5,
				ScoreSeverity.OK,
				Map.of("velocityZ", 96.5, "altitude", 98.0)));
		scores.put(FlightPhaseWindow.BOOST, new PhaseTwoScoreResult(
				95.0,
				ScoreSeverity.OK,
				Map.of("velocityZ", 95.0)));
		scores.put(FlightPhaseWindow.COAST, new PhaseTwoScoreResult(
				94.0,
				ScoreSeverity.OK,
				Map.of("velocityZ", 94.0)));

		DerivedTelemetryQuantities.Quantities reference = new DerivedTelemetryQuantities.Quantities(
				10.0, 50.0, 5.0, 12.0, 0.08, 1000.0, 12.0, 0.1, 2.2, 12.0);
		DerivedTelemetryQuantities.Quantities candidate = new DerivedTelemetryQuantities.Quantities(
				11.0, 52.0, 4.5, 11.0, 0.07, 990.0, 11.8, 0.1, 2.1, 11.9);

		PhaseTwoDatasetResult dataset = new PhaseTwoDatasetResult(
				"Example Flight",
				false,
				new AbPluginExecutionResult(AbPluginExecutionResult.Status.SKIPPED, 0, "unit-test"),
				scores,
				List.of(new TuningFlag("velocityZ", "rom.drag.force-balance", ScoreSeverity.WARNING, 88.0)),
				reference,
				candidate,
				TelemetryParserDiagnostics.EMPTY,
				TelemetryParserDiagnostics.EMPTY,
				"ROM_USABLE",
				"reference.csv",
				"CURRENT_SIMULATION_RERUN",
				"example.ork",
				"FOUR_D",
				"FOUR_D_ACTIVE",
				1.72,
				"altitude",
				0.03,
				0.98,
				12.0,
				11.9,
				-0.1,
				0.1,
				Map.of(),
				VerticalIntegratorDiagnostics.EMPTY);

		PhaseTwoBatchReportWriter.write(new PhaseTwoBatchResult(List.of(dataset)), tempDir);

		Path overview = tempDir.resolve("phase-three-tuning-overview.csv");
		Path summary = tempDir.resolve("phase-two-summary.csv");
		Path analysis = tempDir.resolve("phase-three-analysis.csv");
		Path aggregates = tempDir.resolve("phase-three-aggregate-metrics.csv");
		String csv = Files.readString(overview);
		String summaryCsv = Files.readString(summary);
		String analysisCsv = Files.readString(analysis);
		String aggregateCsv = Files.readString(aggregates);

		assertTrue(Files.exists(overview));
		assertTrue(Files.exists(summary));
		assertTrue(Files.exists(analysis));
		assertTrue(Files.exists(aggregates));
		assertTrue(csv.contains("dataset,datasetClass,romMode,romSurfaceSource"));
		assertTrue(csv.contains("apogeeSignedDeltaM"));
		assertTrue(csv.contains("effectiveApogeeTimeErrorSec"));
		assertTrue(csv.contains("Example Flight"));
		assertTrue(csv.contains("CURRENT_SIMULATION_RERUN"));
		assertTrue(csv.contains("velocityZ"));
		assertTrue(summaryCsv.contains("apogeeSignedDeltaM"));
		assertTrue(summaryCsv.contains("effectiveApogeeTimeDeltaSec"));
		assertTrue(analysisCsv.contains("rawApogeeTimeDeltaSec"));
		assertTrue(analysisCsv.contains("effectiveApogeeTimeErrorSec"));
		assertTrue(aggregateCsv.contains("scope,datasetCount,apogeeMeanSignedDeltaM"));
		assertTrue(aggregateCsv.contains("\"usable\",1"));
	}
}

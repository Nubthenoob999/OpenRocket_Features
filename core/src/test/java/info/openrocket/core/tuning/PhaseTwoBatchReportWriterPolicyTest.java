package info.openrocket.core.tuning;

import info.openrocket.core.util.BaseTestCase;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PhaseTwoBatchReportWriterPolicyTest extends BaseTestCase {
	@Test
	public void skippedPluginWithOkScoreIsNotFailure() throws Exception {
		Map<FlightPhaseWindow, PhaseTwoScoreResult> scores = new EnumMap<>(FlightPhaseWindow.class);
		PhaseTwoScoreResult full = new PhaseTwoScoreResult(
				100.0,
				ScoreSeverity.OK,
				Map.of("velocityZ", 100.0),
				1.0,
				100,
				100,
				null,
				null);
		scores.put(FlightPhaseWindow.FULL, full);

		PhaseTwoDatasetResult dataset = new PhaseTwoDatasetResult(
				"plugin_skipped_ok",
				false,
				new AbPluginExecutionResult(AbPluginExecutionResult.Status.SKIPPED, 0, "Plugin disabled for dataset"),
				scores,
				List.of(),
				new DerivedTelemetryQuantities.Quantities(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN),
				new DerivedTelemetryQuantities.Quantities(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN),
				TelemetryParserDiagnostics.EMPTY,
				TelemetryParserDiagnostics.EMPTY);

		Path out = Files.createTempDirectory("phase-two-report-policy");
		PhaseTwoBatchReportWriter.write(new PhaseTwoBatchResult(List.of(dataset)), out);

		String summary = Files.readString(out.resolve("phase-two-summary.csv"));
		assertTrue(summary.contains("ascentLaneStatus"), "summary should include ascent lane columns");
		assertTrue(summary.contains("reliabilityLaneStatus"), "summary should include reliability lane columns");
		assertTrue(summary.contains("\"\",\"\""), "failureReason and insufficientDataReason should be blank for passing dataset");

		String junit = Files.readString(out.resolve("phase-two-junit.xml"));
		assertTrue(junit.contains("failures=\"0\""), "Skipped plugin with OK score should not be reported as failure");
		assertFalse(junit.contains("<failure"), "No failure node expected for passing dataset");
	}

	@Test
	public void brokenDatasetIsExcludedFromAscentLaneAndFailsReliabilityLane() throws Exception {
		Map<FlightPhaseWindow, PhaseTwoScoreResult> scores = new EnumMap<>(FlightPhaseWindow.class);
		scores.put(FlightPhaseWindow.FULL, new PhaseTwoScoreResult(
				95.0,
				ScoreSeverity.OK,
				Map.of("velocityZ", 95.0),
				1.0,
				100,
				100,
				null,
				null));
		scores.put(FlightPhaseWindow.BOOST, new PhaseTwoScoreResult(92.0, ScoreSeverity.OK, Map.of("velocityZ", 92.0)));
		scores.put(FlightPhaseWindow.COAST, new PhaseTwoScoreResult(94.0, ScoreSeverity.OK, Map.of("velocityZ", 94.0)));

		PhaseTwoDatasetResult dataset = new PhaseTwoDatasetResult(
				"broken_lane_dataset",
				true,
				new AbPluginExecutionResult(AbPluginExecutionResult.Status.SKIPPED, 0, "Plugin disabled for dataset"),
				scores,
				List.of(),
				new DerivedTelemetryQuantities.Quantities(0.0, 0.0, 0.0, 0.0, 0.1, 1000.0, 12.0),
				new DerivedTelemetryQuantities.Quantities(0.0, 0.0, 0.0, 0.0, 0.1, 995.0, 12.1),
				TelemetryParserDiagnostics.EMPTY,
				TelemetryParserDiagnostics.EMPTY,
				"BROKEN",
				"IMU",
				"ORK_FALLBACK_TO_CSV",
				"",
				"FOUR_D",
				"FOUR_D_ACTIVE",
				0.45,
				"altitude",
				0.0,
				1.0,
				12.0,
				12.1,
				0.1,
				0.1,
				Map.of(),
				VerticalIntegratorDiagnostics.EMPTY);

		Path out = Files.createTempDirectory("phase-two-report-policy-broken");
		PhaseTwoBatchReportWriter.write(new PhaseTwoBatchResult(List.of(dataset)), out);

		String summary = Files.readString(out.resolve("phase-two-summary.csv"));
		assertTrue(summary.contains(",EXCLUDED,\"datasetClass=BROKEN\",FAIL,\""),
				"BROKEN datasets should be excluded from ascent lane and fail reliability lane");
		assertTrue(summary.contains("datasetClass=BROKEN"), "Reliability failure reason should mention BROKEN dataset class");
	}
}

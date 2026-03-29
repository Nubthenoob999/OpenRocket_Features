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
		assertTrue(summary.contains("\"\",\"\""), "failureReason and insufficientDataReason should be blank for passing dataset");

		String junit = Files.readString(out.resolve("phase-two-junit.xml"));
		assertTrue(junit.contains("failures=\"0\""), "Skipped plugin with OK score should not be reported as failure");
		assertFalse(junit.contains("<failure"), "No failure node expected for passing dataset");
	}
}

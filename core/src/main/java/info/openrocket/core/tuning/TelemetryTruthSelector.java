package info.openrocket.core.tuning;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class TelemetryTruthSelector {
	private TelemetryTruthSelector() {
	}

	public static TruthSelection select(Path datasetDir) throws IOException {
		if (datasetDir == null || !Files.isDirectory(datasetDir)) {
			throw new IllegalArgumentException("Dataset directory not found: " + datasetDir);
		}

		List<TruthCandidate> candidates = new ArrayList<>();
		try (var files = Files.list(datasetDir)) {
			files.filter(Files::isRegularFile)
					.filter(path -> path.getFileName().toString().toLowerCase(Locale.US).endsWith(".csv"))
					.filter(TelemetryTruthSelector::isTelemetryCandidate)
					.forEach(path -> {
						try {
							TelemetrySeries series = TelemetryParsers.parse(path);
							candidates.add(new TruthCandidate(path, series));
						} catch (Exception ignored) {
							// Ignore non-telemetry or unparseable CSV files.
						}
					});
		}

		if (candidates.isEmpty()) {
			throw new IllegalArgumentException("No telemetry truth CSV candidates found in " + datasetDir);
		}

		TruthCandidate best = candidates.stream()
				.sorted(Comparator
						.comparingInt(TruthCandidate::priority).reversed()
						.thenComparingDouble(TruthCandidate::dropRate)
						.thenComparing(Comparator.comparingInt(TruthCandidate::usableChannelCount).reversed())
						.thenComparing(Comparator.comparingInt(TruthCandidate::acceptedRows).reversed())
						.thenComparing(candidate -> candidate.path().getFileName().toString()))
				.findFirst()
				.orElseThrow();
		return new TruthSelection(best.path(), best.series());
	}

	private static boolean isTelemetryCandidate(Path path) {
		String name = path.getFileName().toString().toLowerCase(Locale.US);
		if (name.startsWith("phase-two-") || name.startsWith("phase-three-")) {
			return false;
		}
		return !name.contains("drag");
	}

	private record TruthCandidate(Path path, TelemetrySeries series) {
		private int priority() {
			TelemetrySchema schema = series.getSchema();
			if (schema == TelemetrySchema.FLUCTUS_SEMICOLON
					|| schema == TelemetrySchema.EASYMINI_ALTIMETER
					|| schema == TelemetrySchema.STRATOLOGGER_COMMA
					|| schema == TelemetrySchema.TAB_DELIMITED_ALTIMETER) {
				return 2;
			}
			if (schema == TelemetrySchema.AB_EXTENDED) {
				return 1;
			}
			return 0;
		}

		private double dropRate() {
			TelemetryParserDiagnostics diagnostics = series.getParserDiagnostics();
			if (diagnostics.getRowsRead() <= 0) {
				return 1.0;
			}
			return (double) diagnostics.getDroppedSamples() / (double) diagnostics.getRowsRead();
		}

		private int usableChannelCount() {
			int count = 0;
			if (series.hasAltitude()) {
				count++;
			}
			if (series.hasVelocityZ()) {
				count++;
			}
			if (series.hasPressure()) {
				count++;
			}
			if (series.hasTemperature()) {
				count++;
			}
			if (series.hasAccelerationXYZ()) {
				count++;
			}
			return count;
		}

		private int acceptedRows() {
			return series.getParserDiagnostics().getRowsAccepted();
		}
	}

	public record TruthSelection(Path path, TelemetrySeries series) {
	}
}

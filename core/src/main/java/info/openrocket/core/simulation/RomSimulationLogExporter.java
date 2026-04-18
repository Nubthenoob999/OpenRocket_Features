package info.openrocket.core.simulation;

import info.openrocket.core.aerodynamics.RomAerodynamicCalculator;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.preferences.ApplicationPreferences;
import info.openrocket.core.startup.Application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Writes Phase I ROM simulation trace files for confidence and fallback diagnostics.
 */
public final class RomSimulationLogExporter {

	private static final Logger log = LoggerFactory.getLogger(RomSimulationLogExporter.class);
	private static final DateTimeFormatter FILE_TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
	private static final DateTimeFormatter VALUE_TS_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

	private RomSimulationLogExporter() {
	}

	public static void exportIfAvailable(Simulation simulation, SimulationOptions beforeOptions,
			SimulationOptions afterOptions, RomAerodynamicCalculator romCalculator) {
		if (simulation == null || beforeOptions == null || afterOptions == null || romCalculator == null
				|| !romCalculator.isEnabled()) {
			return;
		}

		List<RomAerodynamicCalculator.RomComputationSnapshot> snapshots = romCalculator.getComputationSnapshots();
		if (snapshots.isEmpty()) {
			return;
		}

		long fallbackSamples = snapshots.stream()
				.filter(snapshot -> snapshot.getFallbackWeight() > 1e-6)
				.count();
		long lowConfidenceSamples = snapshots.stream()
				.filter(snapshot -> snapshot.getConfidence() < 0.5)
				.count();

		try {
			Path outputDirectory = resolveOutputDirectory();
			Files.createDirectories(outputDirectory);

			String timestamp = LocalDateTime.now().format(FILE_TS_FORMAT);
			String baseName = sanitizeFilePart(simulation.getName());
			if (baseName.isBlank()) {
				baseName = "simulation";
			}

			Path summaryFile = outputDirectory.resolve(baseName + "_" + timestamp + "_rom_summary.log");
			Path traceFile = outputDirectory.resolve(baseName + "_" + timestamp + "_rom_trace.csv");

			writeSummary(summaryFile, simulation, beforeOptions, afterOptions, snapshots.size(),
					fallbackSamples, lowConfidenceSamples);
			writeTrace(traceFile, snapshots);

			log.info("ROM simulation logs exported: summary={} trace={}", summaryFile, traceFile);
		} catch (IOException ex) {
			log.warn("Unable to export ROM simulation logs", ex);
		}
	}

	private static Path resolveOutputDirectory() {
		ApplicationPreferences preferences = Application.getPreferences();
		Path baseDirectory;
		if (preferences.getDefaultDirectory() != null) {
			baseDirectory = preferences.getDefaultDirectory().toPath();
		} else {
			baseDirectory = Path.of(System.getProperty("user.home"));
		}
		return baseDirectory.resolve("openrocket-rom-logs");
	}

	private static void writeSummary(Path summaryFile,
			Simulation simulation,
			SimulationOptions beforeOptions,
			SimulationOptions afterOptions,
			int sampleCount,
			long fallbackSamples,
			long lowConfidenceSamples) throws IOException {
		StringBuilder out = new StringBuilder(512);
		out.append("timestamp=")
				.append(LocalDateTime.now().format(VALUE_TS_FORMAT))
				.append('\n');
		out.append("simulationName=").append(sanitizeValue(simulation.getName())).append('\n');
		out.append("simulationStatus=").append(simulation.getStatus().name()).append('\n');
		out.append("before.romEnabled=").append(beforeOptions.isRomEnabled()).append('\n');
		out.append("before.romMode=").append(beforeOptions.getRomMode()).append('\n');
		out.append("before.romFallbackMode=").append(beforeOptions.getRomFallbackMode()).append('\n');
		out.append("after.romEnabled=").append(afterOptions.isRomEnabled()).append('\n');
		out.append("after.romMode=").append(afterOptions.getRomMode()).append('\n');
		out.append("after.romFallbackMode=").append(afterOptions.getRomFallbackMode()).append('\n');
		out.append("samples=").append(sampleCount).append('\n');
		out.append("fallbackSamples=").append(fallbackSamples).append('\n');
		out.append("lowConfidenceSamples=").append(lowConfidenceSamples).append('\n');
		Files.writeString(summaryFile, out.toString(), StandardCharsets.UTF_8);
	}

	private static void writeTrace(Path traceFile,
			List<RomAerodynamicCalculator.RomComputationSnapshot> snapshots) throws IOException {
		StringBuilder out = new StringBuilder(Math.max(2048, snapshots.size() * 128));
		out.append("time_s,mach,re_l,alpha_deg,beta_deg,regime,seeds,confidence,fallback_weight,separation_fraction,cd_legacy,cd_rom,cd_final,cn_legacy,cn_rom,cn_final,cm_legacy,cm_rom,cm_final,notes\n");
		for (RomAerodynamicCalculator.RomComputationSnapshot snapshot : snapshots) {
			out.append(format(snapshot.getTimeSeconds())).append(',')
					.append(format(snapshot.getMach())).append(',')
					.append(format(snapshot.getReynoldsLength())).append(',')
					.append(format(snapshot.getAlphaDeg())).append(',')
					.append(format(snapshot.getBetaDeg())).append(',')
					.append(csv(snapshot.getRegime())).append(',')
					.append(snapshot.getSeedCount()).append(',')
					.append(format(snapshot.getConfidence())).append(',')
					.append(format(snapshot.getFallbackWeight())).append(',')
					.append(format(snapshot.getSeparationFraction())).append(',')
					.append(format(snapshot.getCdLegacy())).append(',')
					.append(format(snapshot.getCdRom())).append(',')
					.append(format(snapshot.getCdFinal())).append(',')
					.append(format(snapshot.getCnLegacy())).append(',')
					.append(format(snapshot.getCnRom())).append(',')
					.append(format(snapshot.getCnFinal())).append(',')
					.append(format(snapshot.getCmLegacy())).append(',')
					.append(format(snapshot.getCmRom())).append(',')
					.append(format(snapshot.getCmFinal())).append(',')
					.append(csv(snapshot.getNotes())).append('\n');
		}
		Files.writeString(traceFile, out.toString(), StandardCharsets.UTF_8);
	}

	private static String format(double value) {
		if (Double.isNaN(value) || Double.isInfinite(value)) {
			return "";
		}
		return String.format(Locale.US, "%.10f", value);
	}

	private static String format(boolean value) {
		return value ? "1" : "0";
	}

	private static String sanitizeFilePart(String value) {
		if (value == null) {
			return "";
		}
		String cleaned = value.replaceAll("[^A-Za-z0-9._-]+", "_");
		while (cleaned.contains("__")) {
			cleaned = cleaned.replace("__", "_");
		}
		return cleaned;
	}

	private static String sanitizeValue(String value) {
		if (value == null) {
			return "";
		}
		return value.replace('\n', ' ').replace('\r', ' ');
	}

	private static String csv(String value) {
		if (value == null) {
			return "";
		}
		String sanitized = value.replace('\n', ' ').replace('\r', ' ');
		if (sanitized.indexOf(',') < 0 && sanitized.indexOf('"') < 0) {
			return sanitized;
		}
		return '"' + sanitized.replace("\"", "\"\"") + '"';
	}
}

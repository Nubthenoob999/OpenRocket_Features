package info.openrocket.core.simulation;

import info.openrocket.core.aerodynamics.RomAerodynamicCalculator;
import info.openrocket.core.aerodynamics.rom.DragSurface;
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
 * Writes ROM simulation trace files for before/after Cd values.
 */
public final class RomSimulationLogExporter {

	private static final Logger log = LoggerFactory.getLogger(RomSimulationLogExporter.class);
	private static final DateTimeFormatter FILE_TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
	private static final DateTimeFormatter VALUE_TS_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

	private RomSimulationLogExporter() {
	}

	public static void exportIfAvailable(Simulation simulation, SimulationOptions beforeOptions,
			SimulationOptions afterOptions, RomAerodynamicCalculator romCalculator) {
		if (simulation == null || beforeOptions == null || afterOptions == null || romCalculator == null || !romCalculator.hasSurface()) {
			return;
		}

		List<RomAerodynamicCalculator.RomComputationSnapshot> snapshots = romCalculator.getComputationSnapshots();
		if (snapshots.isEmpty()) {
			return;
		}

		DragSurface beforeSurface = beforeOptions.getRomDragSurface();
		DragSurface afterSurface = afterOptions.getRomDragSurface();
		long outsideDomainSamples = snapshots.stream()
				.filter(RomAerodynamicCalculator.RomComputationSnapshot::isOutsideDomain)
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

			writeSummary(summaryFile, simulation, beforeOptions, afterOptions, beforeSurface, afterSurface,
					snapshots.size(), outsideDomainSamples);
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
			DragSurface beforeSurface,
			DragSurface afterSurface,
			int sampleCount,
			long outsideDomainSamples) throws IOException {
		StringBuilder out = new StringBuilder(512);
		out.append("timestamp=")
				.append(LocalDateTime.now().format(VALUE_TS_FORMAT))
				.append('\n');
		out.append("simulationName=").append(sanitizeValue(simulation.getName())).append('\n');
		out.append("simulationStatus=").append(simulation.getStatus().name()).append('\n');
		out.append("before.hasRomSurface=").append(beforeOptions.hasRomDragSurface()).append('\n');
		out.append("after.hasRomSurface=").append(afterOptions.hasRomDragSurface()).append('\n');
		out.append("before.geometryHash=").append(surfaceHash(beforeSurface)).append('\n');
		out.append("after.geometryHash=").append(surfaceHash(afterSurface)).append('\n');
		out.append("samples=").append(sampleCount).append('\n');
		out.append("outsideDomainSamples=").append(outsideDomainSamples).append('\n');
		Files.writeString(summaryFile, out.toString(), StandardCharsets.UTF_8);
	}

	private static void writeTrace(Path traceFile,
			List<RomAerodynamicCalculator.RomComputationSnapshot> snapshots) throws IOException {
		StringBuilder out = new StringBuilder(Math.max(2048, snapshots.size() * 128));
		out.append("time_s,mach,re_l,alpha_deg,theta_query_deg,plume_state,blend_weight,query_mach,query_re_l,query_alpha_deg,query_beta_deg,mach_clamped,re_clamped,alpha_clamped,beta_clamped,cd_before,cd_after,cd_plume_off,cd_plume_on\n");
		for (RomAerodynamicCalculator.RomComputationSnapshot snapshot : snapshots) {
			out.append(format(snapshot.getTimeSeconds())).append(',')
					.append(format(snapshot.getMach())).append(',')
					.append(format(snapshot.getReynoldsLength())).append(',')
					.append(format(snapshot.getAlphaDeg())).append(',')
					.append(format(snapshot.getThetaQueryDeg())).append(',')
					.append(format(snapshot.getPlumeState())).append(',')
					.append(format(snapshot.getBlendWeight())).append(',')
					.append(format(snapshot.getQueryMach())).append(',')
					.append(format(snapshot.getQueryReynoldsLength())).append(',')
					.append(format(snapshot.getQueryAlphaDeg())).append(',')
					.append(format(snapshot.getQueryBetaDeg())).append(',')
					.append(format(snapshot.isMachClamped())).append(',')
					.append(format(snapshot.isReynoldsClamped())).append(',')
					.append(format(snapshot.isAlphaClamped())).append(',')
					.append(format(snapshot.isBetaClamped())).append(',')
					.append(format(snapshot.getCdBefore())).append(',')
					.append(format(snapshot.getCdAfter())).append(',')
					.append(format(snapshot.getCdPlumeOff())).append(',')
					.append(format(snapshot.getCdPlumeOn())).append('\n');
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

	private static String surfaceHash(DragSurface surface) {
		if (surface == null || surface.geometryHash == null) {
			return "";
		}
		return surface.geometryHash;
	}
}

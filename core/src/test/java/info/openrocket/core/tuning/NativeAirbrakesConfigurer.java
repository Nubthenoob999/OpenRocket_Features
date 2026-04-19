package info.openrocket.core.tuning;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import info.openrocket.core.aerodynamics.rom.RomFallbackMode;
import info.openrocket.core.aerodynamics.rom.RomMode;
import info.openrocket.core.aerodynamics.rom.RomSurfaceMode;
import info.openrocket.core.simulation.SimulationOptions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

final class NativeAirbrakesConfigurer {
	private static final Gson GSON = new Gson();
	private static final double SQUARE_INCH_TO_SQUARE_METER = 0.00064516;
	private static final double FOOT_TO_METER = 0.3048;

	private NativeAirbrakesConfigurer() {
	}

	static AbPluginExecutionResult configure(PhaseTwoDatasetConfig dataset,
											 Path configDir,
											 SimulationOptions options) throws IOException {
		if (options == null) {
			return new AbPluginExecutionResult(AbPluginExecutionResult.Status.FAILED, -1,
					"Native airbrakes configuration failed: simulation options were unavailable");
		}

		forcePathlineRuntime(options, null);

		if (!dataset.isAirbrakeEnabled()) {
			options.setAirbrakesEnabled(false);
			return new AbPluginExecutionResult(AbPluginExecutionResult.Status.SKIPPED, 0,
					"Native airbrakes disabled for dataset");
		}

		options.setAirbrakesEnabled(true);
		List<String> notes = new ArrayList<>();
		List<String> arguments = resolveArguments(dataset, configDir, notes);
		try {
			applyArguments(arguments, dataset, configDir, options, notes);
		} catch (IllegalArgumentException ex) {
			options.setAirbrakesEnabled(false);
			return autoDisabledResult(notes, ex.getMessage());
		}

		Path resolvedCfd = resolveCfdPath(configDir, dataset, options.getCfdDataFilePath(), notes);
		if (resolvedCfd == null || !Files.exists(resolvedCfd)) {
			options.setAirbrakesEnabled(false);
			return autoDisabledResult(notes, "no CFD CSV could be resolved");
		}
		options.setCfdDataFilePath(resolvedCfd.toAbsolutePath().normalize().toString());

		StringBuilder message = new StringBuilder("Native airbrakes configured in-process");
		if (arguments.isEmpty()) {
			message.append(" using ORK settings");
		} else {
			message.append(" using ORK settings with legacy argument overrides");
		}
		message.append(" (CFD=").append(resolvedCfd.getFileName()).append(')');
		if (!notes.isEmpty()) {
			message.append("; ").append(String.join("; ", notes));
		}

		return new AbPluginExecutionResult(AbPluginExecutionResult.Status.SUCCEEDED, 0, message.toString());
	}

	static void forcePathlineRuntime(SimulationOptions options, List<String> notes) {
		if (options == null) {
			return;
		}

		options.setRomEnabled(true);
		options.setRomMode(RomMode.STANDARD);
		options.setRomFallbackMode(RomFallbackMode.FORCE_ROM);
		options.setRomSurfaceMode(RomSurfaceMode.THREE_D);
		options.setRomDragSurface(null);
		options.setRomAeroSurface4D(null);

		if (notes != null) {
			notes.add("forced pathline ROM runtime");
			notes.add("romMode=STANDARD");
			notes.add("romSurfaceMode=THREE_D");
			notes.add("legacy ROM surfaces cleared");
			notes.add("fallback=FORCE_ROM");
		}
	}

	private static AbPluginExecutionResult autoDisabledResult(List<String> notes, String reason) {
		StringBuilder message = new StringBuilder("Native airbrakes auto-disabled");
		if (reason != null && !reason.isBlank()) {
			message.append(": ").append(reason);
		}
		if (!notes.isEmpty()) {
			message.append("; ").append(String.join("; ", notes));
		}
		return new AbPluginExecutionResult(AbPluginExecutionResult.Status.SKIPPED, 0, message.toString());
	}

	private static List<String> resolveArguments(PhaseTwoDatasetConfig dataset,
												 Path configDir,
												 List<String> notes) throws IOException {
		List<String> resolved = new ArrayList<>();
		PhaseTwoDatasetConfig.PluginConfig plugin = dataset.getPlugin();
		if (plugin == null) {
			return resolved;
		}

		if (plugin.getArgumentsFile() != null && !plugin.getArgumentsFile().isBlank()) {
			Path argumentsPath = resolvePath(configDir, plugin.getArgumentsFile());
			if (Files.exists(argumentsPath)) {
				resolved.addAll(readArgumentsFromJson(argumentsPath));
			} else {
				notes.add("arguments file missing, kept ORK airbrake settings");
			}
		}

		if (plugin.getArguments() != null) {
			for (String arg : plugin.getArguments()) {
				if (arg != null && !arg.isBlank()) {
					resolved.add(expandArgument(arg, dataset, configDir));
				}
			}
		}
		return resolved;
	}

	private static List<String> readArgumentsFromJson(Path jsonPath) throws IOException {
		String content = Files.readString(jsonPath, StandardCharsets.UTF_8);
		JsonObject root = GSON.fromJson(content, JsonObject.class);
		if (root == null) {
			return List.of();
		}
		JsonArray arguments = root.getAsJsonArray("arguments");
		if (arguments == null) {
			return List.of();
		}
		List<String> out = new ArrayList<>();
		for (JsonElement element : arguments) {
			if (element == null || element.isJsonNull()) {
				continue;
			}
			out.add(element.getAsString());
		}
		return out;
	}

	private static void applyArguments(List<String> arguments,
									   PhaseTwoDatasetConfig dataset,
									   Path configDir,
									   SimulationOptions options,
									   List<String> notes) {
		for (int i = 0; i < arguments.size(); i++) {
			String flag = arguments.get(i);
			if (flag == null || flag.isBlank()) {
				continue;
			}
			String value = nextValue(arguments, i);
			switch (flag) {
				case "--cfd-csv" -> {
					if (value != null && !value.isBlank()) {
						Path resolved = resolveCfdPath(configDir, dataset, value, notes);
						if (resolved != null && Files.exists(resolved)) {
							options.setCfdDataFilePath(resolved.toAbsolutePath().normalize().toString());
						} else {
							notes.add("legacy CFD path missing, kept ORK CFD setting");
						}
						i++;
					}
				}
				case "--airbrake-area-in2" -> {
					options.setReferenceArea(parseDouble(flag, value) * SQUARE_INCH_TO_SQUARE_METER);
					i++;
				}
				case "--reference-length-ft" -> {
					options.setReferenceLength(parseDouble(flag, value) * FOOT_TO_METER);
					i++;
				}
				case "--target-apogee-ft" -> {
					options.setTargetApogee(parseDouble(flag, value) * FOOT_TO_METER);
					i++;
				}
				case "--max-mach-for-deployment" -> {
					options.setMaxMachForDeployment(parseDouble(flag, value));
					i++;
				}
				case "--apogee-tolerance-ft" -> {
					options.setApogeeToleranceMeters(parseDouble(flag, value) * FOOT_TO_METER);
					i++;
				}
				case "--burnout-only-deployment" -> {
					options.setDeployAfterBurnoutOnly(parseBoolean(flag, value));
					i++;
				}
				case "--delay-after-burnout-s" -> {
					options.setDeployAfterBurnoutDelayS(parseDouble(flag, value));
					i++;
				}
				case "--always-open-mode" -> {
					options.setAlwaysOpenMode(parseBoolean(flag, value));
					i++;
				}
				case "--always-open-percentage" -> {
					options.setAlwaysOpenPercentage(parseDouble(flag, value));
					i++;
				}
				case "--debug-enabled" -> {
					options.setDebugEnabled(parseBoolean(flag, value));
					i++;
				}
				case "--dbg-always-open" -> {
					options.setDbgAlwaysOpen(parseBoolean(flag, value));
					i++;
				}
				case "--dbg-forced-deploy-frac" -> {
					options.setDbgForcedDeployFrac(parseDouble(flag, value));
					i++;
				}
				case "--dbg-trace-predictor" -> {
					options.setDbgTracePredictor(parseBoolean(flag, value));
					i++;
				}
				case "--dbg-trace-controller" -> {
					options.setDbgTraceController(parseBoolean(flag, value));
					i++;
				}
				default -> {
					if (value != null) {
						i++;
					}
				}
			}
		}
	}

	private static String nextValue(List<String> arguments, int index) {
		int nextIndex = index + 1;
		if (nextIndex >= arguments.size()) {
			return null;
		}
		String next = arguments.get(nextIndex);
		if (next == null || next.isBlank()) {
			return null;
		}
		return next;
	}

	private static double parseDouble(String flag, String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Missing value for " + flag);
		}
		try {
			return Double.parseDouble(value.trim());
		} catch (NumberFormatException ex) {
			throw new IllegalArgumentException("Invalid numeric value for " + flag + ": " + value, ex);
		}
	}

	private static boolean parseBoolean(String flag, String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Missing value for " + flag);
		}
		String normalized = value.trim().toLowerCase(Locale.ROOT);
		if ("true".equals(normalized)) {
			return true;
		}
		if ("false".equals(normalized)) {
			return false;
		}
		throw new IllegalArgumentException("Invalid boolean value for " + flag + ": " + value);
	}

	private static String expandArgument(String argument, PhaseTwoDatasetConfig dataset, Path configDir) {
		String value = argument;
		value = value.replace("{datasetName}", dataset.getName() == null ? "" : dataset.getName());
		value = value.replace("{referenceCsv}", resolveDatasetValuePath(configDir, dataset.getReferenceCsv()));
		value = value.replace("{candidateCsv}", resolveDatasetValuePath(configDir, dataset.getCandidateCsv()));
		value = value.replace("{orkPath}", resolveDatasetValuePath(configDir, dataset.getOrkPath()));
		value = value.replace("{datasetDir}", resolveDatasetDir(configDir, dataset));
		return value;
	}

	private static String resolveDatasetDir(Path configDir, PhaseTwoDatasetConfig dataset) {
		String source = dataset.getOrkPath();
		if (source == null || source.isBlank()) {
			source = dataset.getTruthCsv();
		}
		if (source == null || source.isBlank()) {
			source = dataset.getReferenceCsv();
		}
		if (source == null || source.isBlank()) {
			source = dataset.getCandidateCsv();
		}
		if (source == null || source.isBlank()) {
			return "";
		}
		Path parent = resolvePath(configDir, source).getParent();
		return parent == null ? "" : parent.toAbsolutePath().normalize().toString().replace('\\', '/');
	}

	private static String resolveDatasetValuePath(Path configDir, String value) {
		if (value == null || value.isBlank()) {
			return "";
		}
		return resolvePath(configDir, value).toAbsolutePath().normalize().toString();
	}

	private static Path resolveCfdPath(Path configDir,
									   PhaseTwoDatasetConfig dataset,
									   String rawValue,
									   List<String> notes) {
		if (rawValue == null || rawValue.isBlank()) {
			return findLikelyCfdCsv(inferDatasetDirectory(configDir, dataset), notes);
		}

		Path direct = resolvePath(configDir, rawValue);
		if (Files.exists(direct)) {
			return direct;
		}

		Path datasetDir = inferDatasetDirectory(configDir, dataset);
		String normalized = normalizeWindowsDrivePath(rawValue);
		try {
			Path fileName = Path.of(normalized).getFileName();
			if (fileName != null) {
				Path byName = datasetDir.resolve(fileName.toString()).normalize();
				if (Files.exists(byName)) {
					notes.add("resolved CFD CSV from dataset folder");
					return byName;
				}
			}
		} catch (RuntimeException ignored) {
			// Fall through to a best-effort dataset search.
		}

		Path likely = findLikelyCfdCsv(datasetDir, notes);
		if (likely != null) {
			return likely;
		}
		return direct;
	}

	private static Path findLikelyCfdCsv(Path datasetDir, List<String> notes) {
		if (datasetDir == null || !Files.isDirectory(datasetDir)) {
			return null;
		}
		try (Stream<Path> files = Files.list(datasetDir)) {
			List<Path> candidates = files
					.filter(Files::isRegularFile)
					.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv"))
					.filter(path -> {
						String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
						return name.contains("drag") || name.contains("cfd") || name.contains("surface");
					})
					.toList();
			if (candidates.size() == 1) {
				notes.add("resolved CFD CSV from dataset drag table");
				return candidates.get(0);
			}
		} catch (IOException ignored) {
			// Best-effort lookup only.
		}
		return null;
	}

	private static Path inferDatasetDirectory(Path configDir, PhaseTwoDatasetConfig dataset) {
		List<String> candidates = new ArrayList<>(4);
		candidates.add(dataset.getTruthCsv());
		candidates.add(dataset.getReferenceCsv());
		candidates.add(dataset.getOrkPath());
		candidates.add(dataset.getCandidateCsv());
		for (String value : candidates) {
			if (value == null || value.isBlank()) {
				continue;
			}
			Path parent = resolvePath(configDir, value).getParent();
			if (parent != null && Files.isDirectory(parent)) {
				return parent;
			}
		}
		return configDir.toAbsolutePath().normalize();
	}

	private static Path resolvePath(Path baseDir, String value) {
		String normalized = normalizeWindowsDrivePath(value);
		Path path = Path.of(normalized);
		if (path.isAbsolute()) {
			return path;
		}

		Path anchor = baseDir == null ? Path.of(".") : baseDir.toAbsolutePath().normalize();
		Path cursor = anchor;
		while (cursor != null) {
			Path candidate = cursor.resolve(path).normalize();
			if (Files.exists(candidate)) {
				return candidate;
			}
			cursor = cursor.getParent();
		}
		return anchor.resolve(path).normalize();
	}

	private static String normalizeWindowsDrivePath(String value) {
		if (value == null) {
			return "";
		}
		if (value.matches("^[A-Za-z]:[^\\\\/].*")) {
			return value.substring(0, 2) + "/" + value.substring(2);
		}
		return value;
	}
}

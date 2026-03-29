package info.openrocket.core.tuning;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class AirBrakesJarExecutor {
	private static final String DEFAULT_JAR = "src/test/java/info/openrocket/core/tuning/Ab_jar/AirBrakes Plugin.jar";
	private static final Gson GSON = new Gson();

	private AirBrakesJarExecutor() {
	}

	public static AbPluginExecutionResult execute(PhaseTwoDatasetConfig dataset,
										String configuredJarPath,
										Path configDir,
										Path outputDir,
										int timeoutSeconds) {
		if (dataset.getPlugin() == null || !dataset.getPlugin().isEnabled()) {
			return new AbPluginExecutionResult(AbPluginExecutionResult.Status.SKIPPED, 0,
					"Plugin disabled for dataset");
		}

		String jarPathText = configuredJarPath == null || configuredJarPath.isBlank() ? DEFAULT_JAR : configuredJarPath;
		Path jarPath = resolvePath(configDir, jarPathText);
		if (!Files.exists(jarPath)) {
			return new AbPluginExecutionResult(AbPluginExecutionResult.Status.FAILED, -1,
					"Plugin jar not found: " + jarPath);
		}

		List<String> command = new ArrayList<>();
		command.add(resolveJavaExecutable());
		command.add("-jar");
		command.add(jarPath.toString());

		List<String> pluginArguments;
		try {
			pluginArguments = resolvePluginArguments(dataset, configDir);
		} catch (IOException | IllegalArgumentException ex) {
			return new AbPluginExecutionResult(AbPluginExecutionResult.Status.FAILED, -1,
					"Plugin arguments config error: " + ex.getMessage());
		}

		for (String arg : pluginArguments) {
			command.add(expandArgument(arg, dataset, configDir));
		}

		ProcessBuilder pb = new ProcessBuilder(command);
		pb.directory(configDir.toFile());

		String datasetSafe = sanitizeFileName(dataset.getName());
		Path stdoutLog = outputDir.resolve(datasetSafe + "-plugin.stdout.log");
		Path stderrLog = outputDir.resolve(datasetSafe + "-plugin.stderr.log");

		try {
			Process process = pb.start();
			boolean finished = process.waitFor(Math.max(1, timeoutSeconds), TimeUnit.SECONDS);

			String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
			Files.writeString(stdoutLog, stdout, StandardCharsets.UTF_8);
			Files.writeString(stderrLog, stderr, StandardCharsets.UTF_8);

			if (!finished) {
				process.destroyForcibly();
				return new AbPluginExecutionResult(AbPluginExecutionResult.Status.TIMED_OUT, -1,
						"Timed out after " + timeoutSeconds + "s");
			}

			int exit = process.exitValue();
			if (exit == 0) {
				return new AbPluginExecutionResult(AbPluginExecutionResult.Status.SUCCEEDED, exit,
						"Plugin completed successfully");
			}
			return new AbPluginExecutionResult(AbPluginExecutionResult.Status.FAILED, exit,
					"Plugin exited with code " + exit);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return new AbPluginExecutionResult(AbPluginExecutionResult.Status.FAILED, -1,
					"Plugin execution error: " + e.getMessage());
		} catch (IOException e) {
			return new AbPluginExecutionResult(AbPluginExecutionResult.Status.FAILED, -1,
					"Plugin execution error: " + e.getMessage());
		}
	}

	private static List<String> resolvePluginArguments(PhaseTwoDatasetConfig dataset, Path configDir) throws IOException {
		List<String> resolved = new ArrayList<>();
		PhaseTwoDatasetConfig.PluginConfig plugin = dataset.getPlugin();
		if (plugin != null && plugin.getArgumentsFile() != null && !plugin.getArgumentsFile().isBlank()) {
			Path argumentsPath = resolvePath(configDir, plugin.getArgumentsFile());
			if (!Files.exists(argumentsPath)) {
				throw new IllegalArgumentException("argumentsFile not found: " + argumentsPath);
			}
			resolved.addAll(readArgumentsFromJson(argumentsPath));
		}
		if (plugin != null && plugin.getArguments() != null) {
			resolved.addAll(plugin.getArguments());
		}
		return resolved;
	}

	private static List<String> readArgumentsFromJson(Path jsonPath) throws IOException {
		String content = Files.readString(jsonPath, StandardCharsets.UTF_8);
		JsonObject root = GSON.fromJson(content, JsonObject.class);
		if (root == null) {
			throw new IllegalArgumentException("arguments file is empty: " + jsonPath);
		}
		JsonArray arguments = root.getAsJsonArray("arguments");
		if (arguments == null) {
			throw new IllegalArgumentException("arguments file missing 'arguments' array: " + jsonPath);
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
			source = dataset.getReferenceCsv();
		}
		if (source == null || source.isBlank()) {
			return "";
		}
		Path parent = resolvePath(configDir, source).getParent();
		return parent == null ? "" : parent.toString().replace('\\', '/');
	}

	private static String resolveDatasetValuePath(Path configDir, String value) {
		if (value == null || value.isBlank()) {
			return "";
		}
		return resolvePath(configDir, value).toString();
	}

	private static Path resolvePath(Path baseDir, String value) {
		if (value == null || value.isBlank()) {
			return baseDir;
		}
		Path path = Path.of(value);
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

	private static String sanitizeFileName(String value) {
		if (value == null || value.isBlank()) {
			return "dataset";
		}
		return value.replaceAll("[^a-zA-Z0-9._-]", "_");
	}

	private static String resolveJavaExecutable() {
		String javaHome = System.getProperty("java.home");
		if (javaHome == null || javaHome.isBlank()) {
			return "java";
		}
		boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
		Path javaBin = Path.of(javaHome, "bin", isWindows ? "java.exe" : "java");
		if (Files.exists(javaBin)) {
			return javaBin.toString();
		}
		return "java";
	}
}
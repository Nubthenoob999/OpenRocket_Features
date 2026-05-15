package info.openrocket.core.airbrakesplugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lightweight CLI entrypoint used by external harnesses.
 *
 * This does not run OpenRocket simulation logic directly. It validates and
 * echoes the dataset-level inputs so test harnesses can run plugin-in-loop
 * command execution consistently.
 */
public final class AirbrakePluginCliMain {

    private AirbrakePluginCliMain() {
    }

    public static void main(String[] args) {
        if (args.length == 0 || hasFlag(args, "--help") || hasFlag(args, "-h")) {
            printUsage();
            System.exit(0);
            return;
        }

        Map<String, String> parsed = parseArgs(args);

        String dataset = parsed.getOrDefault("--dataset", "");
        String telemetry = parsed.getOrDefault("--telemetry", "");
        String candidate = parsed.getOrDefault("--candidate", "");
        String orkPath = parsed.getOrDefault("--ork", "");

        if (telemetry.isBlank()) {
            System.err.println("Missing required argument: --telemetry <path>");
            printUsage();
            System.exit(2);
            return;
        }

        Path telemetryPath = Path.of(telemetry);
        if (!Files.exists(telemetryPath)) {
            System.err.println("Telemetry file not found: " + telemetryPath.toAbsolutePath());
            System.exit(3);
            return;
        }

        if (!candidate.isBlank()) {
            Path candidatePath = Path.of(candidate);
            if (!Files.exists(candidatePath)) {
                System.err.println("Candidate file not found: " + candidatePath.toAbsolutePath());
                System.exit(4);
                return;
            }
        }

        if (!orkPath.isBlank()) {
            Path ork = Path.of(orkPath);
            if (!Files.exists(ork)) {
                System.err.println("ORK file not found: " + ork.toAbsolutePath());
                System.exit(5);
                return;
            }
        }

        System.out.println("Airbrake plugin CLI invoked");
        System.out.println("dataset=" + dataset);
        System.out.println("telemetry=" + telemetryPath.toAbsolutePath());
        System.out.println("candidate=" + (candidate.isBlank() ? "" : Path.of(candidate).toAbsolutePath()));
        System.out.println("ork=" + (orkPath.isBlank() ? "" : Path.of(orkPath).toAbsolutePath()));

        System.exit(0);
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            String token = args[i];
            if (!token.startsWith("--")) {
                continue;
            }
            String value = "";
            if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                value = args[++i];
            }
            out.put(token, value);
        }
        return out;
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) {
            if (flag.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  java -jar <plugin.jar> --dataset <name> --telemetry <csv> [--candidate <csv>] [--ork <ork-file>]");
    }
}

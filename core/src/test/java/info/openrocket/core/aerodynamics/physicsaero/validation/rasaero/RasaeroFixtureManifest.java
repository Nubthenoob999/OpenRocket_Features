package info.openrocket.core.aerodynamics.physicsaero.validation.rasaero;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record RasaeroFixtureManifest(Map<String, String> values) {
	private static final List<String> COEFFICIENT_REQUIRED = List.of("schema_version", "case_id", "reference_software",
			"reference_version", "raw_export", "raw_sha256", "normalized_export", "normalized_sha256", "cp_origin");
	public RasaeroFixtureManifest { values = Map.copyOf(values); }
	public static RasaeroFixtureManifest read(Path path) throws IOException {
		Map<String, String> values = new LinkedHashMap<>();
		for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
			String line = raw.strip(); if (line.isBlank() || line.startsWith("#")) continue; int colon = line.indexOf(':');
			if (colon <= 0) throw new RasaeroParseException(RasaeroParseException.Reason.INVALID_MANIFEST, "malformed line " + raw);
			values.put(line.substring(0, colon).strip(), line.substring(colon + 1).strip().replaceAll("^\"|\"$", ""));
		}
		return new RasaeroFixtureManifest(values);
	}
	public void requireCoefficientProvenance() {
		for (String key : COEFFICIENT_REQUIRED) if (!values.containsKey(key) || values.get(key).isBlank())
			throw new RasaeroParseException(RasaeroParseException.Reason.INVALID_MANIFEST, "missing " + key);
	}
	public String value(String key) { return values.get(key); }
}

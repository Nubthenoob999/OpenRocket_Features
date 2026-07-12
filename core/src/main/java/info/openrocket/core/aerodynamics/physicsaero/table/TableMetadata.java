package info.openrocket.core.aerodynamics.physicsaero.table;

import java.time.Instant;
import java.util.Map;

public record TableMetadata(String schemaVersion, String geometryHash, String settingsHash, String codeVersion,
		String correlationRegistryVersion, String units, String axisConvention, Instant createdAt,
		Map<String, Double> references, Map<String, Double> tolerances, String validationStatus) {
	public static final String CURRENT_SCHEMA = "physics-aero-table/1";
	public TableMetadata { references = Map.copyOf(references); tolerances = Map.copyOf(tolerances); }
}

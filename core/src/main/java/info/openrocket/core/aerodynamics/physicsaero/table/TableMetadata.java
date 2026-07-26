package info.openrocket.core.aerodynamics.physicsaero.table;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic metadata embedded in a binary table.  In particular this type
 * contains no build time or host information; those belong in the separate
 * human-readable build report.
 */
public record TableMetadata(String schemaVersion, String geometryHash, String settingsHash,
		String codeVersion, String correlationRegistryVersion, String units,
		String axisConvention, Map<String, Double> references,
		Map<String, Double> tolerances, CertificationState certificationState) {
	public static final String CURRENT_SCHEMA = "physics-aero-table/5";
	public static final String REQUIRED_UNITS = "SI;radians";
	public static final String REQUIRED_AXIS_CONVENTION = "OPENROCKET_BODY_AXES_V1";

	public TableMetadata {
		references = Map.copyOf(references);
		tolerances = Map.copyOf(tolerances);
		if (schemaVersion == null || geometryHash == null || settingsHash == null
				|| codeVersion == null || correlationRegistryVersion == null || units == null
				|| axisConvention == null || certificationState == null) {
			throw new IllegalArgumentException("table metadata fields are required");
		}
	}

	/** Source-compatible bridge for v1/v2 callers; the timestamp is intentionally ignored. */
	public TableMetadata(String schemaVersion, String geometryHash, String settingsHash,
			String codeVersion, String correlationRegistryVersion, String units,
			String axisConvention, Instant ignoredCreatedAt, Map<String, Double> references,
			Map<String, Double> tolerances, String validationStatus) {
		this(schemaVersion, geometryHash, settingsHash, codeVersion, correlationRegistryVersion,
				units, axisConvention, references, tolerances, parseCertification(validationStatus));
	}

	/** Compatibility accessor for code which displayed the old free-form status. */
	public String validationStatus() {
		return certificationState.name();
	}

	private static CertificationState parseCertification(String value) {
		if (value == null || value.isBlank()) return CertificationState.NOT_READY;
		try {
			return CertificationState.valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ignored) {
			// Old experimental/certified labels did not represent measured-flight
			// validation and must not be promoted to FLIGHT_VALIDATED.
			return value.toUpperCase(Locale.ROOT).contains("EXPERIMENT")
					? CertificationState.EXPERIMENTAL_FLIGHT_PENDING
					: CertificationState.NOT_READY;
		}
	}
}

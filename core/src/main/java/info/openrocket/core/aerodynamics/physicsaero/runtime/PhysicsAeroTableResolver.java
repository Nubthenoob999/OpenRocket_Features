package info.openrocket.core.aerodynamics.physicsaero.runtime;

import java.io.IOException;
import java.util.Locale;

import info.openrocket.core.aerodynamics.physicsaero.diagnostics.FailureReason;
import info.openrocket.core.aerodynamics.physicsaero.geometry.GeometryExtractor;
import info.openrocket.core.aerodynamics.physicsaero.integration.PhysicsAeroValidationGate;
import info.openrocket.core.aerodynamics.physicsaero.table.AerodynamicTable;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.ParallelStage;
import info.openrocket.core.rocketcomponent.RocketComponent;

/** Resolves and verifies the portable table identity before simulation starts. */
public final class PhysicsAeroTableResolver {
	public static final double DEFAULT_ROUGHNESS_M = 0;
	public static final String DEFAULT_WALL_MODEL_ID = "ADIABATIC";
	private final PhysicsAeroTableCache cache;
	private final double roughnessM;
	private final String wallModelId;

	public PhysicsAeroTableResolver() {
		this(new PhysicsAeroTableCache(), DEFAULT_ROUGHNESS_M, DEFAULT_WALL_MODEL_ID);
	}
	public PhysicsAeroTableResolver(PhysicsAeroTableCache cache, double roughnessM, String wallModelId) {
		if (cache == null || !Double.isFinite(roughnessM) || roughnessM < 0
				|| wallModelId == null || wallModelId.isBlank())
			throw new IllegalArgumentException("invalid physics-aero resolver configuration");
		this.cache = cache; this.roughnessM = roughnessM; this.wallModelId = wallModelId;
	}

	public AerodynamicTable resolve(FlightConfiguration activeConfiguration, PhysicsAeroSettings identity) {
		if (activeConfiguration == null || identity == null || !identity.isEnabled())
			throw failure(FailureReason.INVALID_STATE, "PHYSICS_AERO_NOT_ENABLED");
		validateSingleStage(activeConfiguration);
		if (identity.getGeometryHash().isBlank() || identity.getSettingsHash().isBlank()
				|| identity.getTableContentHash().isBlank())
			throw failure(FailureReason.TABLE_STALE, "PHYSICS_AERO_IDENTITY_INCOMPLETE_REBUILD_REQUIRED");
		String activeGeometryHash;
		try {
			GeometryExtractor extractor = new GeometryExtractor();
			activeGeometryHash = (roughnessM == DEFAULT_ROUGHNESS_M
					? extractor.extractWithComponentRoughness(
							activeConfiguration, wallModelId, identity.getSettingsHash(),
							identity.isForceTurbulentBoundaryLayer())
					: extractor.extract(activeConfiguration, roughnessM,
							wallModelId, identity.getSettingsHash(),
							identity.isForceTurbulentBoundaryLayer())).geometryHash();
		} catch (RuntimeException exception) {
			throw new ResolutionException(FailureReason.UNSUPPORTED_GEOMETRY,
					"PHYSICS_AERO_GEOMETRY_EXTRACTION_FAILED", exception);
		}
		if (!identity.getGeometryHash().equals(activeGeometryHash))
			throw failure(FailureReason.HASH_MISMATCH, "PHYSICS_AERO_GEOMETRY_HASH_STALE_REBUILD_REQUIRED");
		PhysicsAeroTableCache.Key key = new PhysicsAeroTableCache.Key(activeGeometryHash,
				identity.getSettingsHash(), PhysicsAeroValidationGate.CODE_VERSION,
				PhysicsAeroValidationGate.REGISTRY_VERSION);
		AerodynamicTable table;
		try {
			table = cache.load(key, identity.getTableContentHash()).orElseThrow(() ->
					failure(FailureReason.TABLE_MISSING, "PHYSICS_AERO_TABLE_MISSING_REBUILD_REQUIRED"));
		} catch (ResolutionException exception) { throw exception;
		} catch (IllegalArgumentException exception) {
			throw new ResolutionException(classify(exception.getMessage()),
					"PHYSICS_AERO_TABLE_METADATA_STALE_REBUILD_REQUIRED", exception);
		} catch (IOException exception) {
			throw new ResolutionException(classify(exception.getMessage()),
					"PHYSICS_AERO_TABLE_LOAD_FAILED_REBUILD_REQUIRED", exception);
		}
		var validation = new PhysicsAeroValidationGate().evaluate(table);
		if (validation.status() != PhysicsAeroValidationGate.Status.PASS)
			throw failure(FailureReason.TABLE_STALE, "PHYSICS_AERO_TABLE_VALIDATION_FAILED_REBUILD_REQUIRED:"
					+ String.join(",", validation.failures()));
		return table;
	}

	private static void validateSingleStage(FlightConfiguration configuration) {
		if (configuration.getRocket().getStageCount() != 1 || configuration.getActiveStages().size() != 1)
			throw failure(FailureReason.UNSUPPORTED_MULTI_STAGE_CONFIGURATION, "UNSUPPORTED_MULTI_STAGE_CONFIGURATION");
		for (RocketComponent component : configuration.getRocket()) if (component instanceof ParallelStage)
			throw failure(FailureReason.UNSUPPORTED_MULTI_STAGE_CONFIGURATION, "UNSUPPORTED_MULTI_STAGE_CONFIGURATION");
	}
	private static FailureReason classify(String message) {
		String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
		if (normalized.contains("checksum")) return FailureReason.CHECKSUM_MISMATCH;
		if (normalized.contains("content hash")) return FailureReason.CONTENT_HASH_MISMATCH;
		if (normalized.contains("schema")) return FailureReason.SCHEMA_MISMATCH;
		if (normalized.contains("units")) return FailureReason.UNITS_MISMATCH;
		if (normalized.contains("axis convention")) return FailureReason.AXIS_CONVENTION_MISMATCH;
		if (normalized.contains("missing")) return FailureReason.TABLE_MISSING;
		return FailureReason.TABLE_STALE;
	}
	private static ResolutionException failure(FailureReason reason, String message) {
		return new ResolutionException(reason, message);
	}
	public static final class ResolutionException extends IllegalStateException {
		private final FailureReason reason;
		public ResolutionException(FailureReason reason, String message) { super(message); this.reason = reason; }
		public ResolutionException(FailureReason reason, String message, Throwable cause) { super(message, cause); this.reason = reason; }
		public FailureReason reason() { return reason; }
	}
}

package info.openrocket.core.aerodynamics.physicsaero.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

import info.openrocket.core.aerodynamics.physicsaero.table.AerodynamicTable;
import info.openrocket.core.aerodynamics.physicsaero.table.TableReader;
import info.openrocket.core.aerodynamics.physicsaero.table.TableWriter;
import info.openrocket.core.aerodynamics.physicsaero.table.TableMetadata;
import info.openrocket.core.aerodynamics.physicsaero.table.CertificationState;
import info.openrocket.core.aerodynamics.physicsaero.integration.PhysicsAeroValidationGate;

/** Machine-local table cache. Only its content identity is persisted in .ork files. */
public final class PhysicsAeroTableCache {
	private final Path root;

	public PhysicsAeroTableCache() {
		this(Path.of(System.getProperty("user.home"), ".openrocket", "physics-aero"));
	}

	public PhysicsAeroTableCache(Path root) {
		this.root = root.toAbsolutePath().normalize();
	}

	public record Key(String geometryHash, String settingsHash, String codeVersion,
			String registryVersion) {
		public Key {
			geometryHash = requireToken(geometryHash);
			settingsHash = requireToken(settingsHash);
			codeVersion = requireToken(codeVersion);
			registryVersion = requireToken(registryVersion);
		}
		private static String requireToken(String value) {
			if (value == null || value.isBlank()) throw new IllegalArgumentException("empty physics-aero cache key");
			return value;
		}
	}

	public Path tablePath(Key key) {
		return root.resolve(fileStem(key) + ".aero");
	}
	public Path manifestPath(Key key) { return root.resolve(fileStem(key) + ".json"); }

	public void store(Key key, AerodynamicTable table) throws IOException {
		verifyMetadata(key, table);
		var validation = new PhysicsAeroValidationGate().evaluate(table);
		if (validation.status() != PhysicsAeroValidationGate.Status.PASS) {
			throw new IllegalArgumentException("invalid physics-aero artifact: "
					+ String.join(",", validation.failures()));
		}
		Path binary = tablePath(key);
		new TableWriter().write(table, binary, manifestPath(key));
	}

	public Optional<AerodynamicTable> load(Key key, String expectedContentHash) throws IOException {
		Path binary = tablePath(key);
		if (!Files.isRegularFile(binary)) return Optional.empty();
		if (expectedContentHash != null && !expectedContentHash.isBlank()
				&& !expectedContentHash.equals(contentHash(binary))) {
			throw new IOException("physics-aero cached table content hash mismatch; rebuild required");
		}
		AerodynamicTable table = new TableReader().read(binary);
		verifyMetadata(key, table);
		return Optional.of(table);
	}

	public static String contentHash(Path file) throws IOException {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	public static void verifyMetadata(Key key, AerodynamicTable table) {
		var metadata = table.metadata();
		if (!key.geometryHash().equals(metadata.geometryHash())
				|| !key.settingsHash().equals(metadata.settingsHash())
				|| !key.codeVersion().equals(metadata.codeVersion())
				|| !key.registryVersion().equals(metadata.correlationRegistryVersion())) {
			throw new IllegalArgumentException("stale physics-aero cache metadata");
		}
		if (!TableMetadata.CURRENT_SCHEMA.equals(metadata.schemaVersion()))
			throw new IllegalArgumentException("physics-aero table schema mismatch; rebuild required");
		if (!TableMetadata.REQUIRED_UNITS.equals(metadata.units()))
			throw new IllegalArgumentException("physics-aero table units mismatch; rebuild required");
		if (!TableMetadata.REQUIRED_AXIS_CONVENTION.equals(metadata.axisConvention()))
			throw new IllegalArgumentException("physics-aero table axis convention mismatch; rebuild required");
		if (metadata.certificationState() == CertificationState.NOT_READY)
			throw new IllegalArgumentException("physics-aero table is not ready");
	}

	private static String fileStem(Key key) {
		return digest(String.join("\n", key.geometryHash(), key.settingsHash(),
				key.codeVersion(), key.registryVersion()));
	}

	private static String digest(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}
}

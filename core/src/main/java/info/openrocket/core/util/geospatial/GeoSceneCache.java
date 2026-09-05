package info.openrocket.core.util.geospatial;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Properties;

/** Bounded persistent byte cache for downloaded geospatial source responses. */
public final class GeoSceneCache {
	public static final long DEFAULT_MAX_BYTES = 512L * 1024L * 1024L;
	public static final Duration DEFAULT_FRESHNESS = Duration.ofDays(30);

	public record Entry(byte[] body, boolean fresh, String etag, String lastModified) { }

	private final Path root;
	private final long maxBytes;

	public GeoSceneCache(Path root) {
		this(root, DEFAULT_MAX_BYTES);
	}

	public GeoSceneCache(Path root, long maxBytes) {
		this.root = root;
		this.maxBytes = Math.max(1024 * 1024, maxBytes);
	}

	public Optional<Entry> read(String key) {
		String name = hash(key);
		Path data = root.resolve(name + ".data");
		Path metadata = root.resolve(name + ".properties");
		if (!Files.isRegularFile(data)) return Optional.empty();
		try {
			Properties properties = load(metadata);
			long expires = Long.parseLong(properties.getProperty("expiresAt", "0"));
			Files.setLastModifiedTime(data, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()));
			return Optional.of(new Entry(Files.readAllBytes(data), expires > System.currentTimeMillis(),
					properties.getProperty("etag", ""), properties.getProperty("lastModified", "")));
		} catch (IOException | NumberFormatException exception) {
			return Optional.empty();
		}
	}

	public synchronized void write(String key, byte[] body, long expiresAt,
			String etag, String lastModified) throws IOException {
		Files.createDirectories(root);
		String name = hash(key);
		Path data = root.resolve(name + ".data");
		Path metadata = root.resolve(name + ".properties");
		atomicWrite(data, body);
		Properties properties = new Properties();
		properties.setProperty("expiresAt", Long.toString(expiresAt));
		if (etag != null) properties.setProperty("etag", etag);
		if (lastModified != null) properties.setProperty("lastModified", lastModified);
		Path temporary = Files.createTempFile(root, "geo-", ".properties");
		try (OutputStream output = Files.newOutputStream(temporary)) {
			properties.store(output, "OpenRocket geospatial cache");
		}
		move(temporary, metadata);
		evict();
	}

	public synchronized void clear() throws IOException {
		if (!Files.isDirectory(root)) return;
		try (var files = Files.list(root)) {
			for (Path file : files.toList()) Files.deleteIfExists(file);
		}
	}

	private void evict() throws IOException {
		try (var stream = Files.list(root)) {
			var files = stream.filter(path -> path.getFileName().toString().endsWith(".data"))
					.sorted(Comparator.comparingLong(GeoSceneCache::lastModified)).toList();
			long total = 0;
			for (Path file : files) total += Files.size(file);
			for (Path file : files) {
				if (total <= maxBytes) break;
				total -= Files.size(file);
				Files.deleteIfExists(file);
				String name = file.getFileName().toString().replaceFirst("\\.data$", ".properties");
				Files.deleteIfExists(root.resolve(name));
			}
		}
	}

	private static Properties load(Path path) throws IOException {
		Properties properties = new Properties();
		if (Files.isRegularFile(path)) try (InputStream input = Files.newInputStream(path)) { properties.load(input); }
		return properties;
	}

	private static void atomicWrite(Path target, byte[] body) throws IOException {
		Path temporary = Files.createTempFile(target.getParent(), "geo-", ".data");
		Files.write(temporary, body);
		move(temporary, target);
	}

	private static void move(Path source, Path target) throws IOException {
		try {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException exception) {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static long lastModified(Path path) {
		try { return Files.getLastModifiedTime(path).toMillis(); }
		catch (IOException exception) { return Long.MIN_VALUE; }
	}

	private static String hash(String key) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(key.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}
}

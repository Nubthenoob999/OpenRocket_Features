package info.openrocket.swing.gui.simulation;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocketFactory;

import info.openrocket.core.util.BuildProperties;

/**
 * Policy-compliant OpenStreetMap raster tile client with a persistent disk cache.
 *
 * Uses {@link HttpURLConnection} rather than {@code java.net.http}: the latter lives in a
 * separate platform module that is not present in every packaged runtime image, and a missing
 * module surfaces as a {@code NoClassDefFoundError} on the tile thread — which reads as a map
 * that never paints. {@code HttpURLConnection} is in {@code java.base} and matches the transport
 * used elsewhere in the project (see {@code TerrainFetcher}, {@code ThrustCurveAPI}).
 */
final class OpenStreetMapTileProvider implements OpenStreetMapPanel.TileProvider {
	/** Opens a connection for a tile URL; overridable so tests can avoid the network. */
	interface ConnectionFactory {
		HttpURLConnection open(URL url) throws IOException;
	}

	static final String DEFAULT_TEMPLATE = "https://tile.openstreetmap.org/{z}/{x}/{y}.png";
	static final String FALLBACK_TEMPLATE = "https://tile.openstreetmap.de/{z}/{x}/{y}.png";
	static final long MINIMUM_CACHE_MILLIS = Duration.ofDays(7).toMillis();
	private static final Pattern MAX_AGE = Pattern.compile("(?:^|,)\\s*max-age=(\\d+)",
			Pattern.CASE_INSENSITIVE);
	private static final int MAX_TILE_BYTES = 5 * 1024 * 1024;
	private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
	private static final int READ_TIMEOUT_MILLIS = 20_000;
	private static volatile SSLSocketFactory tls12SocketFactory;

	private final ConnectionFactory connectionFactory;
	private final Path cacheRoot;
	private final List<String> templates;
	private final String userAgent;
	private final AtomicInteger preferredTemplateIndex = new AtomicInteger();

	OpenStreetMapTileProvider() {
		this(url -> (HttpURLConnection) url.openConnection(), defaultCacheRoot(),
				System.getProperty("openrocket.osm.tileUrl", DEFAULT_TEMPLATE),
				System.getProperty("openrocket.osm.fallbackTileUrl", FALLBACK_TEMPLATE));
	}

	OpenStreetMapTileProvider(ConnectionFactory connectionFactory, Path cacheRoot, String template) {
		this(connectionFactory, cacheRoot, template, null);
	}

	OpenStreetMapTileProvider(ConnectionFactory connectionFactory, Path cacheRoot,
			String primaryTemplate, String fallbackTemplate) {
		this.connectionFactory = connectionFactory;
		this.cacheRoot = cacheRoot;
		this.templates = tileTemplates(primaryTemplate, fallbackTemplate);
		this.userAgent = "Project-Imperia/" + BuildProperties.getVersion()
				+ " (+https://github.com/Nubthenoob999/OpenRocket_Features)";
	}

	@Override
	public BufferedImage load(int zoom, int x, int y) throws IOException {
		Path tileFile = cacheRoot.resolve(Integer.toString(zoom))
				.resolve(Integer.toString(x)).resolve(y + ".png");
		Path metadataFile = tileFile.resolveSibling(y + ".properties");
		Properties metadata = loadMetadata(metadataFile);
		long now = System.currentTimeMillis();

		if (Files.isRegularFile(tileFile) && cachedUntil(metadata, tileFile) > now) {
			BufferedImage cached = readTile(tileFile);
			if (cached != null) return cached;
		}

		IOException failure = null;
		List<String> tls12Retries = new ArrayList<>();
		int preferred = Math.min(preferredTemplateIndex.get(), Math.max(0, templates.size() - 1));
		for (int offset = 0; offset < templates.size(); offset++) {
			int templateIndex = (preferred + offset) % templates.size();
			String template = templates.get(templateIndex);
			try {
				BufferedImage image = downloadTile(
						template, zoom, x, y, tileFile, metadataFile, metadata, now, false);
				preferredTemplateIndex.set(templateIndex);
				return image;
			} catch (SSLHandshakeException exception) {
				tls12Retries.add(template);
				failure = latestFailure(failure, exception);
			} catch (TileHttpException exception) {
				failure = latestFailure(failure, exception);
				if (exception.status >= 400 && exception.status < 500) break;
			} catch (IOException exception) {
				failure = latestFailure(failure, exception);
			}
		}

		// Some packaged Java 17 runtimes fail default protocol negotiation with a tile host.
		// Try the independent fallback host first, then retain trust-store and hostname checks while
		// constraining only those endpoints that actually failed their handshake to TLS 1.2.
		for (String template : tls12Retries) {
			try {
				BufferedImage image = downloadTile(
						template, zoom, x, y, tileFile, metadataFile, metadata, now, true);
				preferredTemplateIndex.set(templates.indexOf(template));
				return image;
			} catch (IOException exception) {
				failure = latestFailure(failure, exception);
			}
		}

		BufferedImage stale = readTile(tileFile);
		if (stale != null) return stale;
		if (failure == null) throw new IOException("No OpenStreetMap HTTPS tile provider is configured");
		throw failure;
	}

	private BufferedImage downloadTile(String template, int zoom, int x, int y,
			Path tileFile, Path metadataFile,
			Properties metadata, long now, boolean forceTls12) throws IOException {
		HttpURLConnection connection = null;
		boolean reusable = false;
		try {
			connection = connectionFactory.open(tileUrl(template, zoom, x, y));
			if (forceTls12 && connection instanceof HttpsURLConnection httpsConnection) {
				httpsConnection.setSSLSocketFactory(tls12SocketFactory());
			}
			connection.setRequestMethod("GET");
			connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
			connection.setReadTimeout(READ_TIMEOUT_MILLIS);
			connection.setInstanceFollowRedirects(true);
			connection.setRequestProperty("User-Agent", userAgent);
			connection.setRequestProperty("Accept", "image/png,image/*");
			String etag = metadata.getProperty("etag", "");
			String lastModified = metadata.getProperty("lastModified", "");
			if (!etag.isBlank()) connection.setRequestProperty("If-None-Match", etag);
			if (!lastModified.isBlank()) connection.setRequestProperty("If-Modified-Since", lastModified);

			int status = connection.getResponseCode();
			if (status == HttpURLConnection.HTTP_NOT_MODIFIED) {
				BufferedImage cached = readTile(tileFile);
				if (cached != null) {
					metadata.setProperty("expiresAt", Long.toString(expiryMillis(
							connection.getHeaderField("cache-control"),
							connection.getHeaderField("expires"), now)));
					storeMetadata(metadataFile, metadata);
					reusable = true;
					return cached;
				}
				throw new IOException("OpenStreetMap reported an unchanged tile that is no longer cached");
			}
			if (status < 200 || status >= 300) {
				throw new TileHttpException(status);
			}

			byte[] body = readBody(connection);
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(body));
			if (image == null) throw new IOException("OpenStreetMap returned an unreadable tile");

			writeTile(tileFile, body);
			metadata.setProperty("expiresAt", Long.toString(expiryMillis(
					connection.getHeaderField("cache-control"),
					connection.getHeaderField("expires"), now)));
			setIfPresent(metadata, "etag", connection.getHeaderField("etag"));
			setIfPresent(metadata, "lastModified", connection.getHeaderField("last-modified"));
			storeMetadata(metadataFile, metadata);
			reusable = true;
			return image;
		} finally {
			// A fully consumed successful response is deliberately not disconnected: closing its
			// input stream returns the TLS socket to HttpURLConnection's keep-alive pool. Failed or
			// partial responses are disconnected so they cannot poison a later request.
			if (connection != null && !reusable) connection.disconnect();
		}
	}

	private static SSLSocketFactory tls12SocketFactory() throws IOException {
		SSLSocketFactory factory = tls12SocketFactory;
		if (factory != null) return factory;
		synchronized (OpenStreetMapTileProvider.class) {
			factory = tls12SocketFactory;
			if (factory != null) return factory;
			try {
				SSLContext context = SSLContext.getInstance("TLSv1.2");
				context.init(null, null, null);
				factory = context.getSocketFactory();
				tls12SocketFactory = factory;
				return factory;
			} catch (GeneralSecurityException exception) {
				throw new IOException("TLS 1.2 is unavailable in this Java runtime", exception);
			}
		}
	}

	private static byte[] readBody(HttpURLConnection connection) throws IOException {
		try (InputStream input = connection.getInputStream()) {
			byte[] body = input.readNBytes(MAX_TILE_BYTES + 1);
			if (body.length == 0 || body.length > MAX_TILE_BYTES) {
				throw new IOException("OpenStreetMap tile response has an invalid size");
			}
			return body;
		}
	}

	private static void writeTile(Path tileFile, byte[] body) throws IOException {
		Files.createDirectories(tileFile.getParent());
		Path temporary = Files.createTempFile(tileFile.getParent(), "osm-", ".png");
		try {
			Files.write(temporary, body);
			try {
				Files.move(temporary, tileFile, StandardCopyOption.REPLACE_EXISTING,
						StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException exception) {
				Files.move(temporary, tileFile, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	private static void setIfPresent(Properties metadata, String key, String value) {
		if (value != null && !value.isBlank()) metadata.setProperty(key, value);
	}

	private URL tileUrl(String template, int zoom, int x, int y) throws IOException {
		String value = template.replace("{z}", Integer.toString(zoom))
				.replace("{x}", Integer.toString(x)).replace("{y}", Integer.toString(y));
		try {
			URI uri = URI.create(value);
			if (!"https".equalsIgnoreCase(uri.getScheme())) {
				throw new IOException("OpenStreetMap tile URL must use HTTPS");
			}
			return uri.toURL();
		} catch (IllegalArgumentException exception) {
			throw new IOException("Invalid OpenStreetMap tile URL", exception);
		}
	}

	private static List<String> tileTemplates(String primaryTemplate, String fallbackTemplate) {
		List<String> values = new ArrayList<>(2);
		if (primaryTemplate != null && !primaryTemplate.isBlank()) values.add(primaryTemplate);
		if (fallbackTemplate != null && !fallbackTemplate.isBlank()
				&& !fallbackTemplate.equals(primaryTemplate)) {
			values.add(fallbackTemplate);
		}
		return List.copyOf(values);
	}

	private static IOException latestFailure(IOException previous, IOException latest) {
		if (previous != null && previous != latest) latest.addSuppressed(previous);
		return latest;
	}

	private static final class TileHttpException extends IOException {
		private final int status;

		private TileHttpException(int status) {
			super("OpenStreetMap tile request returned HTTP " + status);
			this.status = status;
		}
	}

	private static Path defaultCacheRoot() {
		return Path.of(System.getProperty("user.home", "."), ".openrocket", "cache", "osm-tiles");
	}

	private static BufferedImage readTile(Path tileFile) {
		if (!Files.isRegularFile(tileFile)) return null;
		try {
			return ImageIO.read(tileFile.toFile());
		} catch (IOException exception) {
			return null;
		}
	}

	private static Properties loadMetadata(Path metadataFile) {
		Properties properties = new Properties();
		if (!Files.isRegularFile(metadataFile)) return properties;
		try (InputStream input = Files.newInputStream(metadataFile)) {
			properties.load(input);
		} catch (IOException ignored) {
			// A missing/corrupt sidecar falls back to the minimum cache lifetime.
		}
		return properties;
	}

	private static void storeMetadata(Path metadataFile, Properties properties) throws IOException {
		Files.createDirectories(metadataFile.getParent());
		try (OutputStream output = Files.newOutputStream(metadataFile)) {
			properties.store(output, "OpenStreetMap tile cache metadata");
		}
	}

	private static long cachedUntil(Properties metadata, Path tileFile) {
		try {
			return Long.parseLong(metadata.getProperty("expiresAt"));
		} catch (NumberFormatException ignored) {
			try {
				return Files.getLastModifiedTime(tileFile).toMillis() + MINIMUM_CACHE_MILLIS;
			} catch (IOException exception) {
				return 0;
			}
		}
	}

	static long expiryMillis(String cacheControl, String expires, long now) {
		Matcher matcher = MAX_AGE.matcher(cacheControl == null
				? "" : cacheControl.toLowerCase(Locale.ROOT));
		if (matcher.find()) {
			try {
				return now + Math.max(0, Long.parseLong(matcher.group(1))) * 1000;
			} catch (NumberFormatException ignored) {
				// Fall through to Expires/minimum lifetime.
			}
		}
		if (expires != null && !expires.isBlank()) {
			try {
				return ZonedDateTime.parse(expires, DateTimeFormatter.RFC_1123_DATE_TIME)
						.toInstant().toEpochMilli();
			} catch (DateTimeParseException ignored) {
				// Fall through to minimum lifetime.
			}
		}
		return now + MINIMUM_CACHE_MILLIS;
	}
}

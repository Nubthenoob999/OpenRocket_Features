package info.openrocket.swing.gui.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;
import javax.net.ssl.SSLHandshakeException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class OpenStreetMapTileProviderTest {
	@TempDir
	Path cacheDirectory;

	@Test
	public void testSuccessfulTileIsDecodedAndCached() throws Exception {
		byte[] png = png(new Color(0x36, 0x78, 0xA8));
		AtomicInteger requests = new AtomicInteger();
		FakeConnection[] connection = new FakeConnection[1];
		OpenStreetMapTileProvider provider = new OpenStreetMapTileProvider(url -> {
			requests.incrementAndGet();
			connection[0] = new FakeConnection(url, png);
			connection[0].headers.put("cache-control", "public, max-age=3600");
			return connection[0];
		}, cacheDirectory, OpenStreetMapTileProvider.DEFAULT_TEMPLATE);

		BufferedImage first = provider.load(7, 20, 44);
		BufferedImage second = provider.load(7, 20, 44);

		assertNotNull(first);
		assertNotNull(second);
		assertEquals(new Color(0x36, 0x78, 0xA8).getRGB(), first.getRGB(20, 20));
		assertEquals(1, requests.get(), "a fresh cached tile must not be downloaded again");
		assertTrue(connection[0].getRequestProperty("User-Agent").contains("Project-Imperia"));
		assertEquals("https://tile.openstreetmap.org/7/20/44.png",
				connection[0].getURL().toExternalForm());
		assertTrue(Files.isRegularFile(cacheDirectory.resolve("7/20/44.png")));
		assertFalse(connection[0].disconnected,
				"successful tile connections should remain eligible for HTTPS keep-alive reuse");
	}

	@Test
	public void testHandshakeFailureRetriesWithCompatibilityConnection() throws Exception {
		byte[] png = png(new Color(0x22, 0x66, 0xAA));
		AtomicInteger attempts = new AtomicInteger();
		FakeConnection[] connections = new FakeConnection[2];
		OpenStreetMapTileProvider provider = new OpenStreetMapTileProvider(url -> {
			int attempt = attempts.getAndIncrement();
			connections[attempt] = new FakeConnection(url, png);
			if (attempt == 0) {
				connections[attempt].responseFailure =
						new SSLHandshakeException("handshake_failure");
			}
			return connections[attempt];
		}, cacheDirectory, OpenStreetMapTileProvider.DEFAULT_TEMPLATE);

		BufferedImage image = provider.load(9, 80, 176);

		assertNotNull(image);
		assertEquals(2, attempts.get());
		assertTrue(connections[0].disconnected,
				"the failed TLS connection must not enter the keep-alive pool");
		assertFalse(connections[1].disconnected);
	}

	@Test
	public void testHandshakeFailureUsesIndependentHttpsFallbackBeforeProtocolRetry() throws Exception {
		byte[] png = png(new Color(0x44, 0x88, 0xCC));
		List<String> requestedHosts = new ArrayList<>();
		OpenStreetMapTileProvider provider = new OpenStreetMapTileProvider(url -> {
			requestedHosts.add(url.getHost());
			FakeConnection connection = new FakeConnection(url, png);
			if ("tile.openstreetmap.org".equals(url.getHost())) {
				connection.responseFailure = new SSLHandshakeException("handshake_failure");
			}
			return connection;
		}, cacheDirectory, OpenStreetMapTileProvider.DEFAULT_TEMPLATE,
				OpenStreetMapTileProvider.FALLBACK_TEMPLATE);

		BufferedImage image = provider.load(9, 80, 176);
		BufferedImage nextImage = provider.load(9, 81, 176);

		assertNotNull(image);
		assertNotNull(nextImage);
		assertEquals(List.of("tile.openstreetmap.org", "tile.openstreetmap.de",
				"tile.openstreetmap.de"), requestedHosts,
				"after failover, later mosaic tiles should stay on the working HTTPS host");
	}

	@Test
	public void testStaleTileSurvivesConnectionOpenFailure() throws Exception {
		byte[] png = png(new Color(0x55, 0x99, 0x44));
		AtomicInteger requests = new AtomicInteger();
		OpenStreetMapTileProvider provider = new OpenStreetMapTileProvider(url -> {
			if (requests.getAndIncrement() == 0) {
				FakeConnection connection = new FakeConnection(url, png);
				connection.headers.put("cache-control", "max-age=3600");
				return connection;
			}
			throw new IOException("offline");
		}, cacheDirectory, OpenStreetMapTileProvider.DEFAULT_TEMPLATE);

		provider.load(8, 40, 88);
		Path metadata = cacheDirectory.resolve("8/40/88.properties");
		Files.writeString(metadata, "expiresAt=0\n");

		BufferedImage stale = provider.load(8, 40, 88);
		assertNotNull(stale);
		assertEquals(new Color(0x55, 0x99, 0x44).getRGB(), stale.getRGB(20, 20));
		assertEquals(2, requests.get());
	}

	private static byte[] png(Color color) throws IOException {
		BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setColor(color);
		graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
		graphics.dispose();
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageIO.write(image, "png", output);
		return output.toByteArray();
	}

	private static final class FakeConnection extends HttpURLConnection {
		private final byte[] body;
		private final Map<String, String> headers = new HashMap<>();
		private IOException responseFailure;
		private boolean disconnected;

		private FakeConnection(URL url, byte[] body) {
			super(url);
			this.body = body;
		}

		@Override
		public int getResponseCode() throws IOException {
			if (responseFailure != null) throw responseFailure;
			return HTTP_OK;
		}

		@Override
		public String getHeaderField(String name) {
			return headers.get(name.toLowerCase(Locale.ROOT));
		}

		@Override
		public InputStream getInputStream() {
			return new ByteArrayInputStream(body);
		}

		@Override
		public void disconnect() {
			disconnected = true;
			connected = false;
		}

		@Override
		public boolean usingProxy() {
			return false;
		}

		@Override
		public void connect() {
			connected = true;
		}
	}
}

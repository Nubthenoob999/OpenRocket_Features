package info.openrocket.core.util.geospatial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import info.openrocket.core.util.TerrainFetcher;
import info.openrocket.core.util.geospatial.LaunchSiteScene.LayerStatus;

class OpenDataProviderIntegrationTest {
	@TempDir java.nio.file.Path cacheDirectory;

	@Test
	void publishesTerrainImageryAndContextFromStubServices() throws Exception {
		String overpassJson = """
				{"elements":[
				 {"type":"way","tags":{"building":"yes","building:levels":"2"},"geometry":[
				  {"lat":28.6100,"lon":-80.6000},{"lat":28.6100,"lon":-80.5999},
				  {"lat":28.6101,"lon":-80.5999},{"lat":28.6101,"lon":-80.6000},{"lat":28.6100,"lon":-80.6000}]},
				 {"type":"way","tags":{"highway":"service"},"geometry":[
				  {"lat":28.6099,"lon":-80.6001},{"lat":28.6101,"lon":-80.5999}]}
				]}""";
		try (StubServer server = new StubServer(elevationImage(), imageryImage(), overpassJson)) {
		String base = server.baseUrl();
		String oldDem = System.getProperty("openrocket.geo.usgsDem");
		String oldImagery = System.getProperty("openrocket.geo.usgsImagery");
		String oldOverpass = System.getProperty("openrocket.geo.overpass");
		System.setProperty("openrocket.geo.usgsDem", base + "/dem");
		System.setProperty("openrocket.geo.usgsImagery", base + "/imagery");
		System.setProperty("openrocket.geo.overpass", base + "/overpass");

		try (OpenDataLaunchSiteSceneProvider provider = new OpenDataLaunchSiteSceneProvider(
				new GeoSceneCache(cacheDirectory, 4 * 1024 * 1024))) {
			TerrainFetcher.TerrainData fallback = TerrainFetcher.generateDesert(2_000, 17, 1);
			LaunchSiteScene initial = new LaunchSiteScene(fallback, null, List.of(), List.of(), List.of(),
					Double.NaN, LayerStatus.LOADING, LayerStatus.LOADING, LayerStatus.LOADING, "Loading", true);
			List<LaunchSiteScene> updates = new ArrayList<>();
			LaunchSiteScene scene = provider.load(new LaunchSiteSceneRequest(
					new Wgs84.GeoPoint(28.61, -80.60, 0), 2_000, 17, 256, 1_000),
					initial, updates::add).get(10, TimeUnit.SECONDS);
			assertEquals(LayerStatus.AVAILABLE, scene.terrainStatus());
			assertEquals(LayerStatus.AVAILABLE, scene.imageryStatus());
			assertEquals(LayerStatus.AVAILABLE, scene.contextStatus());
			assertNotNull(scene.groundImage());
			assertFalse(scene.buildings().isEmpty());
			assertFalse(scene.roads().isEmpty());
			assertEquals(0, scene.terrain().relativeElevationAt(0, 0), 1e-9);
			assertFalse(updates.isEmpty());
		} finally {
			restore("openrocket.geo.usgsDem", oldDem);
			restore("openrocket.geo.usgsImagery", oldImagery);
			restore("openrocket.geo.overpass", oldOverpass);
		}
		}
	}

	private static BufferedImage elevationImage() {
		BufferedImage image = new BufferedImage(17, 17, BufferedImage.TYPE_USHORT_GRAY);
		for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
			image.getRaster().setSample(x, y, 0, 100 + x + y);
		}
		return image;
	}

	private static BufferedImage imageryImage() {
		BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
		var graphics = image.createGraphics();
		graphics.setColor(new Color(70, 120, 60));
		graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
		graphics.dispose();
		return image;
	}

	private static byte[] imageBytes(BufferedImage image) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ImageIO.write(image, "png", output);
		return output.toByteArray();
	}

	private static void restore(String key, String value) {
		if (value == null) System.clearProperty(key); else System.setProperty(key, value);
	}

	/** Minimal loopback HTTP server, avoiding a production-module dependency on jdk.httpserver. */
	private static final class StubServer implements AutoCloseable {
		private final ServerSocket socket;
		private final ExecutorService executor = Executors.newCachedThreadPool();
		private final byte[] dem;
		private final byte[] imagery;
		private final byte[] overpass;

		StubServer(BufferedImage dem, BufferedImage imagery, String overpass) throws IOException {
			this.dem = imageBytes(dem);
			this.imagery = imageBytes(imagery);
			this.overpass = overpass.getBytes(StandardCharsets.UTF_8);
			socket = new ServerSocket();
			socket.bind(new InetSocketAddress("127.0.0.1", 0));
			executor.execute(this::acceptLoop);
		}

		String baseUrl() { return "http://127.0.0.1:" + socket.getLocalPort(); }

		private void acceptLoop() {
			while (!socket.isClosed()) {
				try {
					Socket client = socket.accept();
					executor.execute(() -> handle(client));
				} catch (IOException exception) {
					if (!socket.isClosed()) throw new RuntimeException(exception);
				}
			}
		}

		private void handle(Socket client) {
			try (client; BufferedReader input = new BufferedReader(new InputStreamReader(
					client.getInputStream(), StandardCharsets.US_ASCII)); OutputStream output = client.getOutputStream()) {
				String request = input.readLine();
				String line;
				while ((line = input.readLine()) != null && !line.isEmpty()) { }
				byte[] body;
				String type;
				if (request != null && request.contains(" /dem")) { body = dem; type = "image/png"; }
				else if (request != null && request.contains(" /imagery")) { body = imagery; type = "image/png"; }
				else { body = overpass; type = "application/json"; }
				String headers = "HTTP/1.1 200 OK\r\nContent-Type: " + type
						+ "\r\nCache-Control: max-age=60\r\nContent-Length: " + body.length
						+ "\r\nConnection: close\r\n\r\n";
				output.write(headers.getBytes(StandardCharsets.US_ASCII));
				output.write(body);
			} catch (IOException ignored) { }
		}

		@Override
		public void close() throws IOException {
			socket.close();
			executor.shutdownNow();
		}
	}
}

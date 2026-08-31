package info.openrocket.swing.gui.simulation;

import java.awt.BorderLayout;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.swing.event.MapPaneAdapter;
import org.geotools.swing.event.MapPaneEvent;
import org.geotools.util.factory.GeoTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.montecarlo.LandingDispersion6DOF;
import info.openrocket.core.montecarlo.LandingDispersion6DOF.BodyPoints;
import info.openrocket.core.montecarlo.LandingDispersion6DOF.Summary;
import info.openrocket.core.montecarlo.MonteCarloBatchRunner;
import info.openrocket.core.montecarlo.MonteCarloExtension;
import info.openrocket.core.montecarlo.MonteCarloRunRecord;
import info.openrocket.core.simulation.extension.SimulationExtension;
import info.openrocket.core.startup.OpenRocketCore;

/**
 * Live end-to-end diagnostic for the GeoTools Monte Carlo landing viewer.
 *
 * <p>Run with {@code ./gradlew :swing:geoToolsMapLiveTest}. The default case loads the bundled
 * simple rocket, runs 100 deterministic dispersed trajectories at the North Carolina regression
 * coordinates, writes a PNG and detailed log under {@code build/reports/geotools-live}, and keeps
 * a temporary viewer open for 20 seconds. Add {@code -Djava.awt.headless=true} for CI.</p>
 */
public final class GeoToolsMapLiveDiagnostic {
	private static final Logger log = LoggerFactory.getLogger(GeoToolsMapLiveDiagnostic.class);
	private static final int MAP_WIDTH = 1_100;
	private static final int MAP_HEIGHT = 700;
	private static final long RENDER_TIMEOUT_SECONDS = 20;

	private GeoToolsMapLiveDiagnostic() { }

	public static void main(String[] arguments) throws Exception {
		Config config = Config.parse(arguments);
		Files.createDirectories(config.outputDirectory());
		Path logFile = config.outputDirectory().resolve("geotools-live.log");
		try (DiagnosticLog diagnostics = new DiagnosticLog(logFile)) {
			try {
				run(config, diagnostics);
			} catch (Throwable throwable) {
				diagnostics.failure(throwable);
				throw throwable;
			}
		} catch (Throwable throwable) {
			log.error("GeoTools live diagnostic failed; see {}", logFile, throwable);
			throw throwable;
		}
	}

	private static void run(Config config, DiagnosticLog diagnostics) throws Exception {
		Instant started = Instant.now();
		diagnostics.note("START ork=%s runs=%d threads=%d launch=(%.7f, %.7f) headless=%s",
				config.orkFile(), config.runs(), config.threads(), config.latitudeDeg(),
				config.longitudeDeg(), GraphicsEnvironment.isHeadless());
		diagnostics.note("RUNTIME java=%s vm=%s os=%s/%s geotools=%s classpathEntries=%d",
				System.getProperty("java.version"), System.getProperty("java.vm.name"),
				System.getProperty("os.name"), System.getProperty("os.arch"), GeoTools.getVersion(),
				System.getProperty("java.class.path", "").split(java.io.File.pathSeparator).length);
		diagnostics.note("GEOTOOLS %s", oneLine(GeoTools.getGeoToolsJarInfo()));

		if (!Files.isRegularFile(config.orkFile())) {
			throw new IOException("ORK file does not exist: " + config.orkFile());
		}
		OpenRocketCore.initialize();
		diagnostics.note("APPLICATION core injector and databases initialized");

		GeneralRocketLoader loader = new GeneralRocketLoader(config.orkFile().toFile());
		OpenRocketDocument document = loader.load();
		diagnostics.note("ORK loaded name=%s simulations=%d warnings=%d bytes=%d",
				document.getRocket().getName(), document.getSimulationCount(), loader.getWarnings().size(),
				Files.size(config.orkFile()));
		if (!loader.getWarnings().isEmpty()) diagnostics.note("ORK warnings=%s", loader.getWarnings());
		if (document.getSimulationCount() == 0) {
			throw new IllegalStateException("ORK file has no configured simulations: " + config.orkFile());
		}

		Simulation simulation = document.getSimulation(0);
		simulation.getOptions().setLaunchLatitude(config.latitudeDeg());
		simulation.getOptions().setLaunchLongitude(config.longitudeDeg());
		MonteCarloExtension extension = configureMonteCarlo(simulation);
		diagnostics.note("SIMULATION selected name=%s config=%s launch=(%.7f, %.7f)",
				simulation.getName(), simulation.getFlightConfigurationId(),
				simulation.getOptions().getLaunchLatitude(), simulation.getOptions().getLaunchLongitude());
		diagnostics.note("MONTE_CARLO seed=%d windSigma=%.3f cdSigma=%.3f thrustSigma=%.3f "
				+ "massSigma=%.3f recoverySigma=%.3f",
				extension.getRandomSeed(), extension.getWindSpeedAverageSigmaMps(),
				extension.getCdMultiplierSigma(), extension.getThrustMultiplierSigma(),
				extension.getMassMultiplierSigma(), extension.getRecoveryDragMultiplierSigma());

		Instant monteCarloStarted = Instant.now();
		List<MonteCarloRunRecord> records = MonteCarloBatchRunner.runBatchParallel(
				simulation, config.runs(), config.threads(), (completed, total) -> {
					if (completed == 1 || completed == total || completed % 10 == 0) {
						diagnostics.note("MONTE_CARLO progress=%d/%d percent=%.1f", completed, total,
								100.0 * completed / total);
					}
				});
		diagnostics.note("MONTE_CARLO completed records=%d dispersed=%d elapsedMs=%d",
				records.size(), records.stream().filter(record -> !record.nominal).count(),
				Duration.between(monteCarloStarted, Instant.now()).toMillis());
		logRecordDiagnostics(records, config, diagnostics);

		Viewer viewer = createViewer(records, config, diagnostics);
		try {
			Path png = renderSnapshot(viewer.mapPanel(), config.outputDirectory(), diagnostics);
			diagnostics.note("SNAPSHOT ready path=%s bytes=%d sha256=%s", png, Files.size(png), sha256(png));
			if (viewer.frame() != null && config.viewerSeconds() > 0) {
				diagnostics.note("VIEWER visible for up to %d seconds; close the window to finish early",
						config.viewerSeconds());
				viewer.closed().await(config.viewerSeconds(), TimeUnit.SECONDS);
			}
		} finally {
			if (viewer.frame() != null) SwingUtilities.invokeAndWait(viewer.frame()::dispose);
			viewer.mapPanel().shutdownRendering();
		}
		diagnostics.note("PASS totalElapsedMs=%d", Duration.between(started, Instant.now()).toMillis());
	}

	private static MonteCarloExtension configureMonteCarlo(Simulation simulation) {
		MonteCarloExtension extension = null;
		for (SimulationExtension candidate : simulation.getSimulationExtensions()) {
			if (candidate instanceof MonteCarloExtension found) {
				extension = found;
				break;
			}
		}
		if (extension == null) {
			extension = new MonteCarloExtension();
			simulation.getSimulationExtensions().add(extension);
		}
		extension.setUseDeterministicSeed(true);
		extension.setRandomSeed(8_675_309L);
		extension.setWindSpeedAverageSigmaMps(0.8);
		extension.setCdMultiplierSigma(0.05);
		extension.setThrustMultiplierSigma(0.03);
		extension.setMassMultiplierSigma(0.02);
		extension.setRecoveryDragMultiplierSigma(0.08);
		return extension;
	}

	private static void logRecordDiagnostics(List<MonteCarloRunRecord> records, Config config,
			DiagnosticLog diagnostics) {
		long failed = records.stream().filter(record -> record.failureMessage != null).count();
		long primaryLandings = records.stream().filter(record -> !record.nominal)
				.filter(record -> record.failureMessage == null && record.results.hasLanding).count();
		diagnostics.note("RESULTS failures=%d primaryLandings=%d/%d", failed, primaryLandings, config.runs());
		if (failed > 0) {
			records.stream().filter(record -> record.failureMessage != null).limit(10).forEach(record ->
					diagnostics.note("RESULT failure run=%d message=%s", record.runIndex, record.failureMessage));
		}

		List<BodyPoints> bodies = LandingDispersion6DOF.collectBodyLandingPoints(records);
		diagnostics.note("RESULTS landingBodies=%d", bodies.size());
		for (BodyPoints body : bodies) {
			Summary summary = LandingDispersion6DOF.summarize(
					body.points, config.latitudeDeg(), config.longitudeDeg());
			diagnostics.note("BODY id=%s name=%s points=%d mean=(%.7f, %.7f) "
					+ "oneSigma=(a=%.2fm,b=%.2fm,bearing=%.2fdeg) containment95=%.2fm",
					body.bodyId, body.branchName, summary.n, summary.meanLat_deg, summary.meanLon_deg,
					summary.oneSigma.a_m, summary.oneSigma.b_m, summary.oneSigma.bearing_deg,
					summary.containment95_m);
		}
		if (primaryLandings == 0 && bodies.isEmpty()) {
			throw new AssertionError("The 100-run live case produced no renderable landing coordinates");
		}
	}

	private static Viewer createViewer(List<MonteCarloRunRecord> records, Config config,
			DiagnosticLog diagnostics) throws Exception {
		AtomicReference<MonteCarloLandingMapPanel> landingPanel = new AtomicReference<>();
		AtomicReference<JFrame> frame = new AtomicReference<>();
		AtomicReference<JTabbedPane> resultTabs = new AtomicReference<>();
		CountDownLatch closed = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			MonteCarloLandingMapPanel panel = new MonteCarloLandingMapPanel();
			landingPanel.set(panel);
			if (!GraphicsEnvironment.isHeadless() && config.viewerSeconds() > 0) {
				JFrame window = new JFrame("GeoTools Monte Carlo live diagnostic — "
						+ config.orkFile().getFileName());
				window.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
				window.addWindowListener(new java.awt.event.WindowAdapter() {
					@Override
					public void windowClosed(java.awt.event.WindowEvent event) {
						closed.countDown();
					}
				});
				window.setLayout(new BorderLayout());
				JTabbedPane tabs = new JTabbedPane();
				JPanel placeholder = new JPanel(new BorderLayout());
				placeholder.add(new JLabel("Populating the hidden landing-map tab...", JLabel.CENTER),
						BorderLayout.CENTER);
				tabs.addTab("Charts & statistics", placeholder);
				tabs.addTab("Landing map", panel);
				tabs.setSelectedIndex(0);
				window.add(tabs, BorderLayout.CENTER);
				window.setSize(MAP_WIDTH + 80, MAP_HEIGHT + 300);
				window.setLocationByPlatform(true);
				window.setVisible(true);
				frame.set(window);
				resultTabs.set(tabs);
			}
			GeoToolsMapPanel map = panel.getMapPanel();
			map.setSize(MAP_WIDTH, MAP_HEIGHT);
			panel.setResults(records, config.latitudeDeg(), config.longitudeDeg());
			map.fitToOverlays();
			if (resultTabs.get() != null) resultTabs.get().setSelectedIndex(1);
		});

		GeoToolsMapPanel map = landingPanel.get().getMapPanel();
		ReferencedEnvelope display = map.getDisplayArea();
		diagnostics.note("VIEWER created frame=%s markers=%d polylines=%d layers=%d zoom=%.3f "
				+ "center=(%.7f, %.7f) display=%s mapBounds=%s",
				frame.get() != null, map.getMarkerCount(), map.getPolylineCount(),
				map.getFeatureLayerCount(), map.getZoomLevel(), map.getCenter().latitudeDeg(),
				map.getCenter().longitudeDeg(), describe(display), describe(map.getMapContent().getMaxBounds()));
		if (map.getFeatureLayerCount() == 0 || map.getMarkerCount() <= 1) {
			throw new AssertionError("GeoTools viewer did not receive landing feature layers");
		}
		return new Viewer(landingPanel.get(), map, frame.get(), closed);
	}

	private static Path renderSnapshot(GeoToolsMapPanel map, Path outputDirectory,
			DiagnosticLog diagnostics) throws Exception {
		if (!GraphicsEnvironment.isHeadless()) {
			boolean baseMapAttempted = map.awaitBaseMapAttempt(RENDER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
			diagnostics.note("BASEMAP attemptFinished=%s ready=%s status=%s",
					baseMapAttempted, map.isBaseMapReady(), map.getBaseMapStatus());
			CountDownLatch renderStopped = new CountDownLatch(1);
			boolean alreadyRendered = map.getBaseImage() != null
					&& map.getLastRenderedFeatureCount() > 0;
			map.addMapPaneListener(new MapPaneAdapter() {
				@Override
				public void onRenderingStopped(MapPaneEvent event) {
					if (map.getLastRenderedFeatureCount() > 0) renderStopped.countDown();
				}
			});
			paintOnce(map);
			boolean stopped = alreadyRendered
					|| renderStopped.await(RENDER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
			diagnostics.note("RENDER swing stopped=%s errors=%d timeoutSeconds=%d baseImage=%s",
					stopped, map.getLastRenderErrorCount(), RENDER_TIMEOUT_SECONDS,
					map.getBaseImage() == null ? "null"
							: map.getBaseImage().getWidth() + "x" + map.getBaseImage().getHeight());
			if (!stopped || map.getBaseImage() == null) {
				throw new AssertionError("GeoTools Swing render did not produce a base image within "
						+ RENDER_TIMEOUT_SECONDS + " seconds");
			}
			if (map.getLastRenderErrorCount() != 0) {
				throw new AssertionError("GeoTools Swing renderer reported "
						+ map.getLastRenderErrorCount() + " error(s); inspect the diagnostic log");
			}
		} else {
			diagnostics.note("RENDER swing skipped because environment is headless");
		}

		BufferedImage image = map.renderSnapshot(MAP_WIDTH, MAP_HEIGHT);
		PixelStats pixels = inspectPixels(image);
		diagnostics.note("RENDER pixels sampled=%d uniqueColors=%d nonTransparent=%d centerArgb=#%08X",
				pixels.sampled(), pixels.uniqueColors(), pixels.nonTransparent(),
				image.getRGB(image.getWidth() / 2, image.getHeight() / 2));
		if (pixels.uniqueColors() < 5 || pixels.nonTransparent() == 0) {
			throw new AssertionError("GeoTools snapshot appears blank: " + pixels);
		}

		Path png = outputDirectory.resolve("geotools-live.png");
		if (!ImageIO.write(image, "png", png.toFile())) {
			throw new IOException("No PNG writer is available");
		}
		return png;
	}

	private static void paintOnce(GeoToolsMapPanel map) throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			BufferedImage image = new BufferedImage(MAP_WIDTH, MAP_HEIGHT, BufferedImage.TYPE_INT_ARGB);
			Graphics2D graphics = image.createGraphics();
			try {
				map.paint(graphics);
			} finally {
				graphics.dispose();
			}
		});
	}

	private static PixelStats inspectPixels(BufferedImage image) {
		Set<Integer> colors = new HashSet<>();
		int sampled = 0;
		int nonTransparent = 0;
		for (int y = 0; y < image.getHeight(); y += 4) {
			for (int x = 0; x < image.getWidth(); x += 4) {
				int argb = image.getRGB(x, y);
				colors.add(argb);
				sampled++;
				if ((argb >>> 24) != 0) nonTransparent++;
			}
		}
		return new PixelStats(sampled, colors.size(), nonTransparent);
	}

	private static String sha256(Path path) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
	}

	private static String describe(ReferencedEnvelope bounds) {
		if (bounds == null) return "null";
		return String.format(Locale.ROOT, "[%.3f..%.3f, %.3f..%.3f, crs=%s]",
				bounds.getMinX(), bounds.getMaxX(), bounds.getMinY(), bounds.getMaxY(),
				bounds.getCoordinateReferenceSystem() == null ? "null"
						: bounds.getCoordinateReferenceSystem().getName());
	}

	private static String oneLine(String value) {
		return value == null ? "null" : value.replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ");
	}

	private record Viewer(MonteCarloLandingMapPanel landingPanel, GeoToolsMapPanel mapPanel,
			JFrame frame, CountDownLatch closed) { }

	private record PixelStats(int sampled, int uniqueColors, int nonTransparent) { }

	private record Config(Path orkFile, int runs, int threads, double latitudeDeg,
			double longitudeDeg, int viewerSeconds, Path outputDirectory) {
		private static Config parse(String[] arguments) {
			Path ork = Path.of("core/src/main/resources/datafiles/examples/A simple model rocket.ork");
			int runs = 100;
			int threads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
			double latitude = 35.1758;
			double longitude = -76.8283;
			int viewerSeconds = GraphicsEnvironment.isHeadless() ? 0 : 20;
			Path output = Path.of("build/reports/geotools-live");
			for (String argument : arguments) {
				if (argument.startsWith("--ork=")) ork = Path.of(value(argument));
				else if (argument.startsWith("--runs=")) runs = Integer.parseInt(value(argument));
				else if (argument.startsWith("--threads=")) threads = Integer.parseInt(value(argument));
				else if (argument.startsWith("--latitude=")) latitude = Double.parseDouble(value(argument));
				else if (argument.startsWith("--longitude=")) longitude = Double.parseDouble(value(argument));
				else if (argument.startsWith("--viewer-seconds=")) {
					viewerSeconds = Integer.parseInt(value(argument));
				} else if (argument.startsWith("--output=")) output = Path.of(value(argument));
				else throw new IllegalArgumentException("Unknown argument: " + argument);
			}
			if (runs < 2) throw new IllegalArgumentException("--runs must be at least 2");
			if (threads < 1) throw new IllegalArgumentException("--threads must be at least 1");
			if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
				throw new IllegalArgumentException("--latitude must be between -90 and 90");
			}
			if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
				throw new IllegalArgumentException("--longitude must be between -180 and 180");
			}
			return new Config(ork.toAbsolutePath().normalize(), runs, threads, latitude, longitude,
					Math.max(0, viewerSeconds), output.toAbsolutePath().normalize());
		}

		private static String value(String argument) {
			return argument.substring(argument.indexOf('=') + 1);
		}
	}

	private static final class DiagnosticLog implements AutoCloseable {
		private final PrintWriter writer;

		private DiagnosticLog(Path path) throws IOException {
			writer = new PrintWriter(Files.newBufferedWriter(path, StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
					StandardOpenOption.WRITE), true);
		}

		private synchronized void note(String format, Object... arguments) {
			String message = String.format(Locale.ROOT, format, arguments);
			String line = Instant.now() + " " + message;
			writer.println(line);
			log.info(message);
		}

		@SuppressWarnings("unused")
		private synchronized void failure(Throwable throwable) {
			StringWriter stack = new StringWriter();
			throwable.printStackTrace(new PrintWriter(stack));
			writer.println(Instant.now() + " FAILURE " + stack);
		}

		@Override
		public void close() {
			writer.close();
		}
	}
}

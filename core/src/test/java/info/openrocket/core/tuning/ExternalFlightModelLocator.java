package info.openrocket.core.tuning;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Optional, deterministic locator for the as-flown model inputs behind
 * {@link ExternalFlightDatabaseFixture}.  It performs no download and does
 * not copy model files into this repository.
 *
 * <p>The preferred source is the public {@code supersonic-aero-dev} revision
 * of OpenRocket Plus.  The RASAero installer is retained as an independent
 * source for the first 24 CDX1 inputs.  MESOS and the three sounding rockets
 * are available from the former source only.</p>
 */
final class ExternalFlightModelLocator {
	static final String OPENROCKET_SUPERSONIC_URL =
			"https://github.com/AidanSYu/openrocketsupersonic.git";
	static final String OPENROCKET_SUPERSONIC_BRANCH = "supersonic-aero-dev";
	static final String OPENROCKET_SUPERSONIC_REVISION =
			"73a3f755c9e4efeea1e188f55873e48715046eda";
	static final String RASAERO_II_1_0_2_0_URL =
			"https://www.rasaero.com/dloads/RASAero_II_Setup_Version_1.0.2.0.zip";

	static final String SUPERSONIC_ROOT_PROPERTY = "openrocket.externalFlightModels.openRocketSupersonicRoot";
	static final String RASAERO_EXAMPLES_PROPERTY = "openrocket.externalFlightModels.rasaeroExamplesRoot";

	private static final Map<Integer, ModelSource> SOURCES = sourceMap();

	private ExternalFlightModelLocator() {
	}

	static List<ModelSource> sources() {
		return List.copyOf(SOURCES.values());
	}

	static ModelSource sourceForFlight(int flightId) {
		ModelSource source = SOURCES.get(flightId);
		if (source == null) {
			throw new IllegalArgumentException("No external-flight model mapping for flight " + flightId);
		}
		return source;
	}

	static LocatorRoots rootsFromSystemProperties() {
		return new LocatorRoots(pathProperty(SUPERSONIC_ROOT_PROPERTY), pathProperty(RASAERO_EXAMPLES_PROPERTY));
	}

	static List<ModelAvailability> locate(LocatorRoots roots) {
		Objects.requireNonNull(roots, "roots");
		List<ModelAvailability> result = new ArrayList<>();
		for (ModelSource source : SOURCES.values()) {
			result.add(locate(source, roots));
		}
		return List.copyOf(result);
	}

	static ModelAvailability locate(ModelSource source, LocatorRoots roots) {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(roots, "roots");

		Path fromSupersonic = resolve(roots.openRocketSupersonicRoot(), source.openRocketSupersonicRelativePath());
		if (isRegularFile(fromSupersonic)) {
			return ModelAvailability.available(source, fromSupersonic, ModelOrigin.OPENROCKET_SUPERSONIC,
					missingCompanions(source, roots.openRocketSupersonicRoot()));
		}

		Path fromRasaero = source.rasaeroExamplesFileName() == null ? null
				: resolve(roots.rasaeroExamplesRoot(), source.rasaeroExamplesFileName());
		if (isRegularFile(fromRasaero)) {
			return ModelAvailability.available(source, fromRasaero, ModelOrigin.RASAERO_II_1_0_2_0, List.of());
		}

		return ModelAvailability.missing(source, fromSupersonic, fromRasaero, missingReason(source, roots));
	}

	private static boolean isRegularFile(Path path) {
		return path != null && Files.isRegularFile(path);
	}

	private static Path resolve(Path root, String relativePath) {
		return root == null ? null : root.resolve(relativePath).normalize();
	}

	private static Path pathProperty(String property) {
		String value = System.getProperty(property);
		return value == null || value.isBlank() ? null : Path.of(value).toAbsolutePath().normalize();
	}

	private static List<Path> missingCompanions(ModelSource source, Path sourceRoot) {
		return source.openRocketSupersonicCompanionPaths().stream()
				.map(path -> resolve(sourceRoot, path))
				.filter(path -> !isRegularFile(path))
				.toList();
	}

	private static String missingReason(ModelSource source, LocatorRoots roots) {
		if (roots.openRocketSupersonicRoot() == null && roots.rasaeroExamplesRoot() == null) {
			return "No model roots configured; set " + SUPERSONIC_ROOT_PROPERTY
					+ " and/or " + RASAERO_EXAMPLES_PROPERTY;
		}
		if (source.rasaeroExamplesFileName() == null && roots.openRocketSupersonicRoot() == null) {
			return "This model is available only from the pinned OpenRocket Supersonic source; set "
					+ SUPERSONIC_ROOT_PROPERTY;
		}
		return "Expected pinned model file is absent from each configured root";
	}

	private static Map<Integer, ModelSource> sourceMap() {
		Map<Integer, ModelSource> sources = new LinkedHashMap<>();
		add(sources, 1, "Thunder&Lightning.CDX1");
		add(sources, 2, "Gibb.CDX1");
		add(sources, 3, "CancerDescending.CDX1");
		add(sources, 4, "EZI65-1.CDX1");
		add(sources, 5, "CalIsp3.CDX1");
		add(sources, 6, "CalIsp1.CDX1");
		add(sources, 7, "CalIsp2.CDX1");
		add(sources, 8, "Byrum.CDX1");
		add(sources, 9, "IonDrive.CDX1");
		add(sources, 10, "CalIsp5.CDX1");
		add(sources, 11, "Blister.CDX1");
		add(sources, 12, "CalIsp4.CDX1");
		add(sources, 13, "Rabia-ShortFinCan.CDX1");
		add(sources, 14, "Raven.CDX1");
		add(sources, 15, "Rabia.CDX1");
		add(sources, 16, "Torrent.CDX1");
		add(sources, 17, "L500Roc.CDX1");
		add(sources, 18, "Kinsel_P4935_A-601_Rocket.CDX1");
		add(sources, 19, "Full Metal Jacket1.CDX1");
		add(sources, 20, "Full Metal Jacket2.CDX1");
		add(sources, 21, "Proteus6.CDX1");
		add(sources, 22, "AeroPac104KStageOne&Two-2.CDX1");
		add(sources, 23, "DontDebateThisN5800MinDia.CDX1");
		add(sources, 24, "Qu8k.CDX1");
		sources.put(25, new ModelSource(25, ModelFormat.CDX1, "simvreal/Docs/Mesos/MESOS 293K Flight.CDX1",
				null, List.of("simvreal/Docs/Mesos/O4374_Sea_Level.eng",
						"simvreal/Docs/Mesos/M787_Expanded_Nozzle_Sea_Level.eng")));
		sources.put(26, new ModelSource(26, ModelFormat.ORK, "paper/data/ork/sounding_rockets/bbv.ork",
				null, List.of()));
		sources.put(27, new ModelSource(27, ModelFormat.ORK,
				"paper/data/ork/sounding_rockets/nike_deacon_flight1.ork", null, List.of()));
		sources.put(28, new ModelSource(28, ModelFormat.ORK,
				"paper/data/ork/sounding_rockets/nike_deacon_flight2.ork", null, List.of()));
		return Collections.unmodifiableMap(new LinkedHashMap<>(sources));
	}

	private static void add(Map<Integer, ModelSource> sources, int id, String fileName) {
		sources.put(id, new ModelSource(id, ModelFormat.CDX1, "simvreal/RasAero Sims/" + fileName,
				fileName, List.of("simvreal/rasp.eng")));
	}

	enum ModelFormat {
		CDX1,
		ORK
	}

	enum ModelOrigin {
		OPENROCKET_SUPERSONIC,
		RASAERO_II_1_0_2_0
	}

	record LocatorRoots(Path openRocketSupersonicRoot, Path rasaeroExamplesRoot) {
		LocatorRoots {
			openRocketSupersonicRoot = normalize(openRocketSupersonicRoot);
			rasaeroExamplesRoot = normalize(rasaeroExamplesRoot);
		}

		private static Path normalize(Path root) {
			return root == null ? null : root.toAbsolutePath().normalize();
		}
	}

	record ModelSource(int flightId, ModelFormat format, String openRocketSupersonicRelativePath,
					   String rasaeroExamplesFileName, List<String> openRocketSupersonicCompanionPaths) {
		ModelSource {
			if (flightId <= 0) {
				throw new IllegalArgumentException("flightId must be positive");
			}
			Objects.requireNonNull(format, "format");
			Objects.requireNonNull(openRocketSupersonicRelativePath, "openRocketSupersonicRelativePath");
			openRocketSupersonicCompanionPaths = List.copyOf(openRocketSupersonicCompanionPaths);
		}
	}

	record ModelAvailability(ModelSource source, Path path, ModelOrigin origin, String missingReason,
							 List<Path> missingCompanionPaths) {
		ModelAvailability {
			missingCompanionPaths = List.copyOf(missingCompanionPaths);
		}

		static ModelAvailability available(ModelSource source, Path path, ModelOrigin origin,
									   List<Path> missingCompanionPaths) {
			return new ModelAvailability(source, path, origin, null, missingCompanionPaths);
		}

		static ModelAvailability missing(ModelSource source, Path supersonicCandidate,
									 Path rasaeroCandidate, String missingReason) {
			Path diagnosticPath = supersonicCandidate != null ? supersonicCandidate : rasaeroCandidate;
			return new ModelAvailability(source, diagnosticPath, null, missingReason, List.of());
		}

		boolean available() {
			return origin != null;
		}

		boolean replayReady() {
			return available() && missingCompanionPaths.isEmpty();
		}
	}
}

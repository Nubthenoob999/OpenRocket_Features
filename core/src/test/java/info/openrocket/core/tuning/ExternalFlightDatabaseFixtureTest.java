package info.openrocket.core.tuning;

import info.openrocket.core.util.BaseTestCase;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalFlightDatabaseFixtureTest extends BaseTestCase {
	@Test
	void loadsPinnedV12GroundTruthWithExplicitReplayConstraints() throws Exception {
		List<ExternalFlightDatabaseFixture.Flight> flights = ExternalFlightDatabaseFixture.load(manifestPath());

		assertEquals(28, flights.size(), "v1.2 snapshot row count");
		assertEquals(1, flights.get(0).id());
		assertEquals(28, flights.get(flights.size() - 1).id());
		assertTrue(flights.stream().allMatch(flight -> flight.measuredApogeeFeet() > 0.0));
		assertTrue(flights.stream().allMatch(flight -> flight.peakMach() >= 0.54 && flight.peakMach() <= 7.22));
		assertTrue(flights.stream().allMatch(flight ->
				flight.definitionStatus() == ExternalFlightDatabaseFixture.DefinitionStatus.NOT_REDISTRIBUTED_BY_UPSTREAM));

		Map<String, Long> measurementCounts = flights.stream().collect(Collectors.groupingBy(
				ExternalFlightDatabaseFixture.Flight::measurementType, Collectors.counting()));
		assertEquals(17L, measurementCounts.get("Barometric Altimeter"));
		assertEquals(3L, measurementCounts.get("GPS"));
		assertEquals(3L, measurementCounts.get("Optical Track"));
	}

	@Test
	void publishedBaselineHasTheDocumentedTenPercentAdmissionBand() throws Exception {
		List<ExternalFlightDatabaseFixture.Flight> flights = ExternalFlightDatabaseFixture.load(manifestPath());

		long withinFivePercent = flights.stream().filter(flight ->
				flight.isWithinApogeeTolerance(flight.publishedOpenRocketPlusApogeeFeet(), 0.05)).count();
		assertEquals(16L, withinFivePercent, "published v1.2 aggregate");
		assertTrue(flights.stream().allMatch(flight ->
				flight.isWithinApogeeTolerance(flight.publishedOpenRocketPlusApogeeFeet(), 0.10)),
				"The source corpus itself is selected for this 10% band");
	}

	@Test
	void errorAndUnitConversionsRemainPhysicsMeaningful() throws Exception {
		ExternalFlightDatabaseFixture.Flight flight = ExternalFlightDatabaseFixture.load(manifestPath()).get(0);

		assertEquals(1_090.2696, flight.measuredApogeeMeters(), 1.0e-9);
		assertEquals(838.2, flight.launchSiteAltitudeMetersMsl(), 1.0e-9);
		assertEquals(8.386916410400, flight.signedApogeeErrorPercent(
				flight.publishedOpenRocketPlusApogeeFeet()), 1.0e-9);
		assertFalse(flight.isWithinApogeeTolerance(flight.publishedOpenRocketPlusApogeeFeet(), 0.05));
	}

	private static Path manifestPath() {
		Path fromCore = Path.of("src/test/java/info/openrocket/core/tuning/external-flight-database-v1.2.tsv");
		if (Files.isRegularFile(fromCore)) {
			return fromCore;
		}
		Path fromProject = Path.of("core/src/test/java/info/openrocket/core/tuning/external-flight-database-v1.2.tsv");
		if (Files.isRegularFile(fromProject)) {
			return fromProject;
		}
		throw new IllegalStateException("External flight database fixture not found from "
				+ Path.of("").toAbsolutePath().normalize());
	}
}

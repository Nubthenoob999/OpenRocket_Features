package info.openrocket.core.tuning;

import info.openrocket.core.util.BaseTestCase;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalFlightModelLocatorTest extends BaseTestCase {
	@Test
	void mapsEveryDatabaseFlightToOnePinnedPublicModel() {
		List<ExternalFlightModelLocator.ModelSource> sources = ExternalFlightModelLocator.sources();

		assertEquals(28, sources.size());
		assertEquals("CalIsp3.CDX1", ExternalFlightModelLocator.sourceForFlight(5).rasaeroExamplesFileName());
		assertEquals("CalIsp1.CDX1", ExternalFlightModelLocator.sourceForFlight(6).rasaeroExamplesFileName());
		assertEquals("Full Metal Jacket1.CDX1",
				ExternalFlightModelLocator.sourceForFlight(19).rasaeroExamplesFileName());
		assertEquals("simvreal/Docs/Mesos/MESOS 293K Flight.CDX1",
				ExternalFlightModelLocator.sourceForFlight(25).openRocketSupersonicRelativePath());
		assertEquals(ExternalFlightModelLocator.ModelFormat.ORK,
				ExternalFlightModelLocator.sourceForFlight(28).format());
		assertEquals(24, sources.stream().filter(source -> source.rasaeroExamplesFileName() != null).count());
	}

	@Test
	void reportsConfiguredModelsWithoutDownloadingOrAssumingAWorkspacePath() throws Exception {
		Path root = Files.createTempDirectory("external-flight-model-root");
		ExternalFlightModelLocator.ModelSource thunder = ExternalFlightModelLocator.sourceForFlight(1);
		Path model = root.resolve(thunder.openRocketSupersonicRelativePath());
		Files.createDirectories(model.getParent());
		Files.createFile(model);
		Path motorLibrary = root.resolve("simvreal/rasp.eng");
		Files.createDirectories(motorLibrary.getParent());
		Files.createFile(motorLibrary);

		ExternalFlightModelLocator.ModelAvailability availability = ExternalFlightModelLocator.locate(thunder,
				new ExternalFlightModelLocator.LocatorRoots(root, null));

		assertTrue(availability.available());
		assertTrue(availability.replayReady());
		assertEquals(model, availability.path());
		assertEquals(ExternalFlightModelLocator.ModelOrigin.OPENROCKET_SUPERSONIC, availability.origin());
	}

	@Test
	void reportsMissingSourceRatherThanReplacingItWithAProxyModel() {
		ExternalFlightModelLocator.ModelAvailability availability = ExternalFlightModelLocator.locate(
				ExternalFlightModelLocator.sourceForFlight(25),
				new ExternalFlightModelLocator.LocatorRoots(null, null));

		assertFalse(availability.available());
		assertTrue(availability.missingReason().contains(
				ExternalFlightModelLocator.SUPERSONIC_ROOT_PROPERTY));
	}
}

package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import info.openrocket.core.aerodynamics.physicsaero.diagnostics.FailureReason;
import info.openrocket.core.aerodynamics.physicsaero.geometry.GeometryExtractor;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroMode;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroSettings;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroTableCache;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroTableResolver;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroTableResolver.ResolutionException;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.startup.OpenRocketCore;
import info.openrocket.core.util.TestRockets;

class PhysicsAeroTableResolverTest {
	@BeforeAll
	static void initializeCore() {
		OpenRocketCore.initialize();
	}

	@Test
	void rejectsIncompleteStaleAndMissingPortableIdentities(@TempDir Path temporary) {
		FlightConfiguration configuration = TestRockets.makeEstesAlphaIII().getSelectedConfiguration();
		PhysicsAeroTableResolver resolver = new PhysicsAeroTableResolver(
				new PhysicsAeroTableCache(temporary), 0, "ADIABATIC");

		PhysicsAeroSettings settings = new PhysicsAeroSettings();
		settings.setMode(PhysicsAeroMode.STRICT);
		assertReason(FailureReason.TABLE_STALE, () -> resolver.resolve(configuration, settings));

		settings.setGeometryHash("stale");
		settings.setSettingsHash("settings");
		settings.setTableContentHash("content");
		assertReason(FailureReason.HASH_MISMATCH, () -> resolver.resolve(configuration, settings));

		String geometryHash = new GeometryExtractor()
				.extractWithComponentRoughness(configuration, "ADIABATIC", "settings")
				.geometryHash();
		settings.setGeometryHash(geometryHash);
		assertReason(FailureReason.TABLE_MISSING, () -> resolver.resolve(configuration, settings));
	}

	@Test
	void rejectsSerialAndParallelConfigurationsBeforeCacheLookup(@TempDir Path temporary) {
		FlightConfiguration configuration = TestRockets.makeMultiStageEventTestRocket()
				.getSelectedConfiguration();
		PhysicsAeroSettings settings = new PhysicsAeroSettings();
		settings.setMode(PhysicsAeroMode.STRICT);
		settings.setGeometryHash("geometry");
		settings.setSettingsHash("settings");
		settings.setTableContentHash("content");
		PhysicsAeroTableResolver resolver = new PhysicsAeroTableResolver(
				new PhysicsAeroTableCache(temporary), 0, "ADIABATIC");
		assertReason(FailureReason.UNSUPPORTED_MULTI_STAGE_CONFIGURATION,
				() -> resolver.resolve(configuration, settings));
	}

	@Test
	void turbulentIdentityCannotResolveANaturalTransitionGeometry(@TempDir Path temporary) {
		FlightConfiguration configuration = TestRockets.makeEstesAlphaIII()
				.getSelectedConfiguration();
		PhysicsAeroSettings settings = new PhysicsAeroSettings();
		settings.setMode(PhysicsAeroMode.STRICT);
		settings.setForceTurbulentBoundaryLayer(true);
		settings.setSettingsHash("settings");
		settings.setTableContentHash("content");
		settings.setGeometryHash(new GeometryExtractor()
				.extractWithComponentRoughness(
						configuration, "ADIABATIC", "settings", false)
				.geometryHash());

		PhysicsAeroTableResolver resolver = new PhysicsAeroTableResolver(
				new PhysicsAeroTableCache(temporary), 0, "ADIABATIC");
		assertReason(FailureReason.HASH_MISMATCH,
				() -> resolver.resolve(configuration, settings));
	}

	private static void assertReason(FailureReason expected, Runnable action) {
		ResolutionException exception = assertThrows(ResolutionException.class, action::run);
		assertEquals(expected, exception.reason());
	}
}

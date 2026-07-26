package info.openrocket.swing.gui.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.util.Modules;

import info.openrocket.core.aerodynamics.physicsaero.config.NumericalTolerances;
import info.openrocket.core.aerodynamics.physicsaero.config.PhysicsAeroSettingsFingerprint;
import info.openrocket.core.aerodynamics.physicsaero.config.PhysicsConfiguration;
import info.openrocket.core.aerodynamics.physicsaero.config.SamplingConfiguration;
import info.openrocket.core.aerodynamics.physicsaero.flow.AtmosphereState;
import info.openrocket.core.aerodynamics.physicsaero.flow.PoweredFlowState;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.PerfectGasAir;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.GeometryExtractor;
import info.openrocket.core.aerodynamics.physicsaero.integration.PhysicsAeroTableService;
import info.openrocket.core.aerodynamics.physicsaero.integration.PhysicsAeroValidationGate;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroMode;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroSettings;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroTableCache;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroTableResolver;
import info.openrocket.core.aerodynamics.physicsaero.table.AerodynamicTable;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.OpenRocketDocumentFactory;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.formatting.RocketDescriptor;
import info.openrocket.core.formatting.RocketDescriptorImpl;
import info.openrocket.core.l10n.DebugTranslator;
import info.openrocket.core.l10n.Translator;
import info.openrocket.core.plugin.PluginModule;
import info.openrocket.core.preferences.ApplicationPreferences;
import info.openrocket.core.startup.Application;
import info.openrocket.core.util.TestRockets;
import info.openrocket.swing.ServicesForTesting;
import info.openrocket.swing.gui.simulation.PhysicsAeroExperimentalPanel.Fidelity;

/**
 * Covers the table-generation path the Aerodynamics tab drives: the preset
 * grids it offers, and an end-to-end build of a real rocket through
 * {@link PhysicsAeroTableService}.
 */
public class PhysicsAeroTableGenerationTest {

	/** The artifact gate rejects a table whose axes do not reach these endpoints. */
	private static final double[] REQUIRED_MACH_JOIN_NODES = {0.85, 0.95, 1.2, 1.3, 5.0};

	@BeforeAll
	public static void setUp() {
		Module applicationModule = new AbstractModule() {
			@Override
			protected void configure() {
				bind(ApplicationPreferences.class).to(ServicesForTesting.PreferencesForTesting.class);
				bind(Translator.class).toProvider(ServicesForTesting.TranslatorProviderForTesting.class);
				bind(RocketDescriptor.class).to(RocketDescriptorImpl.class);
			}
		};
		Module debugTranslator = new AbstractModule() {
			@Override
			protected void configure() {
				bind(Translator.class).toInstance(new DebugTranslator(null));
			}
		};
		Injector injector = Guice.createInjector(
				Modules.override(applicationModule).with(debugTranslator), new PluginModule());
		Application.setInjector(injector);
	}

	@Test
	public void testEveryGridPresetSatisfiesTheArtifactAxisContract() {
		for (Fidelity preset : Fidelity.values()) {
			SamplingConfiguration sampling = preset.sampling();
			double[] mach = sampling.mach();
			double[] alphaDeg = degrees(sampling.alphaRad());
			double[] betaDeg = degrees(sampling.betaRad());

			assertEquals(0.0, mach[0], 1e-12, preset + " must start at Mach 0");
			assertEquals(7.0, mach[mach.length - 1], 1e-12, preset + " must reach Mach 7");
			assertEquals(-15.0, alphaDeg[0], 1e-9, preset + " must start at -15 deg alpha");
			assertEquals(15.0, alphaDeg[alphaDeg.length - 1], 1e-9, preset + " must reach +15 deg alpha");
			assertEquals(-5.0, betaDeg[0], 1e-9, preset + " must start at -5 deg sideslip");
			assertEquals(5.0, betaDeg[betaDeg.length - 1], 1e-9, preset + " must reach +5 deg sideslip");

			for (double join : REQUIRED_MACH_JOIN_NODES) {
				int index = Arrays.binarySearch(mach, join);
				assertTrue(index > 0 && index < mach.length - 1,
						preset + " must carry Mach " + join + " as an interior regime-join node");
			}
		}
	}

	@Test
	public void testStandardPresetMatchesTheValidatedFlightDomain() {
		SamplingConfiguration expected = SamplingConfiguration.flightDomainDefaults();
		SamplingConfiguration actual = Fidelity.STANDARD.sampling();
		assertArrayEqualsExactly(expected.mach(), actual.mach());
		assertArrayEqualsExactly(expected.alphaRad(), actual.alphaRad());
		assertArrayEqualsExactly(expected.betaRad(), actual.betaRad());
	}

	@Test
	public void testPresetsGrowMonotonicallyInSolverWork() {
		int preview = cellCount(Fidelity.PREVIEW);
		int standard = cellCount(Fidelity.STANDARD);
		int fine = cellCount(Fidelity.FINE);
		assertTrue(preview < standard, "Preview should solve fewer cells than Standard");
		assertTrue(standard < fine, "Fine should solve more cells than Standard");
	}

	@Test
	public void testPreviewGridBuildsAValidatedTableForARocket(@TempDir Path cacheRoot) throws Exception {
		OpenRocketDocument document =
				OpenRocketDocumentFactory.createDocumentFromRocket(TestRockets.makeEstesAlphaIII());
		Simulation simulation = new Simulation(document.getRocket());
		document.addSimulation(simulation);

		SamplingConfiguration sampling = Fidelity.PREVIEW.sampling();
		String settingsHash = PhysicsAeroSettingsFingerprint.hash(
				new PhysicsAeroSettingsFingerprint.Input(sampling,
						List.of("EXPLICIT_REGIME_HANDOFFS_AND_TRANSONIC_REFINEMENT_V1"),
						new PhysicsConfiguration(PhysicsConfiguration.allRegisteredMethods(),
								"ADIABATIC", 0, false, false),
						NumericalTolerances.defaults(),
						"TYPED_GENERATION_FALLBACKS_ONLY",
						PhysicsAeroValidationGate.CODE_VERSION,
						PhysicsAeroValidationGate.REGISTRY_VERSION, null));
		AeroGeometry geometry = new GeometryExtractor().extractWithComponentRoughness(
				simulation.getActiveConfiguration(), "ADIABATIC", settingsHash, false);

		Files.createDirectories(cacheRoot);
		PhysicsAeroTableCache cache = new PhysicsAeroTableCache(cacheRoot);
		List<Integer> progress = new ArrayList<>();

		PerfectGasAir air = new PerfectGasAir();
		AtmosphereState atmosphere = new AtmosphereState(101325, 288.15,
				101325 / (air.gasConstant() * 288.15), air.viscosity(288.15));
		PhysicsAeroTableService.Result result = new PhysicsAeroTableService(cache).buildToCache(
				geometry, sampling.mach(), sampling.alphaRad(), sampling.betaRad(),
				new PoweredFlowState[0], atmosphere, air, settingsHash, new AtomicBoolean(), progress::add);

		assertEquals(PhysicsAeroValidationGate.Status.PASS, result.validation().status(),
				"artifact validation failed: " + result.validation().failures());
		assertEquals(cellCount(Fidelity.PREVIEW), result.table().cells().size());
		assertTrue(Files.isRegularFile(result.tableFile()), "table file should be written to the cache");
		assertTrue(Files.isRegularFile(result.manifestFile()), "manifest should be written next to the table");

		assertTrue(progress.size() > 1, "the build should report incremental progress");
		assertEquals(100, progress.get(progress.size() - 1), "the build should finish at 100%");
		for (int index = 1; index < progress.size(); index++) {
			assertTrue(progress.get(index) >= progress.get(index - 1),
					"progress must never move backwards: " + progress);
		}
		assertTrue(progress.stream().anyMatch(value -> value > 0 && value < 5),
				"the supersonic pre-solve phase should report progress of its own: " + progress);

		simulation.getOptions().setPhysicsAeroTableIdentity(result.table(), result.tableHash());
		simulation.getOptions().setPhysicsAeroMode(PhysicsAeroMode.STRICT);
		PhysicsAeroSettings settings = simulation.getOptions().getPhysicsAeroSettings();
		assertEquals(geometry.geometryHash(), settings.getGeometryHash());

		AerodynamicTable resolved = new PhysicsAeroTableResolver(cache, 0, "ADIABATIC")
				.resolve(simulation.getActiveConfiguration(), settings);
		assertNotNull(resolved);
		assertEquals(result.table().cells().size(), resolved.cells().size(),
				"the cached table should reload with every cell intact");

		double axialAtLowSpeed = resolved.cell(1, 2, 1).coefficients().ca();
		assertTrue(Double.isFinite(axialAtLowSpeed) && axialAtLowSpeed > 0,
				"subsonic axial force coefficient should be positive and finite, was " + axialAtLowSpeed);
	}

	private static int cellCount(Fidelity preset) {
		SamplingConfiguration sampling = preset.sampling();
		return sampling.mach().length * sampling.alphaRad().length * sampling.betaRad().length;
	}

	private static double[] degrees(double[] radians) {
		return Arrays.stream(radians).map(Math::toDegrees).toArray();
	}

	private static void assertArrayEqualsExactly(double[] expected, double[] actual) {
		assertEquals(expected.length, actual.length);
		for (int index = 0; index < expected.length; index++) {
			assertEquals(expected[index], actual[index], 0.0);
		}
	}
}

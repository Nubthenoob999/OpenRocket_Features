package info.openrocket.core.database;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import info.openrocket.core.ServicesForTesting;
import info.openrocket.core.l10n.ResourceBundleTranslator;
import info.openrocket.core.l10n.Translator;
import info.openrocket.core.material.Material;
import info.openrocket.core.material.MaterialGroup;
import info.openrocket.core.plugin.PluginModule;
import info.openrocket.core.startup.Application;
import info.openrocket.core.util.BaseTestCase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MaterialDatabaseTest {
	@BeforeAll
	public static void setUp() throws Exception {
		Module applicationModule = new ServicesForTesting();
		Module debugTranslator = new AbstractModule() {

			@Override
			protected void configure() {
				bind(Translator.class).toInstance(new ResourceBundleTranslator("l10n.messages", Locale.US));
			}

		};
		Module pluginModule = new PluginModule();
		Injector injector = Guice.createInjector(Modules.override(applicationModule).with(debugTranslator),
				pluginModule);
		Application.setInjector(injector);
	}

	@Test
	void testDatabasesInitialization() {
		assertNotNull(Databases.BULK_MATERIAL);
		assertNotNull(Databases.SURFACE_MATERIAL);
		assertNotNull(Databases.LINE_MATERIAL);

		assertFalse(Databases.BULK_MATERIAL.isEmpty());
		assertFalse(Databases.SURFACE_MATERIAL.isEmpty());
		assertFalse(Databases.LINE_MATERIAL.isEmpty());
	}

	/**
	 * Verify the exact number of built-in materials so accidental removals or duplicate additions
	 * cannot silently reduce or expand the choices presented to users.
	 */
	@Test
	void testDefaultMaterialCounts() {
		assertEquals(41, Databases.BULK_MATERIAL.size());
		assertEquals(8, Databases.SURFACE_MATERIAL.size());
		assertEquals(42, Databases.LINE_MATERIAL.size());

		int totalMaterialCount = Databases.BULK_MATERIAL.size() + Databases.SURFACE_MATERIAL.size()
				+ Databases.LINE_MATERIAL.size();
		assertEquals(91, totalMaterialCount);
	}

	@Test
	void testFindMaterialByTypeAndName() {
		Material aluminum = Databases.findMaterial(Material.Type.BULK, "Aluminum");
		assertNotNull(aluminum);
		assertTrue(aluminum.getName().contains("Aluminum"));
		assertEquals(Material.Type.BULK, aluminum.getType());
		assertEquals(2700, aluminum.getDensity(), 0.001);
		assertEquals(68.3e9, aluminum.getYoungsModulus(), 1.0);
		assertEquals(241e6, aluminum.getTensileStrength(), 1.0);
		assertEquals(0.33, aluminum.getPoissonRatio(), 1e-12);
	}

	@Test
	void testResearchedStructuralMaterialProperties() {
		assertStructuralMaterial("Polypropylene, bulk", 946, 0.517e9, 1.50e9, 28e6,
				30.3e6, 0.45);
		assertStructuralMaterial("Aluminum 6061-T6", 2700, 25.68e9, 68.3e9, 241e6,
				Double.NaN, 0.33);
		assertStructuralMaterial("Aluminum 7075-T6", 2810, 26.95e9, 71.7e9, 434e6,
				Double.NaN, 0.33);
		assertStructuralMaterial("Steel 4130 normalized", 7850, 80e9, 205e9, 460e6,
				Double.NaN, 0.29);
		assertStructuralMaterial("Stainless steel 304 annealed", 7900, 76.92e9, 200e9, 210e6,
				Double.NaN, 0.30);
		assertStructuralMaterial("Carbon fiber", 1600, 5.5e9,
				70e9, 800e6, 700e6, 0.05);
		assertStructuralMaterial("Carbon fiber/epoxy fabric (approx. 60% fiber volume)", 1600, 5.5e9,
				70e9, 800e6, 700e6, 0.05);
		assertStructuralMaterial("Fiberglass", 1800, 6.20e9,
				14.37e9, 262e6, 448e6, 0.159);
		assertStructuralMaterial("Fiberglass (G10/FR4 laminate, in-plane)", 1800, 6.20e9,
				14.37e9, 262e6, 448e6, 0.159);
		assertStructuralMaterial("S2Fiberglass", 2000, 3.79e9,
				28.5e9, 551e6, 561e6, 0.138);
		assertStructuralMaterial("Balsa", 170, 0.111e9, 3.0e9, 14e6, 7e6, Double.NaN);
		assertStructuralMaterial("Basswood", 500, 0.331e9, 10.1e9, 60e6,
				32.6e6, Double.NaN);
		assertStructuralMaterial("Birch", 670, 0.945e9, 13.9e9, 114e6, 56.3e6, Double.NaN);
		assertStructuralMaterial("PLA - 100% infill", 1250, 1.24e9, 2.71e9, 50e6,
				Double.NaN, 0.328);
		assertStructuralMaterial("PLA (Verbatim FDM, 100% infill, direction 1)", 1240, 1.24e9, 2.71e9, 50e6,
				Double.NaN, 0.328);
		assertStructuralMaterial("Blue tube", 1200, 2.529e9, 5.516e9, 62.05e6,
				241.3e6, 0.227);
		assertStructuralMaterial("Cardboard", 680, 0.4e9, 1.5e9, 8e6,
				11.05e6, Double.NaN);

		assertIsotropicConsistency("Aluminum", 0.01);
		assertIsotropicConsistency("Aluminum 6061-T6", 0.01);
		assertIsotropicConsistency("Aluminum 7075-T6", 0.01);
		assertIsotropicConsistency("Steel 4130 normalized", 0.01);
		assertIsotropicConsistency("Stainless steel 304 annealed", 0.01);
	}

	@Test
	void testLegacyMaterialReceivesKnownPropertiesWithoutChangingDensity() {
		Material blueTube = Databases.findLegacyMaterial(Material.Type.BULK, "Blue tube", 1300.0,
				null, MaterialGroup.COMPOSITES);
		assertEquals(1300.0, blueTube.getDensity(), 1e-12);
		assertEquals(5.516e9, blueTube.getYoungsModulus(), 1.0);
		assertEquals(2.529e9, blueTube.getInPlaneShearModulus(), 1.0);
		assertEquals(62.05e6, blueTube.getTensileStrength(), 1.0);
		assertEquals(241.3e6, blueTube.getCompressiveStrength(), 1.0);

		Material unrelated = Databases.findLegacyMaterial(Material.Type.BULK, "Blue tube", 500.0,
				0.0, MaterialGroup.COMPOSITES);
		assertTrue(Double.isNaN(unrelated.getYoungsModulus()));
		assertTrue(Double.isNaN(unrelated.getTensileStrength()));
	}

	@Test
	void testFlatElasticCordLinearDensityIncreasesWithWidth() {
		Material cord12 = Databases.findMaterial(Material.Type.LINE,
				"Elastic cord (flat 12 mm, 1/2 in)");
		Material cord19 = Databases.findMaterial(Material.Type.LINE,
				"Elastic cord (flat 19 mm, 3/4 in)");
		Material cord25 = Databases.findMaterial(Material.Type.LINE,
				"Elastic cord (flat 25 mm, 1 in)");

		assertTrue(cord12.getDensity() < cord19.getDensity());
		assertTrue(cord19.getDensity() < cord25.getDensity());
		assertEquals(0.012, cord19.getDensity(), 1e-12);
		assertEquals(0.016, cord25.getDensity(), 1e-12);
	}

	private static void assertIsotropicConsistency(String name, double relativeTolerance) {
		Material material = Databases.findMaterial(Material.Type.BULK, name);
		double expectedShearModulus = material.getYoungsModulus() / (2 * (1 + material.getPoissonRatio()));
		assertEquals(expectedShearModulus, material.getInPlaneShearModulus(),
				expectedShearModulus * relativeTolerance);
	}

	private static void assertStructuralMaterial(String name, double density, double shearModulus,
			double youngsModulus, double tensileStrength, double compressiveStrength, double poissonRatio) {
		Material material = Databases.findMaterial(Material.Type.BULK, name);
		assertNotNull(material);
		assertEquals(density, material.getDensity(), 1e-9);
		assertEquals(shearModulus, material.getInPlaneShearModulus(), 1.0);
		assertEquals(youngsModulus, material.getYoungsModulus(), 1.0);
		assertEquals(tensileStrength, material.getTensileStrength(), 1.0);
		if (Double.isNaN(compressiveStrength)) {
			assertTrue(Double.isNaN(material.getCompressiveStrength()));
		} else {
			assertEquals(compressiveStrength, material.getCompressiveStrength(), 1.0);
		}
		if (Double.isNaN(poissonRatio)) {
			assertTrue(Double.isNaN(material.getPoissonRatio()));
		} else {
			assertEquals(poissonRatio, material.getPoissonRatio(), 1e-12);
		}
	}

	@Test
	void testFindMaterialByTypeNameAndDensity() {
		Material customMaterial = Databases.findMaterial(Material.Type.BULK, "CustomMaterial", 1000, MaterialGroup.PLASTICS);
		assertNotNull(customMaterial);
		assertTrue(customMaterial.getName().contains("CustomMaterial"));
		assertEquals(Material.Type.BULK, customMaterial.getType());
		assertEquals(1000, customMaterial.getDensity(), 0.001);
		assertEquals(MaterialGroup.PLASTICS, customMaterial.getGroup());
		assertTrue(customMaterial.isUserDefined());
	}

	@Test
	void testGetDatabase() {
		assertSame(Databases.BULK_MATERIAL, Databases.getDatabase(Material.Type.BULK));
		assertSame(Databases.SURFACE_MATERIAL, Databases.getDatabase(Material.Type.SURFACE));
		assertSame(Databases.LINE_MATERIAL, Databases.getDatabase(Material.Type.LINE));
	}

	@Test
	void testGetDatabaseInvalidType() {
		assertThrows(NullPointerException.class, () -> {
			Databases.getDatabase(null);
		});
	}
}

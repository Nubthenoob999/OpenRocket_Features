package info.openrocket.core.structures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import com.google.inject.Guice;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.file.GeneralRocketLoader;
import info.openrocket.core.material.Material;
import info.openrocket.core.material.MaterialGroup;
import info.openrocket.core.rocketcomponent.AxialStage;
import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.Bulkhead;
import info.openrocket.core.rocketcomponent.NoseCone;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.Transition;
import info.openrocket.core.rocketcomponent.TubeCoupler;
import info.openrocket.core.rocketcomponent.position.AxialMethod;
import info.openrocket.core.structures.geometry.StructuralComponentExtractor;
import info.openrocket.core.structures.geometry.StructuralComponentExtractor.ExtractedStructuralComponent;
import info.openrocket.core.structures.geometry.BulkheadGeometry;
import info.openrocket.core.structures.geometry.CenteringRingGeometry;
import info.openrocket.core.structures.calculators.BulkheadCalculator;
import info.openrocket.core.structures.calculators.CenteringRingCalculator;
import info.openrocket.core.structures.loads.AxialLoadModel;
import info.openrocket.core.structures.loads.FlightLoadCase;
import info.openrocket.core.structures.loads.FlightLoadSeries;
import info.openrocket.core.structures.loads.SimulationLoadExtractor;
import info.openrocket.core.structures.materials.StructuralMaterial;
import info.openrocket.core.plugin.PluginModule;
import info.openrocket.core.startup.Application;
import info.openrocket.core.startup.CoreModule;
import info.openrocket.core.util.BaseTestCase;

class StructuresAnalysisServiceTest extends BaseTestCase {
	@Test
	void huntsvilleSavedFlightDataProducesStructuralResults() throws Exception {
		// Other tests install reduced injectors with intentionally tiny motor databases.
		// Restore the production core services so the saved Huntsville motor and flight
		// data can be resolved regardless of suite order.
		CoreModule coreModule = new CoreModule();
		Application.setInjector(Guice.createInjector(coreModule, new PluginModule()));
		coreModule.startLoader();
		Application.getMotorSetDatabase(); // Provider blocks until the asynchronous motor load completes.

		Path rocketFile = Path.of("src", "test", "java", "info", "openrocket", "core",
				"Rockets_NewStuff", "NASA_26_Huntsville_DOL_ROM.ork");
		Assumptions.assumeTrue(Files.isRegularFile(rocketFile),
				"requires the optional Huntsville saved-flight fixture");
		OpenRocketDocument document = new GeneralRocketLoader(rocketFile.toFile()).load();
		FlightLoadSeries loads = new SimulationLoadExtractor().extract(document.getSimulations().get(0));
		List<ExtractedStructuralComponent> extracted = new StructuralComponentExtractor().extract(document.getRocket());
		List<ExtractedStructuralComponent> selected = extracted
				.stream()
				.filter(ExtractedStructuralComponent::isSelectedByDefault)
				.collect(Collectors.toList());

		assertFalse(loads.isEmpty(), "The saved REAL LAUNCH Post simulation should contain flight loads");
		List<ExtractedStructuralComponent> s2Components = extracted.stream()
				.filter(component -> component.getStructuralMaterial().getName().equals("S2Fiberglass"))
				.collect(Collectors.toList());
		assertFalse(s2Components.isEmpty(), "The Huntsville airframe should contain S2 fiberglass components");
		assertTrue(s2Components.stream()
				.filter(component -> component.getType().equals("Body tube"))
				.allMatch(ExtractedStructuralComponent::isSelectedByDefault),
				"Legacy S2 fiberglass body tubes should receive the sourced screening properties");
		assertTrue(extracted.stream()
				.filter(component -> component.getStructuralMaterial().getName().equals("Thick CR"))
				.noneMatch(ExtractedStructuralComponent::isSelectedByDefault),
				"The custom Thick CR has no strength data and should require explicit selection/input");
		StructuresReport report = new StructuresAnalysisService().analyze(selected, loads);
		List<String> insufficient = report.getResults().stream()
				.filter(result -> result.getStatus() == StructuresStatus.INSUFFICIENT_DATA)
				.map(result -> result.getComponentName() + " / " + result.getAnalysisType() + ": "
						+ String.join(", ", result.getWarnings()))
				.collect(Collectors.toList());
		assertFalse(report.getResults().isEmpty(), "The default selection should produce structural checks");
		assertTrue(report.getResults().stream()
				.anyMatch(result -> result.getComponentName().equals("Forward Bodytube")
						&& result.getAnalysisType().equals("Tube stress")),
				"The regression must exercise an S2 fiberglass tube, not merely skip it");
		assertTrue(insufficient.isEmpty(), "Unexpected insufficient-data results: " + insufficient);
		assertTrue(report.getResults().stream()
				.allMatch(result -> Double.isFinite(result.getFactorOfSafety())),
				"Every default Huntsville result should have a finite factor of safety");
		List<StructuresResult> plateResults = report.getResults().stream()
				.filter(result -> result.getAnalysisType().equals("Bulkhead capacity")
						|| result.getAnalysisType().equals("Centering ring capacity"))
				.collect(Collectors.toList());
		assertFalse(plateResults.isEmpty(), "The saved rocket should exercise bulkhead/centering-ring checks");
		assertTrue(plateResults.stream().allMatch(result -> result.getValues().get("appliedLoad_N") < 4000.0),
				"Recovery-event drag must not be reused as a powered-ascent internal plate load");
		assertTrue(plateResults.stream()
				.filter(result -> result.getFactorOfSafety() < 1.0)
				.allMatch(result -> result.getStatus() == StructuresStatus.FAIL),
				"Sub-unity plate factors of safety must be reported as failures, not clamped or hidden");
		List<StructuresResult> bondedStacks = plateResults.stream()
				.filter(result -> result.getValues().getOrDefault("bondedLayerCount", 0.0) == 2.0)
				.collect(Collectors.toList());
		assertEquals(5, bondedStacks.size(), "The legacy model contains five two-layer physical bulkheads");
		assertTrue(bondedStacks.stream().allMatch(result -> result.getFactorOfSafety() >= 2.0),
				"Bonded bulkhead stacks should pass the workbook FoS 2 criterion: " + bondedStacks.stream()
						.map(result -> result.getComponentName() + " FoS=" + result.getFactorOfSafety())
						.collect(Collectors.toList()));

		double poweredLoad = AxialLoadModel.conservativeAxialLoad(loads.getMaxPoweredAxialLoadCase());
		StructuralMaterial workbookBirch = workbookBalticBirch();
		List<StructuresResult> workbookPlateResults = new ArrayList<>();
		for (ExtractedStructuralComponent component : selected) {
			if (component.getGeometry() instanceof BulkheadGeometry) {
				workbookPlateResults.add(new BulkheadCalculator().calculate(
						(BulkheadGeometry) component.getGeometry(), workbookBirch, poweredLoad, 2.0));
			} else if (component.getGeometry() instanceof CenteringRingGeometry) {
				workbookPlateResults.add(new CenteringRingCalculator().calculate(
						(CenteringRingGeometry) component.getGeometry(), workbookBirch, poweredLoad, 2.0));
			}
		}
		List<String> workbookPlateFailures = workbookPlateResults.stream()
				.filter(result -> result.getFactorOfSafety() < 1.0)
				.map(result -> result.getComponentName() + " FoS=" + result.getFactorOfSafety())
				.collect(Collectors.toList());
		assertTrue(workbookPlateFailures.isEmpty(),
				"NASA plate geometries should exceed FoS 1 with the workbook Baltic-birch properties: "
						+ workbookPlateFailures);
	}

	@Test
	void unsupportedContextComponentsDoNotProduceInsufficientDataByDefault() {
		Rocket rocket = new Rocket();
		AxialStage stage = new AxialStage();
		rocket.addChild(stage);

		NoseCone nose = new NoseCone(Transition.Shape.OGIVE, 0.30, 0.05);
		nose.setName("Nose");
		stage.addChild(nose);

		BodyTube body = new BodyTube(1.0, 0.05, 0.002);
		body.setName("Body");
		body.setMaterial(completeMaterial());
		stage.addChild(body);

		TubeCoupler coupler = new TubeCoupler();
		coupler.setName("Coupler");
		coupler.setMaterial(completeMaterial());
		body.addChild(coupler);

		List<ExtractedStructuralComponent> extracted = new StructuralComponentExtractor().extract(rocket);
		ExtractedStructuralComponent noseComponent = componentNamed(extracted, "Nose");
		ExtractedStructuralComponent couplerComponent = componentNamed(extracted, "Coupler");
		assertTrue(noseComponent.isSelectedByDefault());
		assertFalse(couplerComponent.isSelectedByDefault());

		List<ExtractedStructuralComponent> selected = extracted.stream()
				.filter(ExtractedStructuralComponent::isSelectedByDefault)
				.collect(Collectors.toList());
		StructuresReport report = new StructuresAnalysisService().analyze(selected, representativeLoads());

		assertEquals(2, report.getResults().size());
		assertTrue(report.getResults().stream()
				.allMatch(result -> result.getStatus() != StructuresStatus.INSUFFICIENT_DATA));
		assertTrue(report.getResults().stream()
				.allMatch(result -> result.getComponentName().equals("Body")));
	}

	@Test
	void contiguousBulkheadLayersAreAnalyzedAsOneBondedStack() {
		Rocket rocket = new Rocket();
		AxialStage stage = new AxialStage();
		rocket.addChild(stage);
		BodyTube body = new BodyTube(1.0, 0.05, 0.002);
		stage.addChild(body);

		Bulkhead firstLayer = bulkheadLayer("Bonded bulkhead", 0.20, 0.004);
		Bulkhead secondLayer = bulkheadLayer("Bonded bulkhead", 0.204, 0.004);
		body.addChild(firstLayer);
		body.addChild(secondLayer);

		List<ExtractedStructuralComponent> layers = new StructuralComponentExtractor().extract(rocket).stream()
				.filter(component -> component.getGeometry() instanceof BulkheadGeometry)
				.collect(Collectors.toList());
		StructuresReport report = new StructuresAnalysisService().analyze(layers, representativeLoads());

		assertEquals(1, report.getResults().size());
		StructuresResult stackResult = report.getResults().get(0);
		assertEquals("Bonded bulkhead (2-layer stack)", stackResult.getComponentName());
		assertEquals(2.0, stackResult.getValues().get("bondedLayerCount"), 0.0);
		assertEquals(0.008, stackResult.getValues().get("effectiveThickness_m"), 1.0e-12);

		double appliedLoad = AxialLoadModel.conservativeAxialLoad(representativeLoads().getMaxPoweredAxialLoadCase());
		BulkheadGeometry singleGeometry = (BulkheadGeometry) layers.get(0).getGeometry();
		StructuresResult singleResult = new BulkheadCalculator().calculate(singleGeometry,
				layers.get(0).getStructuralMaterial(), appliedLoad, StructuresToolModel.DEFAULT_BULKHEAD_FOS);
		assertEquals(4.0 * singleResult.getFactorOfSafety(), stackResult.getFactorOfSafety(), 1.0e-10);
	}

	private static ExtractedStructuralComponent componentNamed(List<ExtractedStructuralComponent> components,
			String name) {
		return components.stream()
				.filter(component -> component.getComponentName().equals(name))
				.findFirst()
				.orElseThrow();
	}

	private static Material completeMaterial() {
		return Material.newMaterial(Material.Type.BULK, "Complete test material", 1200.0, 2.0e9,
				5.0e9, 60.0e6, 80.0e6, 0.25, MaterialGroup.COMPOSITES, true, true);
	}

	private static Bulkhead bulkheadLayer(String name, double axialOffset, double thickness) {
		Bulkhead bulkhead = new Bulkhead();
		bulkhead.setName(name);
		bulkhead.setOuterRadius(0.048);
		bulkhead.setLength(thickness);
		bulkhead.setAxialMethod(AxialMethod.TOP);
		bulkhead.setAxialOffset(axialOffset);
		bulkhead.setMaterial(completeMaterial());
		return bulkhead;
	}

	private static StructuralMaterial workbookBalticBirch() {
		return new StructuralMaterial("Workbook Baltic Birch", 1.95e6 * 6894.757293168,
				88000.0 * 6894.757293168, 975000.0 * 6894.757293168, Double.NaN,
				17400.0 * 6894.757293168, 680.0, 0.431, 1750.0 * 6894.757293168);
	}

	private static FlightLoadSeries representativeLoads() {
		return new FlightLoadSeries(List.of(new FlightLoadCase(
				0.5, 100.0, 100.0 / 340.0, 1.2, 6000.0, 10.0, 0.5,
				100.0, 20.0, 10.0, 0.05, 101325.0, 340.0)));
	}
}

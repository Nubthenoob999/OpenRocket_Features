package info.openrocket.core.structures.calculators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import info.openrocket.core.material.Material;
import info.openrocket.core.material.MaterialGroup;
import info.openrocket.core.structures.StructuresResult;
import info.openrocket.core.structures.StructuresStatus;
import info.openrocket.core.structures.geometry.BulkheadGeometry;
import info.openrocket.core.structures.geometry.CenteringRingGeometry;
import info.openrocket.core.structures.geometry.FinGeometryStructural;
import info.openrocket.core.structures.geometry.NoseConeGeometryStructural;
import info.openrocket.core.structures.geometry.TubeGeometry;
import info.openrocket.core.structures.loads.AxialLoadModel;
import info.openrocket.core.structures.loads.FlightLoadCase;
import info.openrocket.core.structures.loads.FlightLoadSeries;
import info.openrocket.core.structures.loads.NormalForceLoadModel;
import info.openrocket.core.structures.loads.StructuralLoadDiagram;
import info.openrocket.core.structures.materials.CompositeMaterialEstimate;
import info.openrocket.core.structures.materials.MaterialLibrary;
import info.openrocket.core.structures.materials.StructuralMaterial;

public class StructuresCalculatorsTest {
	private static final StructuralMaterial ALUMINUM = new StructuralMaterial(
			"Test aluminum", 70.0e9, 26.0e9, 300.0e6, 350.0e6, 280.0e6, 2700.0, 0.33);

	@Test
	public void tubeAreaAndInertiaUseSiGeometry() {
		TubeGeometry tube = new TubeGeometry(0.05, 0.045, 0.005, 1.0, 1.0, 0.0, "Tube");

		assertEquals(Math.PI * (0.05 * 0.05 - 0.045 * 0.045), tube.getArea(), 1.0e-12);
		assertEquals(Math.PI / 4.0 * (Math.pow(0.05, 4) - Math.pow(0.045, 4)),
				tube.getSecondMomentOfArea(), 1.0e-14);
	}

	@Test
	public void tubeBucklingSelectsEulerForSlenderTube() {
		TubeGeometry tube = new TubeGeometry(0.05, 0.048, 0.002, 5.0, 5.0, 0.0, "Long tube");

		StructuresResult result = new TubeBucklingCalculator().calculate(tube, ALUMINUM, 1.0, 100.0, 2.0);

		assertEquals("Euler", result.getGoverningCaseDescription());
		assertTrue(result.getValues().get("criticalLoad_N") > 0);
	}

	@Test
	public void tubeBucklingSelectsJohnsonForStockyTube() {
		TubeGeometry tube = new TubeGeometry(0.05, 0.045, 0.005, 0.2, 0.2, 0.0, "Short tube");

		StructuresResult result = new TubeBucklingCalculator().calculate(tube, ALUMINUM, 1.0, 1000.0, 2.0);

		assertEquals("Johnson", result.getGoverningCaseDescription());
		assertTrue(result.getValues().get("criticalLoad_N") > 0);
	}

	@Test
	public void conservativeAxialLoadIsForceConsistent() {
		FlightLoadCase loadCase = loadCase(0.0, 100.0, 1.2, 10.0, 500.0, 80.0, 30.0, 0.0);

		assertEquals(500.0 + 80.0 + 10.0 * 30.0, AxialLoadModel.conservativeAxialLoad(loadCase), 1.0e-9);
	}

	@Test
	public void tubeStressUsesAxialAndBendingStress() {
		TubeGeometry tube = new TubeGeometry(0.05, 0.045, 0.005, 1.0, 1.0, 0.0, "Tube");
		NoseConeGeometryStructural nose = new NoseConeGeometryStructural(0.3, 0.05, 0.0, "Conical", "Nose");
		FinGeometryStructural fin = fin();
		FlightLoadSeries loads = new FlightLoadSeries(Arrays.asList(loadCase(0.5, 80.0, 1.225, 5.0, 100.0, 20.0, 5.0, 0.1)));

		StructuresResult result = new TubeStressCalculator().calculate(tube, ALUMINUM, loads, nose, fin, 2.0);

		assertTrue(result.getValues().get("maxStress_Pa") > 0);
		assertTrue(result.getValues().get("bendingMoment_Nm") > 0);
	}

	@Test
	public void finNormalForceAndCpAreFinite() {
		TubeGeometry tube = new TubeGeometry(0.05, 0.045, 0.005, 1.0, 1.0, 0.0, "Tube");
		NoseConeGeometryStructural nose = new NoseConeGeometryStructural(0.3, 0.05, 0.0, "Ogive", "Nose");
		FinGeometryStructural fin = fin();

		assertTrue(NormalForceLoadModel.finCnAlpha(fin, tube.getOuterRadius()) > 0);
		assertTrue(Double.isFinite(NormalForceLoadModel.approximateCp(tube, nose, fin)));
	}

	@Test
	public void finFlutterReturnsPositiveThicknessTarget() {
		FlightLoadSeries loads = new FlightLoadSeries(Arrays.asList(loadCase(0.0, 120.0, 1.0, 5.0, 0.0, 0.0, 0.0, 0.0)));

		StructuresResult result = new FinFlutterCalculator().calculate(fin(), ALUMINUM, loads, 1.5);

		assertTrue(result.getValues().get("flutterVelocity_mps") > 0);
		assertTrue(result.getValues().get("minimumThicknessForTargetFoS_m") > 0);
	}

	@Test
	public void finRootStressUsesRootSectionModulus() {
		FlightLoadSeries loads = new FlightLoadSeries(Arrays.asList(loadCase(0.0, 90.0, 1.225, 5.0, 0.0, 0.0, 0.0, 0.1)));

		StructuresResult result = new FinRootStressCalculator().calculate(fin(), ALUMINUM, loads, 0.05, 2.0);

		assertTrue(result.getValues().get("rootStress_Pa") > 0);
	}

	@Test
	public void shearAndBendingMomentDiagramUsesNormalForcePointLoads() {
		TubeGeometry tube = new TubeGeometry(0.05, 0.045, 0.005, 1.0, 1.0, 0.2, "Tube");
		NoseConeGeometryStructural nose = new NoseConeGeometryStructural(0.3, 0.05, 0.0, "Ogive", "Nose");
		FinGeometryStructural fin = fin();
		FlightLoadCase loadCase = loadCase(0.5, 80.0, 1.225, 5.0, 100.0, 20.0, 5.0, 0.1);

		StructuralLoadDiagram diagram = new ShearBendingMomentDiagramCalculator().calculate(tube, nose, fin, loadCase, 10);

		assertEquals(10, diagram.getPoints().size());
		assertTrue(diagram.getPoints().get(9).getShearForce() > 0);
		assertTrue(diagram.getPoints().get(9).getBendingMoment() > 0);
	}

	@Test
	public void bulkheadCapacityDoesNotDoubleDivideFactorOfSafety() {
		StructuralMaterial material = new StructuralMaterial("Test", 1.0, 1.0, 100.0e6, 100.0e6, 100.0e6, 1.0, null);
		BulkheadGeometry bulkhead = new BulkheadGeometry(0.1, 0.01, 0.0, "Bulkhead");

		StructuresResult result = new BulkheadCalculator().calculate(bulkhead, material, 1000.0, 2.0);

		double expectedBendingCapacity = Math.PI * (100.0e6 / 2.0) * 0.01 * 0.01;
		assertEquals(expectedBendingCapacity, result.getValues().get("governingCapacity_N"), 1.0e-6);
		assertEquals(expectedBendingCapacity / 1000.0, result.getFactorOfSafety(), 1.0e-9);
	}

	@Test
	public void centeringRingCapacityScalesWithNumberOfRings() {
		StructuralMaterial material = new StructuralMaterial("Test", 1.0, 1.0, 100.0e6, 100.0e6, 100.0e6, 1.0, null);
		CenteringRingGeometry ring = new CenteringRingGeometry(0.1, 0.03, 0.01, 2, 0.0, "Rings");

		StructuresResult result = new CenteringRingCalculator().calculate(ring, material, 1000.0, 2.0);

		assertEquals(2.0 * result.getValues().get("singleRingCapacity_N"),
				result.getValues().get("totalCapacity_N"), 1.0e-6);
	}

	@Test
	public void fastenerCalculatorReportsTensileAndShearStress() {
		StructuresResult result = new FastenerCalculator().calculate("Fasteners", 0.004, 2, 0.003, 4, 1,
				1000.0, 500.0, ALUMINUM, 2.0);

		assertTrue(result.getValues().get("tensileStress_Pa") > 0);
		assertTrue(result.getValues().get("shearStress_Pa") > 0);
	}

	@Test
	public void couplerSizingSolvesForEqualBendingStiffness() {
		StructuresResult result = new CouplerSizingCalculator().calculate("Coupler", 0.1, 0.09, 70.0e9, 70.0e9, 1.0);

		assertTrue(result.getValues().get("requiredWallThickness_m") > 0);
		assertEquals(StructuresStatus.PASS, result.getStatus());
	}

	@Test
	public void compositeRuleOfMixturesEstimateIsFinite() {
		CompositeMaterialEstimate estimate = new CompositePropertyCalculator().calculate(0.4, 1.0, 1800.0, 1200.0,
				1000.0e6, 70.0e6, 70.0e9, 3.0e9, 0.8, 0.5, 0.6, 0.8);

		assertTrue(estimate.getFiberVolumeFraction() > 0);
		assertTrue(estimate.getEstimatedTensileModulus() > 0);
	}

	@Test
	public void invalidInputsReturnInsufficientData() {
		TubeGeometry invalidTube = new TubeGeometry(0.05, 0.05, 0.0, 1.0, 1.0, 0.0, "Bad tube");
		StructuresResult result = new TubeBucklingCalculator().calculate(invalidTube, ALUMINUM, 1.0, 1000.0, 2.0);

		assertEquals(StructuresStatus.INSUFFICIENT_DATA, result.getStatus());
	}

	@Test
	public void componentMaterialDoesNotReceiveNameMatchedStructuralOverrides() {
		Material componentMaterial = Material.newMaterial(Material.Type.BULK, "Carbon fiber", 1780.0, 4.14e9,
				65.0e9, 600.0e6, 450.0e6, 0.30, MaterialGroup.COMPOSITES, true, true);

		StructuralMaterial structuralMaterial = MaterialLibrary.fromOpenRocketMaterial(componentMaterial);

		assertEquals(1780.0, structuralMaterial.getDensity(), 1.0e-12);
		assertEquals(4.14e9, structuralMaterial.getShearModulus(), 1.0e-3);
		assertEquals(65.0e9, structuralMaterial.getYoungsModulus(), 1.0e-3);
		assertEquals(600.0e6, structuralMaterial.getYieldStrength(), 1.0e-3);
		assertEquals(450.0e6, structuralMaterial.getCompressiveStrength(), 1.0e-3);
		assertEquals(0.30, structuralMaterial.getPoissonRatio(), 1.0e-12);
	}

	private static FinGeometryStructural fin() {
		return new FinGeometryStructural(3, 0.20, 0.10, 0.10, 0.03, 0.005, 0.8, 0.015, "Fins");
	}

	private static FlightLoadCase loadCase(double time, double velocity, double density, double mass, double thrust,
			double drag, double acceleration, double angleOfAttack) {
		double q = 0.5 * density * velocity * velocity;
		return new FlightLoadCase(time, velocity, velocity / 340.0, density, q, mass, 0.6, thrust, drag,
				acceleration, angleOfAttack, 101325.0, 340.0);
	}
}

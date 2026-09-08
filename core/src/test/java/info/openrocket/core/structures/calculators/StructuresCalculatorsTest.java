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
	private static final double INCH_TO_METER = 0.0254;
	private static final double PSI_TO_PASCAL = 6894.757293168;
	private static final double POUND_FORCE_TO_NEWTON = 4.4482216152605;
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
	public void tubeBucklingUsesCompressiveLimitWhenAvailable() {
		StructuralMaterial balsa = new StructuralMaterial(
				"Balsa", 3.0e9, 0.111e9, 14.0e6, Double.NaN, 7.0e6, 170.0, null);
		TubeGeometry tube = new TubeGeometry(0.05, 0.045, 0.005, 0.2, 0.2, 0.0, "Balsa tube");

		StructuresResult result = new TubeBucklingCalculator().calculate(tube, balsa, 1.0, 1000.0, 2.0);

		assertEquals(7.0e6, balsa.getColumnStrength(), 1.0e-6);
		assertEquals(7.0e6, balsa.getBestAllowableStress(), 1.0e-6);
		assertTrue(result.getValues().get("criticalLoad_N") > 0.0);
	}

	@Test
	public void workbookV4TubeBucklingFixtureMatchesInSi() {
		TubeGeometry tube = new TubeGeometry(inches(4.024 / 2.0), inches(3.9 / 2.0), inches(0.062),
				inches(15.0), inches(15.0), 0.0, "Workbook tube");
		StructuralMaterial blueTube = new StructuralMaterial("Workbook Blue Tube", psi(350000.0),
				Double.NaN, psi(3850.0), Double.NaN, psi(3850.0), Double.NaN, null);

		StructuresResult result = new TubeBucklingCalculator().calculate(tube, blueTube, 1.0,
				poundsForce(563.177), 2.0);

		assertEquals("Johnson", result.getGoverningCaseDescription());
		assertEquals(poundsForce(2876.192007), result.getValues().get("criticalLoad_N"), 0.02);
		assertEquals(5.107083575, result.getFactorOfSafety(), 1.0e-6);
	}

	@Test
	public void conservativeAxialLoadIsForceConsistent() {
		FlightLoadCase loadCase = loadCase(0.0, 100.0, 1.2, 10.0, 500.0, 80.0, 30.0, 0.0);

		assertEquals(500.0 + 80.0 + 10.0 * AxialLoadModel.STANDARD_GRAVITY,
				AxialLoadModel.conservativeAxialLoad(loadCase), 1.0e-9);
	}

	@Test
	public void poweredAxialEnvelopeExcludesRecoveryDragSpike() {
		FlightLoadCase powered = loadCase(1.0, 100.0, 1.2, 10.0, 1600.0, 20.0, 80.0, 0.0);
		FlightLoadCase recovery = loadCase(40.0, -20.0, 1.2, 10.0, 0.0, 15000.0, -12.0, 0.0);
		FlightLoadSeries loads = new FlightLoadSeries(Arrays.asList(powered, recovery));

		assertEquals(recovery, loads.getMaxAxialLoadCase());
		assertEquals(powered, loads.getMaxPoweredAxialLoadCase());
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
	public void finFlutterMatchesNacaEquation18InSi() {
		FinGeometryStructural workbookFin = new FinGeometryStructural(4, inches(6.5), 0.0, inches(2.5),
				inches(5.777), inches(0.117), 0.0, squareInches(8.125), "Workbook fin");

		double velocity = FinFlutterCalculator.flutterVelocity(workbookFin, psi(500000.0),
				1117.342923 * 0.3048, psi(14.7), inches(0.117));

		assertEquals(323.56790484025294, velocity, 1.0e-9);
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
		StructuralMaterial material = new StructuralMaterial("Workbook Baltic Birch", 1.0, 1.0,
				psi(975000.0), Double.NaN, psi(17400.0), 1.0, 0.431, psi(1750.0));
		BulkheadGeometry bulkhead = new BulkheadGeometry(inches(1.95), inches(0.2), 0.0, "Bulkhead");

		StructuresResult result = new BulkheadCalculator().calculate(bulkhead, material, poundsForce(100.0), 2.0);

		assertEquals(0.0720937655, result.getValues().get("L6"), 1.0e-10);
		assertEquals(0.1350857573, result.getValues().get("L9"), 1.0e-10);
		assertEquals(4.484065385, result.getValues().get("C4"), 1.0e-9);
		assertEquals(6.324966796, result.getValues().get("C7"), 1.0e-9);
		assertEquals(-0.03339440497, result.getValues().get("alphaM"), 1.0e-11);
		assertEquals(poundsForce(699.5351701), result.getValues().get("designGoverningCapacity_N"), 1.0e-5);
		assertEquals(13.990703402, result.getFactorOfSafety(), 1.0e-8);
		assertEquals(StructuresStatus.PASS, result.getStatus());
	}

	@Test
	public void centeringRingCapacityScalesWithNumberOfRings() {
		StructuralMaterial material = new StructuralMaterial("Workbook Baltic Birch", 1.0, 1.0,
				psi(975000.0), Double.NaN, psi(17400.0), 1.0, 0.431, psi(1750.0));
		CenteringRingGeometry ring = new CenteringRingGeometry(inches(1.95), inches(0.77), inches(0.117),
				2, 0.0, "Rings");

		StructuresResult result = new CenteringRingCalculator().calculate(ring, material, poundsForce(100.0), 2.0);

		assertEquals(2.0 * result.getValues().get("singleRingCapacity_N"),
				result.getValues().get("totalCapacity_N"), 1.0e-6);
		assertEquals(0.1001308008, result.getValues().get("L6"), 1.0e-10);
		assertEquals(0.3099381309, result.getValues().get("L9"), 1.0e-10);
		assertEquals(-0.3099381309, result.getValues().get("alphaM"), 1.0e-10);
		assertEquals(poundsForce(317.7830243), result.getValues().get("designTotalCapacity_N"), 1.0e-5);
		assertEquals(6.355660486, result.getFactorOfSafety(), 1.0e-8);
		assertEquals(StructuresStatus.PASS, result.getStatus());
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
	public void workbookV4CouplerFixtureMatchesEqualStiffnessResult() {
		StructuresResult result = new CouplerSizingCalculator().calculate("Workbook coupler", inches(4.024),
				inches(3.9), 1.0, 1.0, 1.0);

		assertEquals(inches(0.06855091635), result.getValues().get("requiredWallThickness_m"), 1.0e-10);
		assertEquals(inches(3.762898167), result.getValues().get("couplerID_m"), 1.0e-10);
	}

	@Test
	public void compositeRuleOfMixturesEstimateIsFinite() {
		CompositeMaterialEstimate estimate = new CompositePropertyCalculator().calculate(0.4, 1.0, 1800.0, 1200.0,
				1000.0e6, 70.0e6, 70.0e9, 3.0e9, 0.8, 0.5, 0.6, 0.8);

		assertTrue(estimate.getFiberVolumeFraction() > 0);
		assertTrue(estimate.getEstimatedTensileModulus() > 0);
		double fiberVolumeFraction = estimate.getFiberVolumeFraction();
		assertEquals(0.5 * 0.8 * (fiberVolumeFraction * 70.0e9 + (1.0 - fiberVolumeFraction) * 3.0e9),
				estimate.getEstimatedTensileModulus(), 1.0e-3);
	}

	@Test
	public void workbookV4S2GlassConstituentFixtureMatchesRuleOfMixtures() {
		CompositeMaterialEstimate estimate = new CompositePropertyCalculator().calculate(
				1.4624, 1.4624 + 0.64, 1.4624, 0.64,
				670000.0, 10900.0, 12.9e6, 211000.0,
				0.6, 0.5, 0.6, 1.15);

		assertEquals(0.5, estimate.getFiberVolumeFraction(), 1.0e-12);
		assertEquals(102135.0, estimate.getEstimatedTensileStrength(), 1.0e-9);
		assertEquals(1966650.0, estimate.getEstimatedTensileModulus(), 1.0e-9);
		assertEquals(61281.0, estimate.getEstimatedCompressionStrength(), 1.0e-9);
		assertEquals(117455.25, estimate.getEstimatedFlexuralStrength(), 1.0e-9);
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

	private static double inches(double value) {
		return value * INCH_TO_METER;
	}

	private static double squareInches(double value) {
		return value * INCH_TO_METER * INCH_TO_METER;
	}

	private static double psi(double value) {
		return value * PSI_TO_PASCAL;
	}

	private static double poundsForce(double value) {
		return value * POUND_FORCE_TO_NEWTON;
	}
}

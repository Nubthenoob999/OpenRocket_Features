package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.physicsaero.api.PhysicalTerm;
import info.openrocket.core.aerodynamics.physicsaero.fin.DatcomFinLiftModel;
import info.openrocket.core.aerodynamics.physicsaero.fin.FinGeometryAdapter;
import info.openrocket.core.aerodynamics.physicsaero.fin.FinLeadingEdgePressureDragModel;
import info.openrocket.core.aerodynamics.physicsaero.fin.FinStrip;
import info.openrocket.core.aerodynamics.physicsaero.fin.FinStripDiscretizer;
import info.openrocket.core.aerodynamics.physicsaero.fin.FinTrailingEdgeBaseDragModel;
import info.openrocket.core.aerodynamics.physicsaero.fin.FinWaveDragModel;
import info.openrocket.core.aerodynamics.physicsaero.fin.FinResult;
import info.openrocket.core.aerodynamics.physicsaero.fin.FinSectionPanelGeometry;
import info.openrocket.core.aerodynamics.physicsaero.fin.SingleWedgeWaveDragModel;
import info.openrocket.core.aerodynamics.physicsaero.fin.SupersonicFinSolver;
import info.openrocket.core.aerodynamics.physicsaero.fin.SymmetricSectionWaveDragFallbackModel;
import info.openrocket.core.aerodynamics.physicsaero.flow.AtmosphereState;
import info.openrocket.core.aerodynamics.physicsaero.flow.FlowCondition;
import info.openrocket.core.aerodynamics.physicsaero.force.ForceContribution;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.PerfectGasAir;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroComponent;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.FinGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.GeometryStation;
import info.openrocket.core.aerodynamics.physicsaero.geometry.ReferenceGeometry;
import info.openrocket.core.util.Coordinate;

class FinProfileCorrectionsTest {
	private static final double THICKNESS_RATIO = 0.04;

	@Test
	void explicitThicknessRatioStaysConstantAcrossTaperedStripsAndAbsoluteThicknessStillWorks() {
		AeroComponent ratioComponent = finComponent("FLAT_PLATE", 1, 0.05,
				Map.of("thicknessM", 0.003, "thicknessRatio", THICKNESS_RATIO));
		List<FinStrip> ratioStrips = strips(ratioComponent);
		assertTrue(ratioStrips.stream().allMatch(
				strip -> Math.abs(strip.thicknessToChord() - THICKNESS_RATIO) < 1e-14));

		double absoluteThickness = 0.003;
		AeroComponent absoluteComponent = finComponent("FLAT_PLATE", 1, 0.05,
				Map.of("thicknessM", absoluteThickness));
		List<FinStrip> absoluteStrips = strips(absoluteComponent);
		assertTrue(absoluteStrips.stream().allMatch(strip -> Math.abs(
				strip.thicknessToChord() * strip.chordM() - absoluteThickness) < 1e-14));
		assertNotEquals(absoluteStrips.get(0).thicknessToChord(),
				absoluteStrips.get(absoluteStrips.size() - 1).thicknessToChord());
	}

	@Test
	void fullChordAsymmetricSingleWedgeIsOneHalfOfDoubleWedgeCoefficient() {
		FinWaveDragModel model = new FinWaveDragModel();
		for (double mach : new double[] { 1.5, 2.0, 2.3 }) {
			assertEquals(model.linearizedDiamondCoefficient(mach, THICKNESS_RATIO) / 2,
					model.linearizedSingleWedgeCoefficient(mach, THICKNESS_RATIO), 1e-15);
		}
	}

	@Test
	void finiteLeadingEdgeBevelUsesAngleThicknessAndSweepRatherThanWholeChordRamp() {
		FinWaveDragModel model = new FinWaveDragModel();
		double mach = 2;
		double bevel = Math.toRadians(20);
		double sweep = Math.toRadians(60);
		double expected = 2 * bevel * THICKNESS_RATIO * Math.cos(sweep)
				/ Math.sqrt(mach * mach - 1);
		assertEquals(expected, model.linearizedAsymmetricBevelCoefficient(
				mach, THICKNESS_RATIO, bevel, sweep), 1e-15);
		assertTrue(expected > model.linearizedSingleWedgeCoefficient(mach, THICKNESS_RATIO));
	}

	@Test
	void squareAndRoundedLeadingEdgesOwnProjectedForePressureDrag() {
		FinLeadingEdgePressureDragModel model = new FinLeadingEdgePressureDragModel();
		AeroComponent square = finComponent("FLAT_PLATE", 4, 0,
				Map.of("thicknessM", 0.003));
		AeroComponent rounded = finComponent("ROUNDED_LEADING_EDGE", 4, 0,
				Map.of("thicknessM", 0.003));
		double referenceArea = Math.PI * 0.05 * 0.05;

		assertTrue(model.dragCoefficient(square, 0, referenceArea) > 0);
		assertEquals(0, model.dragCoefficient(rounded, 0, referenceArea), 0);
		double justBelow = model.dragCoefficient(rounded, 0.9 - 1e-7, referenceArea);
		double justAbove = model.dragCoefficient(rounded, 0.9 + 1e-7, referenceArea);
		assertEquals(justBelow, justAbove, 3e-4);
		assertTrue(model.dragCoefficient(rounded, 1.2, referenceArea)
				< model.dragCoefficient(square, 1.2, referenceArea));

		AeroComponent swept = finComponent("FLAT_PLATE", 4, 0.10,
				Map.of("thicknessM", 0.003));
		assertEquals(0.5 * model.dragCoefficient(square, 0.5, referenceArea),
				model.dragCoefficient(swept, 0.5, referenceArea), 1e-14);
	}

	@Test
	void symmetricDiamondHasNoBluntTrailingEdgeBaseArea() {
		FinTrailingEdgeBaseDragModel model = new FinTrailingEdgeBaseDragModel();
		AeroComponent diamond = finComponent("SYMMETRIC_DIAMOND", 4, 0.10,
				Map.of("thicknessM", 0.003));
		AeroComponent flatPlate = finComponent("FLAT_PLATE", 4, 0.10,
				Map.of("thicknessM", 0.003));

		assertEquals(0, model.trailingEdgeAreaPerFinM2(diamond), 0);
		assertEquals(0.003 * diamond.finGeometry().spanM(),
				model.trailingEdgeAreaPerFinM2(flatPlate), 1e-15);
	}

	@Test
	void importedHexagonalSectionUsesMeasuredLeadingAndTrailingRamps() {
		double thickness = 0.004;
		double leadingRamp = 0.020;
		double trailingRamp = 0.010;
		AeroComponent component = finComponent("SYMMETRIC_DIAMOND", 1, 0,
				Map.of("thicknessM", thickness,
						"sectionLeadingRampLengthM", leadingRamp,
						"sectionTrailingRampLengthM", trailingRamp));
		FinStrip strip = strips(component).get(0);
		FinSectionPanelGeometry.PanelLayout layout =
				new FinSectionPanelGeometry().layout(component, strip);
		double representativeChord =
				component.finGeometry().planformAreaM2()
						/ component.finGeometry().spanM();

		assertEquals(FinSectionPanelGeometry.EXPLICIT_HEXAGONAL_METHOD_ID,
				layout.methodId());
		assertEquals(3, layout.surfaceAnglesRad().length);
		assertEquals(Math.atan(0.5 * thickness / leadingRamp),
				layout.surfaceAnglesRad()[0], 1e-14);
		assertEquals(0, layout.surfaceAnglesRad()[1], 0);
		assertEquals(-Math.atan(0.5 * thickness / trailingRamp),
				layout.surfaceAnglesRad()[2], 1e-14);
		assertEquals(leadingRamp / representativeChord,
				layout.chordFractions()[0], 1e-14);
		assertEquals(trailingRamp / representativeChord,
				layout.chordFractions()[2], 1e-14);
		assertEquals(1, java.util.Arrays.stream(
				layout.chordFractions()).sum(), 1e-14);
	}

	@Test
	void unspecifiedAirfoilGeometryPreservesHistoricalHalfChordDoubleWedge() {
		AeroComponent component = finComponent("SYMMETRIC_DIAMOND", 1, 0,
				Map.of("thicknessRatio", THICKNESS_RATIO));
		FinStrip strip = strips(component).get(0);
		FinSectionPanelGeometry.PanelLayout layout =
				new FinSectionPanelGeometry().layout(component, strip);

		assertEquals(FinSectionPanelGeometry.DEFAULT_DIAMOND_METHOD_ID,
				layout.methodId());
		assertEquals(Math.atan(THICKNESS_RATIO),
				layout.surfaceAnglesRad()[0], 1e-14);
		assertEquals(-Math.atan(THICKNESS_RATIO),
				layout.surfaceAnglesRad()[1], 1e-14);
		assertEquals(0.5, layout.chordFractions()[0], 0);
		assertEquals(0.5, layout.chordFractions()[1], 0);
	}

	@Test
	void detachedHexagonalBevelRetainsBoundedProfileDrag() {
		AeroComponent component = finComponent("SYMMETRIC_DIAMOND", 1, 0,
				Map.of("thicknessM", 0.006,
						"sectionLeadingRampLengthM", 0.003,
						"sectionTrailingRampLengthM", 0.003));
		FinResult result = new SupersonicFinSolver(20).evaluate(
				geometry(component), flow(2));
		ForceContribution profile = contributions(result,
				PhysicalTerm.FIN_ZERO_LIFT_WAVE_DRAG).get(0);

		assertTrue(profile.forceBodyN().x > 0);
		assertEquals(SymmetricSectionWaveDragFallbackModel.METHOD_ID,
				profile.methodId().value());
		assertTrue(profile.validityFlags().contains(
				"MODIFIED_NEWTONIAN_LEADING_FACE_PRESSURE_BOUND"));
		assertTrue(profile.validityFlags().contains(
				"VACUUM_TRAILING_FACE_PRESSURE_BOUND"));
		assertTrue(profile.fallbackReason().contains(
				"ATTACHED_SHOCK_EXPANSION_INVALID"));
	}

	@Test
	void sweptSymmetricDiamondUsesNormalFlowWaveDragScaling() {
		double thicknessRatio = 0.01;
		double leadingEdgeSweepM = 0.10;
		AeroComponent component = finComponent("SYMMETRIC_DIAMOND", 1,
				leadingEdgeSweepM, Map.of("thicknessRatio", thicknessRatio));
		AeroGeometry geometry = geometry(component);
		FlowCondition condition = flow(2);
		FinResult result = new SupersonicFinSolver(20).evaluate(geometry, condition);
		ForceContribution profile = contributions(
				result, PhysicalTerm.FIN_ZERO_LIFT_WAVE_DRAG).get(0);

		double sweep = Math.atan2(leadingEdgeSweepM,
				component.finGeometry().spanM());
		double cosine = Math.cos(sweep);
		double normalMach = condition.mach() * cosine;
		double expectedCoefficient = 4 * thicknessRatio * thicknessRatio * cosine
				/ Math.sqrt(normalMach * normalMach - 1);
		double expectedForce = condition.dynamicPressurePa()
				* component.finGeometry().planformAreaM2() * expectedCoefficient;

		assertEquals(expectedForce, profile.forceBodyN().x, 0.08 * expectedForce);
	}

	@Test
	void sweptSingleWedgeOwnsPositiveZeroLiftProfileDragAcrossInitialSupersonicRange() {
		// dy/dx = 1/3 keeps the leading edge locally subsonic through M=2.3 and
		// exercises the named finite-wing fallback rather than invalid 2-D shock expansion.
		double bevel = Math.toRadians(20);
		AeroGeometry geometry = geometry(finComponent("SINGLE_WEDGE", 4, 0.30,
				Map.of("thicknessRatio", THICKNESS_RATIO, "leadingEdgeWedgeAngleRad", bevel)));
		for (double mach : new double[] { 1.5, 2.0, 2.3 }) {
			FlowCondition condition = flow(mach);
			FinResult result = new SupersonicFinSolver(20).evaluate(geometry, condition);
			List<ForceContribution> profile = contributions(result, PhysicalTerm.FIN_ZERO_LIFT_WAVE_DRAG);
			assertEquals(4, profile.size());
			double fullChordRampForce = condition.dynamicPressurePa()
					* geometry.components().get(0).finGeometry().planformAreaM2()
					* new FinWaveDragModel().linearizedSingleWedgeCoefficient(mach, THICKNESS_RATIO);
			assertTrue(profile.stream().allMatch(c -> c.forceBodyN().x > fullChordRampForce));
			assertTrue(profile.stream().allMatch(c -> c.methodId().value().equals(
					SingleWedgeWaveDragModel.LINEARIZED_FALLBACK_METHOD_ID)));
			assertTrue(profile.stream().allMatch(c -> c.validityFlags().contains(
					SingleWedgeWaveDragModel.SUBSONIC_LEADING_EDGE_FLAG)));
			assertTrue(profile.stream().allMatch(c -> c.validityFlags().contains(
					SingleWedgeWaveDragModel.LINEARIZED_FALLBACK_FLAG)));
			assertTrue(profile.stream().allMatch(c -> c.validityFlags().contains(
					SingleWedgeWaveDragModel.FORWARD_FACE_OWNERSHIP_FLAG)));
			assertTrue(profile.stream().allMatch(c -> c.fallbackReason() != null
					&& c.fallbackReason().contains("SUBSONIC_LEADING_EDGE")));
			List<ForceContribution> trailingEdge = contributions(result, PhysicalTerm.FIN_BASE_PRESSURE_DRAG);
			assertEquals(4, trailingEdge.size());
			assertTrue(trailingEdge.stream().allMatch(c -> c.forceBodyN().x > 0));
			assertTrue(trailingEdge.stream().allMatch(c -> c.methodId().value().equals(
					FinTrailingEdgeBaseDragModel.METHOD_ID)));
			assertTrue(result.coefficients().ca() > 0);
			assertEquals(0, result.coefficients().cn(), 1e-12);
			assertTrue(contributions(result, PhysicalTerm.FIN_LIFT).stream().allMatch(
					c -> c.methodId().value().equals(DatcomFinLiftModel.METHOD_ID)));
		}
	}

	@Test
	void unsweptSingleWedgeUsesNormalShockExpansionAtZeroLift() {
		AeroGeometry geometry = geometry(finComponent("SINGLE_WEDGE", 1, 0,
				Map.of("thicknessRatio", THICKNESS_RATIO)));
		FlowCondition condition = flow(2);
		FinResult result = new SupersonicFinSolver(20).evaluate(geometry, condition);
		ForceContribution profile = contributions(result, PhysicalTerm.FIN_ZERO_LIFT_WAVE_DRAG).get(0);
		double linearizedForce = condition.dynamicPressurePa()
				* geometry.components().get(0).finGeometry().planformAreaM2()
				* new FinWaveDragModel().linearizedSingleWedgeCoefficient(2, THICKNESS_RATIO);
		assertEquals(linearizedForce, profile.forceBodyN().x, 0.3 * linearizedForce);
		assertEquals(SingleWedgeWaveDragModel.EXACT_METHOD_ID, profile.methodId().value());
		assertTrue(profile.validityFlags().contains("NORMAL_FLOW_SHOCK_EXPANSION"));
		assertNull(profile.fallbackReason());
		assertEquals(DatcomFinLiftModel.METHOD_ID,
				contributions(result, PhysicalTerm.FIN_LIFT).get(0).methodId().value());
	}

	private static List<FinStrip> strips(AeroComponent component) {
		var physicalFin = new FinGeometryAdapter().expand(component).get(0);
		return new FinStripDiscretizer().discretize(physicalFin, 20);
	}

	private static List<ForceContribution> contributions(FinResult result, PhysicalTerm term) {
		return result.contributions().stream().filter(c -> c.owner().term() == term).toList();
	}

	private static FlowCondition flow(double mach) {
		PerfectGasAir air = new PerfectGasAir();
		double pressure = 101325, temperature = 288.15;
		AtmosphereState atmosphere = new AtmosphereState(pressure, temperature,
				pressure / (air.gasConstant() * temperature), air.viscosity(temperature));
		return FlowCondition.fromAngles(mach, 0, 0, atmosphere, air, false, "fin-profile-corrections");
	}

	private static AeroGeometry geometry(AeroComponent component) {
		double radius = component.rootRadiusM();
		ReferenceGeometry references = new ReferenceGeometry(Math.PI * radius * radius, 0,
				2, 2 * radius, Map.of(), new Coordinate(), 2 * radius);
		return new AeroGeometry(List.of(component), references, "fin-profile-corrections");
	}

	private static AeroComponent finComponent(String section, int count, double leadingEdgeSweepM,
			Map<String, Double> sectionReferences) {
		double rootChord = 0.45, tipChord = 0.10, span = 0.10;
		double area = 0.5 * (rootChord + tipChord) * span;
		List<GeometryStation> outline = List.of(
				new GeometryStation(0, 0, 0, 0),
				new GeometryStation(leadingEdgeSweepM, span, 0, 0),
				new GeometryStation(leadingEdgeSweepM + tipChord, span, 0, 0),
				new GeometryStation(rootChord, 0, 0, 0));
		FinGeometry fin = new FinGeometry("TRAPEZOIDAL", section, count, rootChord, span, area, 0, outline);
		return new AeroComponent("fins", "/fins", "test", "FIN_TRAPEZOIDAL", "stage", 0,
				new Coordinate(1, 0, 0), 1, 1 + rootChord, 0.05, 2 * area * count, area * count,
				0, 0, "ADIABATIC", sectionReferences, List.of(), null, fin, null);
	}
}

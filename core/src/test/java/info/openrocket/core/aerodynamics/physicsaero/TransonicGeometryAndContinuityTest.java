package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.physicsaero.flow.AtmosphereState;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.PerfectGasAir;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroComponent;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AxisymmetricProfile;
import info.openrocket.core.aerodynamics.physicsaero.geometry.FinGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.GeometryStation;
import info.openrocket.core.aerodynamics.physicsaero.geometry.ReferenceGeometry;
import info.openrocket.core.aerodynamics.physicsaero.integration.FullRegimeTableBuilder;
import info.openrocket.core.aerodynamics.physicsaero.subsonic.SubsonicBaseDragModel;
import info.openrocket.core.aerodynamics.physicsaero.table.AerodynamicTable;
import info.openrocket.core.aerodynamics.physicsaero.table.TableMetadata;
import info.openrocket.core.aerodynamics.physicsaero.transonic.TransonicBaseDragModel;
import info.openrocket.core.aerodynamics.physicsaero.transonic.BodyCriticalMachEstimator;
import info.openrocket.core.aerodynamics.physicsaero.transonic.TransonicDragRiseHierarchy;
import info.openrocket.core.aerodynamics.physicsaero.transonic.TransonicDragRiseModel;
import info.openrocket.core.util.Coordinate;

class TransonicGeometryAndContinuityTest {
	@Test
	void nasaTrR100TipAngleControlsBodyDragRiseOnset() {
		BodyCriticalMachEstimator estimator = new BodyCriticalMachEstimator();
		double angle = Math.toRadians(19.2);
		double expected = 0.95 - 0.15
				* Math.pow(Math.sin(angle), 0.4);
		var steepCone = estimator.estimateFromTipHalfAngle(angle);
		var nearlySharp = estimator.estimateFromTipHalfAngle(0);

		assertEquals(expected, steepCone.criticalMach(), 1e-15);
		assertTrue(steepCone.criticalMach() < nearlySharp.criticalMach());
		assertEquals(BodyCriticalMachEstimator.TIP_ANGLE_METHOD_ID,
				steepCone.methodId());
	}

	@Test
	void baseCorrelationsRemainPhysicalInsideTheirDeclaredRanges() {
		double subsonic = new SubsonicBaseDragModel()
				.dragCoefficient(geometry(0.04), 0.05);
		double transonic = new TransonicBaseDragModel()
				.basePressureCoefficient(1.05, 0);
		assertTrue(subsonic > 0 && subsonic <= 0.3);
		assertTrue(transonic < 0 && transonic >= -0.45);
	}

	@Test
	void dragRiseIsContinuousAtDivergenceAndUsesActualGeometry() {
		TransonicDragRiseHierarchy hierarchy = new TransonicDragRiseHierarchy();
		var nominalGeometry = new TransonicDragRiseHierarchy.GeometryInputs(
				10, 1, 0.08, 0.04, 5, 0);
		var nominal = hierarchy.select(nominalGeometry, 0.86, 0.94);
		var thicker = hierarchy.select(new TransonicDragRiseHierarchy.GeometryInputs(
				10, 1, 0.08, 0.08, 5, 0), 0.86, 0.94);
		var swept = hierarchy.select(new TransonicDragRiseHierarchy.GeometryInputs(
				10, 1, 0.08, 0.04, 5, Math.toRadians(55)), 0.86, 0.94);
		var slender = hierarchy.select(new TransonicDragRiseHierarchy.GeometryInputs(
				20, 1, 0.08, 0.04, 5, 0), 0.86, 0.94);

		assertTrue(thicker.peakDeltaCd() > nominal.peakDeltaCd());
		assertTrue(swept.peakDeltaCd() < nominal.peakDeltaCd());
		assertTrue(slender.peakDeltaCd() < nominal.peakDeltaCd());

		TransonicDragRiseModel model = new TransonicDragRiseModel();
		double epsilon = 1e-8;
		double sonicFraction = 0.6;
		double below = model.deltaCd(nominal.dragDivergenceMach() - epsilon,
				sonicFraction, nominal);
		double at = model.deltaCd(nominal.dragDivergenceMach(), sonicFraction, nominal);
		double above = model.deltaCd(nominal.dragDivergenceMach() + epsilon,
				sonicFraction, nominal);
		assertEquals(at, below, 1e-7);
		assertEquals(at, above, 1e-7);
		assertEquals(0.002, at, 1e-12);

		var decomposition = hierarchy.decompose(nominalGeometry, 0.86, 0.94);
		assertEquals(decomposition.total().peakDeltaCd(),
				decomposition.body().peakDeltaCd() + decomposition.fin().peakDeltaCd()
						+ decomposition.interference().peakDeltaCd(), 1e-15);
		assertTrue(decomposition.body().peakDeltaCd() > 0);
		assertTrue(decomposition.fin().peakDeltaCd() > 0);
		assertTrue(decomposition.interference().peakDeltaCd() > 0);
	}

	@Test
	void fullRegimeBlendsEveryCoefficientAcrossPhysicalHandoffs() {
		AeroGeometry geometry = geometry(0.04);
		double epsilon = 1e-6;
		double[] mach = {
				0.85 - epsilon, 0.85 + epsilon,
				0.95 - epsilon, 0.95 + epsilon,
				1.20 - epsilon, 1.20 + epsilon,
				1.30 - epsilon, 1.30 + epsilon
		};
		AerodynamicTable table = build(geometry, mach, Math.toRadians(3), Math.toRadians(1));
		for (int pair = 0; pair < mach.length; pair += 2) {
			double[] below = table.cell(pair, 0, 0).coefficients().toArray();
			double[] above = table.cell(pair + 1, 0, 0).coefficients().toArray();
			for (int coefficient = 0; coefficient < below.length; coefficient++) {
				assertEquals(below[coefficient], above[coefficient], 1e-3,
						"coefficient " + coefficient + " discontinuity near Mach "
								+ 0.5 * (mach[pair] + mach[pair + 1]));
			}
		}
		assertTrue(table.cell(4, 0, 0).validityFlags()
				.contains("TRANSONIC_CORRELATION_DOMINANT"));
		assertTrue(table.cell(5, 0, 0).validityFlags()
				.contains("TRANSONIC_SUPERSONIC_SMOOTH_OVERLAP"));
	}

	@Test
	void thickerFinGeometryRaisesProductionTransonicZeroLiftDrag() {
		AerodynamicTable thin = build(geometry(0.02), new double[] {1.02}, 0, 0);
		AerodynamicTable thick = build(geometry(0.08), new double[] {1.02}, 0, 0);

		double thinCa = thin.cell(0, 0, 0).coefficients().ca();
		double thickCa = thick.cell(0, 0, 0).coefficients().ca();
		assertTrue(thickCa > thinCa);
		assertTrue(thick.cell(0, 0, 0).methodIds()
				.contains(TransonicDragRiseHierarchy.METHOD_ID));
		assertTrue(thick.cell(0, 0, 0).methodIds()
				.contains("FIN_BLUNT_TRAILING_EDGE_BASE_PRESSURE_V1"));
		assertEquals(thick.cell(0, 0, 0).coefficients().ca(),
				thick.cell(0, 0, 0).componentTotals().values().stream()
						.mapToDouble(value -> value.ca()).sum(), 1e-12);
		assertEquals(thick.cell(0, 0, 0).coefficients().ca(),
				thick.cell(0, 0, 0).ownerTotals().values().stream()
				.mapToDouble(value -> value.ca()).sum(), 1e-12);
	}

	@Test
	void machOnePointTwoBodyWaveOwnerMatchesDirectPressureEndpoint() {
		AeroGeometry geometry = geometry(0.04);
		PerfectGasAir gas = new PerfectGasAir();
		double pressure = 101325;
		double temperature = 288.15;
		AtmosphereState atmosphere = new AtmosphereState(pressure, temperature,
				pressure / (gas.gasConstant() * temperature), gas.viscosity(temperature));
		var endpointFlow = info.openrocket.core.aerodynamics.physicsaero.flow.FlowCondition
				.fromAngles(1.2, 0, 0, atmosphere, gas, false,
						geometry.geometryHash());
		var direct = new info.openrocket.core.aerodynamics.physicsaero.body.AxisymmetricBodySolver()
				.evaluate(geometry, endpointFlow);
		double directPressureCd = direct.contributions().stream()
				.filter(contribution -> contribution.owner().term()
						== info.openrocket.core.aerodynamics.physicsaero.api.PhysicalTerm.BODY_PRESSURE_FOREBODY
						|| contribution.owner().term()
								== info.openrocket.core.aerodynamics.physicsaero.api.PhysicalTerm.BODY_PRESSURE_TRANSITION)
				.mapToDouble(contribution -> contribution.forceBodyN().x
						/ (endpointFlow.dynamicPressurePa()
								* geometry.references().referenceAreaM2()))
				.sum();

		var endpoint = build(geometry, new double[] {1.2}, 0, 0).cell(0, 0, 0);
		assertEquals(directPressureCd,
				endpoint.ownerTotals().get("BODY_TRANSONIC_DRAG_RISE").ca(), 1e-12);
		assertTrue(endpoint.methodIds().contains(
				"MACH_1P2_DIRECT_BODY_PRESSURE_ENDPOINT_BRIDGE_V1"));
		assertTrue(endpoint.validityFlags().contains(
				"DIRECT_MACH_1P2_BODY_PRESSURE_ENDPOINT_BRIDGE"));
	}

	private static AerodynamicTable build(AeroGeometry geometry, double[] mach,
			double alpha, double beta) {
		PerfectGasAir gas = new PerfectGasAir();
		double pressure = 101325;
		double temperature = 288.15;
		AtmosphereState atmosphere = new AtmosphereState(pressure, temperature,
				pressure / (gas.gasConstant() * temperature), gas.viscosity(temperature));
		TableMetadata metadata = new TableMetadata(TableMetadata.CURRENT_SCHEMA,
				geometry.geometryHash(), "transonic-continuity", "test", "v1", "SI;radians",
				"OPENROCKET_BODY_AXES_V1", Instant.parse("2026-01-01T00:00:00Z"),
				Map.of(), Map.of(), "TEST");
		return new FullRegimeTableBuilder().build(geometry, mach,
				new double[] {alpha}, new double[] {beta}, atmosphere, gas, metadata);
	}

	private static AeroGeometry geometry(double thicknessRatio) {
		double radius = 0.1;
		AxisymmetricProfile noseProfile = new AxisymmetricProfile(List.of(
				new GeometryStation(0, 0, radius, 0),
				new GeometryStation(1, radius, radius, 0)), List.of(), "TEST", 1e-9);
		AxisymmetricProfile tubeProfile = new AxisymmetricProfile(List.of(
				new GeometryStation(1, radius, 0, 0),
				new GeometryStation(2, radius, 0, 0)), List.of(), "TEST", 1e-9);
		AeroComponent nose = bodyComponent("nose", 0, 1, 0, radius, noseProfile);
		AeroComponent tube = bodyComponent("tube", 1, 2, radius, radius, tubeProfile);

		double rootChord = 0.45;
		double tipChord = 0.20;
		double span = 0.12;
		double leadingEdgeSweep = 0.12;
		double area = 0.5 * (rootChord + tipChord) * span;
		List<GeometryStation> outline = List.of(
				new GeometryStation(0, 0, 0, 0),
				new GeometryStation(leadingEdgeSweep, span, 0, 0),
				new GeometryStation(leadingEdgeSweep + tipChord, span, 0, 0),
				new GeometryStation(rootChord, 0, 0, 0));
		FinGeometry fin = new FinGeometry("TRAPEZOIDAL", "SINGLE_WEDGE", 4,
				rootChord, span, area, 0, outline);
		AeroComponent fins = new AeroComponent("fins", "/fins", "test", "FIN_TRAPEZOIDAL",
				"stage", 1, new Coordinate(1.5, 0, 0), 1.5, 1.5 + rootChord, radius,
				2 * area * fin.count(), area * fin.count(), 0, 0, "ADIABATIC",
				Map.of("thicknessRatio", thicknessRatio,
						"thicknessM", thicknessRatio * 0.5 * (rootChord + tipChord)),
				List.of(), null, fin, null);

		double referenceArea = Math.PI * radius * radius;
		ReferenceGeometry references = new ReferenceGeometry(referenceArea, referenceArea,
				2, 2 * radius, Map.of(), new Coordinate(), 2 * radius);
		return new AeroGeometry(List.of(nose, tube, fins), references,
				"transonic-geometry-tc-" + thicknessRatio);
	}

	private static AeroComponent bodyComponent(String id, double start, double end,
			double startRadius, double endRadius, AxisymmetricProfile profile) {
		double radius = Math.max(startRadius, endRadius);
		String classification = startRadius == 0 ? "NOSE_CONICAL" : "CYLINDER";
		return new AeroComponent(id, "/" + id, "test", classification, "stage", 0,
				new Coordinate(start, 0, 0), start, end, radius, profile.wettedAreaM2(),
				2 * radius * (end - start), Math.PI * endRadius * endRadius, 0,
				"ADIABATIC", Map.of(), List.of(), profile, null, null);
	}
}

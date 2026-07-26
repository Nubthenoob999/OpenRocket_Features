package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.physicsaero.api.PhysicalTerm;
import info.openrocket.core.aerodynamics.physicsaero.boundarylayer.EngineeringSkinFrictionCorrelation;
import info.openrocket.core.aerodynamics.physicsaero.coupling.FinnedBodyBasePressureInteractionModel;
import info.openrocket.core.aerodynamics.physicsaero.diagnostics.DiagnosticFlag;
import info.openrocket.core.aerodynamics.physicsaero.flow.AtmosphereState;
import info.openrocket.core.aerodynamics.physicsaero.flow.FlowCondition;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.PerfectGasAir;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroComponent;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AxisymmetricProfile;
import info.openrocket.core.aerodynamics.physicsaero.geometry.FinGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.GeometryStation;
import info.openrocket.core.aerodynamics.physicsaero.geometry.ReferenceGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.ProtuberanceGeometry;
import info.openrocket.core.aerodynamics.physicsaero.table.AerodynamicTable;
import info.openrocket.core.aerodynamics.physicsaero.table.CombinedBodyFinTableBuilder;
import info.openrocket.core.aerodynamics.physicsaero.table.TableMetadata;
import info.openrocket.core.util.Coordinate;

class CombinedBodyFinViscousOwnershipTest {
	private static final double MACH = 2.0;

	@Test
	void finFrictionIsPresentWhenResolvedBodyCouplingIsDisabled() {
		AeroGeometry geometry = geometry(false);
		FlowCondition flow = flow(geometry, MACH);
		double expectedFinCd = new EngineeringSkinFrictionCorrelation().evaluate(geometry, flow).finCd();
		var cell = table(geometry, false).cell(0, 0, 0);

		assertEquals(expectedFinCd, cell.ownerTotals().get(PhysicalTerm.SKIN_FRICTION.name()).ca(), 1e-12);
		assertEquals(expectedFinCd, cell.componentTotals().get("fins").ca(), 1e-12);
		assertTrue(cell.validityFlags().contains("ENGINEERING_FIN_SKIN_FRICTION"));
		assertFalse(cell.validityFlags().contains("ENGINEERING_BODY_SKIN_FRICTION_FALLBACK"));
		assertFalse(cell.validityFlags().stream().anyMatch(flag -> flag.startsWith("PHASE5_")));
	}

	@Test
	void resolvedBodyAndEngineeringFinFrictionHaveDistinctOwnership() {
		AeroGeometry geometry = geometry(false);
		double expectedFinCd = new EngineeringSkinFrictionCorrelation().evaluate(
				geometry, flow(geometry, MACH)).finCd();
		var cell = table(geometry, true).cell(0, 0, 0);

		assertEquals(expectedFinCd, cell.componentTotals().get("fins").ca(), 1e-12);
		assertTrue(cell.ownerTotals().get(PhysicalTerm.SKIN_FRICTION.name()).ca() > expectedFinCd);
		assertTrue(cell.validityFlags().contains("PHASE5_ONE_WAY_VISCOUS_COUPLING"),
				() -> cell.diagnostics().messages().toString());
		assertTrue(cell.validityFlags().contains("RESOLVED_BODY_SKIN_FRICTION"));
		assertFalse(cell.validityFlags().contains("ENGINEERING_BODY_SKIN_FRICTION_FALLBACK"));
		assertFalse(cell.diagnostics().fallback(),
				"the DATCOM-named engineering friction method is not a pressure-method fallback");
	}

	@Test
	void allTurbulentOptionUsesAverageFlatPlateBodyAndFinClosure() {
		AeroGeometry geometry = fullyTurbulentGeometry();
		var expected = new EngineeringSkinFrictionCorrelation().evaluate(
				geometry, flow(geometry, MACH));
		var cell = table(geometry, true).cell(0, 0, 0);

		assertEquals(expected.totalCd(),
				cell.ownerTotals().get(PhysicalTerm.SKIN_FRICTION.name()).ca(), 1e-12);
		assertTrue(cell.validityFlags().contains(
				"FULLY_TURBULENT_ENGINEERING_BODY_SKIN_FRICTION"));
		assertTrue(cell.validityFlags().contains("AVERAGE_FLAT_PLATE_BODY_SKIN_FRICTION"));
		assertFalse(cell.validityFlags().contains("RESOLVED_BODY_SKIN_FRICTION"));
		assertFalse(cell.diagnostics().fallback());
	}

	@Test
	void invalidBodyMarchUsesOneExplicitEngineeringBodyFallback() {
		AeroGeometry geometry = geometry(true);
		var expected = new EngineeringSkinFrictionCorrelation().evaluate(geometry, flow(geometry, MACH));
		var cell = table(geometry, true).cell(0, 0, 0);

		assertEquals(expected.totalCd(), cell.ownerTotals().get(PhysicalTerm.SKIN_FRICTION.name()).ca(), 1e-12);
		assertTrue(cell.validityFlags().contains("PHASE5_VISCOUS_COUPLING_INVALID"));
		assertTrue(cell.validityFlags().contains("ENGINEERING_BODY_SKIN_FRICTION_FALLBACK"));
		assertTrue(cell.diagnostics().flags().contains(DiagnosticFlag.FALLBACK_USED));
		assertEquals(EngineeringSkinFrictionCorrelation.METHOD_ID, cell.diagnostics().fallbackMethodId());
		assertEquals("PHASE5_VISCOUS_COUPLING_INVALID", cell.diagnostics().fallbackReasonCode());
		assertEquals(1, cell.methodIds().stream()
				.filter(EngineeringSkinFrictionCorrelation.METHOD_ID::equals).count());
		assertTrue(cell.diagnostics().messages().stream()
				.anyMatch(message -> message.startsWith("PHASE5_INVALID:BoundaryLayerException:")));
		assertTrue(cell.diagnostics().messages().contains("ENGINEERING_BODY_SKIN_FRICTION_FALLBACK:"
				+ EngineeringSkinFrictionCorrelation.METHOD_ID));
	}

	@Test
	void prescribedTransitionAndColdWallAreAppliedOnlyInsideTheirMachValidity() {
		AeroGeometry geometry = prescribedBodyInputsGeometry();
		PerfectGasAir air = new PerfectGasAir();
		AerodynamicTable table = new CombinedBodyFinTableBuilder(false, true).build(geometry,
				new double[] {1.2, 2.0}, new double[] {0}, new double[] {0}, atmosphere(air), air, metadata(geometry));
		var belowMinimum = table.cell(0, 0, 0);
		var prescribed = table.cell(1, 0, 0);

		assertFalse(belowMinimum.validityFlags().contains("PRESCRIBED_BODY_TRANSITION"));
		assertTrue(belowMinimum.validityFlags().contains("PRESCRIBED_BODY_WALL_TEMPERATURE"));
		assertTrue(prescribed.validityFlags().contains("PRESCRIBED_BODY_TRANSITION"));
		assertTrue(prescribed.validityFlags().contains("PRESCRIBED_BODY_WALL_TEMPERATURE"));
		assertTrue(prescribed.diagnostics().messages().contains("PRESCRIBED_BODY_TRANSITION_REYNOLDS:1000000.0"));
		assertTrue(prescribed.diagnostics().messages().contains(
				"PRESCRIBED_BODY_WALL_TEMPERATURE:ISOTHERMAL_COMPONENT"));
	}

	@Test
	void finnedBaseInteractionModifiesBodyAndFinPressureOwnersWithoutPlanformForce() {
		var cell = table(geometry(false), false).cell(0, 0, 0);

		assertTrue(cell.methodIds().contains(FinnedBodyBasePressureInteractionModel.METHOD_ID));
		assertTrue(cell.validityFlags().contains("FINNED_BODY_BASE_PRESSURE_INTERACTION"));
		assertTrue(cell.ownerTotals().get(PhysicalTerm.BASE_PRESSURE_DRAG.name()).ca() > 0.125);
		assertTrue(cell.ownerTotals().get(PhysicalTerm.FIN_BASE_PRESSURE_DRAG.name()).ca() > 0);
	}

	@Test
	void aftProtuberanceDoesNotChangeBodyOrFinBasePressureInteraction() {
		AeroGeometry baseline = geometry(false);
		var baselineCell = table(baseline, false).cell(0, 0, 0);
		var extendedCell = table(withAftProtuberance(baseline), false)
				.cell(0, 0, 0);

		assertEquals(baselineCell.ownerTotals()
						.get(PhysicalTerm.BASE_PRESSURE_DRAG.name()).ca(),
				extendedCell.ownerTotals()
						.get(PhysicalTerm.BASE_PRESSURE_DRAG.name()).ca(),
				1e-15);
		assertEquals(baselineCell.ownerTotals()
						.get(PhysicalTerm.FIN_BASE_PRESSURE_DRAG.name()).ca(),
				extendedCell.ownerTotals()
						.get(PhysicalTerm.FIN_BASE_PRESSURE_DRAG.name()).ca(),
				1e-15);
	}

	private static AerodynamicTable table(AeroGeometry geometry, boolean phaseFive) {
		PerfectGasAir air = new PerfectGasAir();
		return new CombinedBodyFinTableBuilder(false, phaseFive).build(geometry, new double[] {MACH},
				new double[] {0}, new double[] {0}, atmosphere(air), air, metadata(geometry));
	}

	private static FlowCondition flow(AeroGeometry geometry, double mach) {
		PerfectGasAir air = new PerfectGasAir();
		return FlowCondition.fromAngles(mach, 0, 0, atmosphere(air), air, false, geometry.geometryHash());
	}

	private static AtmosphereState atmosphere(PerfectGasAir air) {
		double pressure = 101325;
		double temperature = 288.15;
		return new AtmosphereState(pressure, temperature,
				pressure / (air.gasConstant() * temperature), air.viscosity(temperature));
	}

	private static TableMetadata metadata(AeroGeometry geometry) {
		return new TableMetadata(TableMetadata.CURRENT_SCHEMA, geometry.geometryHash(), "viscous-settings",
				"viscous-ownership-test", "viscous-ownership-v1", "SI;radians", "OPENROCKET_BODY_AXES_V1",
				Instant.parse("2026-01-01T00:00:00Z"), Map.of(), Map.of(), "VISCOUS_OWNERSHIP_TESTED");
	}

	private static AeroGeometry geometry(boolean forceInvalidBodyMarch) {
		double referenceRadius = 0.05;
		double bodyRadius = forceInvalidBodyMarch ? 0 : referenceRadius;
		AxisymmetricProfile profile = new AxisymmetricProfile(List.of(
				new GeometryStation(0, bodyRadius, 0, 0),
				new GeometryStation(1, bodyRadius, 0, 0)), List.of(), "TEST", 1e-12);
		double bodyWettedArea = 2 * Math.PI * referenceRadius;
		AeroComponent body = new AeroComponent("body", "/body", "test", "CYLINDER", "stage", 0,
				new Coordinate(), 0, 1, bodyRadius, bodyWettedArea, 2 * referenceRadius,
				Math.PI * referenceRadius * referenceRadius, 0, "ADIABATIC", Map.of(), List.of(),
				profile, null, null);

		double root = 0.2;
		double tip = 0.1;
		double span = 0.1;
		double area = 0.5 * (root + tip) * span;
		FinGeometry fin = new FinGeometry("TRAPEZOIDAL", "FLAT_PLATE", 4, root, span, area, 0,
				List.of(new GeometryStation(0, 0, 0, 0),
						new GeometryStation(root - tip, span, 0, 0),
						new GeometryStation(root, span, 0, 0),
						new GeometryStation(root, 0, 0, 0)));
		AeroComponent fins = new AeroComponent("fins", "/fins", "test", "FIN_TRAPEZOIDAL", "stage", 1,
				new Coordinate(0.8, 0, 0), 0.8, 1, referenceRadius, 2 * area * fin.count(),
				area * fin.count(), 0, 0, "ADIABATIC",
				Map.of("thicknessM", 0.004, "thicknessRatio", 0.04, "baseRotationRad", 0.0),
				List.of(), null, fin, null);
		double referenceArea = Math.PI * referenceRadius * referenceRadius;
		return new AeroGeometry(List.of(body, fins), new ReferenceGeometry(referenceArea, referenceArea, 1,
				2 * referenceRadius, Map.of("body", bodyWettedArea, "fins", fins.wettedAreaM2()),
				new Coordinate(), 2 * referenceRadius), forceInvalidBodyMarch ? "invalid-body-march" : "body-fin");
	}

	private static AeroGeometry prescribedBodyInputsGeometry() {
		AeroGeometry original = geometry(false);
		AeroComponent body = original.components().get(0);
		AeroComponent prescribedBody = new AeroComponent(body.id(), body.sourcePath(), body.type(),
				body.classification(), body.parentStageId(), body.axialOrder(), body.originM(), body.axialStartM(),
				body.axialEndM(), body.rootRadiusM(), body.wettedAreaM2(), body.projectedAreaM2(), body.baseAreaM2(),
				body.roughnessM(), "ISOTHERMAL_COMPONENT",
				Map.of("transitionReynolds", 1_000_000.0, "transitionMachMinimum", 1.5,
						"wallTemperatureK", 250.0), body.eligibleCorrelationIds(), body.axisymmetricProfile(),
				body.finGeometry(), body.protuberanceGeometry());
		return new AeroGeometry(List.of(prescribedBody, original.components().get(1)), original.references(),
				"prescribed-body-inputs");
	}

	private static AeroGeometry withAftProtuberance(
			AeroGeometry original) {
		List<AeroComponent> components = new ArrayList<>(
				original.components());
		components.add(new AeroComponent("aft-button", "/aft-button",
				"test", "PROTUBERANCE", "stage", components.size(),
				new Coordinate(1.2, 0, 0), 1.2, 1.21, 0.05,
				0.001, 0.001, 0, 0, "ADIABATIC", Map.of(),
				List.of(), null, null,
				new ProtuberanceGeometry("RAIL_BUTTON", 1, 1.2,
						0.01, 0.005, 0.001, 0.0001)));
		ReferenceGeometry references = original.references();
		return new AeroGeometry(components, new ReferenceGeometry(
				references.referenceAreaM2(),
				references.exposedBaseAreaM2(), 1.21,
				references.maximumBodyDiameterM(),
				references.wettedAreaByComponentM2(),
				references.momentOriginM(),
				references.referenceLengthM()),
				original.geometryHash() + "-aft-protuberance");
	}

	private static AeroGeometry fullyTurbulentGeometry() {
		AeroGeometry original = geometry(false);
		List<AeroComponent> components = original.components().stream()
				.map(component -> {
					Map<String, Double> references = new java.util.LinkedHashMap<>(
							component.localReferences());
					references.put("forceFullyTurbulent", 1.0);
					return new AeroComponent(component.id(), component.sourcePath(), component.type(),
							component.classification(), component.parentStageId(), component.axialOrder(),
							component.originM(), component.axialStartM(), component.axialEndM(),
							component.rootRadiusM(), component.wettedAreaM2(),
							component.projectedAreaM2(), component.baseAreaM2(), component.roughnessM(),
							component.wallTemperatureModelId(), references,
							component.eligibleCorrelationIds(), component.axisymmetricProfile(),
							component.finGeometry(), component.protuberanceGeometry());
				}).toList();
		return new AeroGeometry(components, original.references(), "fully-turbulent-body-fin");
	}
}

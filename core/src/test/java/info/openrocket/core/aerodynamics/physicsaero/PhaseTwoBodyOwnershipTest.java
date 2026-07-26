package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.physicsaero.api.MethodId;
import info.openrocket.core.aerodynamics.physicsaero.api.OwnershipMode;
import info.openrocket.core.aerodynamics.physicsaero.api.PhysicalOwner;
import info.openrocket.core.aerodynamics.physicsaero.api.PhysicalTerm;
import info.openrocket.core.aerodynamics.physicsaero.body.BaseDragModel;
import info.openrocket.core.aerodynamics.physicsaero.body.HartTn3393SupersonicBasePressureCorrelation;
import info.openrocket.core.aerodynamics.physicsaero.flow.AtmosphereState;
import info.openrocket.core.aerodynamics.physicsaero.flow.FlowCondition;
import info.openrocket.core.aerodynamics.physicsaero.force.AerodynamicCoefficients;
import info.openrocket.core.aerodynamics.physicsaero.force.CoefficientAssembler;
import info.openrocket.core.aerodynamics.physicsaero.force.ContributionLedger;
import info.openrocket.core.aerodynamics.physicsaero.force.ForceContribution;
import info.openrocket.core.aerodynamics.physicsaero.force.ReferenceState;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.PerfectGasAir;
import info.openrocket.core.aerodynamics.physicsaero.geometry.ReferenceGeometry;
import info.openrocket.core.util.Coordinate;

class PhaseTwoBodyOwnershipTest {
	private static final ReferenceState REFERENCE = new ReferenceState(100.0, 1.0, 1.0, new Coordinate());

	@Test
	void disablingBaseRemovesExactlyBaseWhileBoattailPressureRemains() {
		ContributionLedger complete = new ContributionLedger();
		complete.add(contribution(PhysicalTerm.BODY_PRESSURE, "forebody", "PRESSURE_INTEGRATION", 10.0,
				OwnershipMode.REPLACES));
		complete.add(contribution(PhysicalTerm.BOATTAIL_PRESSURE_DRAG, "boattail", "PRANDTL_MEYER", 3.0,
				OwnershipMode.REPLACES));
		complete.add(contribution(PhysicalTerm.BASE_PRESSURE_DRAG, "base", "UNPOWERED_BASE_PRESSURE", 2.0,
				OwnershipMode.REPLACES));

		ContributionLedger baseDisabled = new ContributionLedger();
		baseDisabled.add(contribution(PhysicalTerm.BODY_PRESSURE, "forebody", "PRESSURE_INTEGRATION", 10.0,
				OwnershipMode.REPLACES));
		baseDisabled.add(contribution(PhysicalTerm.BOATTAIL_PRESSURE_DRAG, "boattail", "PRANDTL_MEYER", 3.0,
				OwnershipMode.REPLACES));

		AerodynamicCoefficients all = CoefficientAssembler.assemble(complete, REFERENCE);
		AerodynamicCoefficients withoutBase = CoefficientAssembler.assemble(baseDisabled, REFERENCE);
		assertEquals(0.02, all.ca() - withoutBase.ca(), 1.0e-15);
		assertEquals(0.13, withoutBase.ca(), 1.0e-15);
	}

	@Test
	void areaRuleCrossCheckCannotAddASecondCompleteWaveDrag() {
		ContributionLedger ledger = new ContributionLedger();
		ledger.add(contribution(PhysicalTerm.BODY_PRESSURE, "forebody", "PRESSURE_INTEGRATION", 10.0,
				OwnershipMode.REPLACES));
		ledger.add(contribution(PhysicalTerm.BODY_PRESSURE, "area-rule-check", "AREA_RULE_WAVE_DRAG", 100.0,
				OwnershipMode.DIAGNOSTIC_ONLY));

		assertEquals(0.10, CoefficientAssembler.assemble(ledger, REFERENCE).ca(), 1.0e-15);
	}

	@Test
	void zeroExposedBaseAreaProducesNoBaseContribution() {
		ContributionLedger ledger = new ContributionLedger();
		ledger.add(contribution(PhysicalTerm.BOATTAIL_PRESSURE_DRAG, "boattail", "PRANDTL_MEYER", 3.0,
				OwnershipMode.REPLACES));
		ledger.add(contribution(PhysicalTerm.BASE_PRESSURE_DRAG, "base", "UNPOWERED_BASE_PRESSURE", 0.0,
				OwnershipMode.REPLACES));

		assertEquals(0.03, CoefficientAssembler.assemble(ledger, REFERENCE).ca(), 1.0e-15);
	}

	@Test
	void namedUnpoweredBaseCorrelationOwnsOnlyExposedBaseAndRejectsPoweredUse() {
		ReferenceGeometry withBase = new ReferenceGeometry(1.0, 0.2, 2.0, 0.5, Map.of(),
				new Coordinate(), 0.5);
		ForceContribution base = new BaseDragModel().evaluate(withBase, flow(2.0, false), "body");
		assertEquals(PhysicalTerm.BASE_PRESSURE_DRAG, base.owner().term());
		assertEquals(HartTn3393SupersonicBasePressureCorrelation.METHOD_ID,
				base.methodId().value());
		assertEquals(0.2 * (0.064 + 0.186 / (2.0 * 2.0)),
				base.forceBodyN().x / flow(2.0, false).dynamicPressurePa(), 1.0e-12);
		assertTrue(new HartTn3393SupersonicBasePressureCorrelation().source()
				.contains("NACA TN 3393"));

		ReferenceGeometry noBase = new ReferenceGeometry(1.0, 0.0, 2.0, 0.5, Map.of(),
				new Coordinate(), 0.5);
		assertEquals(0.0, new BaseDragModel().evaluate(noBase, flow(2.0, false), "body").forceBodyN().x);
		IllegalArgumentException powered = assertThrows(IllegalArgumentException.class,
				() -> new BaseDragModel().evaluate(withBase, flow(2.0, true), "body"));
		assertEquals("POWERED_BASE_MODEL_NOT_IMPLEMENTED", powered.getMessage());
	}

	private static ForceContribution contribution(PhysicalTerm term, String region, String method,
			double axialForceN, OwnershipMode mode) {
		MethodId methodId = new MethodId(method);
		return new ForceContribution("body", new PhysicalOwner(term, mode, region, null), methodId,
				new Coordinate(axialForceN, 0.0, 0.0), new Coordinate(), new Coordinate(), region,
				List.of(), 1.0, 0.0, null);
	}

	private static FlowCondition flow(double mach, boolean powered) {
		PerfectGasAir air = new PerfectGasAir();
		double temperature = 288.15;
		double pressure = 101_325.0;
		AtmosphereState atmosphere = new AtmosphereState(pressure, temperature,
				pressure / (air.gasConstant() * temperature), air.viscosity(temperature));
		return FlowCondition.fromAngles(mach, 0.0, 0.0, atmosphere, air, powered, "base-test");
	}
}

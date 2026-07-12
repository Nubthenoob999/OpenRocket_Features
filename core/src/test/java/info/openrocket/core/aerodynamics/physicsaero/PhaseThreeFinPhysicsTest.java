package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import info.openrocket.core.aerodynamics.physicsaero.fin.*;
import info.openrocket.core.aerodynamics.physicsaero.flow.*;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.*;
import info.openrocket.core.aerodynamics.physicsaero.geometry.*;
import info.openrocket.core.aerodynamics.physicsaero.interaction.*;
import info.openrocket.core.util.Coordinate;

class PhaseThreeFinPhysicsTest {
	@Test void ackeretRecoversTwoDimensionalSlopeAndRejectsSonicLeadingEdge() {
		AckeretThinFinModel model = new AckeretThinFinModel(); double mach = 2, alpha = 1e-4;
		var result = model.evaluate(mach, alpha, 1, 1000);
		assertEquals(4 / Math.sqrt(3), result.liftSlopePerRad(), 1e-12);
		assertEquals(result.liftSlopePerRad() * alpha, result.normalForceN() / 1000, 1e-12);
		assertFalse(model.isValid(1.01, 0, alpha, alpha));
		assertEquals(LeadingEdgeClassification.NEAR_SONIC_LEADING_EDGE, LeadingEdgeClassification.classify(1.01, .02));
	}

	@Test void shockExpansionMarchesSurfacesIndependentlyAndCreatesZeroAlphaWaveDrag() {
		PerfectGasAir air = new PerfectGasAir(); GasState state = new GasState(2, 101325, 288.15,
				101325 / (air.gasConstant() * 288.15), 2 * air.speedOfSound(288.15));
		double angle = Math.toRadians(3);
		var result = new WedgeDiamondShockExpansionModel().evaluate(state, air, 0,
				new double[] {angle, -angle}, new double[] {angle, -angle}, new double[] {.5, .5}, .1);
		assertTrue(result.valid()); assertEquals(0, result.normalForceN(), 1e-8); assertTrue(result.axialForceN() > 0);
		assertNotSame(result.upper().panelStates(), result.lower().panelStates());
		assertEquals(List.of("COMPRESSION", "EXPANSION"), result.upper().events());
	}

	@Test void pnkUsesBoundedVersionedTableAndReturnsOnlyInterferenceIncrement() {
		PnkFactorTable.Factors node = new PnkFactorTable().interpolate(.5);
		assertEquals(1.6667, node.wingBodyFactor(), 1e-12); assertEquals(.8889, node.bodyWingFactor(), 1e-12);
		assertThrows(IllegalArgumentException.class, () -> new PnkFactorTable().interpolate(.95));
		var result = new PnkInterferenceModel().evaluate(2, .5, 100, 20);
		assertTrue(result.valid()); assertEquals(result.combinedNormalN() - 120, result.interferenceIncrementN(), 1e-12);
		assertFalse(new PnkInterferenceModel().evaluate(2, .9, 100, 20).valid());
	}

	@Test void stripsPreserveAreaAndEveryPhysicalFinIsEvaluatedInItsOrientation() {
		AeroGeometry geometry = finGeometry(); AeroComponent component = geometry.components().get(0);
		List<FinGeometryAdapter.PhysicalFin> fins = new FinGeometryAdapter().expand(component);
		assertEquals(4, fins.size()); assertNotEquals(fins.get(0).frame().normal(), fins.get(1).frame().normal());
		for (var fin : fins) assertEquals(.075, new FinStripDiscretizer().discretize(fin, 20).stream().mapToDouble(FinStrip::areaM2).sum(), 1e-12);
		PerfectGasAir air = new PerfectGasAir(); AtmosphereState atmosphere = atmosphere(air);
		double alpha = Math.toRadians(3);
		var positive = new SupersonicFinSolver(20).evaluate(geometry, FlowCondition.fromAngles(2, alpha, 0, atmosphere, air, false, "fin-test"));
		var negative = new SupersonicFinSolver(20).evaluate(geometry, FlowCondition.fromAngles(2, -alpha, 0, atmosphere, air, false, "fin-test"));
		assertEquals(-positive.coefficients().cn(), negative.coefficients().cn(), 1e-10);
		assertEquals(4, positive.contributions().stream().filter(c -> c.owner().term() ==
				info.openrocket.core.aerodynamics.physicsaero.api.PhysicalTerm.FIN_LIFT).count());
		assertTrue(positive.coefficients().cn() > 0);
		List<info.openrocket.core.aerodynamics.physicsaero.force.ForceContribution> interference =
				new BodyFinInterferenceSolver().evaluate(geometry, 2, positive.contributions());
		assertEquals(2, interference.size());
		assertTrue(interference.stream().allMatch(c -> c.validityFlags().contains("PNK_INCREMENT_ONLY")));
		var beta = new SupersonicFinSolver(20).evaluate(geometry, FlowCondition.fromAngles(2, 0, Math.toRadians(2), atmosphere, air, false, "fin-test"));
		assertNotEquals(0, beta.coefficients().cy(), 1e-10);
	}

	@Test void datcomDistributionExactlyRecoversTotalAndSelectorNeverAddsFullLoads() {
		double[] loads = new FinForceDistributor().distributeByChord(123.4, new double[] {1,.5,.2}, new double[] {.1,.1,.1});
		assertEquals(123.4, Arrays.stream(loads).sum(), 0);
		var selection = new FinMethodSelector().select(FinSectionFamily.FLAT_PLATE, true, true);
		assertEquals(FinMethodSelector.Method.ACKERET, selection.authoritative());
		assertEquals(List.of(FinMethodSelector.Method.DATCOM_DIAGNOSTIC), selection.diagnostics());
	}

	private static AtmosphereState atmosphere(PerfectGasAir air) {
		double p = 101325, t = 288.15; return new AtmosphereState(p, t, p / (air.gasConstant() * t), air.viscosity(t));
	}
	private static AeroGeometry finGeometry() {
		List<GeometryStation> outline = List.of(new GeometryStation(0,0,0,0), new GeometryStation(.2,.1,0,0),
				new GeometryStation(.7,.1,0,0), new GeometryStation(1,0,0,0));
		FinGeometry fin = new FinGeometry("TRAPEZOIDAL", "FLAT_PLATE", 4, 1, .1, .075, 0, outline);
		AeroComponent component = new AeroComponent("fins", "/fins", "test", "FIN_TRAPEZOIDAL", "stage", 0,
				new Coordinate(1,0,0), 1, 2, .05, .6, .3, 0, 0, "ADIABATIC",
				Map.of("thicknessM", .001, "spanM", .1, "baseRotationRad", 0.0), List.of(), null, fin, null);
		ReferenceGeometry ref = new ReferenceGeometry(Math.PI * .05 * .05, 0, 2, .1, Map.of("fins", .6), new Coordinate(), .1);
		return new AeroGeometry(List.of(component), ref, "fin-test");
	}
}

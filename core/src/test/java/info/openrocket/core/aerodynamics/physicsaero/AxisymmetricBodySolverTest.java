package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.physicsaero.api.PhysicalTerm;
import info.openrocket.core.aerodynamics.physicsaero.body.AxisymmetricBodyResult;
import info.openrocket.core.aerodynamics.physicsaero.body.AxisymmetricBodySolver;
import info.openrocket.core.aerodynamics.physicsaero.flow.AtmosphereState;
import info.openrocket.core.aerodynamics.physicsaero.flow.ExpansionEvent;
import info.openrocket.core.aerodynamics.physicsaero.flow.FlowCondition;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.PerfectGasAir;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroComponent;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AxisymmetricProfile;
import info.openrocket.core.aerodynamics.physicsaero.geometry.GeometryStation;
import info.openrocket.core.aerodynamics.physicsaero.geometry.ReferenceGeometry;
import info.openrocket.core.util.Coordinate;

class AxisymmetricBodySolverTest {
	@Test
	void coneCylinderProducesFinitePressureAndBaseResultWithoutAuthoritativeAreaRule() {
		double slope = 0.10;
		AeroComponent cone = component("cone", "NOSE_CONICAL", 0.0, 1.0, 0.0, 0.10,
				List.of(station(0.0, 0.0, slope), station(1.0, 0.10, slope)));
		AeroComponent tube = component("tube", "CYLINDER", 1.0, 2.0, 0.10, 0.10,
				List.of(station(1.0, 0.10, 0.0), station(2.0, 0.10, 0.0)));

		AxisymmetricBodyResult result = new AxisymmetricBodySolver().evaluate(
				geometry(List.of(cone, tube), 2.0, 0.10), flow(3.0));

		assertTrue(Double.isFinite(result.coefficients().ca()));
		assertTrue(result.coefficients().ca() > 0.0);
		assertTrue(result.contributions().stream().anyMatch(c -> c.owner().term() == PhysicalTerm.BODY_PRESSURE_FOREBODY));
		assertTrue(result.contributions().stream().anyMatch(c -> c.owner().term() == PhysicalTerm.BASE_PRESSURE_DRAG));
		assertTrue(result.contributions().stream()
				.filter(c -> c.owner().term() == PhysicalTerm.BODY_PRESSURE_FOREBODY)
				.anyMatch(c -> c.forceBodyN().x > 0.0));
		assertFalse(result.areaRuleDiagnostic().authoritative());
		assertEquals("DIAGNOSTIC_ONLY", result.diagnostics().get("areaRuleOwnership"));
		assertTrue(result.edgeStateHistory().states().size() >= 3);
	}

	@Test
	void boattailPressureAndExposedBaseHaveSeparateOwnersAndOneExpansionTurn() {
		AeroComponent tube = component("tube", "CYLINDER", 0.0, 1.0, 0.10, 0.10,
				List.of(station(0.0, 0.10, 0.0), station(1.0, 0.10, 0.0)));
		double slope = -0.05;
		AeroComponent boattail = component("boattail", "BOATTAIL", 1.0, 2.0, 0.10, 0.05,
				List.of(station(1.0, 0.10, slope), station(2.0, 0.05, slope)));

		AxisymmetricBodyResult result = new AxisymmetricBodySolver().evaluate(
				geometry(List.of(tube, boattail), 2.0, 0.05), flow(3.0));

		assertEquals(1, result.contributions().stream()
				.filter(c -> c.owner().term() == PhysicalTerm.BOATTAIL_PRESSURE_DRAG).count());
		assertEquals(1, result.contributions().stream()
				.filter(c -> c.owner().term() == PhysicalTerm.BASE_PRESSURE_DRAG).count());
		assertEquals(1, result.edgeStateHistory().events().stream().filter(ExpansionEvent.class::isInstance).count());
		assertTrue(result.edgeStateHistory().events().stream().filter(ExpansionEvent.class::isInstance)
				.map(ExpansionEvent.class::cast).allMatch(e -> Math.abs(e.totalPressureRatio() - 1.0) < 1.0e-12));
	}

	private static FlowCondition flow(double mach) {
		PerfectGasAir air = new PerfectGasAir();
		double temperature = 288.15;
		double pressure = 101_325.0;
		AtmosphereState atmosphere = new AtmosphereState(pressure, temperature,
				pressure / (air.gasConstant() * temperature), air.viscosity(temperature));
		return FlowCondition.fromAngles(mach, 0.0, 0.0, atmosphere, air, false, "body-solver-test");
	}

	private static GeometryStation station(double x, double radius, double slope) {
		return new GeometryStation(x, radius, slope, 0.0);
	}

	private static AeroGeometry geometry(List<AeroComponent> components, double length, double exposedBaseRadius) {
		double maxRadius = components.stream().flatMap(c -> c.axisymmetricProfile().stations().stream())
				.mapToDouble(GeometryStation::radiusM).max().orElseThrow();
		ReferenceGeometry references = new ReferenceGeometry(Math.PI * maxRadius * maxRadius,
				Math.PI * exposedBaseRadius * exposedBaseRadius, length, 2.0 * maxRadius, Map.of(),
				new Coordinate(), 2.0 * maxRadius);
		return new AeroGeometry(components, references, "phase-2-body-solver");
	}

	private static AeroComponent component(String id, String classification, double x0, double x1,
			double r0, double r1, List<GeometryStation> stations) {
		AxisymmetricProfile profile = new AxisymmetricProfile(stations, List.of(), "TEST_PROFILE", 1.0e-9);
		return new AeroComponent(id, "/" + id, "test", classification, "stage", 0,
				new Coordinate(x0, 0, 0), x0, x1, r1, profile.wettedAreaM2(), 0.0,
				Math.PI * r1 * r1, 0.0, "ADIABATIC", Map.of(), List.of(), profile, null, null);
	}
}

package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.physicsaero.body.AxisymmetricBodySegment;
import info.openrocket.core.aerodynamics.physicsaero.body.AxisymmetricEdgeStateHistory;
import info.openrocket.core.aerodynamics.physicsaero.body.BodySegmentType;
import info.openrocket.core.aerodynamics.physicsaero.body.SurfaceStateMarcher;
import info.openrocket.core.aerodynamics.physicsaero.flow.AtmosphereState;
import info.openrocket.core.aerodynamics.physicsaero.flow.ExpansionEvent;
import info.openrocket.core.aerodynamics.physicsaero.flow.FlowCondition;
import info.openrocket.core.aerodynamics.physicsaero.flow.ShockEvent;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.PerfectGasAir;
import info.openrocket.core.aerodynamics.physicsaero.geometry.GeometryStation;

class SurfaceStateMarcherTest {
	@Test
	void piecewiseTurnsUseCumulativeStateAndPreserveThermodynamicInvariants() {
		double compression = Math.toRadians(5.0);
		double expansion = Math.toRadians(4.0);
		AxisymmetricBodySegment shoulder = segment("shoulder", BodySegmentType.DISCRETE_COMPRESSION_CORNER,
				0.50, 0.51, 0.10, 0.10, 0.0, compression);
		AxisymmetricBodySegment boattail = segment("boattail", BodySegmentType.BOATTAIL,
				0.51, 0.80, 0.10, 0.08, compression, compression - expansion);

		AxisymmetricEdgeStateHistory history = new SurfaceStateMarcher().march(List.of(shoulder, boattail),
				flow(3.0));

		assertEquals(2, history.events().size());
		ShockEvent shock = (ShockEvent) history.events().get(0);
		ExpansionEvent fan = (ExpansionEvent) history.events().get(1);
		assertEquals(shock.downstream(), fan.upstream(),
				"the second event must consume the first event's downstream state");
		assertTrue(shock.downstream().totalState().pressurePa() < shock.upstream().totalState().pressurePa());
		assertEquals(shock.totalPressureRatio(),
				shock.downstream().totalState().pressurePa() / shock.upstream().totalState().pressurePa(), 1.0e-10);
		assertEquals(1.0, fan.totalPressureRatio(), 1.0e-12);
		assertEquals(fan.upstream().totalState().pressurePa(), fan.downstream().totalState().pressurePa(),
				1.0e-8 * fan.upstream().totalState().pressurePa());
		assertEquals(fan.upstream().totalState().temperatureK(), fan.downstream().totalState().temperatureK(),
				1.0e-10 * fan.upstream().totalState().temperatureK());
		assertTrue(fan.downstreamMach() > fan.upstream().staticState().mach());
	}

	@Test
	void trueCornersCreateOneEventEachAndHistoryIsAxiallyOrdered() {
		AxisymmetricBodySegment first = segment("corner-1", BodySegmentType.DISCRETE_COMPRESSION_CORNER,
				0.20, 0.21, 0.05, 0.05, 0.0, Math.toRadians(3.0));
		AxisymmetricBodySegment cylinder = segment("cylinder", BodySegmentType.CYLINDER,
				0.21, 0.60, 0.05, 0.05, Math.toRadians(3.0), Math.toRadians(3.0));
		AxisymmetricBodySegment second = segment("corner-2", BodySegmentType.DISCRETE_COMPRESSION_CORNER,
				0.60, 0.61, 0.05, 0.05, Math.toRadians(3.0), Math.toRadians(5.0));

		AxisymmetricEdgeStateHistory history = new SurfaceStateMarcher().march(List.of(first, cylinder, second),
				flow(2.5));

		assertEquals(2, history.events().size());
		assertTrue(history.events().stream().allMatch(ShockEvent.class::isInstance));
		for (int i = 1; i < history.states().size(); i++) {
			assertTrue(history.states().get(i).xM() >= history.states().get(i - 1).xM());
		}
	}

	@Test
	void marcherRejectsNonzeroIncidenceAndPoweredStateExplicitly() {
		AxisymmetricBodySegment cylinder = segment("cylinder", BodySegmentType.CYLINDER,
				0.0, 1.0, 0.05, 0.05, 0.0, 0.0);
		PerfectGasAir air = new PerfectGasAir();
		double temperature = 288.15;
		double pressure = 101_325.0;
		AtmosphereState atmosphere = new AtmosphereState(pressure, temperature,
				pressure / (air.gasConstant() * temperature), air.viscosity(temperature));
		SurfaceStateMarcher marcher = new SurfaceStateMarcher();

		IllegalArgumentException incidence = assertThrows(IllegalArgumentException.class,
				() -> marcher.march(List.of(cylinder), FlowCondition.fromAngles(2.0, 1.0e-6, 0.0,
						atmosphere, air, false, "nonzero-incidence")));
		assertEquals("ZERO_INCIDENCE_ONLY", incidence.getMessage());
		IllegalArgumentException powered = assertThrows(IllegalArgumentException.class,
				() -> marcher.march(List.of(cylinder), FlowCondition.fromAngles(2.0, 0.0, 0.0,
						atmosphere, air, true, "powered")));
		assertEquals("POWERED_BASE_MODEL_NOT_IMPLEMENTED", powered.getMessage());
	}

	private static AxisymmetricBodySegment segment(String id, BodySegmentType type, double x0, double x1,
			double r0, double r1, double theta0, double theta1) {
		return new AxisymmetricBodySegment("body", id, type, x0, x1, r0, r1, theta0, theta1,
				List.of(new GeometryStation(x0, r0, Math.tan(theta0), 0.0),
						new GeometryStation(x1, r1, Math.tan(theta1), 0.0)),
				List.of("OBLIQUE_SHOCK", "PRANDTL_MEYER"));
	}

	private static FlowCondition flow(double mach) {
		PerfectGasAir air = new PerfectGasAir();
		double temperature = 288.15;
		double pressure = 101_325.0;
		AtmosphereState atmosphere = new AtmosphereState(pressure, temperature,
				pressure / (air.gasConstant() * temperature), air.viscosity(temperature));
		return FlowCondition.fromAngles(mach, 0.0, 0.0, atmosphere, air, false, "phase-2-marcher-test");
	}
}

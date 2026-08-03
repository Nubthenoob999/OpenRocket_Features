package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.physicsaero.flow.AtmosphereState;
import info.openrocket.core.aerodynamics.physicsaero.flow.FlowCondition;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.PerfectGasAir;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroComponent;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.FinGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.GeometryStation;
import info.openrocket.core.aerodynamics.physicsaero.geometry.ReferenceGeometry;
import info.openrocket.core.aerodynamics.physicsaero.subsonic.SubsonicComponentAssembler;
import info.openrocket.core.util.Coordinate;

class SubsonicComponentAssemblerTest {
	@Test
	void rectangularFinCenterIsAtQuarterMeanAerodynamicChord() {
		double finStart = 1.2;
		var coefficients = new SubsonicComponentAssembler().evaluate(
				finOnlyGeometry(finStart, 4), flow()).coefficients();

		double centerOfPressure = -coefficients.cm() * REFERENCE_LENGTH / coefficients.cn();
		assertEquals(finStart + 0.25 * ROOT_CHORD, centerOfPressure, 2.0e-6);
	}

	@Test
	void movingTheFinSetAftMovesTheAerodynamicCenterAft() {
		var forward = new SubsonicComponentAssembler().evaluate(finOnlyGeometry(0.8, 4), flow()).coefficients();
		var aft = new SubsonicComponentAssembler().evaluate(finOnlyGeometry(1.3, 4), flow()).coefficients();

		double forwardCp = -forward.cm() * REFERENCE_LENGTH / forward.cn();
		double aftCp = -aft.cm() * REFERENCE_LENGTH / aft.cn();
		assertEquals(0.5, aftCp - forwardCp, 1.0e-12);
		assertTrue(Math.abs(aft.cm()) > Math.abs(forward.cm()));
	}

	@Test
	void evenlySpacedFinOrientationSumScalesAsCountOverTwo() {
		var twoFins = new SubsonicComponentAssembler().evaluate(finOnlyGeometry(1, 2), flow()).coefficients();
		var fourFins = new SubsonicComponentAssembler().evaluate(finOnlyGeometry(1, 4), flow()).coefficients();

		assertEquals(2.0, fourFins.cn() / twoFins.cn(), 1.0e-12);
	}

	@Test
	void veryHighIncidenceUsesJorgensenBodyForceAndFinStallHold() {
		PerfectGasAir air = new PerfectGasAir();
		double pressure = 101325, temperature = 288.15;
		AtmosphereState atmosphere = new AtmosphereState(pressure, temperature,
				pressure / (air.gasConstant() * temperature), air.viscosity(temperature));
		FlowCondition corner = FlowCondition.fromAngles(0.3, Math.toRadians(35),
				Math.toRadians(5), atmosphere, air, false, "test");

		var result = new SubsonicComponentAssembler().evaluate(finOnlyGeometry(1, 4), corner);

		assertTrue(result.diagnostics().contains("FIN_NORMAL_FORCE_HELD_AT_20DEG_STALL"));
		assertTrue(result.methods().contains(
				"JORGENSEN_GALEJS_VERY_HIGH_INCIDENCE_BODY_FORCE_V1"));
		assertTrue(Double.isFinite(result.coefficients().cn()));
		assertTrue(Double.isFinite(result.coefficients().cy()));
	}

	private static final double ROOT_CHORD = 0.4;
	private static final double SPAN = 0.2;
	private static final double FIN_AREA = ROOT_CHORD * SPAN;
	private static final double BODY_RADIUS = 0.05;
	private static final double REFERENCE_AREA = Math.PI * BODY_RADIUS * BODY_RADIUS;
	private static final double REFERENCE_LENGTH = 2 * BODY_RADIUS;

	private static AeroGeometry finOnlyGeometry(double start, int count) {
		List<GeometryStation> outline = List.of(
				new GeometryStation(0, 0, 0, 0),
				new GeometryStation(ROOT_CHORD, 0, 0, 0),
				new GeometryStation(ROOT_CHORD, SPAN, 0, 0),
				new GeometryStation(0, SPAN, 0, 0));
		FinGeometry fin = new FinGeometry("RECTANGULAR", "FLAT_PLATE", count,
				ROOT_CHORD, SPAN, FIN_AREA, 0, outline);
		AeroComponent component = new AeroComponent("fins", "/fins", "test", "FIN_RECTANGULAR",
				"stage", 0, new Coordinate(start, 0, 0), start, start + ROOT_CHORD,
				BODY_RADIUS, 2 * FIN_AREA * count, FIN_AREA * count, 0, 0, "ADIABATIC",
				Map.of("thicknessM", 0.002, "spanM", SPAN, "baseRotationRad", 0.0),
				List.of(), null, fin, null);
		ReferenceGeometry references = new ReferenceGeometry(REFERENCE_AREA, 0, 2,
				2 * BODY_RADIUS, Map.of("fins", 2 * FIN_AREA * count), new Coordinate(), REFERENCE_LENGTH);
		return new AeroGeometry(List.of(component), references, "subsonic-fin-ac-test");
	}

	private static FlowCondition flow() {
		PerfectGasAir air = new PerfectGasAir();
		double pressure = 101325, temperature = 288.15;
		AtmosphereState atmosphere = new AtmosphereState(pressure, temperature,
				pressure / (air.gasConstant() * temperature), air.viscosity(temperature));
		return FlowCondition.fromAngles(0.3, Math.toRadians(2), 0, atmosphere, air, false, "test");
	}
}

package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.physicsaero.flow.AtmosphereState;
import info.openrocket.core.aerodynamics.physicsaero.force.AerodynamicCoefficients;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.PerfectGasAir;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroComponent;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AxisymmetricProfile;
import info.openrocket.core.aerodynamics.physicsaero.geometry.FinGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.GeometryStation;
import info.openrocket.core.aerodynamics.physicsaero.geometry.ReferenceGeometry;
import info.openrocket.core.aerodynamics.physicsaero.integration.FullRegimeTableBuilder;
import info.openrocket.core.aerodynamics.physicsaero.table.AerodynamicTable;
import info.openrocket.core.aerodynamics.physicsaero.table.CombinedBodyFinTableBuilder;
import info.openrocket.core.aerodynamics.physicsaero.table.TableCell;
import info.openrocket.core.aerodynamics.physicsaero.table.TableMetadata;
import info.openrocket.core.util.Coordinate;

class PhysicsAeroIntegrationInvariantTest {
	private static final double REGIME_EPSILON = 1e-4;

	@Test
	void fullRegimeZeroLiftDragIsContinuousAtCorrelationHandoffs() {
		AeroGeometry geometry = geometry();
		double[] mach = {0.85 - REGIME_EPSILON, 0.85,
				0.95 - REGIME_EPSILON, 0.95,
				1.2 - REGIME_EPSILON, 1.2,
				1.3, 1.3 + REGIME_EPSILON};
		AerodynamicTable table = new FullRegimeTableBuilder().build(geometry, mach,
				new double[] {0}, new double[] {0}, atmosphere(), new PerfectGasAir(), metadata(geometry));

		double[] ca = new double[mach.length];
		for (int index = 0; index < ca.length; index++) {
			TableCell cell = table.cell(index, 0, 0);
			ca[index] = cell.coefficients().ca();
			double currentMach = mach[index];
			assertTrue(Double.isFinite(ca[index]) && ca[index] >= 0,
					() -> "invalid axial coefficient at Mach " + currentMach);
			for (double coefficient : Arrays.copyOfRange(cell.coefficients().toArray(), 1, 6)) {
				assertEquals(0, coefficient, 1e-12,
						"zero incidence must not create a lateral force or moment");
			}
		}
		assertAll(
				() -> assertTrue(Math.abs(ca[1] - ca[0]) < 0.01,
						() -> "discontinuous start of subsonic/transonic overlap: "
								+ ca[0] + " -> " + ca[1]),
				() -> assertTrue(Math.abs(ca[3] - ca[2]) < 0.01,
						() -> "discontinuous end of subsonic/transonic overlap: "
								+ ca[2] + " -> " + ca[3]),
				() -> assertTrue(Math.abs(ca[5] - ca[4]) < 0.01,
						() -> "discontinuous start of transonic/supersonic overlap: "
								+ ca[4] + " -> " + ca[5]),
				() -> assertTrue(Math.abs(ca[7] - ca[6]) < 0.01,
						() -> "discontinuous end of transonic/supersonic overlap: "
								+ ca[6] + " -> " + ca[7]));
	}

	@Test
	void groupedComponentAndPhysicalOwnerTotalsExactlyRecoverTheCell() {
		AeroGeometry geometry = geometry();
		double angle = Math.toRadians(3);
		AerodynamicTable table = new CombinedBodyFinTableBuilder(false, false).build(geometry,
				new double[] {2}, new double[] {angle}, new double[] {Math.toRadians(2)},
				atmosphere(), new PerfectGasAir(), metadata(geometry));
		TableCell cell = table.cell(0, 0, 0);

		assertArrayEquals(cell.coefficients().toArray(), sum(cell.componentTotals().values()), 1e-12,
				"component grouping lost or duplicated force");
		assertArrayEquals(cell.coefficients().toArray(), sum(cell.ownerTotals().values()), 1e-12,
				"physical-owner grouping lost or duplicated force");
		assertTrue(cell.ownerTotals().get("FIN_BASE_PRESSURE_DRAG").ca() > 0);
		assertTrue(cell.methodIds().contains("FIN_BLUNT_TRAILING_EDGE_BASE_PRESSURE_V1"));
	}

	@Test
	void subsonicAndOverlapCellsRetainMechanismLevelOwnership() {
		AeroGeometry geometry = geometry();
		double angle = Math.toRadians(3);
		AerodynamicTable table = new FullRegimeTableBuilder().build(geometry,
				new double[] {0.5, 0.9}, new double[] {angle}, new double[] {0},
				atmosphere(), new PerfectGasAir(), metadata(geometry));

		TableCell subsonic = table.cell(0, 0, 0);
		assertArrayEquals(subsonic.coefficients().toArray(),
				sum(subsonic.componentTotals().values()), 1e-12,
				"subsonic component ownership must recover the cell");
		assertArrayEquals(subsonic.coefficients().toArray(),
				sum(subsonic.ownerTotals().values()), 1e-12,
				"subsonic physical ownership must recover the cell");
		assertTrue(subsonic.ownerTotals().get("BODY_BASE_PRESSURE_DRAG").ca() > 0);
		assertTrue(subsonic.ownerTotals().get("FIN_BASE_PRESSURE_DRAG").ca() > 0);
		assertTrue(subsonic.ownerTotals().get("BODY_SKIN_FRICTION").ca() > 0);
		assertTrue(subsonic.ownerTotals().get("FIN_SKIN_FRICTION").ca() > 0);
		assertTrue(subsonic.ownerTotals().containsKey("FIN_LEADING_EDGE_PRESSURE_DRAG"));
		assertTrue(subsonic.ownerTotals().containsKey("SUBSONIC_INCIDENCE_LOADS"));

		TableCell overlap = table.cell(1, 0, 0);
		assertArrayEquals(overlap.coefficients().toArray(),
				sum(overlap.ownerTotals().values()), 1e-12,
				"overlap physical ownership must recover the cell");
		assertTrue(overlap.ownerTotals().containsKey("BODY_BASE_PRESSURE_DRAG"));
		assertTrue(overlap.ownerTotals().containsKey("BODY_TRANSONIC_DRAG_RISE"));
		assertTrue(!overlap.ownerTotals().containsKey("FULL_REGIME_OWNER"),
				"two resolved decompositions must not collapse at a smooth join");
	}

	private static double[] sum(Iterable<AerodynamicCoefficients> values) {
		double[] result = new double[6];
		for (var coefficients : values) {
			double[] current = coefficients.toArray();
			for (int index = 0; index < result.length; index++) {
				result[index] += current[index];
			}
		}
		return result;
	}

	private static void assertArrayEquals(double[] expected, double[] actual, double tolerance, String message) {
		assertEquals(expected.length, actual.length, message);
		for (int index = 0; index < expected.length; index++) {
			assertEquals(expected[index], actual[index], tolerance, message + " at coefficient " + index);
		}
	}

	private static AtmosphereState atmosphere() {
		PerfectGasAir air = new PerfectGasAir();
		double pressure = 101325;
		double temperature = 288.15;
		return new AtmosphereState(pressure, temperature,
				pressure / (air.gasConstant() * temperature), air.viscosity(temperature));
	}

	private static TableMetadata metadata(AeroGeometry geometry) {
		return new TableMetadata(TableMetadata.CURRENT_SCHEMA, geometry.geometryHash(), "integration-invariant",
				"test", "physics-aero", "SI;radians", "OPENROCKET_BODY_AXES_V1",
				Instant.parse("2026-01-01T00:00:00Z"), Map.of(), Map.of(), "TEST");
	}

	private static AeroGeometry geometry() {
		double radius = 0.05;
		double coneSlope = radius;
		AxisymmetricProfile coneProfile = new AxisymmetricProfile(List.of(
				new GeometryStation(0, 0, coneSlope, 0),
				new GeometryStation(1, radius, coneSlope, 0)), List.of(), "TEST_CONE", 1e-12);
		AxisymmetricProfile tubeProfile = new AxisymmetricProfile(List.of(
				new GeometryStation(1, radius, 0, 0),
				new GeometryStation(2, radius, 0, 0)), List.of(), "TEST_CYLINDER", 1e-12);
		AeroComponent cone = body("cone", "NOSE_CONICAL", 0, 1, 0, radius, coneProfile);
		AeroComponent tube = body("tube", "CYLINDER", 1, 2, radius, radius, tubeProfile);

		double rootChord = 0.7;
		double tipChord = 0.5;
		double span = 0.1;
		double area = 0.5 * (rootChord + tipChord) * span;
		List<GeometryStation> outline = List.of(
				new GeometryStation(0, 0, 0, 0),
				new GeometryStation(rootChord - tipChord, span, 0, 0),
				new GeometryStation(rootChord, span, 0, 0),
				new GeometryStation(rootChord, 0, 0, 0));
		FinGeometry fin = new FinGeometry("TRAPEZOIDAL", "SINGLE_WEDGE", 4,
				rootChord, span, area, 0, outline);
		AeroComponent fins = new AeroComponent("fins", "/fins", "test", "FIN_TRAPEZOIDAL",
				"stage", 2, new Coordinate(2 - rootChord, 0, 0), 2 - rootChord, 2, radius,
				2 * area * fin.count(), area * fin.count(), 0, 0, "ADIABATIC",
				Map.of("thicknessRatio", 0.04, "baseRotationRad", 0.0), List.of(), null, fin, null);

		double referenceArea = Math.PI * radius * radius;
		ReferenceGeometry references = new ReferenceGeometry(referenceArea, referenceArea, 2,
				2 * radius, Map.of("cone", cone.wettedAreaM2(), "tube", tube.wettedAreaM2(),
						"fins", fins.wettedAreaM2()), new Coordinate(), 2 * radius);
		return new AeroGeometry(List.of(cone, tube, fins), references, "integration-invariant-geometry");
	}

	private static AeroComponent body(String id, String classification, double start, double end,
			double startRadius, double endRadius, AxisymmetricProfile profile) {
		double radius = Math.max(startRadius, endRadius);
		return new AeroComponent(id, '/' + id, "test", classification, "stage", 0,
				new Coordinate(start, 0, 0), start, end, radius, profile.wettedAreaM2(),
				2 * radius * (end - start), Math.PI * endRadius * endRadius, 0, "ADIABATIC",
				Map.of(), List.of(), profile, null, null);
	}
}

package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import info.openrocket.core.aerodynamics.physicsaero.body.AxisymmetricBodyTableBuilder;
import info.openrocket.core.aerodynamics.physicsaero.flow.AtmosphereState;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.PerfectGasAir;
import info.openrocket.core.aerodynamics.physicsaero.geometry.*;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroTableCalculator;
import info.openrocket.core.aerodynamics.physicsaero.table.*;
import info.openrocket.core.util.Coordinate;

class PhaseTwoBodyTableEndToEndTest {
	@TempDir Path temporary;
	@Test void directBodySolutionsRoundTripThroughExecutableZeroIncidenceMachTable() throws Exception {
		double slope = 0.1, radius = 0.1; AeroGeometry geometry = geometry(slope, radius);
		PerfectGasAir air = new PerfectGasAir(); double temperature = 288.15, pressure = 101325;
		AtmosphereState atmosphere = new AtmosphereState(pressure, temperature,
				pressure / (air.gasConstant() * temperature), air.viscosity(temperature));
		TableMetadata metadata = new TableMetadata(TableMetadata.CURRENT_SCHEMA, geometry.geometryHash(), "phase2-settings",
				"phase2-test", "phase2-body-v1", "SI;radians", "OPENROCKET_BODY_AXES_V1", Instant.parse("2026-01-01T00:00:00Z"),
				Map.of("areaM2", Math.PI * radius * radius, "lengthM", 2 * radius), Map.of("cornerAngleRad", Math.toRadians(0.05)), "PHASE2_TESTED");
		AerodynamicTable generated = new AxisymmetricBodyTableBuilder().build(geometry, new double[] {2, 3, 4}, atmosphere, air, metadata);
		Path binary = temporary.resolve("phase2.aero"); new TableWriter().write(generated, binary, temporary.resolve("phase2.json"));
		PhysicsAeroTableCalculator calculator = new PhysicsAeroTableCalculator(new TableReader().read(binary), geometry.geometryHash(), "phase2-settings");
		assertEquals(generated.cell(1, 0, 0).coefficients(), calculator.query(3, 0, 0).coefficients());
		assertTrue(calculator.query(2.5, 0, 0).coefficients().ca() > 0); assertThrows(IllegalArgumentException.class, () -> calculator.query(3, 1e-5, 0));
	}
	private static AeroGeometry geometry(double slope, double radius) {
		AxisymmetricProfile coneProfile = new AxisymmetricProfile(List.of(new GeometryStation(0, 0, slope, 0),
				new GeometryStation(1, radius, slope, 0)), List.of(), "TEST", 1e-9);
		AxisymmetricProfile tubeProfile = new AxisymmetricProfile(List.of(new GeometryStation(1, radius, 0, 0),
				new GeometryStation(2, radius, 0, 0)), List.of(), "TEST", 1e-9);
		AeroComponent cone = component("cone", "NOSE_CONICAL", 0, 1, 0, radius, coneProfile);
		AeroComponent tube = component("tube", "CYLINDER", 1, 2, radius, radius, tubeProfile);
		ReferenceGeometry reference = new ReferenceGeometry(Math.PI * radius * radius, Math.PI * radius * radius,
				2, 2 * radius, Map.of(), new Coordinate(), 2 * radius);
		return new AeroGeometry(List.of(cone, tube), reference, "phase2-cone-cylinder");
	}
	private static AeroComponent component(String id, String classification, double x0, double x1,
			double r0, double r1, AxisymmetricProfile profile) {
		return new AeroComponent(id, "/" + id, "test", classification, "stage", 0, new Coordinate(x0, 0, 0),
				x0, x1, Math.max(r0, r1), profile.wettedAreaM2(), 0, Math.PI * r1 * r1, 0, "ADIABATIC",
				Map.of(), List.of(), profile, null, null);
	}
}

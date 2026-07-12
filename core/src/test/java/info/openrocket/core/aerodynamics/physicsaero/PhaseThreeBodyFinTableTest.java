package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import info.openrocket.core.aerodynamics.physicsaero.flow.*;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.PerfectGasAir;
import info.openrocket.core.aerodynamics.physicsaero.geometry.*;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroTableCalculator;
import info.openrocket.core.aerodynamics.physicsaero.table.*;
import info.openrocket.core.util.Coordinate;

class PhaseThreeBodyFinTableTest {
	@Test void bodyFinTablePopulatesAlphaBetaAndIsDirectlyQueryable() {
		AeroGeometry geometry = geometry(); PerfectGasAir air = new PerfectGasAir(); double p = 101325, t = 288.15;
		AtmosphereState atmosphere = new AtmosphereState(p, t, p / (air.gasConstant() * t), air.viscosity(t));
		TableMetadata metadata = new TableMetadata(TableMetadata.CURRENT_SCHEMA, geometry.geometryHash(), "phase3-settings",
				"phase3-test", "phase3-fin-v1", "SI;radians", "OPENROCKET_BODY_AXES_V1", Instant.parse("2026-01-01T00:00:00Z"),
				Map.of("areaM2", geometry.references().referenceAreaM2()), Map.of("stripCount", 24.0), "PHASE3_TESTED");
		double a = Math.toRadians(3), b = Math.toRadians(2);
		AerodynamicTable table = new CombinedBodyFinTableBuilder(false).build(geometry, new double[] {2,3},
				new double[] {-a,0,a}, new double[] {-b,0,b}, atmosphere, air, metadata);
		var calculator = new PhysicsAeroTableCalculator(table, geometry.geometryHash(), "phase3-settings");
		assertEquals(table.cell(0, 2, 1).coefficients(), calculator.query(2, a, 0).coefficients());
		assertEquals(-calculator.query(2, a, 0).coefficients().cn(), calculator.query(2, -a, 0).coefficients().cn(), 1e-9);
		assertEquals(-calculator.query(2, 0, b).coefficients().cy(), calculator.query(2, 0, -b).coefficients().cy(), 1e-9);
		assertTrue(calculator.query(2, a, 0).coefficients().ca() > 0);
		assertTrue(table.cell(0, 2, 1).validityFlags().contains("PNK_DISABLED_ISOLATED_VALIDATION_GATE"));
		AerodynamicTable withPnk = new CombinedBodyFinTableBuilder(true).build(geometry, new double[] {2},
				new double[] {0,a}, new double[] {0}, atmosphere, air, metadata);
		assertTrue(withPnk.cell(0,1,0).coefficients().cn() > table.cell(0,2,1).coefficients().cn());
		assertTrue(withPnk.cell(0,1,0).ownerTotals().containsKey("BODY_FIN_INTERFERENCE_NORMAL_FORCE"));
	}

	private static AeroGeometry geometry() {
		double radius = .05, slope = radius;
		AxisymmetricProfile coneProfile = new AxisymmetricProfile(List.of(new GeometryStation(0,0,slope,0), new GeometryStation(1,radius,slope,0)), List.of(), "TEST", 1e-9);
		AxisymmetricProfile tubeProfile = new AxisymmetricProfile(List.of(new GeometryStation(1,radius,0,0), new GeometryStation(2,radius,0,0)), List.of(), "TEST", 1e-9);
		AeroComponent cone = body("cone", "NOSE_CONICAL", 0,1,0,radius,coneProfile);
		AeroComponent tube = body("tube", "CYLINDER", 1,2,radius,radius,tubeProfile);
		List<GeometryStation> outline = List.of(new GeometryStation(0,0,0,0), new GeometryStation(.2,.1,0,0), new GeometryStation(.7,.1,0,0), new GeometryStation(1,0,0,0));
		FinGeometry fg = new FinGeometry("TRAPEZOIDAL", "FLAT_PLATE", 4, 1,.1,.075,0,outline);
		AeroComponent fins = new AeroComponent("fins", "/fins", "test", "FIN_TRAPEZOIDAL", "stage", 2, new Coordinate(1,0,0), 1,2,radius,.6,.3,0,0,"ADIABATIC",
				Map.of("thicknessM",.001,"spanM",.1,"baseRotationRad",0.0), List.of(), null,fg,null);
		ReferenceGeometry reference = new ReferenceGeometry(Math.PI*radius*radius, Math.PI*radius*radius,2,2*radius,Map.of(),new Coordinate(),2*radius);
		return new AeroGeometry(List.of(cone,tube,fins),reference,"phase3-body-fin");
	}
	private static AeroComponent body(String id, String classification, double x0, double x1, double r0, double r1, AxisymmetricProfile profile) {
		return new AeroComponent(id,"/"+id,"test",classification,"stage",0,new Coordinate(x0,0,0),x0,x1,Math.max(r0,r1),profile.wettedAreaM2(),0,Math.PI*r1*r1,0,"ADIABATIC",Map.of(),List.of(),profile,null,null);
	}
}

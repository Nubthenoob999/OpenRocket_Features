package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import info.openrocket.core.aerodynamics.physicsaero.diagnostics.CellDiagnostics;
import info.openrocket.core.aerodynamics.physicsaero.fin.SupersonicFinSolver;
import info.openrocket.core.aerodynamics.physicsaero.flow.*;
import info.openrocket.core.aerodynamics.physicsaero.force.*;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.PerfectGasAir;
import info.openrocket.core.aerodynamics.physicsaero.geometry.*;
import info.openrocket.core.aerodynamics.physicsaero.runtime.PhysicsAeroTableCalculator;
import info.openrocket.core.aerodynamics.physicsaero.table.*;
import info.openrocket.core.aerodynamics.physicsaero.validation.rasaero.*;
import info.openrocket.core.aerodynamics.physicsaero.validation.rasaero.RasaeroNormalizationAdapter.*;
import info.openrocket.core.util.Coordinate;

class RasaeroPipelineContractTest {
	private static final Path ROOT=Path.of("src/test/resources/physicsaero/rasaero");

	@Test void p1Ras008ExactNodesMidpointsBoundariesAndExtrapolation() throws Exception {
		AerodynamicTable table=table(); PhysicsAeroTableCalculator calculator=new PhysicsAeroTableCalculator(table,"rasaero-synthetic","settings-v1");
		assertEquals(.1,calculator.query(2,Math.toRadians(2),0).coefficients().cn(),1e-12);
		assertEquals(.05,calculator.query(2,Math.toRadians(1),0).coefficients().cn(),1e-12);
		assertEquals(0,calculator.query(2,0,0).coefficients().cn(),1e-12);
		assertEquals(.2,calculator.query(2,Math.toRadians(4),0).coefficients().cn(),1e-12);
		assertThrows(IllegalArgumentException.class,()->calculator.query(2.01,Math.toRadians(2),0));
		assertThrows(IllegalArgumentException.class,()->calculator.query(2,Math.toRadians(5),0));
	}

	@Test void p1Ras009StaleGeometryAndSettingsHashesAreRejectedBeforeQuery() throws Exception {
		AerodynamicTable table=table();
		assertThrows(IllegalArgumentException.class,()->new PhysicsAeroTableCalculator(table,"changed-geometry","settings-v1"));
		assertThrows(IllegalArgumentException.class,()->new PhysicsAeroTableCalculator(table,"rasaero-synthetic","changed-settings"));
		assertDoesNotThrow(()->new PhysicsAeroTableCalculator(table,"rasaero-synthetic","settings-v1"));
	}

	@Test void p1Ras010InvalidMethodStatesAreNamedAndNeverReusePriorValues() {
		AeroGeometry geometry=minimalFinGeometry();
		IllegalArgumentException below=assertThrows(IllegalArgumentException.class,()->new SupersonicFinSolver().evaluate(geometry,flow(1.19)));
		assertTrue(below.getMessage().contains("OUTSIDE_PHASE3_MACH_RANGE"));
		IllegalArgumentException above=assertThrows(IllegalArgumentException.class,()->new SupersonicFinSolver().evaluate(geometry,flow(5.01)));
		assertTrue(above.getMessage().contains("OUTSIDE_PHASE3_MACH_RANGE"));
	}

	@Test void requiredCoefficientAndFlightReportsAreGeneratedWithExplicitSkips() throws Exception {
		Path reports=Path.of("build/reports/physicsaero"); var comparison=new RasaeroCoefficientComparator().compare(List.of(.3,.31),List.of(.3,.30),.02);
		RasaeroComparisonReportWriter writer=new RasaeroComparisonReportWriter(); writer.writeCoefficientReport(reports,comparison,"geometry-hash","table-hash",2,0);
		var rows=new RasaeroFlightDatasetReader().read(ROOT.resolve("upstream/flight_comparison.csv"));
		var results=new RasaeroFlightRegressionRunner().run(rows,Map.of(),"unavailable","unavailable"); writer.writeFlightReport(reports,results);
		assertEquals("EXPECTED_MULTI_STAGE_SKIP",results.stream().filter(r->r.flightId()==22).findFirst().orElseThrow().reasonCode());
		assertEquals("EXPECTED_MULTI_STAGE_SKIP",results.stream().filter(r->r.flightId()==25).findFirst().orElseThrow().reasonCode());
		for(String file:List.of("rasaero-coefficient-report.json","rasaero-coefficient-report.md","rasaero-flight-report.json","rasaero-flight-report.md"))
			assertTrue(Files.size(reports.resolve(file))>0);
	}

	private static AerodynamicTable table() throws Exception {
		Path raw=ROOT.resolve("raw-aeroplots/phase3/SYNTHETIC-AEROPLOT-001.csv"); var sources=new RasaeroAeroPlotReader().read(raw);
		Context context=new Context("case","geometry",.1016,1.6256,0,raw.toString(),"fixture-hash");
		List<NormalizedRow> off=sources.stream().map(s->new RasaeroNormalizationAdapter().normalize(s,context).get(0)).toList();
		ReferenceState reference=new ReferenceState(1000,.01,1.6256,new Coordinate()); List<TableCell> cells=new ArrayList<>();
		for(NormalizedRow row:off)cells.add(new TableCell(new AerodynamicCoefficients(row.ca(),row.cn(),0,0,0,0),Map.of(),Map.of(),List.of("RASAERO_FIXTURE"),
				new double[]{1,1,1,1,1,1},new double[]{0,0,0,0,0,0},List.of("DIRECT_REFERENCE_NODE"),reference,CellDiagnostics.direct(),true));
		TableMetadata metadata=new TableMetadata(TableMetadata.CURRENT_SCHEMA,"rasaero-synthetic","settings-v1","rasaero-test","rasaero-fixture-v1","SI;radians","OPENROCKET_BODY_AXES_V1",
				Instant.parse("2026-01-01T00:00:00Z"),Map.of(),Map.of(),"REFERENCE_FIXTURE");
		return new AerodynamicTable(new TableAxes(new double[]{2},new double[]{0,Math.toRadians(2),Math.toRadians(4)},new double[]{0}),cells,metadata);
	}
	private static FlowCondition flow(double mach){PerfectGasAir air=new PerfectGasAir();double p=101325,t=288.15;return FlowCondition.fromAngles(mach,0,0,new AtmosphereState(p,t,p/(air.gasConstant()*t),air.viscosity(t)),air,false,"invalid-state");}
	private static AeroGeometry minimalFinGeometry(){
		List<GeometryStation> outline=List.of(new GeometryStation(0,0,0,0),new GeometryStation(0,.1,0,0),new GeometryStation(.1,.1,0,0),new GeometryStation(.2,0,0,0));
		FinGeometry fin=new FinGeometry("TRAPEZOIDAL","FLAT_PLATE",4,.2,.1,.015,0,outline);
		AeroComponent component=new AeroComponent("fins","/fins","test","FIN_TRAPEZOIDAL","stage",0,new Coordinate(),0,.2,.05,.12,.06,0,0,"ADIABATIC",Map.of("thicknessM",.001,"baseRotationRad",0.0),List.of(),null,fin,null);
		return new AeroGeometry(List.of(component),new ReferenceGeometry(Math.PI*.05*.05,0,1,.1,Map.of(),new Coordinate(),.1),"invalid-state");
	}
}

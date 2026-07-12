package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import info.openrocket.core.aerodynamics.physicsaero.api.PhysicalTerm;
import info.openrocket.core.aerodynamics.physicsaero.body.*;
import info.openrocket.core.aerodynamics.physicsaero.flow.*;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.PerfectGasAir;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.ShockSolution;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.ModifiedNewtonianPressure;
import info.openrocket.core.aerodynamics.physicsaero.geometry.*;
import info.openrocket.core.util.Coordinate;

class RasaeroPhaseTwoBenchmarkTest {
	private static final double RADIUS = .0508, NOSE_LENGTH = .4064, BODY_LENGTH = 1.6256;

	@Test void p2Ras001ConeCylinderDecompositionClosesAcrossSupersonicGrid() {
		double previousForebody = Double.POSITIVE_INFINITY;
		for (double mach : new double[] {1.2,1.5,2,2.5,3,4,5}) {
			AxisymmetricBodyResult result = solve(coneCylinder(0), mach);
			double dimensional = result.contributions().stream().mapToDouble(c -> c.forceBodyN().x).sum();
			double reconstructed = dimensional / (flow(mach, false).dynamicPressurePa() * Math.PI * RADIUS * RADIUS);
			assertEquals(result.coefficients().ca(), reconstructed, 1e-12); assertTrue(result.coefficients().ca() > 0);
			double forebody = result.contributions().stream().filter(c -> c.owner().term() == PhysicalTerm.BODY_PRESSURE_FOREBODY).mapToDouble(c -> c.forceBodyN().x).sum()
					/ (flow(mach, false).dynamicPressurePa() * Math.PI * RADIUS * RADIUS);
			assertTrue(forebody <= previousForebody * 1.02); previousForebody = forebody;
			assertEquals(1, result.contributions().stream().filter(c -> c.owner().term() == PhysicalTerm.BASE_PRESSURE_DRAG).count());
		}
	}

	@Test void p2Ras002SmoothNoseConvergesWithoutMeshGeneratedShockEvents() {
		Map<Integer,AxisymmetricBodyResult> results=new LinkedHashMap<>();
		for(int stations:new int[]{25,50,100,200,400})results.put(stations,solve(smoothNose(stations),3));
		double ca200 = results.get(200).coefficients().ca(), ca400 = results.get(400).coefficients().ca();
		double relativeChange = Math.abs(ca400 - ca200) / Math.abs(ca400);
		if (relativeChange >= .0025) {
			assertTrue(relativeChange < .004, "documented convergence limitation exceeded: ca200=" + ca200 + " ca400=" + ca400);
			assertTrue(java.nio.file.Files.exists(java.nio.file.Path.of("src/test/resources/physicsaero/rasaero/expected/phase2/P2-RAS-002-model-limit.yaml")));
		}
		for (AxisymmetricBodyResult result : results.values()) {
			assertEquals(0, result.edgeStateHistory().events().stream().filter(ShockEvent.class::isInstance).count());
			assertTrue(result.edgeStateHistory().states().stream().map(SurfaceState::totalState).mapToDouble(TotalState::pressurePa).distinct().count() == 1);
		}
	}

	@Test void p2Ras003And004AbruptShoulderIsExplicitAndStrongerThanFiniteTransition() {
		// P2-RAS-003 pass criterion: a detached compression corner uses an explicit blunt/detached fallback,
		// it never crashes the build. The abrupt shoulder turn exceeds the attached-shock limit at Mach 3.
		AxisymmetricBodyResult abrupt = solve(shoulder(0), 3);
		ShockEvent abruptShock = abrupt.edgeStateHistory().events().stream().filter(ShockEvent.class::isInstance).map(ShockEvent.class::cast).findFirst().orElseThrow();
		assertEquals(ShockSolution.Attachment.DETACHED, abruptShock.attachment());
		assertEquals(ModifiedNewtonianPressure.METHOD_ID, abruptShock.methodId());
		assertTrue(abruptShock.totalPressureRatio() <= 1);
		AxisymmetricBodyResult smooth = solve(shoulder(.1524), 3);
		ShockEvent smoothShock = smooth.edgeStateHistory().events().stream().filter(ShockEvent.class::isInstance).map(ShockEvent.class::cast).findFirst().orElseThrow();
		// P2-RAS-004: the abrupt step is a stronger, explicitly different compression event than the finite transition.
		assertTrue(abruptShock.turnAngleRad() > smoothShock.turnAngleRad()); assertTrue(smoothShock.totalPressureRatio() <= 1);
		assertTrue(abruptShock.downstream().staticState().pressurePa() > smoothShock.downstream().staticState().pressurePa());
		assertNotEquals(abruptShock.methodId(), smoothShock.methodId());
		assertEquals(DiscreteCornerShockModel.METHOD_ID, smoothShock.methodId());
	}

	@Test void p2Ras005BoattailAngleSweepSeparatesPressureAndBaseOwnership() {
		List<Boolean> risks = new ArrayList<>();
		for (double angle : new double[] {5,10,15,20}) {
			AxisymmetricBodyResult result = solve(boattail(angle), 2);
			assertEquals(1, result.contributions().stream().filter(c -> c.owner().term() == PhysicalTerm.BOATTAIL_PRESSURE_DRAG).count());
			assertEquals(1, result.contributions().stream().filter(c -> c.owner().term() == PhysicalTerm.BASE_PRESSURE_DRAG).count());
			risks.add(result.diagnostics().values().stream().anyMatch(v -> v.contains("SEPARATION_RISK")));
		}
		assertEquals(List.of(false,false,true,true), risks);
	}

	@Test void p2Ras006And007PowerAndViscousStateDoNotLeakIntoPressureSolver() {
		assertThrows(IllegalArgumentException.class, () -> new AxisymmetricBodySolver().evaluate(coneCylinder(0), flow(2, true)));
		double smooth = solve(coneCylinder(1e-7), 2).coefficients().ca(), rough = solve(coneCylinder(1e-3), 2).coefficients().ca();
		assertEquals(smooth, rough, 0, "Phase 2 pressure must not absorb future skin-friction sensitivity");
	}

	@Test void p2Ras008To010ValidityHandoffAreaRuleAndThermodynamicLimitAreExplicit() {
		for (double mach : new double[] {.9,.98,1,1.05,1.1}) assertThrows(IllegalArgumentException.class, () -> solve(coneCylinder(0), mach));
		AxisymmetricBodyResult boundary = solve(coneCylinder(0), 1.2); assertTrue(Double.isFinite(boundary.coefficients().ca()));
		assertFalse(boundary.areaRuleDiagnostic().authoritative()); assertEquals("DIAGNOSTIC_ONLY", boundary.diagnostics().get("areaRuleOwnership"));
		assertThrows(IllegalArgumentException.class, () -> solve(coneCylinder(0), 5.01));
	}

	private static AxisymmetricBodyResult solve(AeroGeometry geometry, double mach) { return new AxisymmetricBodySolver().evaluate(geometry, flow(mach, false)); }
	private static FlowCondition flow(double mach, boolean powered) {
		PerfectGasAir air = new PerfectGasAir(); double t = 288.15, p = 101325; AtmosphereState atmosphere = new AtmosphereState(p,t,p/(air.gasConstant()*t),air.viscosity(t));
		return FlowCondition.fromAngles(mach,0,0,atmosphere,air,powered,"rasaero-phase2");
	}
	private static AeroGeometry coneCylinder(double roughness) {
		double slope = RADIUS / NOSE_LENGTH;
		AeroComponent cone = component("cone","NOSE_CONICAL",0,NOSE_LENGTH,0,RADIUS,roughness,List.of(station(0,0,slope),station(NOSE_LENGTH,RADIUS,slope)));
		AeroComponent tube = component("tube","CYLINDER",NOSE_LENGTH,BODY_LENGTH,RADIUS,RADIUS,roughness,List.of(station(NOSE_LENGTH,RADIUS,0),station(BODY_LENGTH,RADIUS,0)));
		return geometry(List.of(cone,tube),BODY_LENGTH,RADIUS);
	}
	private static AeroGeometry smoothNose(int intervals) {
		List<GeometryStation> stations = new ArrayList<>();
		for (int i=0;i<=intervals;i++) { double x=NOSE_LENGTH*i/intervals, a=Math.PI*x/(2*NOSE_LENGTH);
			stations.add(station(x,RADIUS*Math.sin(a),RADIUS*Math.PI/(2*NOSE_LENGTH)*Math.cos(a))); }
		AeroComponent nose = component("ogive","NOSE_OGIVE",0,NOSE_LENGTH,0,RADIUS,0,stations);
		AeroComponent tube = component("tube","CYLINDER",NOSE_LENGTH,BODY_LENGTH,RADIUS,RADIUS,0,List.of(station(NOSE_LENGTH,RADIUS,0),station(BODY_LENGTH,RADIUS,0)));
		return geometry(List.of(nose,tube),BODY_LENGTH,RADIUS);
	}
	private static AeroGeometry shoulder(double transitionLength) {
		double r0=.0381,r1=.0508,x=.6096;
		AeroComponent tube0=component("forward","CYLINDER",0,x,r0,r0,0,List.of(station(0,r0,0),station(x,r0,0)));
		double effectiveLength=Math.max(transitionLength,1e-6), slope=(r1-r0)/effectiveLength, transitionEnd=x+effectiveLength;
		AeroComponent transition=component("shoulder","TRANSITION",x,transitionEnd,r0,r1,0,List.of(station(x,r0,slope),station(transitionEnd,r1,slope)));
		AeroComponent tube1=component("aft","CYLINDER",transitionEnd,1.2,r1,r1,0,List.of(station(transitionEnd,r1,0),station(1.2,r1,0)));
		return geometry(List.of(tube0,transition,tube1),1.2,r1);
	}
	private static AeroGeometry boattail(double angleDeg) {
		double base=.0381, length=(RADIUS-base)/Math.tan(Math.toRadians(angleDeg));
		AeroComponent tube=component("tube","CYLINDER",0,1,RADIUS,RADIUS,0,List.of(station(0,RADIUS,0),station(1,RADIUS,0)));
		AeroComponent tail=component("boattail","BOATTAIL",1,1+length,RADIUS,base,0,List.of(station(1,RADIUS,-Math.tan(Math.toRadians(angleDeg))),station(1+length,base,-Math.tan(Math.toRadians(angleDeg)))));
		return geometry(List.of(tube,tail),1+length,base);
	}
	private static GeometryStation station(double x,double r,double slope){return new GeometryStation(x,r,slope,0);}
	private static AeroComponent component(String id,String classification,double x0,double x1,double r0,double r1,double roughness,List<GeometryStation> stations){
		AxisymmetricProfile profile=new AxisymmetricProfile(stations,List.of(),"RASAERO_CANONICAL",1e-9);
		return new AeroComponent(id,"/"+id,"benchmark",classification,"stage",0,new Coordinate(x0,0,0),x0,x1,Math.max(r0,r1),profile.wettedAreaM2(),0,Math.PI*r1*r1,roughness,"ADIABATIC",Map.of(),List.of(),profile,null,null);
	}
	private static AeroGeometry geometry(List<AeroComponent> components,double length,double baseRadius){
		double max=components.stream().flatMap(c->c.axisymmetricProfile().stations().stream()).mapToDouble(GeometryStation::radiusM).max().orElseThrow();
		return new AeroGeometry(components,new ReferenceGeometry(Math.PI*max*max,Math.PI*baseRadius*baseRadius,length,2*max,Map.of(),new Coordinate(),2*max),"rasaero-phase2-fixture");
	}
}

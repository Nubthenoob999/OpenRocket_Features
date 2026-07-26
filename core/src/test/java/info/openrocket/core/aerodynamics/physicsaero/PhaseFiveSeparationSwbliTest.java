package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import info.openrocket.core.aerodynamics.physicsaero.api.PhysicalTerm;
import info.openrocket.core.aerodynamics.physicsaero.body.*;
import info.openrocket.core.aerodynamics.physicsaero.boundarylayer.*;
import info.openrocket.core.aerodynamics.physicsaero.coupling.*;
import info.openrocket.core.aerodynamics.physicsaero.flow.*;
import info.openrocket.core.aerodynamics.physicsaero.force.*;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.*;
import info.openrocket.core.aerodynamics.physicsaero.geometry.ReferenceGeometry;
import info.openrocket.core.aerodynamics.physicsaero.geometry.*;
import info.openrocket.core.aerodynamics.physicsaero.table.*;
import info.openrocket.core.aerodynamics.physicsaero.roughness.RoughnessRegime;
import info.openrocket.core.aerodynamics.physicsaero.separation.*;
import info.openrocket.core.aerodynamics.physicsaero.swbli.*;
import info.openrocket.core.aerodynamics.physicsaero.thermal.ReynoldsAnalogyHeatFluxDiagnostic;
import info.openrocket.core.aerodynamics.physicsaero.transition.TransitionState;
import info.openrocket.core.util.Coordinate;

class PhaseFiveSeparationSwbliTest {
	@Test void persistenceRejectsOneStationNoiseAndPromotesSustainedSignal(){
		SeparationPersistenceFilter filter=new SeparationPersistenceFilter();
		assertEquals(SeparationState.ADVERSE_GRADIENT_WARNING,filter.update(SeparationState.INCIPIENT_SEPARATION,false));
		assertEquals(SeparationState.ATTACHED,filter.update(SeparationState.ATTACHED,false));
		assertEquals(SeparationState.ADVERSE_GRADIENT_WARNING,filter.update(SeparationState.INCIPIENT_SEPARATION,false));
		assertEquals(SeparationState.INCIPIENT_SEPARATION,filter.update(SeparationState.INCIPIENT_SEPARATION,false));
	}

	@Test void laminarAndTurbulentRulesAreDistinctAndBubbleIsExplicit(){
		SeparationSignals signals=new SeparationSignals(.0004,1,3.2,-.08,10,-5,1000,.03,.01,0,1,1,1,1,400,false,false);
		var laminar=new LaminarSeparationModel().classify(signals,2);var turbulent=new TurbulentSeparationModel().classify(signals,2);
		assertNotEquals(laminar.reasons(),turbulent.reasons());assertEquals(SeparationState.INCIPIENT_SEPARATION,laminar.state());
		var bubble=new LaminarSeparationBubbleModel().evaluate(signals,.5);
		assertNotNull(bubble.state());assertEquals("UNRESOLVED",bubble.reattachmentStatus());
	}

	@Test void hotWallRaisesRiskColdWallLowersItAndStrongColdCaseStillSeparates(){
		ReferenceState ref=new ReferenceState(100000,.01,1,new Coordinate());
		var cold=new ShockBoundaryLayerCoupler().couple(input(.5,2.6,.002,500,150000),ref);
		var nominal=new ShockBoundaryLayerCoupler().couple(input(1,2.6,.002,500,150000),ref);
		var hot=new ShockBoundaryLayerCoupler().couple(input(1.5,2.6,.002,500,150000),ref);
		assertTrue(cold.thermalRisk()<nominal.thermalRisk());assertTrue(nominal.thermalRisk()<hot.thermalRisk());
		var overwhelming=new ShockBoundaryLayerCoupler().couple(input(.5,4,.0001,120,280000),ref);
		assertEquals(ShockInteractionClassification.SEPARATED,overwhelming.classification());
	}

	@Test void weakShockDoesNotForceTransitionButSeparatedShearCan(){
		ReferenceState ref=new ReferenceState(100000,.01,1,new Coordinate());
		var weak=new ShockBoundaryLayerCoupler().couple(input(1,2.59,.0035,700,65000),ref);
		assertFalse(weak.transitionModification().forced());
		var strong=new ShockBoundaryLayerCoupler().couple(input(1.4,4,.0001,120,280000),ref);
		assertTrue(strong.transitionModification().forced());
	}

	@Test void uncertaintyScenariosRemainNamedBoundsRatherThanAnAverage(){
		ReferenceState ref=new ReferenceState(100000,.01,1,new Coordinate());
		var envelope=new SwbliUncertaintyRunner().run(input(1,3,.001,300,180000),ref);
		assertEquals(6,envelope.scenarios().size());assertSame(envelope.nominal(),envelope.scenarios().get("nominal"));
		assertTrue(envelope.minimumResidualCd()<=envelope.nominal().drag().residualDeltaCd());
		assertTrue(envelope.maximumResidualCd()>=envelope.nominal().drag().residualDeltaCd());
		assertFalse(envelope.minimumScenario().isBlank());assertFalse(envelope.maximumScenario().isBlank());
	}

	@Test void separatedInteractionStopsAttachedShearAndOwnsOnlyResidualDrag(){
		BoundaryLayerHistory history=history();ShockEvent shock=shock(280000);
		ReferenceState ref=new ReferenceState(100000,.01,1,new Coordinate());
		var result=new OneWayViscousCoupling().apply(history,List.of(shock),.1,1,ref,new Coordinate(),1.4,.72);
		assertTrue(result.attachedMarchStopped());
		int firstDownstreamStation=3;assertTrue(result.correctedHistory().states().subList(firstDownstreamStation,result.correctedHistory().states().size()).stream().allMatch(s->s.wallShearPa()==0));
		assertEquals(1,result.contributions().stream().filter(c->c.owner().term()==PhysicalTerm.SKIN_FRICTION).count());
		assertEquals(1,result.contributions().stream().filter(c->c.owner().term()==PhysicalTerm.SEPARATION_DRAG).count());
		var reconciled=new SwbliDragModel().reconcile(input(1,4,.0001,120,280000),1,.03,ref);
		assertEquals(Math.max(0,reconciled.correlationDeltaCd()-.03),reconciled.residualDeltaCd(),1e-15);
	}

	@Test void baseAdapterKeepsBaseOwnershipAndActualExposedArea(){
		PerfectGasAir air=new PerfectGasAir();double p=101325,t=288.15,rho=p/(air.gasConstant()*t);
		AtmosphereState atmosphere=new AtmosphereState(p,t,rho,air.viscosity(t));FlowCondition flow=FlowCondition.fromAngles(2,0,0,atmosphere,air,false,"phase5");
		ReferenceGeometry geometry=new ReferenceGeometry(.02,.003,1,.15,Map.of(),new Coordinate(),.15);
		BoundaryLayerState terminal=state(TransitionState.TURBULENT,1.8,.002,500,1);
		var baseState=new BaseFlowBoundaryLayerAdapter().adapt(SeparationState.SEPARATED,terminal,1,.003,.6);
		ForceContribution plain=new BaseDragModel().evaluate(geometry,flow,"base");ForceContribution coupled=new BaseDragModel().evaluate(geometry,flow,"base",baseState);
		assertEquals(PhysicalTerm.BASE_PRESSURE_DRAG,coupled.owner().term());assertTrue(Math.abs(coupled.forceBodyN().x)>Math.abs(plain.forceBodyN().x));
		double expected=-new HartTn3393SupersonicBasePressureCorrelation().basePressureCoefficient(2,1.4)*flow.dynamicPressurePa()*.003;
		assertEquals(expected,plain.forceBodyN().x,1e-12);
	}

	@Test void separatedHeatFluxIsExplicitlyInvalid(){
		var result=new ReynoldsAnalogyHeatFluxDiagnostic().evaluate(.003,1,500,1005,400,300,.72,SeparationState.SEPARATED);
		assertFalse(result.valid());assertTrue(Double.isNaN(result.heatFluxWM2()));
	}

	@Test void phaseFiveTablePathSerializesExplicitViscousValidity(){
		AeroGeometry geometry=bodyGeometry();PerfectGasAir air=new PerfectGasAir();double p=101325,t=288.15;
		AtmosphereState atmosphere=new AtmosphereState(p,t,p/(air.gasConstant()*t),air.viscosity(t));
		TableMetadata metadata=new TableMetadata(TableMetadata.CURRENT_SCHEMA,geometry.geometryHash(),"phase5-settings","phase5-test","phase5-v1","SI;radians",
				"OPENROCKET_BODY_AXES_V1",Instant.parse("2026-01-01T00:00:00Z"),Map.of(),Map.of(),"PHASE5_TESTED");
		AerodynamicTable table=new CombinedBodyFinTableBuilder(false,true).build(geometry,new double[]{2},new double[]{0},new double[]{0},atmosphere,air,metadata);
		assertTrue(table.cell(0,0,0).validityFlags().contains("PHASE5_ONE_WAY_VISCOUS_COUPLING"),()->table.cell(0,0,0).diagnostics().messages().toString());
		assertTrue(table.cell(0,0,0).ownerTotals().containsKey(PhysicalTerm.SKIN_FRICTION.name()));
	}

	private static ShockInteractionInput input(double thermalRatio,double h,double cf,double reTheta,double p2){
		BoundaryLayerState state=state(TransitionState.LAMINAR,h,cf,reTheta,thermalRatio);
		return new ShockInteractionInput("i","s","track","body",.5,2,1.7,50000,p2,250,320,1,.7,100000,80000,1,.2,"OBLIQUE","WEAK",
				ShockSolution.Attachment.ATTACHED,"test",new Coordinate(1,0,0),1,new Coordinate(500,0,0),new Coordinate(350,0,0),0,state,
				thermalRatio*350,350,.1,false,false,false);
	}
	private static BoundaryLayerState state(TransitionState transition,double h,double cf,double reTheta,double thermalRatio){
		double theta=.001,delta=h*theta,tau=cf*.5*1*500*500;
		return new BoundaryLayerState(theta,delta,.008,h,transition==TransitionState.LAMINAR?0:4,cf,tau,1e6,reTheta,-.03,transition==TransitionState.LAMINAR?0:1,
				transition,AttachedFlowHealth.ATTACHED,RoughnessRegime.HYDRAULICALLY_SMOOTH,thermalRatio*350,"synthetic");
	}
	private static BoundaryLayerHistory history(){
		var edgeUp=surface(.0,500,50000,250,1,2);List<BoundaryLayerStation> stations=List.of(station(.01,edgeUp),station(.3,edgeUp),station(.5,edgeUp),station(.7,edgeUp),station(1,edgeUp));
		List<BoundaryLayerState> states=stations.stream().map(s->state(TransitionState.LAMINAR,4,.0001,120,1.4)).toList();
		SurfaceTrack track=new SurfaceTrack("body","track",BoundaryLayerMode.PLANAR,stations,List.of(),false);return new BoundaryLayerHistory(track,states);
	}
	private static BoundaryLayerStation station(double s,SurfaceState edge){return new BoundaryLayerStation(s,s,new Coordinate(s,0,0),.1,0,0,500,0,0,0,490,0,false,"NONE",edge,new Coordinate(1,0,0),1);}
	private static ShockEvent shock(double p2){SurfaceState up=surface(.5,500,50000,250,1,2),down=surface(.5,350,p2,320,.7,1.2);return new ShockEvent(.5,.2,1,ShockSolution.Attachment.ATTACHED,.8,up,down,"synthetic-shock");}
	private static SurfaceState surface(double x,double velocity,double p,double t,double rho,double mach){double a=velocity/mach;return new SurfaceState(x,.1,new GasState(mach,p,t,rho,velocity),new TotalState(100000,350,1),0,0,a,1.7e-5,"synthetic");}
	private static AeroGeometry bodyGeometry(){
		double r=.1,slope=.1;AxisymmetricProfile coneProfile=new AxisymmetricProfile(List.of(new GeometryStation(0,0,slope,0),new GeometryStation(1,r,slope,0)),List.of(),"TEST",1e-9);
		AxisymmetricProfile tubeProfile=new AxisymmetricProfile(List.of(new GeometryStation(1,r,0,0),new GeometryStation(2,r,0,0)),List.of(),"TEST",1e-9);
		AeroComponent cone=component("cone","NOSE_CONICAL",0,1,0,r,coneProfile),tube=component("tube","CYLINDER",1,2,r,r,tubeProfile);
		return new AeroGeometry(List.of(cone,tube),new ReferenceGeometry(Math.PI*r*r,Math.PI*r*r,2,2*r,Map.of(),new Coordinate(),2*r),"phase5-body");
	}
	private static AeroComponent component(String id,String classification,double x0,double x1,double r0,double r1,AxisymmetricProfile profile){return new AeroComponent(id,"/"+id,"test",classification,"stage",0,new Coordinate(x0,0,0),x0,x1,Math.max(r0,r1),profile.wettedAreaM2(),0,Math.PI*r1*r1,0,"ADIABATIC",Map.of(),List.of(),profile,null,null);}
}

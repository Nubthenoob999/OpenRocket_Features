package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import info.openrocket.core.aerodynamics.physicsaero.api.*;
import info.openrocket.core.aerodynamics.physicsaero.fin.*;
import info.openrocket.core.aerodynamics.physicsaero.flow.*;
import info.openrocket.core.aerodynamics.physicsaero.force.*;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.*;
import info.openrocket.core.aerodynamics.physicsaero.geometry.*;
import info.openrocket.core.aerodynamics.physicsaero.interaction.*;
import info.openrocket.core.aerodynamics.physicsaero.validation.rasaero.*;
import info.openrocket.core.util.Coordinate;

class RasaeroPhaseThreeBenchmarkTest {
	private static final double BODY_RADIUS=.0508, ROOT=.2032, TIP=.1016, SPAN=.1016, SWEEP=.0762, THICKNESS=.003175;

	@Test void p3Ras001DatcomSlopeIsPerRadianOddAndSourceIdentified() {
		DatcomFinLiftModel model = new DatcomFinLiftModel(); double ar=SPAN*SPAN/(((ROOT+TIP)/2)*SPAN), alpha=Math.toRadians(2);
		var positive=model.evaluate(2,ar,Math.atan(SWEEP/SPAN),alpha);
		var negative=model.evaluate(2,ar,Math.atan(SWEEP/SPAN),-alpha);
		assertEquals(positive.normalForceSlopePerRad()*alpha,positive.normalForceCoefficient(),1e-15);
		assertEquals(-positive.normalForceCoefficient(),negative.normalForceCoefficient(),1e-15);
		assertEquals(DatcomFinLiftModel.METHOD_ID,model.metadata().methodId().value());
		assertTrue(model.metadata().sourceCitation().contains("Section 4.1.3.2"));
	}

	@Test void p3Ras002ThreeAndFourFinSetsUseIndividualOrientationsAndCancelCrossAxes() {
		FinResult three=solve(finGeometry(3,SWEEP,"FLAT_PLATE",BODY_RADIUS,0),2,Math.toRadians(3),0);
		FinResult four=solve(finGeometry(4,SWEEP,"FLAT_PLATE",BODY_RADIUS,0),2,Math.toRadians(3),0);
		assertEquals(3,three.contributions().stream().filter(c->c.owner().term()==PhysicalTerm.FIN_LIFT).count());
		assertEquals(4,four.contributions().stream().filter(c->c.owner().term()==PhysicalTerm.FIN_LIFT).count());
		assertTrue(four.coefficients().cn()>three.coefficients().cn());
		assertEquals(0,four.coefficients().cl(),1e-12); assertEquals(0,four.coefficients().cYaw(),1e-12);
	}

	@Test void p3Ras003LeadingEdgeClassificationUsesNormalMachAndActualSweep() {
		assertEquals(LeadingEdgeClassification.SUPERSONIC_LEADING_EDGE,classification(0,2));
		assertEquals(LeadingEdgeClassification.SUPERSONIC_LEADING_EDGE,classification(.0762,2));
		assertEquals(LeadingEdgeClassification.SUBSONIC_LEADING_EDGE,classification(.22,2));
		assertEquals(LeadingEdgeClassification.NEAR_SONIC_LEADING_EDGE,LeadingEdgeClassification.classify(1.01,.02));
	}

	@Test void p3Ras004SectionMethodsRemainExclusiveAndDiamondHasZeroAlphaWaveDrag() {
		AckeretThinFinModel thin=new AckeretThinFinModel(); assertEquals(0,thin.evaluate(2,0,.1,1000).normalForceN(),0);
		PerfectGasAir air=new PerfectGasAir(); GasState gas=new GasState(2,101325,288.15,101325/(air.gasConstant()*288.15),2*air.speedOfSound(288.15));
		double angle=Math.toRadians(3); var diamond=new WedgeDiamondShockExpansionModel().evaluate(gas,air,0,
				new double[]{angle,-angle},new double[]{angle,-angle},new double[]{.5,.5},.1);
		assertTrue(diamond.valid()); assertEquals(0,diamond.normalForceN(),1e-8); assertTrue(diamond.axialForceN()>0);
		var selection=new FinMethodSelector().select(FinSectionFamily.SYMMETRIC_DIAMOND,true,true);
		assertEquals(FinMethodSelector.Method.SHOCK_EXPANSION,selection.authoritative());
		assertEquals(List.of(FinMethodSelector.Method.DATCOM_DIAGNOSTIC),selection.diagnostics());
	}

	@Test void p3Ras005RoundedLeadingEdgeIsNamedExplicitFallback() {
		var result=new RoundedLeadingEdgeCorrection().unavailable(); assertFalse(result.valid());
		assertTrue(result.reason().contains("ROUNDED_LEADING_EDGE")); assertEquals(0,result.dragCorrectionN());
	}

	@Test void p3Ras006UpwashAndPnkAreSeparateIncrementalOwnersAppliedOnce() {
		AeroGeometry attachedGeometry=finGeometry(4,SWEEP,"FLAT_PLATE",BODY_RADIUS,0);
		FinResult attached=solve(attachedGeometry,2,Math.toRadians(3),0);
		assertTrue(attached.contributions().stream().anyMatch(c->c.owner().term()==PhysicalTerm.BODY_UPWASH_NORMAL_FORCE
				&& c.methodId().value().equals(SlenderCircularBodyUpwashModel.METHOD_ID)));
		List<ForceContribution> pnk=new BodyFinInterferenceSolver().evaluate(attachedGeometry,2,attached.contributions());
		assertFalse(pnk.isEmpty()); assertTrue(pnk.stream().allMatch(c->c.owner().term()==PhysicalTerm.BODY_FIN_INTERFERENCE_NORMAL_FORCE
				&& c.validityFlags().contains("PNK_INCREMENT_ONLY")));
		ContributionLedger ledger=new ContributionLedger(); attached.contributions().forEach(ledger::add); pnk.forEach(ledger::add);
		ReferenceState reference=reference(attachedGeometry,flow(2,Math.toRadians(3),0));
		double combined=CoefficientAssembler.assemble(ledger,reference).cn();
		double increment=pnk.stream().mapToDouble(c->c.forceBodyN().z).sum()/(reference.dynamicPressurePa()*reference.referenceAreaM2());
		assertEquals(attached.coefficients().cn()+increment,combined,1e-12);
	}

	@Test void p3Ras007PnkBoundaryIsDeterministicAndNeverExtrapolates() {
		PnkInterferenceModel model=new PnkInterferenceModel(); assertTrue(model.evaluate(2,.8,100,20).valid());
		assertFalse(model.evaluate(2,.8000001,100,20).valid()); assertTrue(model.evaluate(2,0,100,20).valid());
		assertThrows(IllegalArgumentException.class,()->new PnkFactorTable().interpolate(.900001));
	}

	@Test void p3Ras008And009MomentDifferencingAndCpRemainWellDefined() {
		var body=RasaeroComponentDifferencer.Cumulative.fromCp(.2,.3,1,0,2);
		var complete=RasaeroComponentDifferencer.Cumulative.fromCp(.25,.5,1.2,0,2);
		var fin=new RasaeroComponentDifferencer().difference(body,complete,1e-12);
		assertEquals(complete.cm(),body.cm()+fin.cm(),1e-15); assertNotNull(fin.cpM());
		AeroGeometry geometry=finGeometry(4,SWEEP,"FLAT_PLATE",BODY_RADIUS,0); FlowCondition flow=flow(2,Math.toRadians(3),0);
		FinResult loaded=new SupersonicFinSolver().evaluate(geometry,flow); ReferenceState reference=reference(geometry,flow);
		assertTrue(CenterOfPressureDiagnostic.derive(loaded.coefficients(),reference,1e-9).pitchXM().isPresent());
		FinResult zero=new SupersonicFinSolver().evaluate(geometry,flow(2,0,0));
		assertTrue(CenterOfPressureDiagnostic.derive(zero.coefficients(),reference,1e-9).pitchXM().isEmpty());
	}

	@Test void p3Ras010TransonicRangeIsExplicitlyUnsupportedWithoutSingularity() {
		AeroGeometry geometry=finGeometry(4,SWEEP,"FLAT_PLATE",BODY_RADIUS,0);
		for(double mach:new double[]{.8,.9,1,1.05,1.19}) assertThrows(IllegalArgumentException.class,()->solve(geometry,mach,Math.toRadians(2),0));
		for(double mach:new double[]{1.2,1.22,1.25,1.3}) assertTrue(Double.isFinite(solve(geometry,mach,Math.toRadians(2),0).coefficients().cn()));
		assertTrue(java.nio.file.Files.exists(Path.of("src/test/resources/physicsaero/rasaero/expected/phase3/P3-RAS-010-model-limit.yaml")));
	}

	@Test void p3Ras011AlphaBetaRotationAndParityHoldForSymmetricFourFinSet() {
		AeroGeometry geometry=finGeometry(4,SWEEP,"FLAT_PLATE",BODY_RADIUS,0); double angle=Math.toRadians(3);
		FinResult plus=solve(geometry,2,angle,0),minus=solve(geometry,2,-angle,0),beta=solve(geometry,2,0,angle);
		assertEquals(-plus.coefficients().cn(),minus.coefficients().cn(),1e-10);
		assertEquals(plus.coefficients().ca(),minus.coefficients().ca(),1e-10);
		assertEquals(Math.abs(plus.coefficients().cn()),Math.abs(beta.coefficients().cy()),1e-10);
		assertEquals(Math.abs(plus.coefficients().cm()),Math.abs(beta.coefficients().cYaw()),1e-10);
	}

	@Test void p3Ras012ShortFinCanFlightPairIsKeptAsUncalibratedSensitivityData() throws Exception {
		var rows=new RasaeroFlightDatasetReader().read(Path.of("src/test/resources/physicsaero/rasaero/upstream/flight_comparison.csv"));
		var shortCan=rows.stream().filter(r->r.vehicle().equals("Rabia - Short Fin Can")).findFirst().orElseThrow();
		var longCan=rows.stream().filter(r->r.vehicle().equals("Rabia")).findFirst().orElseThrow();
		assertEquals(.86,shortCan.peakMach()); assertEquals(1.14,longCan.peakMach());
		assertNotEquals(shortCan.measuredApogeeFt(),longCan.measuredApogeeFt());
	}

	private static LeadingEdgeClassification classification(double sweep,double mach){
		AeroGeometry geometry=finGeometry(1,sweep,"FLAT_PLATE",BODY_RADIUS,0); var fin=new FinGeometryAdapter().expand(geometry.components().get(0)).get(0);
		FinStrip strip=new FinStripDiscretizer().discretize(fin,20).get(10);
		return new FinLocalFlowFactory().fromFreestream(flow(mach,0,0),strip,new Coordinate(),.02).leadingEdge();
	}
	private static FinResult solve(AeroGeometry geometry,double mach,double alpha,double beta){return new SupersonicFinSolver(20).evaluate(geometry,flow(mach,alpha,beta));}
	private static FlowCondition flow(double mach,double alpha,double beta){PerfectGasAir air=new PerfectGasAir();double t=288.15,p=101325;return FlowCondition.fromAngles(mach,alpha,beta,new AtmosphereState(p,t,p/(air.gasConstant()*t),air.viscosity(t)),air,false,"rasaero-phase3");}
	private static ReferenceState reference(AeroGeometry geometry,FlowCondition flow){return new ReferenceState(flow.dynamicPressurePa(),geometry.references().referenceAreaM2(),geometry.references().referenceLengthM(),geometry.references().momentOriginM());}
	private static AeroGeometry finGeometry(int count,double sweep,String section,double rootRadius,double cant){
		double root=Math.max(ROOT,sweep+TIP),area=(root+TIP)*SPAN/2;
		List<GeometryStation> outline=List.of(new GeometryStation(0,0,0,0),new GeometryStation(sweep,SPAN,0,0),new GeometryStation(sweep+TIP,SPAN,0,0),new GeometryStation(root,0,0,0));
		FinGeometry fin=new FinGeometry("TRAPEZOIDAL",section,count,root,SPAN,area,cant,outline);
		AeroComponent component=new AeroComponent("fins","/fins","benchmark","FIN_TRAPEZOIDAL","stage",0,new Coordinate(1.3,0,0),1.3,1.3+root,rootRadius,2*area*count,area*count,0,0,"ADIABATIC",
				Map.of("thicknessM",THICKNESS,"spanM",SPAN,"baseRotationRad",0.0),List.of(),null,fin,null);
		double referenceRadius=Math.max(BODY_RADIUS,rootRadius);
		return new AeroGeometry(List.of(component),new ReferenceGeometry(Math.PI*referenceRadius*referenceRadius,0,1.6256,2*referenceRadius,Map.of(),new Coordinate(),2*referenceRadius),"rasaero-phase3-fixture");
	}
}

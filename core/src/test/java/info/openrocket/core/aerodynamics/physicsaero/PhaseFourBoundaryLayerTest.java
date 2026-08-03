package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import info.openrocket.core.aerodynamics.physicsaero.boundarylayer.*;
import info.openrocket.core.aerodynamics.physicsaero.flow.*;
import info.openrocket.core.aerodynamics.physicsaero.force.ContributionLedger;
import info.openrocket.core.aerodynamics.physicsaero.force.ViscousContributionAssembler;
import info.openrocket.core.aerodynamics.physicsaero.roughness.RoughnessRegime;
import info.openrocket.core.aerodynamics.physicsaero.thermal.*;
import info.openrocket.core.aerodynamics.physicsaero.transition.*;
import info.openrocket.core.util.Coordinate;

class PhaseFourBoundaryLayerTest {
	@Test void thwaitesRecoversBlasiusAndPressureGradientTrends() {
		double u=30, nu=1.5e-5, s0=1e-4, s1=1;
		double theta0=.664*s0/Math.sqrt(u*s0/nu);
		double theta=Math.sqrt(new ThwaitesLaminarModel().nextThetaSquared(station(s0,u,0,.1,1),station(s1,u,0,.1,1),theta0*theta0));
		double blasius=.664*s1/Math.sqrt(u*s1/nu);
		assertEquals(blasius,theta,blasius*.011);
		LaminarClosure closure=new LaminarClosure();
		assertTrue(closure.shapeFactor(.05)<closure.shapeFactor(0));
		assertTrue(closure.shapeFactor(-.05)>closure.shapeFactor(0));
		assertEquals(2.61014,closure.shapeFactor(0),1e-4);
	}

	@Test void axisymmetricConstantRadiusRecoversPlanarMarch() {
		BoundaryLayerStation a=station(.01,40,0,.2,2*Math.PI*.2), b=station(.5,40,0,.2,2*Math.PI*.2); double t2=2e-8;
		assertEquals(new ThwaitesLaminarModel().nextThetaSquared(a,b,t2),new AxisymmetricThwaitesModel().nextThetaSquared(a,b,t2),1e-18);
	}

	@Test void transitionModelsUsePercentConventionAndRemainIndependent() {
		AbuGhannamShawModel ags=new AbuGhannamShawModel();
		assertTrue(ags.evaluate(1e6,2000,2.59,0,.2).triggered());
		double zeroGradientCriticalAtPointTwoPercent=163+Math.exp(6.91-.2);
		assertEquals(0,ags.evaluate(1e6,zeroGradientCriticalAtPointTwoPercent,2.59,0,.2).margin(),1e-12);
		assertFalse(ags.evaluate(1e6,850,2.59,0,.2).triggered());
		assertTrue(ags.evaluate(1e6,850,2.59,0,.5).triggered(),
				"higher freestream turbulence must move natural transition upstream");
		assertEquals("OUTSIDE_VALIDITY_DOMAIN",ags.evaluate(1e6,2000,2.59,0,.002).reason());
		MichelTransitionCheck michel=new MichelTransitionCheck();
		double expected=1.174*(1+22400.0/1e6)*Math.pow(1e6,.46);
		assertEquals(0,michel.evaluate(1e6,expected,2.59,0,.2).margin(),1e-12);
		assertNotEquals(ags.methodId(),michel.methodId());
	}

	@Test void thermalModelsRecoverReferenceAndAdiabaticWall() {
		assertEquals(SutherlandViscosity.MU0_PA_S,new SutherlandViscosity().viscosityPaS(SutherlandViscosity.T0_K),1e-15);
		RecoveryTemperatureModel recovery=new RecoveryTemperatureModel();
		double laminar=recovery.recoveryTemperatureK(250,2,1.4,.72,false);
		double tr=recovery.recoveryTemperatureK(250,2,1.4,.72,true);
		assertTrue(tr>250);
		assertTrue(tr>laminar);
		assertEquals(.5*(laminar+tr),recovery.recoveryTemperatureK(250,2,1.4,.72,.5),1e-12);
		assertEquals(300,new EckertReferenceTemperature().temperatureK(300,300,0),1e-12);
		double cf=.003;
		assertEquals(cf,new VanDriestIICorrection().apply(cf,1.2,1.8e-5,1.2,1.8e-5),1e-15);
	}

	@Test void adiabaticWallRecoveryFollowsBoundaryLayerTransitionState() {
		SurfaceTrack track=planarTrack(40,300,0);
		BoundaryLayerConfiguration defaults=BoundaryLayerConfiguration.defaults();
		BoundaryLayerConfiguration turbulent=new BoundaryLayerConfiguration(TransitionMode.FULLY_TURBULENT,
				defaults.turbulencePercent(),defaults.transitionBlendLengthM(),defaults.intermittencyExponent(),
				WallThermalBoundary.ADIABATIC,defaults.gamma(),defaults.prandtl(),defaults.minimumVelocityMS(),
				defaults.totalStateRelativeTolerance(),defaults.skinFrictionCompressibilityMode());
		BoundaryLayerResult result=new BoundaryLayerMarcher().march(track,turbulent,new Coordinate());
		BoundaryLayerState terminal=result.history().states().get(result.history().states().size()-1);
		double expected=new RecoveryTemperatureModel().recoveryTemperatureK(
				track.stations().get(track.stations().size()-1).temperatureK(),
				track.stations().get(track.stations().size()-1).mach(),1.4,.72,true);
		assertEquals(expected,terminal.wallTemperatureK(),1e-12);
	}

	@Test void fullyTurbulentMarchStartsFromTurbulentMomentumThicknessWithoutIntermittencyRamp() {
		SurfaceTrack track=planarTrack(40,300,0);
		BoundaryLayerConfiguration defaults=BoundaryLayerConfiguration.defaults();
		BoundaryLayerConfiguration turbulent=new BoundaryLayerConfiguration(TransitionMode.FULLY_TURBULENT,
				defaults.turbulencePercent(),defaults.transitionBlendLengthM(),defaults.intermittencyExponent(),
				WallThermalBoundary.ADIABATIC,defaults.gamma(),defaults.prandtl(),defaults.minimumVelocityMS(),
				defaults.totalStateRelativeTolerance(),defaults.skinFrictionCompressibilityMode());
		BoundaryLayerResult result=new BoundaryLayerMarcher().march(track,turbulent,new Coordinate());
		int start=0;
		while (track.stations().get(start).sM() <= 0) start++;
		BoundaryLayerStation station=track.stations().get(start);
		double reynoldsS=station.streamwiseVelocityMS()*station.sM()/station.kinematicViscosityM2S();
		double expectedTheta=0.036*station.sM()/Math.pow(reynoldsS,0.2);

		assertEquals(expectedTheta,result.history().states().get(start).thetaM(),1e-15);
		assertTrue(result.history().states().stream()
				.allMatch(state -> state.transition()==TransitionState.TURBULENT));
		assertTrue(result.history().states().stream()
				.allMatch(state -> state.intermittency()==1));
	}

	@Test void marcherProducesFiniteHistoryAndExclusiveVectorForce() {
		SurfaceTrack track=planarTrack(121,50,0);
		BoundaryLayerResult result=new BoundaryLayerMarcher().march(track,BoundaryLayerConfiguration.defaults(),new Coordinate());
		assertEquals(track.stations().size(),result.history().states().size());
		assertTrue(result.history().states().stream().allMatch(s->s.thetaM()>0&&s.skinFrictionCoefficient()>0));
		assertTrue(result.skinFriction().forceBodyN().x>0); assertEquals(0,result.skinFriction().forceBodyN().y,1e-12);
		ContributionLedger ledger=new ContributionLedger();
		new ViscousContributionAssembler().assemble(result.history(),new Coordinate(),ledger);
		assertThrows(IllegalStateException.class,()->new ViscousContributionAssembler().assemble(result.history(),new Coordinate(),ledger));
	}

	@Test void validatorRejectsNonMonotoneAndUnregisteredJumps() {
		List<BoundaryLayerStation> stations=new ArrayList<>(planarTrack(3,40,0).stations());
		stations.set(2,station(stations.get(1).sM(),40,0,.1,1));
		SurfaceTrack invalid=new SurfaceTrack("plate","side",BoundaryLayerMode.PLANAR,stations,List.of(),false);
		BoundaryLayerException ex=assertThrows(BoundaryLayerException.class,()->new EdgeStateHistoryValidator().validate(invalid,BoundaryLayerConfiguration.defaults()));
		assertEquals(BoundaryLayerException.Reason.NON_MONOTONE_TRACK,ex.reason());
	}

	@Test void prescribedTransitionReynoldsCreatesOneUserTripAtFirstCrossing() {
		SurfaceTrack original=planarTrack(101,50,0);
		BoundaryLayerStation first=original.stations().get(0);
		double transitionReynolds=1_000_000;
		SurfaceTrack tripped=new SurfaceTrackBuilder().withForcedTransitionReynolds(original,transitionReynolds,
				first.densityKgM3(),first.streamwiseVelocityMS(),first.viscosityPaS());
		List<BoundaryLayerStation> trips=tripped.stations().stream().filter(BoundaryLayerStation::forcedTrip).toList();
		assertEquals(1,trips.size());
		double reynoldsPerM=first.densityKgM3()*first.streamwiseVelocityMS()/first.viscosityPaS();
		assertEquals(transitionReynolds,trips.get(0).sM()*reynoldsPerM,1e-9);
		int tripIndex=tripped.stations().indexOf(trips.get(0));
		assertTrue(tripIndex==0||tripped.stations().get(tripIndex-1).sM()*reynoldsPerM<transitionReynolds);

		BoundaryLayerConfiguration d=BoundaryLayerConfiguration.defaults();
		BoundaryLayerConfiguration userTrip=new BoundaryLayerConfiguration(TransitionMode.USER_TRIPPED,
				d.turbulencePercent(),d.transitionBlendLengthM(),d.intermittencyExponent(),d.wallMode(),
				d.gamma(),d.prandtl(),d.minimumVelocityMS(),d.totalStateRelativeTolerance(),
				d.skinFrictionCompressibilityMode());
		BoundaryLayerResult result=new BoundaryLayerMarcher().march(tripped,userTrip,new Coordinate());
		assertEquals(trips.get(0).sM(),result.transitionLocationM(),0);
		assertTrue(result.history().states().stream().anyMatch(s->s.transition()!=TransitionState.LAMINAR));
	}

	@Test void roughnessIsAppliedInsideWallShearExactlyOnce() {
		BoundaryLayerResult smooth=new BoundaryLayerMarcher().march(planarTrack(80,60,0),BoundaryLayerConfiguration.defaults(),new Coordinate());
		BoundaryLayerResult rough=new BoundaryLayerMarcher().march(planarTrack(80,60,2e-3),BoundaryLayerConfiguration.defaults(),new Coordinate());
		double smoothDrag=Math.abs(smooth.skinFriction().forceBodyN().x),roughDrag=Math.abs(rough.skinFriction().forceBodyN().x);
		assertTrue(roughDrag>smoothDrag);
		assertTrue(rough.history().states().stream().anyMatch(s->s.roughnessRegime()!=RoughnessRegime.HYDRAULICALLY_SMOOTH));
	}

	@Test void compressibilityCorrectionModeIsExplicitAndSelectsVanDriestAboveMachOne() {
		SurfaceTrack track=planarTrack(80,680,0);
		BoundaryLayerConfiguration d=BoundaryLayerConfiguration.defaults();
		BoundaryLayerConfiguration eckertOnly=new BoundaryLayerConfiguration(
				TransitionMode.FULLY_TURBULENT,d.turbulencePercent(),d.transitionBlendLengthM(),
				d.intermittencyExponent(),d.wallMode(),d.gamma(),d.prandtl(),
				d.minimumVelocityMS(),d.totalStateRelativeTolerance(),
				SkinFrictionCompressibilityMode.ECKERT_REFERENCE_ONLY);
		BoundaryLayerConfiguration vanDriest=new BoundaryLayerConfiguration(
				TransitionMode.FULLY_TURBULENT,d.turbulencePercent(),d.transitionBlendLengthM(),
				d.intermittencyExponent(),d.wallMode(),d.gamma(),d.prandtl(),
				d.minimumVelocityMS(),d.totalStateRelativeTolerance(),
				SkinFrictionCompressibilityMode.VAN_DRIEST_II_TRANSITION);
		BoundaryLayerState eckertTerminal=new BoundaryLayerMarcher().march(
				track,eckertOnly,new Coordinate()).history().states().get(track.stations().size()-1);
		BoundaryLayerState vanDriestTerminal=new BoundaryLayerMarcher().march(
				track,vanDriest,new Coordinate()).history().states().get(track.stations().size()-1);

		assertTrue(eckertTerminal.methodId().contains(VanDriestIICorrection.METHOD_ID));
		assertTrue(vanDriestTerminal.methodId().contains(VanDriestIITransformation.METHOD_ID));
		assertNotEquals(eckertTerminal.skinFrictionCoefficient(),
				vanDriestTerminal.skinFrictionCoefficient());
	}

	private static SurfaceTrack planarTrack(int count,double velocity,double roughness) {
		List<BoundaryLayerStation> stations=new ArrayList<>(); double start=1e-4,end=1;
		for(int i=0;i<count;i++){ double s=start+(end-start)*i/(count-1.0); stations.add(station(s,velocity,roughness,.1,1)); }
		return new SurfaceTrack("plate","upper",BoundaryLayerMode.PLANAR,stations,List.of(new BoundaryLayerRegion("upper",BoundaryLayerRegionType.FIN_UPPER,0,count-1)),false);
	}
	private static BoundaryLayerStation station(double s,double velocity,double roughness,double radius,double width) {
		double t=288.15,a=Math.sqrt(1.4*287.05287*t),rho=101325/(287.05287*t),mach=velocity/a;
		GasState gas=new GasState(mach,101325,t,rho,velocity); TotalState total=new TotalState(101325*Math.pow(1+.2*mach*mach,3.5),t*(1+.2*mach*mach),rho);
		SurfaceState edge=new SurfaceState(s,radius,gas,total,0,0,a,1.7894e-5,"synthetic-zpg");
		return new BoundaryLayerStation(s,s,new Coordinate(s,0,0),radius,0,0,velocity,0,0,0,t,roughness,false,"NONE",edge,new Coordinate(1,0,0),width);
	}
}

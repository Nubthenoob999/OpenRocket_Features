package info.openrocket.core.aerodynamics.physicsaero.boundarylayer;

import java.util.ArrayList;
import java.util.List;
import info.openrocket.core.aerodynamics.physicsaero.blending.RegimeOverlap;
import info.openrocket.core.aerodynamics.physicsaero.force.ForceContribution;
import info.openrocket.core.aerodynamics.physicsaero.force.SkinFrictionForceIntegrator;
import info.openrocket.core.aerodynamics.physicsaero.roughness.RoughWallSkinFrictionCorrection;
import info.openrocket.core.aerodynamics.physicsaero.roughness.RoughnessRegime;
import info.openrocket.core.aerodynamics.physicsaero.thermal.EckertReferenceTemperature;
import info.openrocket.core.aerodynamics.physicsaero.thermal.RecoveryTemperatureModel;
import info.openrocket.core.aerodynamics.physicsaero.thermal.SutherlandViscosity;
import info.openrocket.core.aerodynamics.physicsaero.thermal.VanDriestIICorrection;
import info.openrocket.core.aerodynamics.physicsaero.thermal.VanDriestIITransformation;
import info.openrocket.core.aerodynamics.physicsaero.thermal.WallThermalBoundary;
import info.openrocket.core.aerodynamics.physicsaero.transition.AbuGhannamShawModel;
import info.openrocket.core.aerodynamics.physicsaero.transition.IntermittencyModel;
import info.openrocket.core.aerodynamics.physicsaero.transition.MichelTransitionCheck;
import info.openrocket.core.aerodynamics.physicsaero.transition.TransitionDecision;
import info.openrocket.core.aerodynamics.physicsaero.transition.TransitionMode;
import info.openrocket.core.aerodynamics.physicsaero.transition.TransitionState;
import info.openrocket.core.util.Coordinate;

/** One-way attached boundary-layer solver: edge history in, viscous history and wall-shear force out. */
public final class BoundaryLayerMarcher {
	private static final RegimeOverlap SKIN_FRICTION_HANDOFF = new RegimeOverlap(0.9, 1.1,
			VanDriestIICorrection.METHOD_ID, VanDriestIITransformation.METHOD_ID);

	private final EdgeStateHistoryValidator validator = new EdgeStateHistoryValidator();
	private final LaminarClosure laminarClosure = new LaminarClosure();
	private final BoundaryLayerThicknessModel thickness = new BoundaryLayerThicknessModel();
	private final AttachedFlowHealthEvaluator health = new AttachedFlowHealthEvaluator();
	private final AbuGhannamShawModel transition = new AbuGhannamShawModel();
	private final MichelTransitionCheck michel = new MichelTransitionCheck();
	private final IntermittencyModel intermittency = new IntermittencyModel();
	private final RoughWallSkinFrictionCorrection roughWall = new RoughWallSkinFrictionCorrection();

	public BoundaryLayerResult march(SurfaceTrack track, BoundaryLayerConfiguration configuration, Coordinate momentReferenceM) {
		validator.validate(track, configuration);
		List<BoundaryLayerState> output = new ArrayList<>(); List<String> diagnostics = new ArrayList<>();
		int start = firstMarchable(track); BoundaryLayerStation initialStation = track.stations().get(start);
		double theta = new LeadingEdgeInitializer().thetaM(initialStation), transitionS = Double.NaN;
		HeadEntrainmentModel head = new HeadEntrainmentModel(); HeadEntrainmentModel.State turbulent = null;
		ThwaitesLaminarModel thwaites = track.mode() == BoundaryLayerMode.AXISYMMETRIC ? new AxisymmetricThwaitesModel() : new ThwaitesLaminarModel();
		BoundaryLayerState initial = laminarState(initialStation, theta, 0, TransitionState.LAMINAR, configuration);
		for (int i=0; i<start; i++) output.add(initial); output.add(initial);
		for (int i = start + 1; i < track.stations().size(); i++) {
			BoundaryLayerStation a = track.stations().get(i-1), b = track.stations().get(i);
			TransitionMode mode = configuration.transitionMode(); boolean trigger = mode == TransitionMode.FULLY_TURBULENT || (mode == TransitionMode.USER_TRIPPED && b.forcedTrip());
			if (turbulent == null && trigger && "FORCED_TRIP".equals(b.event()) && b.sM() > a.sM()) {
				// March the upstream interval as laminar and initialize the turbulent
				// closure exactly at the prescribed station.  Stepping Head's model
				// over this same interval would move the trip one grid cell upstream.
				theta = Math.sqrt(thwaites.nextThetaSquared(a, b, theta * theta));
				BoundaryLayerState onset = laminarState(b, theta, 0, TransitionState.TRANSITIONAL, configuration);
				transitionS = b.sM();
				turbulent = head.initialize(theta, onset.displacementThicknessM(),
						Math.max(b.streamwiseVelocityMS(), configuration.minimumVelocityMS()),
						Math.max(onset.reynoldsTheta(), 1));
				output.add(onset);
				continue;
			}
			if (!"NONE".equals(b.event())) {
				BoundaryLayerState upstream = output.get(output.size() - 1);
				if ("FORCED_TRIP".equals(b.event())) trigger = true;
				if (turbulent != null) {
					turbulent = head.initialize(theta, upstream.displacementThicknessM(), Math.max(b.streamwiseVelocityMS(), configuration.minimumVelocityMS()), Math.max(1, b.streamwiseVelocityMS()*theta/b.kinematicViscosityM2S()));
					output.add(turbulentState(b, turbulent, 1, configuration));
					continue;
				}
				if (!trigger) {
					output.add(laminarState(b, theta, 0, TransitionState.LAMINAR, configuration));
					continue;
				}
			}
			if (turbulent == null && !trigger) {
				theta = Math.sqrt(thwaites.nextThetaSquared(a, b, theta*theta));
				double lambda = theta*theta / b.kinematicViscosityM2S() * b.velocityGradientPerS();
				double reTheta = b.streamwiseVelocityMS()*theta/b.kinematicViscosityM2S();
				if (mode == TransitionMode.NATURAL) {
					TransitionDecision primary = transition.evaluate(b.streamwiseVelocityMS()*b.sM()/b.kinematicViscosityM2S(), reTheta, 2.59, lambda, configuration.turbulencePercent());
					TransitionDecision check = michel.evaluate(b.streamwiseVelocityMS()*b.sM()/b.kinematicViscosityM2S(), reTheta, 2.59, lambda, configuration.turbulencePercent());
					trigger = primary.triggered(); if (primary.triggered() != check.triggered()) diagnostics.add("TRANSITION_MODEL_DISAGREEMENT@"+b.sM());
				}
			}
			if (turbulent == null && trigger && mode != TransitionMode.FULLY_LAMINAR) {
				transitionS = b.sM(); BoundaryLayerState previous = output.get(output.size()-1);
				turbulent = head.initialize(theta, previous.displacementThicknessM(), Math.max(b.streamwiseVelocityMS(), configuration.minimumVelocityMS()), Math.max(previous.reynoldsTheta(),1));
			}
			if (turbulent != null) {
				turbulent = head.step(a,b,turbulent); theta=turbulent.thetaM();
				double gamma = intermittency.value(b.sM(), transitionS, configuration.transitionBlendLengthM(), configuration.intermittencyExponent());
				output.add(turbulentState(b,turbulent,gamma,configuration));
			} else output.add(laminarState(b,theta,0,TransitionState.LAMINAR,configuration));
		}
		BoundaryLayerHistory history = new BoundaryLayerHistory(track, output);
		ForceContribution force = new SkinFrictionForceIntegrator().integrate(history, momentReferenceM);
		double drag = Math.sqrt(force.forceBodyN().x*force.forceBodyN().x + force.forceBodyN().y*force.forceBodyN().y + force.forceBodyN().z*force.forceBodyN().z);
		return new BoundaryLayerResult(history, force, transitionS, drag*0.95, drag*1.05, diagnostics);
	}
	private int firstMarchable(SurfaceTrack track) {
		for (int i=0;i<track.stations().size();i++) { BoundaryLayerStation s=track.stations().get(i); if (s.sM()>0 && s.streamwiseVelocityMS()>0 && (track.mode()==BoundaryLayerMode.PLANAR || s.radiusM()>0)) return i; }
		throw new BoundaryLayerException(BoundaryLayerException.Reason.INVALID_EDGE_HISTORY,"track has no finite-distance initialization station");
	}
	private BoundaryLayerState laminarState(BoundaryLayerStation s,double theta,double gamma,TransitionState transitionState,BoundaryLayerConfiguration c) {
		double lambda=theta*theta/s.kinematicViscosityM2S()*s.velocityGradientPerS(); double h=laminarClosure.shapeFactor(lambda);
		double reTheta=s.streamwiseVelocityMS()*theta/s.kinematicViscosityM2S();
		if (reTheta <= 0) throw new BoundaryLayerException(BoundaryLayerException.Reason.NONPHYSICAL_STATE,"nonpositive momentum-thickness Reynolds number");
		double cf=laminarClosure.skinFriction(lambda,reTheta);
		return finish(s,theta,h,0,cf,gamma,transitionState,c,LaminarClosure.METHOD_ID,lambda);
	}
	private BoundaryLayerState turbulentState(BoundaryLayerStation s,HeadEntrainmentModel.State state,double gamma,BoundaryLayerConfiguration c) {
		return finish(s,state.thetaM(),state.h(),state.h1(),state.cf(),gamma,gamma<0.99?TransitionState.TRANSITIONAL:TransitionState.TURBULENT,c,HeadEntrainmentModel.METHOD_ID,0);
	}
	private BoundaryLayerState finish(BoundaryLayerStation s,double theta,double h,double h1,double baseCf,double gamma,TransitionState ts,BoundaryLayerConfiguration c,String method,double lambda) {
		double wallTemperature = c.wallMode() == WallThermalBoundary.ADIABATIC
				? new RecoveryTemperatureModel().recoveryTemperatureK(s.temperatureK(), s.mach(),
						c.gamma(), c.prandtl(), gamma)
				: s.wallTemperatureK();
		double tStar=new EckertReferenceTemperature().temperatureK(s.temperatureK(),wallTemperature,s.mach()); double muStar=new SutherlandViscosity().viscosityPaS(tStar);
		double rhoStar=s.pressurePa()/(287.05287*tStar);
		double eckertCf=new VanDriestIICorrection().apply(baseCf,s.densityKgM3(),s.viscosityPaS(),rhoStar,muStar);
		double reS=s.streamwiseVelocityMS()*s.sM()/s.kinematicViscosityM2S();
		double cf=eckertCf;
		String compressibilityMethod=VanDriestIICorrection.METHOD_ID;
		if (c.skinFrictionCompressibilityMode()==SkinFrictionCompressibilityMode.VAN_DRIEST_II_TRANSITION
				&& s.mach()>SKIN_FRICTION_HANDOFF.startMach()) {
			VanDriestIITransformation vanDriest=new VanDriestIITransformation();
			double transformedCf=baseCf*vanDriest.compressibilityFactor(
					s.mach(),Math.max(1_000,reS),s.temperatureK(),wallTemperature);
			if (s.mach()>=SKIN_FRICTION_HANDOFF.endMach()) {
				cf=transformedCf;
				compressibilityMethod=VanDriestIITransformation.METHOD_ID;
			} else {
				double weight=SKIN_FRICTION_HANDOFF.smoothWeight(s.mach());
				cf=eckertCf+(transformedCf-eckertCf)*weight;
				compressibilityMethod=VanDriestIICorrection.METHOD_ID+"+"+VanDriestIITransformation.METHOD_ID;
			}
		}
		double tau=0.5*cf*s.densityKgM3()*s.streamwiseVelocityMS()*s.streamwiseVelocityMS(); double uTau=Math.sqrt(Math.max(0,tau/s.densityKgM3()));
		double nuWall=new SutherlandViscosity().viscosityPaS(wallTemperature)/(s.pressurePa()/(287.05287*wallTemperature)); double ksPlus=uTau*s.roughnessM()/nuWall;
		cf=roughWall.apply(cf,ksPlus); tau=0.5*cf*s.densityKgM3()*s.streamwiseVelocityMS()*s.streamwiseVelocityMS(); RoughnessRegime rr=roughWall.regime(ksPlus);
		double deltaStar=h*theta, delta99=thickness.delta99M(theta,h,ts), reTheta=s.streamwiseVelocityMS()*theta/s.kinematicViscosityM2S();
		return new BoundaryLayerState(theta,deltaStar,delta99,h,h1,cf,tau,reS,reTheta,lambda,gamma,ts,health.evaluate(lambda,h,ts),rr,wallTemperature,method+"+"+compressibilityMethod);
	}
}

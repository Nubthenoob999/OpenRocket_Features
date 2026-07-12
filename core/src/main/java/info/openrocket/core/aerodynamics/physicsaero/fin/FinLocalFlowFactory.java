package info.openrocket.core.aerodynamics.physicsaero.fin;

import info.openrocket.core.aerodynamics.physicsaero.flow.*;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.IsentropicRelations;
import info.openrocket.core.util.Coordinate;
import info.openrocket.core.util.CoordinateIF;

/** Builds incidence and leading-edge normal Mach from the final local velocity vector. */
public final class FinLocalFlowFactory {
	public FinLocalFlow fromFreestream(FlowCondition flow, FinStrip strip, CoordinateIF upwashVelocity, double epsilonMach) {
		Coordinate velocity = new Coordinate(flow.velocityBody().getX() + upwashVelocity.getX(),
				flow.velocityBody().getY() + upwashVelocity.getY(), flow.velocityBody().getZ() + upwashVelocity.getZ());
		double speed = velocity.length(), sound = flow.thermodynamics().speedOfSound(flow.atmosphere().temperatureK());
		GasState state = new GasState(speed / sound, flow.atmosphere().pressurePa(), flow.atmosphere().temperatureK(),
				flow.atmosphere().densityKgM3(), speed);
		return build(state, IsentropicRelations.totalState(state, flow.thermodynamics()), velocity, strip, epsilonMach, "FREESTREAM_PLUS_UPWASH");
	}
	public FinLocalFlow replaceWithPostShock(SurfaceState downstream, CoordinateIF velocityDirectionBody,
			FinStrip strip, double epsilonMach, String shockId) {
		Coordinate direction = new Coordinate(velocityDirectionBody.getX(), velocityDirectionBody.getY(), velocityDirectionBody.getZ());
		Coordinate velocity = (Coordinate) direction.normalize().multiply(downstream.staticState().velocityMS());
		return build(downstream.staticState(), downstream.totalState(), velocity, strip, epsilonMach, "POST_SHOCK:" + shockId);
	}
	private static FinLocalFlow build(GasState state, TotalState total, Coordinate velocity, FinStrip strip, double epsilonMach, String source) {
		double chord = velocity.dot(strip.localFrame().chordwise()), normal = velocity.dot(strip.localFrame().normal());
		double incidence = Math.atan2(normal, chord);
		double slope = Math.tan(strip.leadingEdgeSweepRad());
		Coordinate inPlaneNormal = strip.localFrame().toBody(new Coordinate(1, -slope, 0));
		// GasState has no sound-speed member; V/M is exact for a consistent complete state.
		double sound = state.mach() > 0 ? state.velocityMS() / state.mach() : Double.POSITIVE_INFINITY;
		double normalMach = Math.abs(velocity.dot(inPlaneNormal.normalize())) / sound;
		return new FinLocalFlow(state, total, velocity, incidence, normalMach,
				LeadingEdgeClassification.classify(normalMach, epsilonMach), source);
	}
}

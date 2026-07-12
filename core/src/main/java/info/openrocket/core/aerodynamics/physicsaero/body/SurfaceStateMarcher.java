package info.openrocket.core.aerodynamics.physicsaero.body;

import java.util.ArrayList;
import java.util.List;
import info.openrocket.core.aerodynamics.physicsaero.flow.*;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.*;
import info.openrocket.core.aerodynamics.physicsaero.geometry.GeometryStation;

/** Cumulative zero-incidence inviscid state marcher; never restarts a corner from freestream. */
public final class SurfaceStateMarcher {
	public AxisymmetricEdgeStateHistory march(List<AxisymmetricBodySegment> segments, FlowCondition flow) {
		if (segments.isEmpty()) throw new IllegalArgumentException("body segment list is empty");
		if (flow.powered()) throw new IllegalArgumentException("POWERED_BASE_MODEL_NOT_IMPLEMENTED");
		if (Math.abs(flow.alphaRad()) > 1e-12 || Math.abs(flow.betaRad()) > 1e-12) throw new IllegalArgumentException("ZERO_INCIDENCE_ONLY");
		ThermodynamicModel model = flow.thermodynamics();
		GasState freestream = new GasState(flow.mach(), flow.atmosphere().pressurePa(), flow.atmosphere().temperatureK(),
				flow.atmosphere().densityKgM3(), flow.velocityBody().length());
		TotalState total = IsentropicRelations.totalState(freestream, model);
		AxisymmetricBodySegment first = segments.get(0);
		SurfaceState current = SurfaceState.of(first.startXM(), first.startRadiusM(), freestream, total, 0, 0, model, "FREESTREAM");
		SurfaceState freestreamSurface = current;
		List<SurfaceState> states = new ArrayList<>(); List<FlowEvent> events = new ArrayList<>(); states.add(current);
		for (AxisymmetricBodySegment segment : segments) {
			// Once a compression event has detached, the edge flow is a subsonic blunt/detached pocket. The supersonic
			// isentropic relations (Prandtl-Meyer, oblique/tangent-cone) are undefined there, so continue every remaining
			// panel with the explicit modified-Newtonian fallback referenced to the freestream instead of aborting.
			if (current.staticState().mach() <= 1.0) {
				double surfaceAngle = Math.abs(segment.endTangentRad());
				current = modifiedNewtonianState(freestreamSurface, segment.endXM(), segment.endRadiusM(), surfaceAngle, model, false);
				states.add(current); continue;
			}
			switch (segment.type()) {
				case TRUE_CONE -> {
					double coneAngle = Math.max(Math.abs(segment.startTangentRad()), Math.abs(segment.endTangentRad()));
					TaylorMaccollSolution cone = new TaylorMaccollSolver().solve(current.staticState(), coneAngle, model);
					SurfaceState downstream; ShockEvent event;
					if (cone.attached()) {
						downstream = SurfaceState.of(segment.endXM(), segment.endRadiusM(), cone.wallState(),
								cone.downstreamTotalState(), coneAngle, cone.wallPressureCoefficient(), model, cone.methodId());
						event = new ShockEvent(segment.startXM(), coneAngle, cone.shockAngleRad(), cone.attachment(),
								cone.totalPressureRatio(), current, downstream, cone.methodId());
					} else {
						downstream = modifiedNewtonianState(current, segment.endXM(), segment.endRadiusM(), coneAngle, model, false);
						double ratio = downstream.totalState().pressurePa() / current.totalState().pressurePa();
						event = new ShockEvent(segment.startXM(), coneAngle, Double.NaN, ShockSolution.Attachment.DETACHED,
								ratio, current, downstream, ModifiedNewtonianPressure.METHOD_ID);
					}
					events.add(event); current = downstream; states.add(current);
				}
				case SMOOTH_COMPRESSION -> {
					List<GeometryStation> stations = segment.quadratureStations();
					SurfaceState segmentUpstream = current;
					GeometryStation firstStation = stations.get(0);
					if (Math.abs(firstStation.slope()) >= 1e-12) {
						double firstAngle = Math.atan(firstStation.slope());
						try { current = new TangentConePressureModel().evaluate(segmentUpstream, firstStation.xM(), firstStation.radiusM(), firstAngle, model); }
						catch (GasDynamicsException detached) { current = modifiedNewtonianState(segmentUpstream, firstStation.xM(), firstStation.radiusM(), firstAngle, model, true); }
						states.add(current);
					}
					for (int i = 1; i < stations.size(); i++) {
						GeometryStation station = stations.get(i);
						if (Math.abs(station.slope()) < 1e-12) {
							current = SurfaceState.of(station.xM(), station.radiusM(), segmentUpstream.staticState(),
									segmentUpstream.totalState(), 0, 0, model, TangentConePressureModel.METHOD_ID);
							states.add(current); continue;
						}
						double angle = Math.atan(station.slope());
						try { current = new TangentConePressureModel().evaluate(segmentUpstream, station.xM(), station.radiusM(), angle, model); }
						catch (GasDynamicsException detached) { current = modifiedNewtonianState(segmentUpstream, station.xM(), station.radiusM(), angle, model, true); }
						states.add(current);
					}
				}
				case DISCRETE_COMPRESSION_CORNER -> {
					double turn = Math.abs(segment.turnAngleRad());
					ShockEvent event;
					try {
						event = new DiscreteCornerShockModel().apply(current, segment.endXM(), segment.endRadiusM(), turn, model);
					} catch (GasDynamicsException detached) {
						// Turn exceeds the attached-shock detachment limit: use the explicit modified-Newtonian
						// blunt/detached fallback (Phase 2 §8), never a silent crash of the whole build.
						SurfaceState downstream = modifiedNewtonianState(current, segment.endXM(), segment.endRadiusM(), turn, model, false);
						double ratio = downstream.totalState().pressurePa() / current.totalState().pressurePa();
						event = new ShockEvent(segment.startXM(), turn, Double.NaN, ShockSolution.Attachment.DETACHED,
								ratio, current, downstream, ModifiedNewtonianPressure.METHOD_ID);
					}
					events.add(event); current = event.downstream(); states.add(current);
				}
				case SMOOTH_EXPANSION, DISCRETE_EXPANSION_CORNER, BOATTAIL -> {
					double turn = Math.abs(segment.turnAngleRad());
					if (turn > 1e-12) {
						ExpansionEvent event = new ExpansionTurnModel().apply(current, segment.endXM(), segment.endRadiusM(), turn, model);
						events.add(event); current = event.downstream(); states.add(current);
					} else { current = copyAt(current, segment.endXM(), segment.endRadiusM(), model, segment.type().name()); states.add(current); }
				}
				case CYLINDER, BASE, NOSE_STAGNATION_REGION -> {
					current = copyAt(current, segment.endXM(), segment.endRadiusM(), model, segment.type().name()); states.add(current);
				}
			}
		}
		return new AxisymmetricEdgeStateHistory(states, events);
	}
	private static SurfaceState copyAt(SurfaceState current, double x, double radius, ThermodynamicModel model, String method) {
		return SurfaceState.of(x, radius, current.staticState(), current.totalState(), current.flowAngleRad(),
				current.pressureCoefficient(), model, method);
	}
	private static SurfaceState modifiedNewtonianState(SurfaceState upstream, double x, double radius, double angle,
			ThermodynamicModel model, boolean preserveIncomingTotal) {
		double cp = new ModifiedNewtonianPressure().pressureCoefficient(upstream.staticState(), angle, model);
		double q = 0.5 * upstream.staticState().densityKgM3() * upstream.staticState().velocityMS() * upstream.staticState().velocityMS();
		double pressure = upstream.staticState().pressurePa() + cp * q;
		TotalState total;
		if (preserveIncomingTotal) total = upstream.totalState();
		else {
			ShockSolution normal = NormalShockCalculator.solve(upstream.staticState(), model);
			double ratio = normal.downstreamTotal().pressurePa() / normal.upstreamTotal().pressurePa();
			total = new TotalState(upstream.totalState().pressurePa() * ratio, upstream.totalState().temperatureK(),
					upstream.totalState().densityKgM3() * ratio);
		}
		double gamma = model.gamma(upstream.staticState().temperatureK());
		double ptOverP = total.pressurePa() / pressure;
		if (ptOverP < 1) throw new GasDynamicsException("MODIFIED_NEWTONIAN_NONPHYSICAL_PRESSURE");
		double mach2 = 2 / (gamma - 1) * (Math.pow(ptOverP, (gamma - 1) / gamma) - 1);
		GasState state = IsentropicRelations.staticState(total, Math.sqrt(Math.max(0, mach2)), model);
		return SurfaceState.of(x, radius, state, total, angle, cp, model, ModifiedNewtonianPressure.METHOD_ID);
	}
}

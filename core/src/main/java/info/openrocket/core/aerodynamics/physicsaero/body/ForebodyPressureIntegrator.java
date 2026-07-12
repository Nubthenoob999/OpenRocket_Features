package info.openrocket.core.aerodynamics.physicsaero.body;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import info.openrocket.core.aerodynamics.physicsaero.api.*;
import info.openrocket.core.aerodynamics.physicsaero.flow.SurfaceState;
import info.openrocket.core.aerodynamics.physicsaero.force.ForceContribution;
import info.openrocket.core.util.Coordinate;

/** Axisymmetric vector pressure integration reduced analytically after sign verification. */
public final class ForebodyPressureIntegrator {
	public ForceContribution integrate(AxisymmetricBodySegment segment, AxisymmetricEdgeStateHistory history,
			double freestreamPressurePa, PhysicalTerm term) {
		if (segment.endXM() <= segment.startXM() || segment.endRadiusM() == segment.startRadiusM()) return zero(segment, term);
		List<SurfaceState> local = history.states().stream().filter(s -> s.xM() >= segment.startXM() - 1e-12
				&& s.xM() <= segment.endXM() + 1e-12).sorted(Comparator.comparingDouble(SurfaceState::xM)).toList();
		if (local.isEmpty()) throw new IllegalStateException("no edge state for segment " + segment.regionId());
		List<SurfaceState> owned = local.stream().filter(s -> ownsSurfaceState(segment.type(), s.methodId())).toList();
		if (!owned.isEmpty()) local = owned;
		double forceX = 0, weightedX = 0;
		List<info.openrocket.core.aerodynamics.physicsaero.geometry.GeometryStation> stations = segment.quadratureStations();
		if (stations.size() < 2) {
			double dp = local.get(local.size() - 1).staticState().pressurePa() - freestreamPressurePa;
			forceX = Math.PI * dp * (segment.endRadiusM() * segment.endRadiusM() - segment.startRadiusM() * segment.startRadiusM());
			weightedX = forceX * 0.5 * (segment.startXM() + segment.endXM());
		} else {
			for (int i = 1; i < stations.size(); i++) {
				var a = stations.get(i - 1); var b = stations.get(i); double xm = 0.5 * (a.xM() + b.xM());
				double pressure = segment.type() == BodySegmentType.SMOOTH_COMPRESSION
						? interpolatedPressure(local, xm) : local.get(local.size() - 1).staticState().pressurePa();
				double dp = pressure - freestreamPressurePa;
				double dForce = Math.PI * dp * (b.radiusM() * b.radiusM() - a.radiusM() * a.radiusM());
				forceX += dForce; weightedX += dForce * xm;
			}
		}
		double applicationX = Math.abs(forceX) > 1e-15 ? weightedX / forceX : 0.5 * (segment.startXM() + segment.endXM());
		SurfaceState provenance = local.get(local.size() - 1);
		return new ForceContribution(segment.componentId(), new PhysicalOwner(term, OwnershipMode.REPLACES, segment.regionId(), null),
				new MethodId(provenance.methodId()), new Coordinate(forceX, 0, 0), new Coordinate(),
				new Coordinate(applicationX, 0, 0), segment.regionId(), List.of(), 0.9, 0.05, null);
	}
	private static SurfaceState nearest(List<SurfaceState> states, double x) {
		return states.stream().min(Comparator.comparingDouble(s -> Math.abs(s.xM() - x))).orElseThrow();
	}
	private static double interpolatedPressure(List<SurfaceState> states, double x) {
		if (x <= states.get(0).xM()) return states.get(0).staticState().pressurePa();
		for (int i = 1; i < states.size(); i++) {
			SurfaceState left = states.get(i - 1), right = states.get(i);
			if (x <= right.xM()) {
				double width = right.xM() - left.xM();
				if (width <= 0) return right.staticState().pressurePa();
				double fraction = (x - left.xM()) / width;
				return left.staticState().pressurePa() + fraction *
						(right.staticState().pressurePa() - left.staticState().pressurePa());
			}
		}
		return states.get(states.size() - 1).staticState().pressurePa();
	}
	private static boolean ownsSurfaceState(BodySegmentType type, String methodId) {
		return switch (type) {
			case TRUE_CONE -> methodId.contains("TAYLOR_MACCOLL") || methodId.contains("MODIFIED_NEWTONIAN");
			case SMOOTH_COMPRESSION -> methodId.contains("TANGENT_CONE") || methodId.contains("MODIFIED_NEWTONIAN");
			case DISCRETE_COMPRESSION_CORNER -> methodId.contains("OBLIQUE_SHOCK");
			case BOATTAIL, SMOOTH_EXPANSION -> methodId.contains("PRANDTL_MEYER");
			default -> false;
		};
	}
	private static ForceContribution zero(AxisymmetricBodySegment segment, PhysicalTerm term) {
		return new ForceContribution(segment.componentId(), new PhysicalOwner(term, OwnershipMode.REPLACES, segment.regionId(), null),
				new MethodId("ZERO_AREA_PRESSURE"), new Coordinate(), new Coordinate(), new Coordinate(segment.endXM(), 0, 0),
				segment.regionId(), new ArrayList<>(), 1, 0, null);
	}
}

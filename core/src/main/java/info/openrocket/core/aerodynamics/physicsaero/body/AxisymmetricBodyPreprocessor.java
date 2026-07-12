package info.openrocket.core.aerodynamics.physicsaero.body;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import info.openrocket.core.aerodynamics.physicsaero.config.NumericalTolerances;
import info.openrocket.core.aerodynamics.physicsaero.geometry.*;

public final class AxisymmetricBodyPreprocessor {
	public List<AxisymmetricBodySegment> preprocess(AeroGeometry geometry, double mach, NumericalTolerances tolerances) {
		List<AeroComponent> bodies = geometry.components().stream().filter(c -> c.axisymmetricProfile() != null)
				.sorted(Comparator.comparingDouble(AeroComponent::axialStartM)).toList();
		if (bodies.isEmpty()) throw new IllegalArgumentException("UNSUPPORTED_GEOMETRY:NO_AXISYMMETRIC_BODY");
		List<AxisymmetricBodySegment> result = new ArrayList<>(); double previousTangent = 0; AeroComponent previous = null;
		for (AeroComponent component : bodies) {
			List<GeometryStation> stations = component.axisymmetricProfile().stations();
			double localStartTangent = tangent(stations.get(0)), localEndTangent = tangent(stations.get(stations.size() - 1));
			BodySegmentType type = classify(component);
			if (previous != null && type != BodySegmentType.BOATTAIL && type != BodySegmentType.DISCRETE_COMPRESSION_CORNER)
				addCornerIfSignificant(result, previous, component, previousTangent, localStartTangent, mach, tolerances);
			// Conical transitions and boattails begin from the upstream axial direction at a true component junction.
			double startTangent = type == BodySegmentType.BOATTAIL || type == BodySegmentType.DISCRETE_COMPRESSION_CORNER
					? previousTangent : localStartTangent;
			result.add(new AxisymmetricBodySegment(component.id(), component.id() + ":surface", type,
					component.axialStartM(), component.axialEndM(), stations.get(0).radiusM(), stations.get(stations.size() - 1).radiusM(),
					startTangent, localEndTangent, stations, eligible(type)));
			previousTangent = localEndTangent; previous = component;
		}
		AeroComponent last = bodies.get(bodies.size() - 1);
		result.add(new AxisymmetricBodySegment(last.id(), last.id() + ":base", BodySegmentType.BASE,
				last.axialEndM(), last.axialEndM(), last.rootRadiusM(), last.rootRadiusM(), 0, 0, List.of(), List.of("OPENROCKET_SUPERSONIC_BASE_CP_V1")));
		return List.copyOf(result);
	}
	private static void addCornerIfSignificant(List<AxisymmetricBodySegment> result, AeroComponent upstream,
			AeroComponent downstream, double from, double to, double mach, NumericalTolerances tolerances) {
		double delta = to - from;
		if (Math.abs(delta) <= tolerances.cornerAngleThresholdRad()
				|| mach * mach * Math.abs(delta) <= tolerances.cornerPressureSignificance()) return;
		BodySegmentType type = delta > 0 ? BodySegmentType.DISCRETE_COMPRESSION_CORNER : BodySegmentType.DISCRETE_EXPANSION_CORNER;
		double x = downstream.axialStartM(), r = downstream.axisymmetricProfile().stations().get(0).radiusM();
		result.add(new AxisymmetricBodySegment(downstream.id(), upstream.id() + "->" + downstream.id(), type,
				x, x, r, r, from, to, List.of(), eligible(type)));
	}
	private static BodySegmentType classify(AeroComponent component) {
		String c = component.classification();
		if (c.equals("CYLINDER")) return BodySegmentType.CYLINDER;
		if (c.equals("BOATTAIL")) return BodySegmentType.BOATTAIL;
		if (c.equals("TRANSITION")) return BodySegmentType.DISCRETE_COMPRESSION_CORNER;
		if (c.equals("NOSE_CONICAL")) return BodySegmentType.TRUE_CONE;
		if (c.startsWith("NOSE_")) return BodySegmentType.SMOOTH_COMPRESSION;
		throw new IllegalArgumentException("UNSUPPORTED_AXISYMMETRIC_CLASSIFICATION:" + c);
	}
	private static double tangent(GeometryStation station) { return Math.atan(station.slope()); }
	private static List<String> eligible(BodySegmentType type) {
		return switch (type) {
			case TRUE_CONE -> List.of(TaylorMaccollSolutionId.VALUE, ModifiedNewtonianPressureId.VALUE);
			case SMOOTH_COMPRESSION -> List.of(TangentConePressureModel.METHOD_ID, ModifiedNewtonianPressureId.VALUE);
			case DISCRETE_COMPRESSION_CORNER -> List.of(DiscreteCornerShockModel.METHOD_ID, ModifiedNewtonianPressureId.VALUE);
			case SMOOTH_EXPANSION, DISCRETE_EXPANSION_CORNER, BOATTAIL -> List.of(ExpansionTurnModel.METHOD_ID);
			case CYLINDER -> List.of("ISENTROPIC_CYLINDER"); case BASE -> List.of("OPENROCKET_SUPERSONIC_BASE_CP_V1");
			case NOSE_STAGNATION_REGION -> List.of(ModifiedNewtonianPressureId.VALUE);
		};
	}
	private static final class TaylorMaccollSolutionId { static final String VALUE = "TAYLOR_MACCOLL_PERFECT_GAS_V1"; }
	private static final class ModifiedNewtonianPressureId { static final String VALUE = "MODIFIED_NEWTONIAN_FALLBACK_V1"; }
}

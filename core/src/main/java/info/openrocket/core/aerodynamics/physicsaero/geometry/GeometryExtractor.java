package info.openrocket.core.aerodynamics.physicsaero.geometry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import info.openrocket.core.rocketcomponent.*;
import info.openrocket.core.util.Coordinate;

/** Snapshots supported single-stage external geometry from OpenRocket's component tree. */
public final class GeometryExtractor {
	private static final int PROFILE_INTERVALS = 64;
	public AeroGeometry extract(Rocket rocket, double roughnessM, String wallModelId, String settingsFingerprint) {
		if (rocket.getStageList().size() != 1) throw new IllegalArgumentException("UNSUPPORTED_GEOMETRY:MULTI_STAGE");
		List<RocketComponent> supported = new ArrayList<>();
		for (RocketComponent c : rocket) {
			String classification = GeometryClassifier.classify(c);
			if (!classification.equals("UNSUPPORTED")) supported.add(c);
			else if (c instanceof ExternalComponent) throw new IllegalArgumentException("UNSUPPORTED_GEOMETRY:" + c.getClass().getSimpleName());
		}
		if (supported.isEmpty()) throw new IllegalArgumentException("UNSUPPORTED_GEOMETRY:NO_EXTERNAL_COMPONENTS");
		supported.sort(Comparator.comparingDouble(c -> c.getComponentLocations()[0].getX()));
		List<AeroComponent> components = new ArrayList<>();
		Map<String, Double> wetted = new LinkedHashMap<>();
		double maxRadius = 0, maxEnd = 0;
		for (int order = 0; order < supported.size(); order++) {
			RocketComponent source = supported.get(order);
			double start = source.getComponentLocations()[0].getX(), end = start + source.getLength();
			String id = source.getID().toString(), classification = GeometryClassifier.classify(source);
			AxisymmetricProfile profile = null; FinGeometry fin = null; ProtuberanceGeometry protuberance = null;
			double radius = 0, wet = 0, projected = 0, base = 0;
			Map<String, Double> local = new LinkedHashMap<>();
			if (source instanceof SymmetricComponent symmetric) {
				radius = Math.max(symmetric.getForeRadius(), symmetric.getAftRadius());
				wet = symmetric.getComponentWetArea(); projected = symmetric.getComponentPlanformArea();
				base = Math.PI * symmetric.getAftRadius() * symmetric.getAftRadius();
				profile = sample(symmetric, id, start, classification);
				local.put("foreRadiusM", symmetric.getForeRadius()); local.put("aftRadiusM", symmetric.getAftRadius());
			} else if (source instanceof FinSet fs) {
				radius = parentRadius(source); wet = 2 * fs.getPlanformArea() * fs.getFinCount();
				projected = fs.getPlanformArea() * fs.getFinCount();
				List<GeometryStation> outline = java.util.Arrays.stream(fs.getFinPoints())
						.map(point -> new GeometryStation(point.getX(), point.getY(), 0, 0)).toList();
				String section = switch (fs.getCrossSection()) {
					case AIRFOIL -> "SYMMETRIC_DIAMOND";
					case ROUNDED -> "ROUNDED_LEADING_EDGE";
					default -> "FLAT_PLATE";
				};
				fin = new FinGeometry(classification.substring(4), section, fs.getFinCount(), fs.getLength(), fs.getSpan(),
						fs.getPlanformArea(), fs.getCantAngle(), outline);
				local.put("thicknessM", fs.getThickness()); local.put("spanM", fs.getSpan()); local.put("baseRotationRad", fs.getBaseRotation());
			} else if (source instanceof LaunchLug lug) {
				radius = parentRadius(source); wet = 2 * Math.PI * lug.getOuterRadius() * lug.getLength();
				projected = 2 * lug.getOuterRadius() * lug.getLength();
				protuberance = new ProtuberanceGeometry("LAUNCH_LUG", lug.getLength(), projected, Math.PI * lug.getOuterRadius() * lug.getOuterRadius());
			} else if (source instanceof RailButton rb) {
				radius = parentRadius(source); double d = rb.getOuterDiameter();
				wet = Math.PI * d * rb.getTotalHeight(); projected = d * rb.getTotalHeight();
				protuberance = new ProtuberanceGeometry("RAIL_BUTTON", d, projected, Math.PI * d * d / 4);
				end = start + d;
			}
			String stageId = findStage(source);
			AeroComponent component = new AeroComponent(id, path(source), source.getClass().getSimpleName(), classification,
					stageId, order, new Coordinate(start, 0, 0), start, end, radius, wet, projected, base, roughnessM,
					wallModelId, local, List.of(), profile, fin, protuberance);
			components.add(component); wetted.put(id, wet); maxRadius = Math.max(maxRadius, radius); maxEnd = Math.max(maxEnd, end);
		}
		// Only the last axisymmetric aft face is exposed vehicle base area.
		double exposedBase = components.stream().filter(c -> c.axisymmetricProfile() != null)
				.max(Comparator.comparingDouble(AeroComponent::axialEndM)).map(AeroComponent::baseAreaM2).orElse(0.0);
		ReferenceGeometry references = new ReferenceGeometry(Math.PI * maxRadius * maxRadius, exposedBase, maxEnd,
				2 * maxRadius, wetted, new Coordinate(), 2 * maxRadius);
		AeroGeometry geometry = new AeroGeometry(components, references, "");
		GeometryValidator.validate(geometry);
		return geometry.withHash(GeometryHasher.hash(geometry, settingsFingerprint));
	}

	private static AxisymmetricProfile sample(SymmetricComponent component, String id, double start, String classification) {
		List<GeometryStation> stations = new ArrayList<>(); double length = component.getLength();
		for (int i = 0; i <= PROFILE_INTERVALS; i++) {
			double x = length * i / PROFILE_INTERVALS, h = Math.max(length * 1e-5, 1e-9);
			double left = Math.max(0, x - h), right = Math.min(length, x + h);
			double r = component.getRadius(x), rl = component.getRadius(left), rr = component.getRadius(right);
			double slope = (rr - rl) / (right - left);
			double second = (i == 0 || i == PROFILE_INTERVALS) ? 0 : (rr - 2 * r + rl) / (h * h);
			stations.add(new GeometryStation(start + x, r, slope, second));
		}
		List<GeometryEvent> events = new ArrayList<>();
		events.add(new GeometryEvent(start, component instanceof NoseCone ? GeometryEvent.Type.NOSE_TIP
				: GeometryEvent.Type.TRANSITION_BOUNDARY, id));
		events.add(new GeometryEvent(start + length, classification.equals("BOATTAIL") ? GeometryEvent.Type.BASE
				: GeometryEvent.Type.CONE_CYLINDER_JUNCTION, id));
		return new AxisymmetricProfile(stations, events, "NONE_DISCRETE_CORNERS_PRESERVED", 0);
	}
	private static double parentRadius(RocketComponent component) {
		return component.getParent() instanceof SymmetricComponent s ? s.getRadius(Math.max(0, Math.min(s.getLength(), component.getAxialOffset()))) : 0;
	}
	private static String findStage(RocketComponent c) {
		for (RocketComponent p = c; p != null; p = p.getParent()) if (p instanceof AxialStage) return p.getID().toString();
		return "UNKNOWN_STAGE";
	}
	private static String path(RocketComponent c) {
		List<String> names = new ArrayList<>();
		for (RocketComponent p = c; p != null; p = p.getParent()) names.add(0, p.getName());
		return String.join("/", names);
	}
}

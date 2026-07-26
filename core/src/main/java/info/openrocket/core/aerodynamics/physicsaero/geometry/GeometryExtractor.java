package info.openrocket.core.aerodynamics.physicsaero.geometry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import info.openrocket.core.rocketcomponent.*;
import info.openrocket.core.util.Coordinate;

/** Snapshots supported external geometry from a rocket or active staged flight configuration. */
public final class GeometryExtractor {
	private static final int PROFILE_INTERVALS = 64;
	public AeroGeometry extract(Rocket rocket, double roughnessM, String wallModelId, String settingsFingerprint) {
		return extract(rocket, null, roughnessM, wallModelId, settingsFingerprint, false);
	}
	public AeroGeometry extract(Rocket rocket, double roughnessM, String wallModelId,
			String settingsFingerprint, boolean forceTurbulentBoundaryLayer) {
		return extract(rocket, null, roughnessM, wallModelId, settingsFingerprint,
				forceTurbulentBoundaryLayer);
	}
	public AeroGeometry extractWithComponentRoughness(Rocket rocket,
			String wallModelId, String settingsFingerprint) {
		return extractWithComponentRoughness(rocket, wallModelId, settingsFingerprint, false);
	}
	public AeroGeometry extractWithComponentRoughness(Rocket rocket,
			String wallModelId, String settingsFingerprint, boolean forceTurbulentBoundaryLayer) {
		return extract(rocket, null, null, wallModelId, settingsFingerprint,
				forceTurbulentBoundaryLayer);
	}
	public AeroGeometry extract(FlightConfiguration configuration, double roughnessM,
			String wallModelId, String settingsFingerprint) {
		configuration.update();
		return extract(configuration.getRocket(), configuration.getActiveInstances().keySet(),
				roughnessM, wallModelId, settingsFingerprint, false);
	}
	public AeroGeometry extract(FlightConfiguration configuration, double roughnessM,
			String wallModelId, String settingsFingerprint, boolean forceTurbulentBoundaryLayer) {
		configuration.update();
		return extract(configuration.getRocket(), configuration.getActiveInstances().keySet(),
				roughnessM, wallModelId, settingsFingerprint, forceTurbulentBoundaryLayer);
	}
	public AeroGeometry extractWithComponentRoughness(FlightConfiguration configuration,
			String wallModelId, String settingsFingerprint) {
		configuration.update();
		return extract(configuration.getRocket(), configuration.getActiveInstances().keySet(),
				null, wallModelId, settingsFingerprint, false);
	}
	public AeroGeometry extractWithComponentRoughness(FlightConfiguration configuration,
			String wallModelId, String settingsFingerprint, boolean forceTurbulentBoundaryLayer) {
		configuration.update();
		return extract(configuration.getRocket(), configuration.getActiveInstances().keySet(),
				null, wallModelId, settingsFingerprint, forceTurbulentBoundaryLayer);
	}
	private AeroGeometry extract(Rocket rocket, Set<RocketComponent> active, Double roughnessOverrideM,
			String wallModelId, String settingsFingerprint, boolean forceTurbulentBoundaryLayer) {
		List<RocketComponent> supported = new ArrayList<>();
		for (RocketComponent c : rocket) {
			if (active != null && !active.contains(c)) continue;
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
			String id = String.format(java.util.Locale.ROOT, "aero-component-%04d", order);
			String classification = GeometryClassifier.classify(source);
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
				List<GeometryStation> outline = new ArrayList<>();
				var finPoints = fs.getFinPoints();
				for (int index = 0; index < finPoints.length; index++) {
					var point = finPoints[index];
					try {
						outline.add(new GeometryStation(point.getX(), point.getY(), 0, 0));
					} catch (IllegalArgumentException exception) {
						throw new IllegalArgumentException("INVALID_FIN_OUTLINE:" + path(source)
								+ ":point=" + index + ":x=" + point.getX() + ":y=" + point.getY(), exception);
					}
				}
				String section = switch (fs.getCrossSection()) {
					case AIRFOIL -> "SYMMETRIC_DIAMOND";
					case ROUNDED -> "ROUNDED_LEADING_EDGE";
					default -> "FLAT_PLATE";
				};
				fin = new FinGeometry(classification.substring(4), section, fs.getFinCount(), fs.getLength(), fs.getSpan(),
						fs.getPlanformArea(), fs.getCantAngle(), outline);
				local.put("thicknessM", fs.getThickness()); local.put("spanM", fs.getSpan()); local.put("baseRotationRad", fs.getBaseRotation());
				if ("Hexagonal".equalsIgnoreCase(fs.getDetailedAirfoilSection())) {
					if (Double.isFinite(fs.getLeadingEdgeAirfoilLength())) {
						local.put("sectionLeadingRampLengthM",
								fs.getLeadingEdgeAirfoilLength());
					}
					if (Double.isFinite(fs.getTrailingEdgeAirfoilLength())) {
						local.put("sectionTrailingRampLengthM",
								fs.getTrailingEdgeAirfoilLength());
					}
				}
				if (Double.isFinite(fs.getLeadingEdgeRadius())) {
					local.put("leadingEdgeRadiusM", fs.getLeadingEdgeRadius());
				}
			} else if (source instanceof LaunchLug lug) {
				int count = lug.getInstanceCount();
				radius = parentRadius(source); wet = count * 2 * Math.PI * lug.getOuterRadius() * lug.getLength();
				projected = count * 2 * lug.getOuterRadius() * lug.getLength();
				double innerArea = Math.PI * lug.getInnerRadius() * lug.getInnerRadius();
				double annularArea = Math.PI * (lug.getOuterRadius() * lug.getOuterRadius()
						- lug.getInnerRadius() * lug.getInnerRadius());
				protuberance = new ProtuberanceGeometry("LAUNCH_LUG", lug.getInstanceCount(), start,
						lug.getLength(), 2 * lug.getOuterRadius(), projected,
						count * annularArea);
				local.put("innerAreaM2", innerArea);
				local.put("innerDiameterM", 2 * lug.getInnerRadius());
			} else if (source instanceof RailButton rb) {
				int count = rb.getInstanceCount();
				radius = parentRadius(source); double d = rb.getOuterDiameter();
				double solidProjectedAreaEach = d * rb.getTotalHeight()
						- (d - rb.getInnerDiameter()) * rb.getInnerHeight();
				wet = count * Math.PI * d * rb.getTotalHeight();
				projected = count * solidProjectedAreaEach;
				String protuberanceType = "Launch Shoe".equals(rb.getName())
						? "LAUNCH_SHOE" : "RAIL_BUTTON";
				protuberance = new ProtuberanceGeometry(protuberanceType, rb.getInstanceCount(), start,
						d, rb.getTotalHeight(), projected, count * Math.PI * d * d / 4);
				end = start + d;
			}
			if (forceTurbulentBoundaryLayer
					&& (profile != null || fin != null)) {
				local.put("forceFullyTurbulent", 1.0);
			}
			String stageId = findStage(rocket, source);
			double componentRoughnessM = roughnessOverrideM != null
					? roughnessOverrideM
					: source instanceof ExternalComponent external
							? external.getFinish().getRoughnessSize()
							: 0;
			AeroComponent component = new AeroComponent(id, path(source), source.getClass().getSimpleName(), classification,
					stageId, order, new Coordinate(start, 0, 0), start, end, radius, wet, projected, base,
					componentRoughnessM,
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
			/*
			 * A first-order one-sided derivative leaves a false nonzero tangent
			 * at analytic component boundaries (notably the aft end of a
			 * tangent ogive).  That residual angle is then convected down a
			 * cylinder and contaminates a later shoulder shock.  Use the
			 * second-order boundary stencil; the central interior stencil is
			 * already second order.
			 */
			double slope;
			if (i == 0) {
				double r2 = component.getRadius(Math.min(length, 2 * h));
				slope = (-3 * r + 4 * rr - r2) / (2 * h);
			} else if (i == PROFILE_INTERVALS) {
				double r2 = component.getRadius(Math.max(0, length - 2 * h));
				slope = (3 * r - 4 * rl + r2) / (2 * h);
			} else {
				slope = (rr - rl) / (right - left);
			}
			double second = (i == 0 || i == PROFILE_INTERVALS) ? 0 : (rr - 2 * r + rl) / (h * h);
			try {
				stations.add(new GeometryStation(start + x, r, slope, second));
			} catch (IllegalArgumentException exception) {
				throw new IllegalArgumentException("INVALID_AXISYMMETRIC_PROFILE:" + path(component)
						+ ":station=" + i + ":x=" + (start + x) + ":radius=" + r
						+ ":slope=" + slope + ":secondDerivative=" + second, exception);
			}
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
	private static String findStage(Rocket rocket, RocketComponent c) {
		AxialStage owningStage = null;
		for (RocketComponent p = c; p != null; p = p.getParent()) {
			if (p instanceof AxialStage stage) {
				owningStage = stage;
				break;
			}
		}
		if (owningStage == null) return "UNKNOWN_STAGE";
		for (int index = 0; index < rocket.getStageCount(); index++) {
			if (rocket.getStage(index) == owningStage) return "stage-" + index;
		}
		return "UNKNOWN_STAGE";
	}
	private static String path(RocketComponent c) {
		List<String> names = new ArrayList<>();
		for (RocketComponent p = c; p != null; p = p.getParent()) names.add(0, p.getName());
		return String.join("/", names);
	}
}

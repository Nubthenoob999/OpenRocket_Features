package info.openrocket.core.aerodynamics.rom.geometry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import info.openrocket.core.rocketcomponent.FinSet;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.rocketcomponent.SymmetricComponent;
import info.openrocket.core.rocketcomponent.TrapezoidFinSet;
import info.openrocket.core.util.CoordinateIF;

public class GeometryFeatureExtractor {
	private static final int BODY_SAMPLES_PER_COMPONENT = 17;

	public GeometryFeatures extract(FlightConfiguration configuration) {
		Set<RocketComponent> activeComponents = new LinkedHashSet<>(configuration.getActiveInstances().keySet());
		List<RocketComponent> sortedComponents = new ArrayList<>(activeComponents);
		sortedComponents.sort(Comparator
				.comparingDouble(GeometryFeatureExtractor::componentX)
				.thenComparing(RocketComponent::getName, String.CASE_INSENSITIVE_ORDER));

		List<AxisymmetricGeometry> bodies = new ArrayList<>();
		List<FinGeometry> fins = new ArrayList<>();
		List<SurfacePatch> patches = new ArrayList<>();
		List<Station> stations = new ArrayList<>();

		double maxRadius = 0.0;
		double baseRadius = 0.0;
		int shoulderCount = 0;
		int boattailCount = 0;

		Double previousAftRadius = null;
		double previousBodyEnd = Double.NaN;
		String previousBodyName = null;

		MessageDigest digest = newDigest();

		for (RocketComponent component : sortedComponents) {
			if (!component.isAerodynamic()) {
				continue;
			}

			if (component instanceof SymmetricComponent symmetric) {
				double xStart = componentX(component);
				double xEnd = xStart + component.getLength();
				double[] xSamples = new double[BODY_SAMPLES_PER_COMPONENT];
				double[] radiusSamples = new double[BODY_SAMPLES_PER_COMPONENT];
				for (int i = 0; i < BODY_SAMPLES_PER_COMPONENT; i++) {
					double fraction = (double) i / (double) (BODY_SAMPLES_PER_COMPONENT - 1);
					double localX = fraction * component.getLength();
					double xAbs = xStart + localX;
					double radius = Math.max(0.0, symmetric.getRadius(localX));
					xSamples[i] = xAbs;
					radiusSamples[i] = radius;
					stations.add(new Station(xAbs, radius));
					maxRadius = Math.max(maxRadius, radius);
				}

				double foreRadius = Math.max(0.0, symmetric.getForeRadius());
				double aftRadius = Math.max(0.0, symmetric.getAftRadius());
				if (previousAftRadius != null && Math.abs(xStart - previousBodyEnd) < 1e-4
						&& Math.abs(foreRadius - previousAftRadius) > 1e-4) {
					shoulderCount++;
				}
				if (aftRadius + 1e-6 < foreRadius) {
					boattailCount++;
				}
				if (xEnd >= configuration.getLengthAerodynamic() - 1e-4) {
					baseRadius = Math.max(baseRadius, aftRadius);
				}
				bodies.add(new AxisymmetricGeometry(component.getName(), component.getClass().getSimpleName(),
						xStart, xEnd, foreRadius, aftRadius, xSamples, radiusSamples));
				patches.add(new SurfacePatch("body:" + component.getID(), component.getName(),
						SurfacePatch.PatchType.BODY, xStart, xEnd, 0.5 * (xStart + xEnd),
						Math.max(1e-6, 2.0 * Math.PI * Math.max(foreRadius, aftRadius) * Math.max(1e-6, xEnd - xStart)),
						Math.atan2(aftRadius - foreRadius, Math.max(1e-6, xEnd - xStart))));
				digest.update(bodySignature(component, xStart, xEnd, foreRadius, aftRadius).getBytes(StandardCharsets.UTF_8));
				previousAftRadius = aftRadius;
				previousBodyEnd = xEnd;
				previousBodyName = component.getName();
				continue;
			}

			if (component instanceof FinSet finSet) {
				double xStart = componentX(component);
				double rootChord = finSet.getLength();
				double span = finSet.getSpan();
				double sweep = estimateSweep(finSet);
				double tipChord = estimateTipChord(finSet);
				double planformArea = finSet.getPlanformArea();
				double bodyRadiusAtRoot = finSet.getBodyRadius();
				double centroidX = xStart + 0.5 * rootChord;
				FinGeometry fin = new FinGeometry(component.getName(), xStart, rootChord, tipChord, sweep, span,
						finSet.getThickness(), finSet.getCantAngle(), planformArea, finSet.getFinCount(),
						bodyRadiusAtRoot, centroidX);
				fins.add(fin);
				patches.add(new SurfacePatch("fin:" + component.getID(), component.getName(),
						SurfacePatch.PatchType.FIN, xStart, xStart + rootChord, centroidX,
						Math.max(1e-6, planformArea * Math.max(1, finSet.getFinCount())), 0.0));
				digest.update(finSignature(component, fin).getBytes(StandardCharsets.UTF_8));
			}
		}

		if (stations.isEmpty()) {
			stations.add(new Station(0.0, 0.0));
			stations.add(new Station(configuration.getLengthAerodynamic(), 0.0));
		}

		stations.sort(Comparator.comparingDouble(Station::x));
		List<Station> merged = mergeStations(stations);

		double[] xStations = new double[merged.size()];
		double[] radiusStations = new double[merged.size()];
		double[] areaStations = new double[merged.size()];
		for (int i = 0; i < merged.size(); i++) {
			Station station = merged.get(i);
			xStations[i] = station.x();
			radiusStations[i] = station.radius();
			areaStations[i] = Math.PI * station.radius() * station.radius();
		}
		smoothInPlace(radiusStations);
		for (int i = 0; i < radiusStations.length; i++) {
			areaStations[i] = Math.PI * radiusStations[i] * radiusStations[i];
		}

		double[] slope = derivative(xStations, areaStations);
		double[] curvature = derivative(xStations, slope);
		int slopeChangeCount = countSlopeChanges(slope);
		double bodyLength = configuration.getLengthAerodynamic();
		double referenceLength = configuration.getReferenceLength();
		double referenceArea = configuration.getReferenceArea();
		double baseArea = Math.PI * baseRadius * baseRadius;

		for (int i = 1; i < xStations.length; i++) {
			double dx = Math.max(1e-6, xStations[i] - xStations[i - 1]);
			double meanRadius = 0.5 * (radiusStations[i] + radiusStations[i - 1]);
			patches.add(new SurfacePatch("body-segment:" + i, previousBodyName != null ? previousBodyName : "body",
					SurfacePatch.PatchType.BODY, xStations[i - 1], xStations[i], 0.5 * (xStations[i - 1] + xStations[i]),
					Math.max(1e-6, 2.0 * Math.PI * meanRadius * dx), Math.atan2(radiusStations[i] - radiusStations[i - 1], dx)));
		}

		return new GeometryFeatures(toHex(digest.digest()), bodyLength, referenceLength, referenceArea,
				maxRadius, 2.0 * maxRadius, baseArea, shoulderCount, boattailCount, slopeChangeCount,
				xStations, radiusStations, areaStations, slope, curvature, bodies, fins, patches);
	}

	private static MessageDigest newDigest() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("Missing SHA-256", e);
		}
	}

	private static double componentX(RocketComponent component) {
		CoordinateIF[] locations = component.getComponentLocations();
		if (locations == null || locations.length == 0) {
			return 0.0;
		}
		return locations[0].getX();
	}

	private static String bodySignature(RocketComponent component, double xStart, double xEnd,
			double foreRadius, double aftRadius) {
		return String.format(Locale.ROOT, "body:%s:%s:%.6f:%.6f:%.6f:%.6f",
				component.getName(), component.getClass().getSimpleName(), xStart, xEnd, foreRadius, aftRadius);
	}

	private static String finSignature(RocketComponent component, FinGeometry fin) {
		return String.format(Locale.ROOT, "fin:%s:%s:%.6f:%.6f:%.6f:%.6f:%.6f:%d",
				component.getName(), component.getClass().getSimpleName(), fin.getXStart(),
				fin.getRootChord(), fin.getTipChord(), fin.getSweep(), fin.getSpan(), fin.getFinCount());
	}

	private static double estimateSweep(FinSet finSet) {
		if (finSet instanceof TrapezoidFinSet trapezoidFinSet) {
			return trapezoidFinSet.getSweep();
		}
		CoordinateIF[] points = finSet.getFinPoints();
		if (points.length < 2) {
			return 0.0;
		}
		return points[1].getX() - points[0].getX();
	}

	private static double estimateTipChord(FinSet finSet) {
		if (finSet instanceof TrapezoidFinSet trapezoidFinSet) {
			return trapezoidFinSet.getTipChord();
		}
		CoordinateIF[] points = finSet.getFinPoints();
		if (points.length < 3) {
			return finSet.getLength();
		}
		double maxX = Double.NEGATIVE_INFINITY;
		double minX = Double.POSITIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;
		for (CoordinateIF point : points) {
			if (point.getY() >= maxY - 1e-6) {
				maxY = Math.max(maxY, point.getY());
				maxX = Math.max(maxX, point.getX());
				minX = Math.min(minX, point.getX());
			}
		}
		if (!Double.isFinite(maxX) || !Double.isFinite(minX)) {
			return finSet.getLength();
		}
		return Math.max(0.0, maxX - minX);
	}

	private static List<Station> mergeStations(List<Station> stations) {
		List<Station> merged = new ArrayList<>();
		for (Station station : stations) {
			if (merged.isEmpty()) {
				merged.add(station);
				continue;
			}
			Station previous = merged.get(merged.size() - 1);
			if (Math.abs(station.x() - previous.x()) < 1e-6) {
				merged.set(merged.size() - 1, new Station(previous.x(), Math.max(previous.radius(), station.radius())));
			} else {
				merged.add(station);
			}
		}
		return merged;
	}

	private static void smoothInPlace(double[] values) {
		if (values.length < 5) {
			return;
		}
		double[] source = values.clone();
		for (int i = 2; i < values.length - 2; i++) {
			values[i] = (-3.0 * source[i - 2] + 12.0 * source[i - 1] + 17.0 * source[i]
					+ 12.0 * source[i + 1] - 3.0 * source[i + 2]) / 35.0;
		}
	}

	private static double[] derivative(double[] xAxis, double[] yAxis) {
		double[] derivative = new double[yAxis.length];
		if (yAxis.length == 1) {
			return derivative;
		}
		for (int i = 0; i < yAxis.length; i++) {
			if (i == 0) {
				derivative[i] = (yAxis[i + 1] - yAxis[i]) / Math.max(1e-6, xAxis[i + 1] - xAxis[i]);
			} else if (i == yAxis.length - 1) {
				derivative[i] = (yAxis[i] - yAxis[i - 1]) / Math.max(1e-6, xAxis[i] - xAxis[i - 1]);
			} else {
				derivative[i] = (yAxis[i + 1] - yAxis[i - 1]) / Math.max(1e-6, xAxis[i + 1] - xAxis[i - 1]);
			}
		}
		return derivative;
	}

	private static int countSlopeChanges(double[] slope) {
		int count = 0;
		int previousSign = 0;
		for (double value : slope) {
			int sign = Math.abs(value) < 1e-8 ? 0 : (value > 0.0 ? 1 : -1);
			if (sign != 0 && previousSign != 0 && sign != previousSign) {
				count++;
			}
			if (sign != 0) {
				previousSign = sign;
			}
		}
		return count;
	}

	private static String toHex(byte[] digest) {
		StringBuilder builder = new StringBuilder(digest.length * 2);
		for (byte value : digest) {
			builder.append(String.format(Locale.ROOT, "%02x", value));
		}
		return builder.toString();
	}

	private record Station(double x, double radius) {
	}
}

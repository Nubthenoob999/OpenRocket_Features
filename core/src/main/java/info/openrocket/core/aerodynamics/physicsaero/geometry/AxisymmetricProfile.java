package info.openrocket.core.aerodynamics.physicsaero.geometry;

import java.util.List;

public record AxisymmetricProfile(List<GeometryStation> stations, List<GeometryEvent> events,
		String smoothingAlgorithm, double smoothingToleranceM) {
	public AxisymmetricProfile {
		stations = List.copyOf(stations); events = List.copyOf(events);
		if (stations.size() < 2) throw new IllegalArgumentException("profile requires at least two stations");
		for (int i = 1; i < stations.size(); i++) if (stations.get(i).xM() <= stations.get(i - 1).xM())
			throw new IllegalArgumentException("profile stations must be increasing");
	}
	public double wettedAreaM2() {
		double area = 0;
		for (int i = 1; i < stations.size(); i++) {
			GeometryStation a = stations.get(i - 1), b = stations.get(i);
			double dx = b.xM() - a.xM();
			double integrandA = 2 * Math.PI * a.radiusM() * Math.sqrt(1 + a.slope() * a.slope());
			double integrandB = 2 * Math.PI * b.radiusM() * Math.sqrt(1 + b.slope() * b.slope());
			area += 0.5 * dx * (integrandA + integrandB);
		}
		return area;
	}
}

package info.openrocket.core.aerodynamics.physicsaero.geometry;

import java.util.List;

public record FinGeometry(String planform, String section, int count, double rootChordM, double spanM,
		double planformAreaM2, double cantRad, List<GeometryStation> outline) {
	public FinGeometry { outline = List.copyOf(outline); if (count <= 0 || rootChordM <= 0 || spanM <= 0 || planformAreaM2 <= 0) throw new IllegalArgumentException("invalid fin geometry"); }
}

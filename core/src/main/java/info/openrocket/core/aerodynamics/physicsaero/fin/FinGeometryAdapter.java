package info.openrocket.core.aerodynamics.physicsaero.fin;

import java.util.ArrayList;
import java.util.List;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroComponent;
import info.openrocket.core.aerodynamics.physicsaero.geometry.FinGeometry;
import info.openrocket.core.util.Coordinate;

/** Expands an OpenRocket fin set snapshot into individually oriented physical fins. */
public final class FinGeometryAdapter {
	public List<PhysicalFin> expand(AeroComponent component) {
		return expand(component, FinRole.FIN);
	}
	public List<PhysicalFin> expand(AeroComponent component, FinRole role) {
		FinGeometry geometry = component.finGeometry();
		if (geometry == null) return List.of();
		double base = component.localReferences().getOrDefault("baseRotationRad", 0.0);
		double thickness = component.localReferences().getOrDefault("thicknessM", 0.0);
		List<PhysicalFin> fins = new ArrayList<>();
		for (int index = 0; index < geometry.count(); index++) {
			double azimuth = base + index * 2 * Math.PI / geometry.count();
			Coordinate radial = new Coordinate(0, Math.cos(azimuth), Math.sin(azimuth));
			Coordinate tangent = new Coordinate(0, -Math.sin(azimuth), Math.cos(azimuth));
			Coordinate chord = new Coordinate(Math.cos(geometry.cantRad()),
					-tangent.y * Math.sin(geometry.cantRad()), -tangent.z * Math.sin(geometry.cantRad()));
			FinLocalFrame frame = new FinLocalFrame(chord, radial);
			fins.add(new PhysicalFin(component.id() + ":fin:" + index, component, geometry, index,
					azimuth, thickness, frame, role));
		}
		return List.copyOf(fins);
	}
	public record PhysicalFin(String id, AeroComponent component, FinGeometry geometry, int index,
			double azimuthRad, double thicknessM, FinLocalFrame frame, FinRole role) {}
}

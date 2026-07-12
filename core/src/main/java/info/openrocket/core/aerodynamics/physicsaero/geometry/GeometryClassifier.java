package info.openrocket.core.aerodynamics.physicsaero.geometry;

import info.openrocket.core.rocketcomponent.*;

public final class GeometryClassifier {
	private GeometryClassifier() {}
	public static String classify(RocketComponent component) {
		if (component instanceof NoseCone nose) return "NOSE_" + nose.getShapeType().name();
		if (component instanceof BodyTube) return "CYLINDER";
		if (component instanceof Transition t) return t.getForeRadius() > t.getAftRadius() ? "BOATTAIL" : "TRANSITION";
		if (component instanceof TrapezoidFinSet) return "FIN_TRAPEZOIDAL";
		if (component instanceof EllipticalFinSet) return "FIN_ELLIPTICAL";
		if (component instanceof FreeformFinSet) return "FIN_FREEFORM";
		if (component instanceof LaunchLug) return "LAUNCH_LUG";
		if (component instanceof RailButton) return "RAIL_BUTTON";
		return "UNSUPPORTED";
	}
}

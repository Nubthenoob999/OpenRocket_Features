package info.openrocket.core.aerodynamics.physicsaero.geometry;

public record GeometryEvent(double xM, Type type, String componentId) {
	public enum Type { NOSE_TIP, SHOULDER, DIAMETER_STEP, CONE_CYLINDER_JUNCTION, TRANSITION_BOUNDARY, BOATTAIL_START, BASE }
}

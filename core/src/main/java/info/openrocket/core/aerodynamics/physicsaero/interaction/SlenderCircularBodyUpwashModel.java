package info.openrocket.core.aerodynamics.physicsaero.interaction;

import info.openrocket.core.aerodynamics.physicsaero.body.AxisymmetricEdgeStateHistory;
import info.openrocket.core.aerodynamics.physicsaero.fin.FinStrip;
import info.openrocket.core.aerodynamics.physicsaero.flow.FlowCondition;
import info.openrocket.core.aerodynamics.physicsaero.geometry.AeroGeometry;
import info.openrocket.core.util.Coordinate;

/** Circular-cylinder crossflow potential relation used as a named slender-body upwash closure. */
public final class SlenderCircularBodyUpwashModel implements BodyUpwashModel {
	public static final String METHOD_ID = "SLENDER_CIRCULAR_BODY_POTENTIAL_UPWASH_V1";
	@Override public UpwashResult evaluate(AeroGeometry body, FinStrip strip, FlowCondition flow, AxisymmetricEdgeStateHistory bodyState) {
		double y = strip.centroidBodyM().y, z = strip.centroidBodyM().z, radius = Math.hypot(y, z), root = strip.bodyRootRadiusM();
		if (root <= 0 || radius < root || flow.mach() < 1.2 || flow.mach() > 5)
			return new UpwashResult(new Coordinate(), false, 0.2, "OUTSIDE_SLENDER_CIRCULAR_BODY_UPWASH_DOMAIN");
		double ry = y / radius, rz = z / radius, uy = flow.velocityBody().getY(), uz = flow.velocityBody().getZ();
		double projection = uy * ry + uz * rz, factor = root * root / (radius * radius);
		return new UpwashResult(new Coordinate(0, factor * (uy - 2 * projection * ry), factor * (uz - 2 * projection * rz)),
				true, 0.6, METHOD_ID + ":CIRCULAR_CYLINDER_CROSSFLOW_POTENTIAL");
	}
}

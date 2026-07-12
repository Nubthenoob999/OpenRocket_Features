package info.openrocket.core.aerodynamics.physicsaero.interaction;

import info.openrocket.core.aerodynamics.physicsaero.fin.FinStrip;
import info.openrocket.core.aerodynamics.physicsaero.flow.ShockEvent;
import info.openrocket.core.aerodynamics.physicsaero.flow.SurfaceState;
import info.openrocket.core.util.Coordinate;

/** Geometric body-shock/strip test. A hit carries the complete post-shock state. */
public final class ShockToFinIntersectionResolver {
	public Intersection resolve(ShockEvent shock, FinStrip strip, double radialToleranceM) {
		if (radialToleranceM < 0) throw new IllegalArgumentException("invalid shock tolerance");
		double dx = strip.centroidBodyM().x - shock.xM();
		if (dx < 0) return new Intersection(false, false, null, null, "UPSTREAM_OF_SHOCK");
		double predictedRadius = shock.upstream().radiusM() + dx * Math.tan(shock.shockAngleRad());
		double stripRadius = Math.hypot(strip.centroidBodyM().y, strip.centroidBodyM().z);
		double miss = Math.abs(stripRadius - predictedRadius);
		boolean hit = miss <= radialToleranceM; boolean uncertain = !hit && miss <= 2 * radialToleranceM;
		Coordinate point = hit || uncertain ? new Coordinate(strip.centroidBodyM().x,
				strip.centroidBodyM().y, strip.centroidBodyM().z) : null;
		return new Intersection(hit, uncertain, hit ? shock.downstream() : null, point,
				hit ? "COMPLETE_POST_SHOCK_STATE_REPLACEMENT" : uncertain ? "SHOCK_INTERSECTION_UNCERTAIN" : "NO_INTERSECTION");
	}
	public record Intersection(boolean intersects, boolean uncertain, SurfaceState replacementState,
			Coordinate intersectionPointM, String diagnostic) {}
}

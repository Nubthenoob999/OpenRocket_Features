package info.openrocket.core.aerodynamics.physicsaero.force;

import java.util.List;
import info.openrocket.core.aerodynamics.physicsaero.api.MethodId;
import info.openrocket.core.aerodynamics.physicsaero.api.OwnershipMode;
import info.openrocket.core.aerodynamics.physicsaero.api.PhysicalOwner;
import info.openrocket.core.aerodynamics.physicsaero.api.PhysicalTerm;
import info.openrocket.core.aerodynamics.physicsaero.boundarylayer.BoundaryLayerHistory;
import info.openrocket.core.aerodynamics.physicsaero.boundarylayer.BoundaryLayerState;
import info.openrocket.core.aerodynamics.physicsaero.boundarylayer.BoundaryLayerStation;
import info.openrocket.core.util.Coordinate;

/** Vector wall-shear integration; this is the sole authoritative skin-friction owner. */
public final class SkinFrictionForceIntegrator {
	public static final MethodId METHOD_ID = new MethodId("boundary-layer-wall-shear-integration-v1");
	public ForceContribution integrate(BoundaryLayerHistory history, Coordinate referencePointM) {
		double fx = 0, fy = 0, fz = 0, mx = 0, my = 0, mz = 0, weightedX = 0, weightedY = 0, weightedZ = 0, weight = 0;
		for (int i = 1; i < history.states().size(); i++) {
			BoundaryLayerStation a = history.track().stations().get(i - 1), b = history.track().stations().get(i);
			BoundaryLayerState sa = history.states().get(i - 1), sb = history.states().get(i);
			double area = 0.5 * (a.areaWidthM() + b.areaWidthM()) * (b.sM() - a.sM());
			double tau = 0.5 * (sa.wallShearPa() + sb.wallShearPa());
			double tx = 0.5 * (a.streamwiseTangent().x + b.streamwiseTangent().x);
			double ty = 0.5 * (a.streamwiseTangent().y + b.streamwiseTangent().y);
			double tz = 0.5 * (a.streamwiseTangent().z + b.streamwiseTangent().z); double tn = Math.sqrt(tx*tx + ty*ty + tz*tz);
			double dfx = -tau * area * tx / tn, dfy = -tau * area * ty / tn, dfz = -tau * area * tz / tn;
			double x = 0.5 * (a.positionM().x + b.positionM().x), y = 0.5 * (a.positionM().y + b.positionM().y), z = 0.5 * (a.positionM().z + b.positionM().z);
			double rx = x - referencePointM.x, ry = y - referencePointM.y, rz = z - referencePointM.z;
			fx += dfx; fy += dfy; fz += dfz; mx += ry*dfz-rz*dfy; my += rz*dfx-rx*dfz; mz += rx*dfy-ry*dfx;
			double w = Math.sqrt(dfx*dfx + dfy*dfy + dfz*dfz); weightedX += x*w; weightedY += y*w; weightedZ += z*w; weight += w;
		}
		Coordinate point = weight > 0 ? new Coordinate(weightedX/weight, weightedY/weight, weightedZ/weight) : referencePointM;
		PhysicalOwner owner = new PhysicalOwner(PhysicalTerm.SKIN_FRICTION, OwnershipMode.REPLACES, history.track().regionId(), null);
		return new ForceContribution(history.track().componentId(), owner, METHOD_ID, new Coordinate(fx,fy,fz), new Coordinate(mx,my,mz), point,
				history.track().regionId(), history.track().reducedConfidence() ? List.of("REDUCED_CONFIDENCE_3D_FLOW") : List.of(), history.track().reducedConfidence()?0.6:0.9, 0, null);
	}
}

package info.openrocket.core.aerodynamics.physicsaero.force;

import java.util.List;
import info.openrocket.core.aerodynamics.physicsaero.api.MethodId;
import info.openrocket.core.aerodynamics.physicsaero.api.OwnershipMode;
import info.openrocket.core.aerodynamics.physicsaero.api.PhysicalOwner;
import info.openrocket.core.aerodynamics.physicsaero.api.PhysicalTerm;
import info.openrocket.core.aerodynamics.physicsaero.boundarylayer.BoundaryLayerHistory;
import info.openrocket.core.aerodynamics.physicsaero.boundarylayer.BoundaryLayerMode;
import info.openrocket.core.aerodynamics.physicsaero.boundarylayer.BoundaryLayerState;
import info.openrocket.core.aerodynamics.physicsaero.boundarylayer.BoundaryLayerStation;
import info.openrocket.core.util.Coordinate;

/** Vector wall-shear integration; this is the sole authoritative skin-friction owner. */
public final class SkinFrictionForceIntegrator {
	public static final MethodId METHOD_ID = new MethodId("boundary-layer-wall-shear-integration-v1");

	public ForceContribution integrate(BoundaryLayerHistory history, Coordinate referencePointM) {
		double fx = 0;
		double fy = 0;
		double fz = 0;
		double momentReferenceX = 0;
		double momentReferenceY = 0;
		double momentReferenceZ = 0;
		double weightedX = 0;
		double weightedY = 0;
		double weightedZ = 0;
		double weight = 0;
		boolean axisymmetric = history.track().mode() == BoundaryLayerMode.AXISYMMETRIC;

		for (int i = 1; i < history.states().size(); i++) {
			BoundaryLayerStation a = history.track().stations().get(i - 1), b = history.track().stations().get(i);
			BoundaryLayerState sa = history.states().get(i - 1), sb = history.states().get(i);
			double area = 0.5 * (a.areaWidthM() + b.areaWidthM()) * (b.sM() - a.sM());
			double tau = 0.5 * (sa.wallShearPa() + sb.wallShearPa());
			double tx = 0.5 * (a.streamwiseTangent().x + b.streamwiseTangent().x);
			double ty = 0.5 * (a.streamwiseTangent().y + b.streamwiseTangent().y);
			double tz = 0.5 * (a.streamwiseTangent().z + b.streamwiseTangent().z);
			double tangentLength = Math.sqrt(tx * tx + ty * ty + tz * tz);
			double dfx = tau * area * tx / tangentLength;
			double dfy = axisymmetric ? 0 : tau * area * ty / tangentLength;
			double dfz = axisymmetric ? 0 : tau * area * tz / tangentLength;
			double x = 0.5 * (a.positionM().x + b.positionM().x);
			double y = axisymmetric ? 0 : 0.5 * (a.positionM().y + b.positionM().y);
			double z = axisymmetric ? 0 : 0.5 * (a.positionM().z + b.positionM().z);
			double rx = x - referencePointM.x;
			double ry = y - referencePointM.y;
			double rz = z - referencePointM.z;

			fx += dfx;
			fy += dfy;
			fz += dfz;
			momentReferenceX += ry * dfz - rz * dfy;
			momentReferenceY += rz * dfx - rx * dfz;
			momentReferenceZ += rx * dfy - ry * dfx;
			double forceMagnitude = Math.sqrt(dfx * dfx + dfy * dfy + dfz * dfz);
			weightedX += x * forceMagnitude;
			weightedY += y * forceMagnitude;
			weightedZ += z * forceMagnitude;
			weight += forceMagnitude;
		}

		Coordinate point = weight > 0
				? new Coordinate(weightedX / weight, weightedY / weight, weightedZ / weight)
				: referencePointM;
		double pointArmX = point.x - referencePointM.x;
		double pointArmY = point.y - referencePointM.y;
		double pointArmZ = point.z - referencePointM.z;
		Coordinate intrinsicMoment = new Coordinate(
				momentReferenceX - (pointArmY * fz - pointArmZ * fy),
				momentReferenceY - (pointArmZ * fx - pointArmX * fz),
				momentReferenceZ - (pointArmX * fy - pointArmY * fx));
		PhysicalOwner owner = new PhysicalOwner(PhysicalTerm.SKIN_FRICTION, OwnershipMode.REPLACES, history.track().regionId(), null);
		return new ForceContribution(history.track().componentId(), owner, METHOD_ID, new Coordinate(fx, fy, fz),
				intrinsicMoment, point, history.track().regionId(),
				history.track().reducedConfidence() ? List.of("REDUCED_CONFIDENCE_3D_FLOW") : List.of(),
				history.track().reducedConfidence() ? 0.6 : 0.9, 0, null);
	}
}

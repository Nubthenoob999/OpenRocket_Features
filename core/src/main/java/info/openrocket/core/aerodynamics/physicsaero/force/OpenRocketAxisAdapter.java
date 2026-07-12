package info.openrocket.core.aerodynamics.physicsaero.force;

import info.openrocket.core.aerodynamics.AerodynamicForces;
import info.openrocket.core.util.Coordinate;

/**
 * The sole boundary for OpenRocket signs. OpenRocket +x points nose-to-tail;
 * dimensional aft force and the reported axial drag coefficient are positive.
 */
public final class OpenRocketAxisAdapter {
	private OpenRocketAxisAdapter() {}
	public static AerodynamicCoefficients coefficients(Coordinate force, Coordinate moment, ReferenceState reference) {
		double forceScale = reference.dynamicPressurePa() * reference.referenceAreaM2();
		double momentScale = forceScale * reference.referenceLengthM();
		return new AerodynamicCoefficients(force.x / forceScale, force.z / forceScale, force.y / forceScale,
				moment.x / momentScale, moment.y / momentScale, moment.z / momentScale);
	}
	public static AerodynamicForces toOpenRocket(AerodynamicCoefficients c) {
		AerodynamicForces forces = new AerodynamicForces();
		forces.setCDaxial(c.ca()); forces.setCD(c.ca()); forces.setCN(c.cn()); forces.setCside(c.cy());
		forces.setCroll(c.cl()); forces.setCm(c.cm()); forces.setCyaw(c.cYaw());
		return forces;
	}
}

package info.openrocket.core.aerodynamics.physicsaero.math;

import info.openrocket.core.util.Coordinate;
import info.openrocket.core.util.CoordinateIF;

public final class VectorTransforms {
	private VectorTransforms() {}
	public static Coordinate cross(CoordinateIF a, CoordinateIF b) {
		return new Coordinate(a.getY() * b.getZ() - a.getZ() * b.getY(),
				a.getZ() * b.getX() - a.getX() * b.getZ(), a.getX() * b.getY() - a.getY() * b.getX());
	}
	public static Coordinate transverse(CoordinateIF velocity, CoordinateIF axialUnit) {
		double dot = velocity.getX() * axialUnit.getX() + velocity.getY() * axialUnit.getY() + velocity.getZ() * axialUnit.getZ();
		return new Coordinate(velocity.getX() - dot * axialUnit.getX(), velocity.getY() - dot * axialUnit.getY(),
				velocity.getZ() - dot * axialUnit.getZ());
	}
}

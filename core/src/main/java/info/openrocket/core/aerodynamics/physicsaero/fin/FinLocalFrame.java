package info.openrocket.core.aerodynamics.physicsaero.fin;

import java.util.Objects;

import info.openrocket.core.util.Coordinate;
import info.openrocket.core.util.CoordinateIF;

/**
 * Immutable, right-handed local coordinate frame for one physical fin.
 * Chordwise points from leading to trailing edge, spanwise from root to tip,
 * and the positive normal is {@code chordwise x spanwise}.
 */
public record FinLocalFrame(Coordinate chordwise, Coordinate spanwise, Coordinate normal) {
	private static final double ORTHONORMAL_TOLERANCE = 1.0e-9;

	public FinLocalFrame {
		chordwise = copy(Objects.requireNonNull(chordwise, "chordwise"));
		spanwise = copy(Objects.requireNonNull(spanwise, "spanwise"));
		normal = copy(Objects.requireNonNull(normal, "normal"));

		requireUnit(chordwise, "chordwise");
		requireUnit(spanwise, "spanwise");
		requireUnit(normal, "normal");
		requireOrthogonal(chordwise, spanwise, "chordwise and spanwise");
		requireOrthogonal(chordwise, normal, "chordwise and normal");
		requireOrthogonal(spanwise, normal, "spanwise and normal");

		CoordinateIF expectedNormal = chordwise.cross(spanwise);
		if (expectedNormal.dot(normal) < 1.0 - ORTHONORMAL_TOLERANCE) {
			throw new IllegalArgumentException("normal must equal chordwise x spanwise");
		}
	}

	/** Construct a frame using the normal implied by the right-hand convention. */
	public FinLocalFrame(CoordinateIF chordwise, CoordinateIF spanwise) {
		this(copy(chordwise), copy(spanwise), copy(cross(chordwise, spanwise)));
	}

	/** Construct and defensively copy a fully specified frame. */
	public FinLocalFrame(CoordinateIF chordwise, CoordinateIF spanwise, CoordinateIF normal) {
		this(copy(chordwise), copy(spanwise), copy(normal));
	}

	/** Resolve a body-frame vector into (chordwise, spanwise, normal) components. */
	public Coordinate toLocal(CoordinateIF bodyVector) {
		Objects.requireNonNull(bodyVector, "bodyVector");
		return new Coordinate(bodyVector.dot(chordwise), bodyVector.dot(spanwise), bodyVector.dot(normal));
	}

	/** Transform local (chordwise, spanwise, normal) components to the body frame. */
	public Coordinate toBody(CoordinateIF localVector) {
		Objects.requireNonNull(localVector, "localVector");
		return new Coordinate(
				localVector.getX() * chordwise.x + localVector.getY() * spanwise.x + localVector.getZ() * normal.x,
				localVector.getX() * chordwise.y + localVector.getY() * spanwise.y + localVector.getZ() * normal.y,
				localVector.getX() * chordwise.z + localVector.getY() * spanwise.z + localVector.getZ() * normal.z);
	}

	/** Resolve a body-frame point relative to an origin into local coordinates. */
	public Coordinate projectPoint(CoordinateIF bodyPoint, CoordinateIF bodyOrigin) {
		Objects.requireNonNull(bodyPoint, "bodyPoint");
		Objects.requireNonNull(bodyOrigin, "bodyOrigin");
		return toLocal(bodyPoint.sub(bodyOrigin));
	}

	public double chordwiseComponent(CoordinateIF bodyVector) {
		return Objects.requireNonNull(bodyVector, "bodyVector").dot(chordwise);
	}

	public double spanwiseComponent(CoordinateIF bodyVector) {
		return Objects.requireNonNull(bodyVector, "bodyVector").dot(spanwise);
	}

	public double normalComponent(CoordinateIF bodyVector) {
		return Objects.requireNonNull(bodyVector, "bodyVector").dot(normal);
	}

	private static CoordinateIF cross(CoordinateIF chordwise, CoordinateIF spanwise) {
		Objects.requireNonNull(chordwise, "chordwise");
		Objects.requireNonNull(spanwise, "spanwise");
		return chordwise.cross(spanwise);
	}

	private static Coordinate copy(CoordinateIF coordinate) {
		Objects.requireNonNull(coordinate, "coordinate");
		return new Coordinate(coordinate.getX(), coordinate.getY(), coordinate.getZ());
	}

	private static void requireUnit(CoordinateIF vector, String name) {
		if (!finite(vector) || Math.abs(vector.length() - 1.0) > ORTHONORMAL_TOLERANCE) {
			throw new IllegalArgumentException(name + " must be a finite unit vector");
		}
	}

	private static void requireOrthogonal(CoordinateIF first, CoordinateIF second, String names) {
		if (Math.abs(first.dot(second)) > ORTHONORMAL_TOLERANCE) {
			throw new IllegalArgumentException(names + " must be orthogonal");
		}
	}

	private static boolean finite(CoordinateIF vector) {
		return Double.isFinite(vector.getX()) && Double.isFinite(vector.getY()) && Double.isFinite(vector.getZ());
	}
}

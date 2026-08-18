package info.openrocket.core.util.ejection;

import java.util.Objects;

import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.NoseCone;
import info.openrocket.core.rocketcomponent.RingComponent;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.rocketcomponent.TubeCoupler;
import info.openrocket.core.util.CoordinateIF;

/**
 * Determines the pressurized axial span of a body tube for an ejection charge.
 *
 * <p>Couplers and nose-cone shoulders that enter either end of the selected
 * tube are treated as joints.  Their overlap is removed from the available
 * bay length.  From the firing/open end, the calculator then searches toward
 * the opposite end for the first transverse internal component.  That
 * component is the closed end of the pressurized bay.
 *
 * <p>A hollow coupler is never itself a transverse barrier.  Components nested
 * in a coupler that forms an end joint are part of that joint assembly and are
 * not searched again as independent barriers.  This distinction is important
 * for couplers with stacked bulkheads at their inboard face: counting one of
 * those bulkheads twice can reduce an otherwise valid bay to almost zero.
 */
public final class EffectiveBayLengthCalculator {

	private static final double POSITION_EPSILON_M = 1.0e-9;

	private EffectiveBayLengthCalculator() {
	}

	/**
	 * Computes the effective bay span for {@code tube} and {@code direction}.
	 */
	public static Result calculate(BodyTube tube, EjectionFiringDirection direction) {
		Objects.requireNonNull(tube, "tube");
		Objects.requireNonNull(direction, "direction");

		double tubeStart = axialStart(tube);
		double tubeLength = tube.getLength();
		if (!Double.isFinite(tubeLength) || tubeLength < 0.0) {
			throw new IllegalArgumentException("Bay tube length must be finite and non-negative");
		}
		if (tubeLength == 0.0) {
			return new Result(0.0, 0.0, 0.0);
		}
		double tubeEnd = tubeStart + tubeLength;

		double[] jointInsets = new double[] { 0.0, 0.0 };
		collectJointInsets(tube, tubeStart, tubeEnd, jointInsets);

		double forwardJointInset = jointInsets[0];
		double aftJointInset = jointInsets[1];
		double openForwardFace = tubeStart + forwardJointInset;
		double openAftFace = tubeEnd - aftJointInset;

		BarrierFaces nearest = new BarrierFaces();
		collectBarriers(tube, tube, tubeStart, tubeEnd,
				openForwardFace, openAftFace, nearest);

		double forwardInset = forwardJointInset;
		double aftInset = aftJointInset;
		if (direction == EjectionFiringDirection.FORWARD
				&& Double.isFinite(nearest.firstFromForward)) {
			aftInset = Math.max(aftJointInset,
					tubeEnd - nearest.firstFromForward);
		} else if (direction == EjectionFiringDirection.AFT
				&& Double.isFinite(nearest.firstFromAft)) {
			forwardInset = Math.max(forwardJointInset,
					nearest.firstFromAft - tubeStart);
		}

		forwardInset = clamp(forwardInset, 0.0, tubeLength);
		aftInset = clamp(aftInset, 0.0, tubeLength);
		if (forwardInset + aftInset > tubeLength) {
			double scale = tubeLength / (forwardInset + aftInset);
			forwardInset *= scale;
			aftInset *= scale;
		}

		return new Result(forwardInset, aftInset,
				Math.max(0.0, tubeLength - forwardInset - aftInset));
	}

	private static void collectJointInsets(BodyTube tube, double tubeStart,
			double tubeEnd, double[] insets) {
		for (RocketComponent child : tube.getChildren()) {
			if (child instanceof TubeCoupler) {
				accumulateCouplerInset((TubeCoupler) child, tubeStart, tubeEnd, insets);
			}
		}

		RocketComponent parent = tube.getParent();
		if (parent == null) {
			return;
		}
		for (RocketComponent sibling : parent.getChildren()) {
			if (sibling == tube) {
				continue;
			}
			if (sibling instanceof TubeCoupler) {
				accumulateCouplerInset((TubeCoupler) sibling, tubeStart, tubeEnd, insets);
			} else if (sibling instanceof BodyTube) {
				for (RocketComponent child : sibling.getChildren()) {
					if (child instanceof TubeCoupler) {
						accumulateCouplerInset((TubeCoupler) child,
								tubeStart, tubeEnd, insets);
					}
				}
			} else if (sibling instanceof NoseCone) {
				accumulateShoulderInset((NoseCone) sibling, tubeStart, tubeEnd, insets);
			}
		}
	}

	private static void accumulateCouplerInset(TubeCoupler coupler,
			double tubeStart, double tubeEnd, double[] insets) {
		double couplerStart = axialStart(coupler);
		double couplerEnd = couplerStart + coupler.getLength();
		double overlapStart = Math.max(couplerStart, tubeStart);
		double overlapEnd = Math.min(couplerEnd, tubeEnd);
		if (overlapEnd <= overlapStart) {
			return;
		}

		// Only overlaps that reach a tube end are joints.  A coupler wholly
		// inside the tube is hollow structure, not an axial-length inset.
		if (couplerStart <= tubeStart + POSITION_EPSILON_M) {
			insets[0] = Math.max(insets[0], overlapEnd - tubeStart);
		}
		if (couplerEnd >= tubeEnd - POSITION_EPSILON_M) {
			insets[1] = Math.max(insets[1], tubeEnd - overlapStart);
		}
	}

	private static void accumulateShoulderInset(NoseCone noseCone,
			double tubeStart, double tubeEnd, double[] insets) {
		double shoulderLength = noseCone.getShoulderLength();
		if (shoulderLength <= 0.0) {
			return;
		}
		double noseStart = axialStart(noseCone);
		double noseEnd = noseStart + noseCone.getLength();

		accumulateEndOverlap(noseEnd, noseEnd + shoulderLength,
				tubeStart, tubeEnd, insets);
		accumulateEndOverlap(noseStart - shoulderLength, noseStart,
				tubeStart, tubeEnd, insets);
	}

	private static void accumulateEndOverlap(double componentStart,
			double componentEnd, double tubeStart, double tubeEnd, double[] insets) {
		double overlapStart = Math.max(componentStart, tubeStart);
		double overlapEnd = Math.min(componentEnd, tubeEnd);
		if (overlapEnd <= overlapStart) {
			return;
		}
		if (componentStart <= tubeStart + POSITION_EPSILON_M) {
			insets[0] = Math.max(insets[0], overlapEnd - tubeStart);
		}
		if (componentEnd >= tubeEnd - POSITION_EPSILON_M) {
			insets[1] = Math.max(insets[1], tubeEnd - overlapStart);
		}
	}

	private static void collectBarriers(BodyTube selectedTube,
			RocketComponent container, double tubeStart, double tubeEnd,
			double bayForward, double bayAft, BarrierFaces nearest) {
		for (RocketComponent child : container.getChildren()) {
			if (child instanceof TubeCoupler) {
				if (!isEndJoint((TubeCoupler) child, tubeStart, tubeEnd)) {
					collectNestedBarriers(child, bayForward, bayAft, nearest);
				}
				continue;
			}
			if (child instanceof RingComponent) {
				nearest.consider((RingComponent) child, bayForward, bayAft);
			}
		}

		if (container != selectedTube) {
			return;
		}
		RocketComponent parent = selectedTube.getParent();
		if (parent == null) {
			return;
		}
		for (RocketComponent sibling : parent.getChildren()) {
			if (sibling == selectedTube || !(sibling instanceof BodyTube)) {
				continue;
			}
			collectBarriers(selectedTube, sibling, tubeStart, tubeEnd,
					bayForward, bayAft, nearest);
		}
	}

	private static void collectNestedBarriers(RocketComponent container,
			double bayForward, double bayAft, BarrierFaces nearest) {
		for (RocketComponent child : container.getChildren()) {
			if (child instanceof TubeCoupler) {
				collectNestedBarriers(child, bayForward, bayAft, nearest);
			} else {
				if (child instanceof RingComponent) {
					nearest.consider((RingComponent) child, bayForward, bayAft);
				}
				collectNestedBarriers(child, bayForward, bayAft, nearest);
			}
		}
	}

	private static boolean isEndJoint(TubeCoupler coupler,
			double tubeStart, double tubeEnd) {
		double couplerStart = axialStart(coupler);
		double couplerEnd = couplerStart + coupler.getLength();
		boolean overlaps = Math.min(couplerEnd, tubeEnd)
				> Math.max(couplerStart, tubeStart);
		return overlaps && (couplerStart <= tubeStart + POSITION_EPSILON_M
				|| couplerEnd >= tubeEnd - POSITION_EPSILON_M);
	}

	private static double axialStart(RocketComponent component) {
		CoordinateIF[] locations = component.getComponentLocations();
		if (locations.length == 0) {
			throw new IllegalArgumentException("Component has no axial location: "
					+ component.getName());
		}
		double start = locations[0].getX();
		if (!Double.isFinite(start)) {
			throw new IllegalArgumentException("Component has a non-finite axial location: "
					+ component.getName());
		}
		return start;
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	private static final class BarrierFaces {
		double firstFromForward = Double.POSITIVE_INFINITY;
		double firstFromAft = Double.NEGATIVE_INFINITY;

		void consider(RingComponent barrier, double bayForward, double bayAft) {
			double start = axialStart(barrier);
			double end = start + barrier.getLength();
			double clippedStart = Math.max(start, bayForward);
			double clippedEnd = Math.min(end, bayAft);
			if (clippedEnd <= clippedStart) {
				return;
			}
			firstFromForward = Math.min(firstFromForward, clippedStart);
			firstFromAft = Math.max(firstFromAft, clippedEnd);
		}
	}

	/** Immutable breakdown of the effective bay span, in metres. */
	public static final class Result {
		private final double forwardInset;
		private final double aftInset;
		private final double effectiveLength;

		private Result(double forwardInset, double aftInset, double effectiveLength) {
			this.forwardInset = forwardInset;
			this.aftInset = aftInset;
			this.effectiveLength = effectiveLength;
		}

		public double getForwardInset() {
			return forwardInset;
		}

		public double getAftInset() {
			return aftInset;
		}

		public double getEffectiveLength() {
			return effectiveLength;
		}
	}
}

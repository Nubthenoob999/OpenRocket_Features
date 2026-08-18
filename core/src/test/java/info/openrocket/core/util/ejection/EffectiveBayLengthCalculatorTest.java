package info.openrocket.core.util.ejection;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.Bulkhead;
import info.openrocket.core.rocketcomponent.TubeCoupler;
import info.openrocket.core.rocketcomponent.position.AxialMethod;
import info.openrocket.core.util.BaseTestCase;

class EffectiveBayLengthCalculatorTest extends BaseTestCase {

	private static final double EPSILON = 1.0e-9;

	@Test
	void jointBulkheadsDoNotCollapseBayToSpinnerMinimum() {
		BodyTube tube = bodyTube(0.50);

		// Add the aft joint first to exercise the old first-coupler search bug.
		TubeCoupler aftJoint = coupler(0.10, AxialMethod.BOTTOM, 0.0);
		aftJoint.addChild(protrudingInboardBulkhead());
		tube.addChild(aftJoint);
		aftJoint.setAxialMethod(AxialMethod.BOTTOM);
		aftJoint.setAxialOffset(0.0);

		TubeCoupler forwardJoint = coupler(0.10, AxialMethod.TOP, 0.0);
		forwardJoint.addChild(protrudingInboardBulkhead());
		tube.addChild(forwardJoint);
		forwardJoint.setAxialMethod(AxialMethod.TOP);
		forwardJoint.setAxialOffset(0.0);

		EffectiveBayLengthCalculator.Result forward =
				EffectiveBayLengthCalculator.calculate(tube, EjectionFiringDirection.FORWARD);
		EffectiveBayLengthCalculator.Result aft =
				EffectiveBayLengthCalculator.calculate(tube, EjectionFiringDirection.AFT);

		assertEquals(0.10, forward.getForwardInset(), EPSILON);
		assertEquals(0.10, forward.getAftInset(), EPSILON);
		assertEquals(0.30, forward.getEffectiveLength(), EPSILON);
		assertEquals(0.30, aft.getEffectiveLength(), EPSILON);
	}

	@Test
	void directionSelectsFirstBarrierFromOpenEnd() {
		BodyTube tube = bodyTube(0.50);
		tube.addChild(bulkhead(0.15, 0.01));
		tube.addChild(bulkhead(0.35, 0.01));

		EffectiveBayLengthCalculator.Result forward =
				EffectiveBayLengthCalculator.calculate(tube, EjectionFiringDirection.FORWARD);
		EffectiveBayLengthCalculator.Result aft =
				EffectiveBayLengthCalculator.calculate(tube, EjectionFiringDirection.AFT);

		assertEquals(0.15, forward.getEffectiveLength(), EPSILON,
				"Forward firing must stop at the first barrier from the forward end");
		assertEquals(0.14, aft.getEffectiveLength(), EPSILON,
				"Aft firing must stop at the first barrier from the aft end");
	}

	@Test
	void internalCouplerIsNotMistakenForEndJointOrBarrier() {
		BodyTube tube = bodyTube(0.50);
		tube.addChild(coupler(0.10, AxialMethod.TOP, 0.20));

		EffectiveBayLengthCalculator.Result result =
				EffectiveBayLengthCalculator.calculate(tube, EjectionFiringDirection.FORWARD);

		assertEquals(0.0, result.getForwardInset(), EPSILON);
		assertEquals(0.0, result.getAftInset(), EPSILON);
		assertEquals(0.50, result.getEffectiveLength(), EPSILON);
	}

	@Test
	void bulkheadInInternalCouplerRemainsARealBarrier() {
		BodyTube tube = bodyTube(0.50);
		TubeCoupler internal = coupler(0.10, AxialMethod.TOP, 0.20);
		internal.addChild(bulkhead(0.09, 0.01));
		tube.addChild(internal);

		EffectiveBayLengthCalculator.Result result =
				EffectiveBayLengthCalculator.calculate(tube, EjectionFiringDirection.FORWARD);

		assertEquals(0.29, result.getEffectiveLength(), EPSILON);
	}

	private static BodyTube bodyTube(double length) {
		BodyTube tube = new BodyTube();
		tube.setLength(length);
		return tube;
	}

	private static TubeCoupler coupler(double length, AxialMethod method, double offset) {
		TubeCoupler coupler = new TubeCoupler();
		coupler.setLength(length);
		coupler.setAxialMethod(method);
		coupler.setAxialOffset(offset);
		return coupler;
	}

	private static Bulkhead protrudingInboardBulkhead() {
		return bulkhead(0.10, 0.01);
	}

	private static Bulkhead bulkhead(double topOffset, double length) {
		Bulkhead bulkhead = new Bulkhead();
		bulkhead.setLength(length);
		bulkhead.setAxialMethod(AxialMethod.TOP);
		bulkhead.setAxialOffset(topOffset);
		return bulkhead;
	}
}

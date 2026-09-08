package info.openrocket.core.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.AerodynamicCalculator;
import info.openrocket.core.aerodynamics.AerodynamicForces;
import info.openrocket.core.aerodynamics.FlightConditions;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.models.wind.WindModel;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.util.BaseTestCase;
import info.openrocket.core.util.Coordinate;
import info.openrocket.core.util.CoordinateIF;
import info.openrocket.core.util.ModID;
import info.openrocket.core.util.TestRockets;
import info.openrocket.core.util.WorldCoordinate;

public class WeathercockingCompensationTest extends BaseTestCase {

	private static final double LB_TO_KG = 0.453592;
	private static final double MPH_TO_MPS = 0.44704;

	@Test
	public void testCalibrationPointsMatchCurrentLaunchCaseFit() {
		assertPredictedAngleForCase(20.0, 33.60, 2.541, 3.576, 0.193);
		assertPredictedAngleForCase(13.0, 18.00, 8.102, 3.771, 0.663);
		assertPredictedAngleForCase(1.0, -8.00, 5.188, 3.288, 0.173);
		assertPredictedAngleForCase(5.0, 23.63, 2.430, 3.000, 1.049);
	}

	@Test
	public void testClampingBoundsRequestedAngle() {
		WeathercockingCompensation.Prediction prediction = WeathercockingCompensation.predictForInputs(
				Math.toRadians(50.0),
				Math.PI / 2.0,
				30.0 * MPH_TO_MPS,
				4.5,
				1.8);

		assertTrue(prediction.isClamped());
		assertEquals(60.0, Math.toDegrees(prediction.getAppliedLaunchAngle()), 1.0e-12);
	}

	private static void assertPredictedAngle(double initialAngleDeg, double expectedAngleDeg, double windMph,
			double stabilityCalibers, double wetMassLb) {
		WeathercockingCompensation.Prediction prediction = WeathercockingCompensation.predictForInputs(
				Math.toRadians(initialAngleDeg),
				Math.PI / 2.0,
				windMph * MPH_TO_MPS,
				stabilityCalibers,
				wetMassLb * LB_TO_KG);

		assertEquals(expectedAngleDeg, Math.toDegrees(prediction.getRequestedLaunchAngle()), 1.0e-2);
	}

	private static void assertPredictedAngleForCase(double initialAngleDeg, double expectedAngleDeg,
			double windMps, double stabilityCalibers, double stabilityToMassRatio) {
		double wetMassKg = stabilityCalibers / stabilityToMassRatio;
		WeathercockingCompensation.Prediction prediction = WeathercockingCompensation.predictForInputs(
				Math.toRadians(initialAngleDeg),
				Math.PI / 2.0,
				windMps,
				stabilityCalibers,
				wetMassKg);

		assertEquals(expectedAngleDeg, Math.toDegrees(prediction.getRequestedLaunchAngle()), 5.0e-2);
	}

	private static final class FixedWindModel implements WindModel {
		private final CoordinateIF velocity;

		private FixedWindModel(double speedMps) {
			this.velocity = new Coordinate(speedMps, 0.0, 0.0);
		}

		@Override
		public CoordinateIF getWindVelocity(double time, double altitudeMSL, double altitudeAGL) {
			return velocity;
		}

		@Override
		public CoordinateIF getWindVelocity(double time, double altitude) {
			return velocity;
		}

		@Override
		public void setSeed(int seed) {
			// The fixed test wind is intentionally independent of random seeds.
		}

		@Override
		public WindModel clone() {
			return new FixedWindModel(velocity.length());
		}

		@Override
		public ModID getModID() {
			return ModID.ZERO;
		}

		@Override
		public void addChangeListener(info.openrocket.core.util.StateChangeListener listener) {
		}

		@Override
		public void removeChangeListener(info.openrocket.core.util.StateChangeListener listener) {
		}
	}

	private static class FixedAerodynamicCalculator implements AerodynamicCalculator {
		private final CoordinateIF cp;

		private FixedAerodynamicCalculator(double cpX, double cpWeight) {
			this.cp = new Coordinate(cpX, 0.0, 0.0, cpWeight);
		}

		@Override
		public double getStallAngle() {
			return Math.PI / 2.0;
		}

		@Override
		public CoordinateIF getCP(FlightConfiguration configuration, FlightConditions conditions, WarningSet warnings) {
			return cp;
		}

		@Override
		public AerodynamicForces getAerodynamicForces(FlightConfiguration configuration,
				FlightConditions conditions, WarningSet warnings) {
			AerodynamicForces forces = new AerodynamicForces();
			forces.setCP(cp);
			return forces;
		}

		@Override
		public Map<RocketComponent, AerodynamicForces> getForceAnalysis(FlightConfiguration configuration,
				FlightConditions conditions, WarningSet warnings) {
			return Collections.emptyMap();
		}

		@Override
		public CoordinateIF getWorstCP(FlightConfiguration configuration, FlightConditions conditions,
				WarningSet warnings) {
			return cp;
		}

		@Override
		public AerodynamicCalculator newInstance() {
			return new FixedAerodynamicCalculator(cp.getX(), cp.getWeight());
		}

		@Override
		public void checkGeometry(FlightConfiguration configuration, RocketComponent component, WarningSet warnings) {
		}

		@Override
		public ModID getModID() {
			return ModID.ZERO;
		}
	}

}

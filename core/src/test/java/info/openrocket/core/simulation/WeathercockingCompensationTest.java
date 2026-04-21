package info.openrocket.core.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.AerodynamicCalculator;
import info.openrocket.core.aerodynamics.AerodynamicForces;
import info.openrocket.core.aerodynamics.FlightConditions;
import info.openrocket.core.aerodynamics.RomAerodynamicCalculator;
import info.openrocket.core.aerodynamics.rom.RomSettings;
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
	public void testCalibrationPointsMatchCurrentGlobalFit() {
		assertPredictedAngle(13.0, 18.0, 20.0, 3.77, 12.5);
		assertPredictedAngle(1.0, -8.0, 9.62, 3.29, 42.0);
		assertPredictedAngle(20.0, 33.6, 5.0, 3.58, 40.8);
		assertPredictedAngle(5.0, 20.0, 5.23, 3.0, 6.31);
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

	@Test
	public void testRomEnabledUsesBaselineStabilityForCompensation() {
		FlightConfiguration configuration = TestRockets.makeEstesAlphaIII().getFlightConfiguration(TestRockets.TEST_FCID_0);
		WeathercockingCompensation.Prediction legacyPrediction = WeathercockingCompensation.evaluate(
				newConditions(null),
				configuration);
		SimulationConditions romConditions = newConditions(new InvalidRomAerodynamicCalculator());
		WeathercockingCompensation.Prediction romPrediction = WeathercockingCompensation.evaluate(
				romConditions,
				configuration);

		assertTrue(legacyPrediction.isActive());
		assertTrue(romPrediction.isActive());
		assertTrue(romConditions.getAerodynamicCalculator() instanceof RomAerodynamicCalculator);
		assertEquals(legacyPrediction.getStabilityCalibers(), romPrediction.getStabilityCalibers(), 1.0e-12);
		assertEquals(legacyPrediction.getRequestedLaunchAngle(), romPrediction.getRequestedLaunchAngle(), 1.0e-12);
		assertEquals(legacyPrediction.getAppliedLaunchAngle(), romPrediction.getAppliedLaunchAngle(), 1.0e-12);
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

	private static SimulationConditions newConditions(RomAerodynamicCalculator romCalculator) {
		SimulationConditions conditions = new SimulationConditions();
		conditions.setWeathercockingCompensationEnabled(true);
		conditions.setLaunchRodAngle(Math.toRadians(5.0));
		conditions.setLaunchRodDirection(Math.PI / 2.0);
		conditions.setLaunchSite(new WorldCoordinate(0.0, 0.0, 0.0));
		conditions.setWindModel(new FixedWindModel(5.0));
		conditions.setAerodynamicCalculator(new FixedAerodynamicCalculator(2.0, 1.0));
		conditions.setRomAerodynamicCalculator(romCalculator);
		return conditions;
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

	private static final class InvalidRomAerodynamicCalculator extends RomAerodynamicCalculator {
		private InvalidRomAerodynamicCalculator() {
			super(new FixedAerodynamicCalculator(0.0, 0.0), enabledRomSettings());
		}

		@Override
		public CoordinateIF getCP(FlightConfiguration configuration, FlightConditions conditions, WarningSet warnings) {
			return Coordinate.ZERO;
		}

		@Override
		public CoordinateIF getWorstCP(FlightConfiguration configuration, FlightConditions conditions,
				WarningSet warnings) {
			return Coordinate.ZERO;
		}
	}

	private static RomSettings enabledRomSettings() {
		RomSettings settings = RomSettings.defaults();
		settings.setEnabled(true);
		return settings;
	}
}
package info.openrocket.core.montecarlo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import info.openrocket.core.simulation.SimulationConditions;
import info.openrocket.core.simulation.SimulationOptions;
import info.openrocket.core.simulation.SimulationStatus;
import info.openrocket.core.util.BaseTestCase;
import info.openrocket.core.util.Coordinate;
import info.openrocket.core.util.CoordinateIF;
import info.openrocket.core.util.TestRockets;

class RunWindDisturbanceProfileTest extends BaseTestCase {
	private static final double EPSILON = 1.0e-12;

	@Test
	void oneMinusCosineGustHasExactSmoothPulseShape() {
		GustEvent gust = new GustEvent(10.0, 4.0, new Vec2(8.0, -4.0));

		assertVector(gust.deltaAtTime(9.99), 0, 0);
		assertVector(gust.deltaAtTime(10.0), 0, 0);
		assertVector(gust.deltaAtTime(11.0), 4, -2);
		assertVector(gust.deltaAtTime(12.0), 8, -4);
		assertVector(gust.deltaAtTime(14.0), 0, 0);
		assertVector(gust.deltaAtTime(14.01), 0, 0);
	}

	@Test
	void finiteShearLayerIsContinuousAndHasExpectedCenterGradient() {
		ShearLayer shear = new ShearLayer(100.0, 20.0, new Vec2(6.0, -2.0));

		assertVector(shear.deltaAtAltitude(100.0), 3.0, -1.0);
		assertEquals(0.0, shear.deltaAtAltitude(-1000).mag(), 1.0e-12);
		assertVector(shear.deltaAtAltitude(1200), 6.0, -2.0);

		double dz = 1.0e-4;
		double duDz = (shear.deltaAtAltitude(100 + dz).u
				- shear.deltaAtAltitude(100 - dz).u) / (2 * dz);
		assertEquals(6.0 / 20.0, duDz, 1.0e-8,
				"for a full thickness of 2*delta, the center gradient is deltaWind/thickness");
	}

	@Test
	void profileQueriesArePureAndAddAllActiveComponents() {
		RunWindDisturbanceProfile profile = new RunWindDisturbanceProfile(
				List.of(new GustEvent(0, 2, new Vec2(2, 4))),
				new ShearLayer(100, 20, new Vec2(6, -2)));

		Vec2 first = profile.deltaWindXY(1, 100);
		Vec2 repeated = profile.deltaWindXY(1, 100);
		assertVector(first, 5, 3);
		assertVector(repeated, first.u, first.v);
	}

	@Test
	void listenerAddsAtQueryTimeWithoutMutatingTheBaseWindModel() {
		SimulationOptions options = new SimulationOptions();
		options.getAverageWindModel().setAverage(5);
		options.getAverageWindModel().setStandardDeviation(0);
		SimulationConditions conditions = options.toSimulationConditions();
		SimulationStatus status = new SimulationStatus(
				TestRockets.makeEstesAlphaIII().getSelectedConfiguration(), conditions);
		status.setSimulationTime(2);
		status.setRocketPosition(new Coordinate(0, 0, 100));

		RunWindDisturbanceProfile profile = new RunWindDisturbanceProfile(
				List.of(new GustEvent(1, 2, new Vec2(2, -1))), null);
		GustShearListener listener = new GustShearListener(profile, new GustShearMetrics(), false);
		CoordinateIF base = conditions.getWindModel().getWindVelocity(2, 100);
		CoordinateIF varied = listener.postWindModel(status, base);

		assertNotNull(varied);
		assertEquals(base.getX() + 2, varied.getX(), EPSILON);
		assertEquals(base.getY() - 1, varied.getY(), EPSILON);
		assertEquals(base, conditions.getWindModel().getWindVelocity(2, 100),
				"the realization must not rewrite the shared mean/profile wind model");
	}

	@Test
	void clonedStageListenersShareTheSameRealizationAtCoLocatedQueries() {
		RunWindDisturbanceProfile profile = new RunWindDisturbanceProfile(
				List.of(new GustEvent(1, 2, new Vec2(2, -1))),
				new ShearLayer(100, 20, new Vec2(4, 6)));
		GustShearListener first = new GustShearListener(profile, new GustShearMetrics(), false);
		GustShearListener separatedStage = (GustShearListener) first.clone();

		SimulationOptions options = new SimulationOptions();
		SimulationStatus status = new SimulationStatus(
				TestRockets.makeEstesAlphaIII().getSelectedConfiguration(),
				options.toSimulationConditions());
		status.setSimulationTime(2);
		status.setRocketPosition(new Coordinate(0, 0, 100));
		CoordinateIF base = new Coordinate(3, 5, 0);

		assertEquals(first.postWindModel(status, base), separatedStage.postWindModel(status, base));
	}

	@Test
	void namedRandomStreamsKeepGustAndShearSamplingIndependent() {
		MonteCarloExtension fewGusts = configuredExtension(1);
		MonteCarloExtension manyGusts = configuredExtension(12);

		RunWindDisturbanceProfile first = RunWindDisturbanceProfile.sampleFromConfig(fewGusts, 123456789L);
		RunWindDisturbanceProfile second = RunWindDisturbanceProfile.sampleFromConfig(manyGusts, 123456789L);

		assertNotNull(first.shear);
		assertNotNull(second.shear);
		assertVector(first.shear.deltaTop, second.shear.deltaTop.u, second.shear.deltaTop.v);
		assertNotEquals(first.getGustCount(), second.getGustCount());

		RandomStreamManager manager = new RandomStreamManager(42);
		Random gust = manager.stream("wind.gusts");
		Random shear = manager.stream("wind.shear");
		assertNotEquals(gust.nextLong(), shear.nextLong());
		assertEquals(manager.stream("wind.gusts").nextLong(),
				new RandomStreamManager(42).stream("wind.gusts").nextLong());
	}

	@Test
	void scalarWindConversionMatchesTheOpenRocketModelCoordinateConvention() {
		double speed = 7.5;
		double direction = 1.234;
		Vec2 vector = Vec2.fromOpenRocketWind(speed, direction);
		Vec2.OpenRocketWind roundTrip = Vec2.toOpenRocketWind(vector);

		assertEquals(speed, roundTrip.speed, EPSILON);
		assertEquals(direction, roundTrip.windFromDirRad, EPSILON);
	}

	private static MonteCarloExtension configuredExtension(int gustCount) {
		MonteCarloExtension extension = new MonteCarloExtension();
		extension.setGustEventsEnabled(true);
		extension.setGustEventCount(gustCount);
		extension.setGustWindowStartS(0);
		extension.setGustWindowEndS(20);
		extension.setGustDurationMeanS(2);
		extension.setGustDurationSigmaS(0.4);
		extension.setGustPeakDeltaMeanMps(5);
		extension.setGustPeakDeltaSigmaMps(1);
		extension.setShearLayerEnabled(true);
		extension.setShearCenterAltM(500);
		extension.setShearThicknessM(100);
		extension.setShearDeltaMeanMps(8);
		extension.setShearDeltaSigmaMps(2);
		return extension;
	}

	private static void assertVector(Vec2 actual, double expectedU, double expectedV) {
		assertEquals(expectedU, actual.u, EPSILON);
		assertEquals(expectedV, actual.v, EPSILON);
	}
}

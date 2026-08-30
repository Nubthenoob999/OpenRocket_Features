package info.openrocket.core.simulation.montecarlo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import info.openrocket.core.simulation.montecarlo.DispersionStatistics.DispersionEllipse;

public class DispersionStatisticsTest {
	private static final double EPSILON = 1.0e-12;

	@Test
	public void testContainmentAndPrincipalAxes() {
		List<LandingPoint> points = List.of(
				new LandingPoint(0, "Rocket", 1, 0),
				new LandingPoint(0, "Rocket", -1, 0),
				new LandingPoint(0, "Rocket", 0, 2),
				new LandingPoint(0, "Rocket", 0, -2));

		DispersionStatistics statistics = DispersionStatistics.from(points);
		assertEquals(4, statistics.getSampleCount());
		assertEquals(0, statistics.getMeanEast(), EPSILON);
		assertEquals(0, statistics.getMeanNorth(), EPSILON);
		assertEquals(1, statistics.getContainmentRadius(0.50), EPSILON);
		assertEquals(2, statistics.getContainmentRadius(0.90), EPSILON);

		DispersionEllipse ellipse = statistics.getEllipse(1);
		assertEquals(Math.sqrt(8.0 / 3.0), ellipse.semiMajor(), EPSILON);
		assertEquals(Math.sqrt(2.0 / 3.0), ellipse.semiMinor(), EPSILON);
		assertEquals(0, statistics.getMajorAxisBearing(), EPSILON);
	}

	@Test
	public void testNearestRankContainmentActuallyContainsRequestedFraction() {
		List<LandingPoint> points = List.of(
				new LandingPoint(0, "Rocket", 0, 0),
				new LandingPoint(0, "Rocket", 1, 0),
				new LandingPoint(0, "Rocket", 2, 0),
				new LandingPoint(0, "Rocket", 3, 0),
				new LandingPoint(0, "Rocket", 4, 0));
		DispersionStatistics statistics = DispersionStatistics.from(points);

		assertEquals(2, statistics.getContainmentRadius(0.80), EPSILON);
		assertThrows(IllegalArgumentException.class, () -> statistics.getContainmentRadius(0));
	}

	@Test
	public void testDegenerateSinglePointCloudHasZeroDispersion() {
		DispersionStatistics statistics = DispersionStatistics.from(
				List.of(new LandingPoint(0, "Rocket", 12.5, -3.0)));

		assertEquals(1, statistics.getSampleCount());
		assertEquals(12.5, statistics.getMeanEast(), EPSILON);
		assertEquals(-3.0, statistics.getMeanNorth(), EPSILON);
		assertEquals(0.0, statistics.getContainmentRadius(1.0), EPSILON);
		assertEquals(0.0, statistics.getEllipse(3).semiMajor(), EPSILON);
		assertEquals(0.0, statistics.getEllipse(3).semiMinor(), EPSILON);
	}

	@Test
	public void testNonFinitePointsAreExcludedAndAllInvalidCloudsFailFast() {
		DispersionStatistics statistics = DispersionStatistics.from(List.of(
				new LandingPoint(0, "Rocket", Double.NaN, 1),
				new LandingPoint(0, "Rocket", 4, 5),
				new LandingPoint(0, "Rocket", 1, Double.POSITIVE_INFINITY)));

		assertEquals(1, statistics.getSampleCount());
		assertEquals(4, statistics.getMeanEast(), EPSILON);
		assertThrows(IllegalArgumentException.class, () -> DispersionStatistics.from(List.of()));
		assertThrows(IllegalArgumentException.class, () -> DispersionStatistics.from(List.of(
				new LandingPoint(0, "Rocket", Double.NaN, Double.NEGATIVE_INFINITY))));
	}

	@Test
	public void testInvalidContainmentAndEllipseArgumentsFailFast() {
		DispersionStatistics statistics = DispersionStatistics.from(
				List.of(new LandingPoint(0, "Rocket", 0, 0)));

		assertThrows(IllegalArgumentException.class,
				() -> statistics.getContainmentRadius(Double.NaN));
		assertThrows(IllegalArgumentException.class,
				() -> statistics.getContainmentRadius(1.01));
		assertThrows(IllegalArgumentException.class, () -> statistics.getEllipse(-1));
		assertThrows(IllegalArgumentException.class,
				() -> statistics.getEllipse(Double.POSITIVE_INFINITY));
	}
}

package info.openrocket.core.simulation.montecarlo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

public class MonteCarloDistributionTest {
	@Test
	public void testZeroSpreadIsExactlyNominalForEveryDistribution() {
		for (MonteCarloDistribution distribution : MonteCarloDistribution.values()) {
			assertEquals(0.0, distribution.sample(new Random(123), 0.0));
		}
	}

	@Test
	public void testUniformSamplesStayInsideConfiguredHalfRange() {
		Random random = new Random(0xB0A7);
		for (int index = 0; index < 10_000; index++) {
			double value = MonteCarloDistribution.UNIFORM.sample(random, 2.5);
			assertTrue(value >= -2.5 && value < 2.5, "out-of-range uniform draw: " + value);
		}
	}

	@Test
	public void testNormalSamplerHasExpectedFirstTwoMoments() {
		Random random = new Random(0xCAFE);
		int sampleCount = 100_000;
		double total = 0;
		double totalSquares = 0;
		for (int index = 0; index < sampleCount; index++) {
			double value = MonteCarloDistribution.NORMAL.sample(random, 2.0);
			total += value;
			totalSquares += value * value;
		}
		double mean = total / sampleCount;
		double variance = (totalSquares - sampleCount * mean * mean) / (sampleCount - 1.0);
		assertEquals(0.0, mean, 0.025);
		assertEquals(4.0, variance, 0.08);
	}

	@Test
	public void testInvalidSamplingInputsFailFast() {
		assertThrows(NullPointerException.class,
				() -> MonteCarloDistribution.NORMAL.sample(null, 1.0));
		assertThrows(IllegalArgumentException.class,
				() -> MonteCarloDistribution.NORMAL.sample(new Random(1), -1.0));
		assertThrows(IllegalArgumentException.class,
				() -> MonteCarloDistribution.UNIFORM.sample(new Random(1), Double.NaN));
		assertThrows(IllegalArgumentException.class,
				() -> MonteCarloDistribution.LOG_NORMAL.sample(new Random(1), Double.POSITIVE_INFINITY));
	}
}

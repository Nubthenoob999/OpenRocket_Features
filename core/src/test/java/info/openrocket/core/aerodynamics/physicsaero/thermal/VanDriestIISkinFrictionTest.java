package info.openrocket.core.aerodynamics.physicsaero.thermal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Validation adapted from AidanSYu/openrocketsupersonic,
 * {@code supersonic-aero-dev}, with the transformation independently evaluated
 * from Hopkins, NASA TN D-6945 (1972).
 */
class VanDriestIISkinFrictionTest {
	private static final double RECOVERY_FACTOR = 0.88;

	private final VanDriestIITransformation transformation = new VanDriestIITransformation();

	@Test
	void schoenherrMatchesKnownReynoldsPoint() {
		assertEquals(0.00293, transformation.solveSchoenherrCF(10_000_000), 0.0002);
	}

	@ParameterizedTest
	@CsvSource({
			"0.01, 1000000, 288.15",
			"0.8, 10000000, 250",
			"1.0, 10000000, 250",
			"2.0, 10000000, 222",
			"4.0, 25000000, 222",
			"7.0, 50000000, 200"
	})
	void productionMatchesIndependentHopkinsImplementation(double mach, double reynoldsX,
			double edgeTemperatureK) {
		double wallTemperatureK = adiabaticWallTemperature(edgeTemperatureK, mach);
		double expected = independentCf(mach, reynoldsX, edgeTemperatureK, wallTemperatureK);
		assertEquals(expected,
				transformation.cf(mach, reynoldsX, edgeTemperatureK, wallTemperatureK),
				expected * 1.0e-10);
	}

	@Test
	void adiabaticWallSkinFrictionDecreasesWithMach() {
		double reynoldsX = 10_000_000;
		double edgeTemperatureK = 222;
		double previous = transformation.cf(0.5, reynoldsX, edgeTemperatureK,
				adiabaticWallTemperature(edgeTemperatureK, 0.5));
		for (double mach = 1; mach <= 10; mach++) {
			double current = transformation.cf(mach, reynoldsX, edgeTemperatureK,
					adiabaticWallTemperature(edgeTemperatureK, mach));
			assertTrue(current < previous,
					"expected lower skin friction at Mach " + mach);
			previous = current;
		}
	}

	/**
	 * NASA TN D-5089 supplies Re_theta rather than Re_x.  NASA TN D-6945
	 * equations 1-7 give Re_bar_theta = F_theta Re_theta and
	 * Re_bar_x C_F_bar = 2 Re_bar_theta, allowing the experimental comparison
	 * without an empirical Re_theta-to-Re_x guess.
	 */
	@ParameterizedTest
	@CsvFileSource(resources = "/physicsaero/nasa_tn_d5089_flat_plate_cf.csv",
			numLinesToSkip = 1)
	void agreesBroadlyWithNasaD5089(double mach, double reynoldsTheta,
			double totalTemperatureRankine, double wallTemperatureRankine,
			double edgeTemperatureRankine, double wallToAdiabaticTemperature,
			double measuredCfTimesThousand, String trips) {
		double edgeTemperatureK = edgeTemperatureRankine / 1.8;
		double wallTemperatureK = wallTemperatureRankine / 1.8;
		double expected = measuredCfTimesThousand / 1_000;
		double fTheta = transformation.fTheta(edgeTemperatureK, wallTemperatureK);
		double transformedReynoldsTheta = fTheta * reynoldsTheta;
		double transformedAverageCf = Math.pow(
				0.242 / Math.log10(2 * transformedReynoldsTheta), 2);
		double actual = transformation.localFromAverage(transformedAverageCf)
				/ transformation.fc(mach, wallTemperatureK / edgeTemperatureK);

		assertEquals(expected, actual, expected * 0.25,
				"NASA D-5089 check at Mach " + mach + ", Re_theta=" + reynoldsTheta);
	}

	private static double independentCf(double mach, double reynoldsX,
			double edgeTemperatureK, double wallTemperatureK) {
		if (mach <= 1.0e-6) {
			return independentLocalFromAverage(independentSchoenherr(reynoldsX));
		}
		double m = 0.2 * mach * mach;
		double f = wallTemperatureK / edgeTemperatureK;
		double a = Math.sqrt(RECOVERY_FACTOR * m / f);
		double b = (1 + RECOVERY_FACTOR * m - f) / f;
		double discriminant = Math.hypot(2 * a, b);
		double alpha = Math.max(-1, Math.min(1, (2 * a * a - b) / discriminant));
		double beta = Math.max(-1, Math.min(1, b / discriminant));
		double denominator = Math.asin(alpha) + Math.asin(beta);
		double fc = RECOVERY_FACTOR * m / (denominator * denominator);

		double muEdge = independentSutherland(edgeTemperatureK);
		double muWall = independentSutherland(wallTemperatureK);
		double fTheta = muEdge / muWall * Math.sqrt(edgeTemperatureK / wallTemperatureK);
		double transformedReynolds = Math.max(1_000, reynoldsX * fTheta / fc);
		return independentLocalFromAverage(independentSchoenherr(transformedReynolds)) / fc;
	}

	private static double independentSchoenherr(double reynoldsX) {
		double boundedReynolds = Math.max(1_000, reynoldsX);
		double coefficient = 0.455 / Math.pow(Math.log10(boundedReynolds), 2.58);
		for (int iteration = 0; iteration < 50; iteration++) {
			double root = Math.sqrt(coefficient);
			double residual = 0.242 / root - Math.log10(boundedReynolds * coefficient);
			double derivative = -0.121 / (coefficient * root)
					- 1 / (coefficient * Math.log(10));
			double delta = residual / derivative;
			coefficient = Math.max(1.0e-8, coefficient - delta);
			if (Math.abs(delta) < 1.0e-12) {
				break;
			}
		}
		return coefficient;
	}

	private static double independentLocalFromAverage(double averageCf) {
		return 0.242 * averageCf / (0.242 + 0.8686 * Math.sqrt(averageCf));
	}

	private static double independentSutherland(double temperatureK) {
		return 1.716e-5 * Math.pow(temperatureK / 273.15, 1.5)
				* (273.15 + 110.4) / (temperatureK + 110.4);
	}

	private static double adiabaticWallTemperature(double edgeTemperatureK, double mach) {
		return edgeTemperatureK * (1 + RECOVERY_FACTOR * 0.2 * mach * mach);
	}
}

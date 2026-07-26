package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.physicsaero.body.HartTn3393SupersonicBasePressureCorrelation;

class HartTn3393SupersonicBasePressureCorrelationTest {
	private final HartTn3393SupersonicBasePressureCorrelation model =
			new HartTn3393SupersonicBasePressureCorrelation();

	@Test
	void agreesWithHartBridgeAndTn3393FormAtDeclaredAnchors() {
		assertEquals(-0.255,
				model.basePressureCoefficient(1.2, 1.4), 1e-12);
		assertEquals(-0.250,
				model.basePressureCoefficient(1.3, 1.4), 1e-12);
		assertEquals(-(0.064 + 0.186 / (1.5 * 1.5)),
				model.basePressureCoefficient(1.5, 1.4), 1e-12);
		assertEquals(-(0.064 + 0.186 / 9),
				model.basePressureCoefficient(3, 1.4), 1e-12);
	}

	@Test
	void validityDistinguishesBridgeExtrapolationAndDirectSourceRange() {
		assertEquals("HART_TRANSONIC_AND_C1_HANDOFF",
				model.validity(1.3, false).reason());
		assertEquals(
				"TN3393_FORM_EXTRAPOLATED_BELOW_DIRECT_MACH_RANGE",
				model.validity(2, false).reason());
		assertEquals("TN3393_TURBULENT_DIRECT_MACH_RANGE",
				model.validity(3, false).reason());
		assertTrue(!model.validity(2, true).valid());
	}

	@Test
	void turbulentDirectRangeTracksDigitizedTn3393Points()
			throws Exception {
		List<double[]> source = tn3393Points();
		double sumAbsolutePercentError = 0;
		for (double[] point : source) {
			double predicted = -model.basePressureCoefficient(
					point[0], 1.4);
			double percentError = 100 * (predicted - point[1])
					/ point[1];
			assertTrue(Math.abs(percentError) <= 30,
					"TN3393 base-pressure error at M=" + point[0]
							+ " was " + percentError + "%");
			sumAbsolutePercentError += Math.abs(percentError);
		}
		assertTrue(sumAbsolutePercentError / source.size() <= 20,
				"TN3393 turbulent-point MAPE exceeded 20%");
	}

	private static List<double[]> tn3393Points() throws Exception {
		var stream = HartTn3393SupersonicBasePressureCorrelationTest.class
				.getResourceAsStream(
						"/physicsaero/naca_tn3393_turbulent_base_pressure.csv");
		assertTrue(stream != null, "missing TN3393 source fixture");
		List<double[]> points = new ArrayList<>();
		try (var reader = new BufferedReader(new InputStreamReader(stream,
				StandardCharsets.UTF_8))) {
			for (String line; (line = reader.readLine()) != null;) {
				if (line.isBlank() || line.startsWith("#")
						|| line.startsWith("mach,")) continue;
				String[] values = line.split(",");
				assertEquals(3, values.length);
				points.add(new double[] {
						Double.parseDouble(values[0]),
						Double.parseDouble(values[2])});
			}
		}
		assertEquals(4, points.size());
		return List.copyOf(points);
	}
}

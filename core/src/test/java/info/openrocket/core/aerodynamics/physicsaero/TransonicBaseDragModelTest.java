package info.openrocket.core.aerodynamics.physicsaero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.physicsaero.transonic.TransonicBaseDragModel;

class TransonicBaseDragModelTest {
	@Test
	void reproducesHartStingFreeFreeFlightCurve() throws Exception {
		List<double[]> rows = new ArrayList<>();
		try (var stream = getClass().getResourceAsStream(
				"/physicsaero/naca_rm_l52e06_base_drag.csv")) {
			assertTrue(stream != null, "Hart benchmark resource missing");
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(stream, StandardCharsets.UTF_8))) {
				reader.lines()
						.filter(line -> !line.isBlank() && !line.startsWith("#")
								&& !line.startsWith("mach,"))
						.map(line -> line.split(","))
						.map(fields -> new double[] {
								Double.parseDouble(fields[0]),
								Double.parseDouble(fields[1])
						})
						.forEach(rows::add);
			}
		}
		TransonicBaseDragModel model = new TransonicBaseDragModel();
		double absoluteError = 0;
		for (double[] row : rows) {
			double predicted = -model.basePressureCoefficient(row[0], 0);
			absoluteError += Math.abs(predicted - row[1]);
			assertEquals(row[1], predicted, 1e-12);
		}
		assertEquals(0, absoluteError / rows.size(), 1e-12);
	}

	@Test
	void interpolationIsSmoothBoundedAndHasOnlyTheMeasuredPeak() {
		TransonicBaseDragModel model = new TransonicBaseDragModel();
		double prior = -model.basePressureCoefficient(0.7, 0);
		int derivativeSignChanges = 0;
		int priorSign = 1;
		double maximum = prior;
		for (int index = 1; index <= 6000; index++) {
			double mach = 0.7 + 0.6 * index / 6000.0;
			double current = -model.basePressureCoefficient(mach, 0);
			assertTrue(current >= 0.15 - 1e-12 && current <= 0.267 + 1e-12);
			int sign = Math.abs(current - prior) < 1e-12 ? priorSign
					: current > prior ? 1 : -1;
			if (sign != priorSign) derivativeSignChanges++;
			priorSign = sign;
			prior = current;
			maximum = Math.max(maximum, current);
		}
		assertTrue(derivativeSignChanges <= 1);
		assertEquals(0.267, maximum, 2e-6);
	}

	@Test
	void resolvedDisplacementThicknessRemainsAnExplicitBoundedIncrement() {
		TransonicBaseDragModel model = new TransonicBaseDragModel();
		double baseline = model.basePressureCoefficient(1.08, 0);
		double corrected = model.basePressureCoefficient(1.08, 0.05);
		assertEquals(-0.015, corrected - baseline, 1e-12);
		assertTrue(model.basePressureCoefficient(1.08, 1) >= -0.45);
	}

	@Test
	void hartEndpointHasC1BridgeToTn3393SupersonicHandoff() {
		TransonicBaseDragModel model = new TransonicBaseDragModel();
		double handoff = 0.064 + 0.186 / (1.5 * 1.5);
		assertEquals(0.250,
				-model.basePressureCoefficient(1.3, 0), 1e-12);
		assertEquals(handoff,
				-model.basePressureCoefficient(1.5, 0), 1e-12);

		double epsilon = 1e-6;
		double hartRightSlope = (-model.basePressureCoefficient(
				1.3 + epsilon, 0) - 0.250) / epsilon;
		double handoffLeftSlope = (handoff
				+ model.basePressureCoefficient(1.5 - epsilon, 0))
				/ epsilon;
		assertEquals(0, hartRightSlope, 1e-4);
		assertEquals(-2 * 0.186 / Math.pow(1.5, 3),
				handoffLeftSlope, 1e-4);
	}
}

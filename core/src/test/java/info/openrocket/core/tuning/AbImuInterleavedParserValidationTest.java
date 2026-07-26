package info.openrocket.core.tuning;

import info.openrocket.core.util.BaseTestCase;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AbImuInterleavedParserValidationTest extends BaseTestCase {
	@Test
	public void governmentWorkLaunch1AbParserProducesPhysicalRanges() throws Exception {
		Path abPath = Path.of("src", "test", "java", "info", "openrocket", "core", "tuning",
				"Govenmnet_Work_launch_1", "ab_government_work_launch_1.csv");
		Path easyMiniPath = Path.of("src", "test", "java", "info", "openrocket", "core", "tuning",
				"Govenmnet_Work_launch_1", "subscale_easymini.csv");
		if (!abPath.toFile().exists()) {
			abPath = Path.of("core", "src", "test", "java", "info", "openrocket", "core", "tuning",
					"Govenmnet_Work_launch_1", "ab_government_work_launch_1.csv");
			easyMiniPath = Path.of("core", "src", "test", "java", "info", "openrocket", "core", "tuning",
					"Govenmnet_Work_launch_1", "subscale_easymini.csv");
		}
		TuningTestInfrastructure.requireFixtures(abPath, easyMiniPath);

		TelemetrySeries ab = TelemetryParsers.parse(abPath);
		TelemetrySeries easyMini = TelemetryParsers.parse(easyMiniPath);

		assertEquals(TelemetrySchema.AB_IMU_INTERLEAVED, ab.getSchema());

		int checked = 0;
		for (Double accelZ : ab.getAccelerationZMetersPerSec2()) {
			if (accelZ == null || !Double.isFinite(accelZ)) {
				continue;
			}
			assertTrue(accelZ >= -50.0 && accelZ <= 300.0,
					"AB accelZ sample out of expected physical range: " + accelZ);
			checked++;
			if (checked >= 10) {
				break;
			}
		}
		assertTrue(checked >= 10, "Expected at least 10 finite accelZ samples");

		double easyMiniPeakVelocity = peakAbs(easyMini.getVelocityZMetersPerSec());
		double integratedPeakVelocity = peakAbs(integrateVelocityFromAccel(ab));
		double relError = Math.abs(integratedPeakVelocity - easyMiniPeakVelocity) / Math.max(1e-9, easyMiniPeakVelocity);
		assertTrue(relError <= 0.45,
				"Integrated AB velocity peak should stay within 45% of EasyMini; got " + relError);

		double cdProxy = DerivedTelemetryQuantities.summarize(ab).getCdProxyMean();
		assertTrue(cdProxy >= 0.05 && cdProxy <= 1.5,
				"AB_IMU_INTERLEAVED cdProxyMean expected in [0.05, 1.5], got " + cdProxy);
	}

	private static List<Double> integrateVelocityFromAccel(TelemetrySeries series) {
		List<Double> time = series.getTimeSec();
		List<Double> accel = series.getAccelerationZMetersPerSec2();
		double bias = prelaunchAccelBias(time, accel);
		double v = 0.0;
		java.util.ArrayList<Double> integrated = new java.util.ArrayList<>(time.size());
		integrated.add(v);
		for (int i = 1; i < time.size() && i < accel.size(); i++) {
			Double t0 = time.get(i - 1);
			Double t1 = time.get(i);
			Double a0 = accel.get(i - 1);
			Double a1 = accel.get(i);
			if (t0 == null || t1 == null || a0 == null || a1 == null
					|| !Double.isFinite(t0) || !Double.isFinite(t1)
					|| !Double.isFinite(a0) || !Double.isFinite(a1)) {
				integrated.add(v);
				continue;
			}
			double dt = t1 - t0;
			if (dt <= 0.0 || dt > 1.0) {
				integrated.add(v);
				continue;
			}
			double correctedA0 = a0 - bias;
			double correctedA1 = a1 - bias;
			v += 0.5 * (correctedA0 + correctedA1) * dt;
			integrated.add(v);
		}
		return integrated;
	}

	private static double prelaunchAccelBias(List<Double> time, List<Double> accel) {
		double sum = 0.0;
		int n = 0;
		for (int i = 0; i < time.size() && i < accel.size(); i++) {
			Double t = time.get(i);
			Double a = accel.get(i);
			if (t == null || a == null || !Double.isFinite(t) || !Double.isFinite(a) || t > 1.0) {
				continue;
			}
			sum += a;
			n++;
		}
		return n == 0 ? 0.0 : sum / n;
	}

	private static double peakAbs(List<Double> values) {
		double peak = 0.0;
		for (Double value : values) {
			if (value == null || !Double.isFinite(value)) {
				continue;
			}
			peak = Math.max(peak, Math.abs(value));
		}
		return peak;
	}
}

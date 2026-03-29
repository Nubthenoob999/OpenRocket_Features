package info.openrocket.core.tuning;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TelemetrySeries {
	private static final int SUSTAINED_SAMPLES = 3;
	private static final double ALTITUDE_LAUNCH_DELTA_M = 1.0;
	private static final double VELOCITY_LAUNCH_THRESHOLD_MPS = 5.0;
	private static final double ACCEL_LAUNCH_THRESHOLD_MPS2 = 15.0;

	private final TelemetrySchema schema;
	private final List<Double> timeSec = new ArrayList<>();
	private final List<Double> altitudeMetersAgl = new ArrayList<>();
	private final List<Double> velocityZMetersPerSec = new ArrayList<>();
	private final List<Double> accelerationXMetersPerSec2 = new ArrayList<>();
	private final List<Double> accelerationYMetersPerSec2 = new ArrayList<>();
	private final List<Double> accelerationZMetersPerSec2 = new ArrayList<>();
	private final List<Double> pressurePa = new ArrayList<>();
	private final List<Double> temperatureC = new ArrayList<>();
	private TelemetryParserDiagnostics parserDiagnostics = TelemetryParserDiagnostics.EMPTY;

	public TelemetrySeries(TelemetrySchema schema) {
		this.schema = schema;
	}

	public TelemetrySchema getSchema() {
		return schema;
	}

	public void addPoint(Double time,
						 Double altitude,
						 Double velocityZ,
						 Double accelX,
						 Double accelY,
						 Double accelZ,
						 Double pressure,
						 Double temperature) {
		timeSec.add(time);
		altitudeMetersAgl.add(altitude);
		velocityZMetersPerSec.add(velocityZ);
		accelerationXMetersPerSec2.add(accelX);
		accelerationYMetersPerSec2.add(accelY);
		accelerationZMetersPerSec2.add(accelZ);
		pressurePa.add(pressure);
		temperatureC.add(temperature);
	}

	public int size() {
		return timeSec.size();
	}

	public List<Double> getTimeSec() {
		return Collections.unmodifiableList(timeSec);
	}

	public List<Double> getAltitudeMetersAgl() {
		return Collections.unmodifiableList(altitudeMetersAgl);
	}

	public List<Double> getVelocityZMetersPerSec() {
		return Collections.unmodifiableList(velocityZMetersPerSec);
	}

	public List<Double> getAccelerationXMetersPerSec2() {
		return Collections.unmodifiableList(accelerationXMetersPerSec2);
	}

	public List<Double> getAccelerationYMetersPerSec2() {
		return Collections.unmodifiableList(accelerationYMetersPerSec2);
	}

	public List<Double> getAccelerationZMetersPerSec2() {
		return Collections.unmodifiableList(accelerationZMetersPerSec2);
	}

	public List<Double> getPressurePa() {
		return Collections.unmodifiableList(pressurePa);
	}

	public List<Double> getTemperatureC() {
		return Collections.unmodifiableList(temperatureC);
	}

	public TelemetryParserDiagnostics getParserDiagnostics() {
		return parserDiagnostics;
	}

	void setParserDiagnostics(TelemetryParserDiagnostics parserDiagnostics) {
		this.parserDiagnostics = parserDiagnostics == null ? TelemetryParserDiagnostics.EMPTY : parserDiagnostics;
	}

	public boolean hasAltitude() {
		return anyNonNull(altitudeMetersAgl);
	}

	public boolean hasVelocityZ() {
		return anyNonNull(velocityZMetersPerSec);
	}

	public boolean hasAccelerationXYZ() {
		return anyNonNull(accelerationXMetersPerSec2)
				&& anyNonNull(accelerationYMetersPerSec2)
				&& anyNonNull(accelerationZMetersPerSec2);
	}

	public boolean hasPressure() {
		return anyNonNull(pressurePa);
	}

	public boolean hasTemperature() {
		return anyNonNull(temperatureC);
	}

	public int indexBeforeFirstAltitudeChange() {
		Double first = firstNonNull(altitudeMetersAgl);
		if (first == null) {
			return 0;
		}
		for (int i = 1; i < altitudeMetersAgl.size(); i++) {
			Double value = altitudeMetersAgl.get(i);
			if (value == null) {
				continue;
			}
			if (Math.abs(value - first) > 1e-9) {
				return i - 1;
			}
		}
		return 0;
	}

	public int estimateLaunchAnchorIndex() {
		int altitudeAnchor = firstSustainedAltitudeChangeIndex(ALTITUDE_LAUNCH_DELTA_M, SUSTAINED_SAMPLES);
		int velocityAnchor = firstSustainedAbove(velocityZMetersPerSec, VELOCITY_LAUNCH_THRESHOLD_MPS, SUSTAINED_SAMPLES, true);
		int accelAnchor = firstSustainedAbove(accelerationZMetersPerSec2, ACCEL_LAUNCH_THRESHOLD_MPS2, SUSTAINED_SAMPLES, true);

		int earliest = minPositive(altitudeAnchor, velocityAnchor, accelAnchor);
		if (earliest < 0) {
			return 0;
		}
		return Math.max(0, earliest - 1);
	}

	private int firstSustainedAltitudeChangeIndex(double deltaMeters, int requiredSamples) {
		Double baseline = firstNonNull(altitudeMetersAgl);
		if (baseline == null) {
			return -1;
		}
		int streak = 0;
		for (int i = 0; i < altitudeMetersAgl.size(); i++) {
			Double altitude = altitudeMetersAgl.get(i);
			if (altitude == null) {
				streak = 0;
				continue;
			}
			if (Math.abs(altitude - baseline) >= deltaMeters) {
				streak++;
				if (streak >= requiredSamples) {
					return i - requiredSamples + 1;
				}
			} else {
				streak = 0;
			}
		}
		return -1;
	}

	private static int firstSustainedAbove(List<Double> values,
														   double threshold,
														   int requiredSamples,
														   boolean absolute) {
		if (values == null || values.isEmpty()) {
			return -1;
		}

		int streak = 0;
		for (int i = 0; i < values.size(); i++) {
			Double value = values.get(i);
			if (value == null || !Double.isFinite(value)) {
				streak = 0;
				continue;
			}

			double probe = absolute ? Math.abs(value) : value;
			if (probe >= threshold) {
				streak++;
				if (streak >= requiredSamples) {
					return i - requiredSamples + 1;
				}
			} else {
				streak = 0;
			}
		}

		return -1;
	}

	private static int minPositive(int... values) {
		int min = Integer.MAX_VALUE;
		for (int value : values) {
			if (value >= 0 && value < min) {
				min = value;
			}
		}
		return min == Integer.MAX_VALUE ? -1 : min;
	}

	private static boolean anyNonNull(List<Double> values) {
		for (Double value : values) {
			if (value != null) {
				return true;
			}
		}
		return false;
	}

	private static Double firstNonNull(List<Double> values) {
		for (Double value : values) {
			if (value != null) {
				return value;
			}
		}
		return null;
	}
}

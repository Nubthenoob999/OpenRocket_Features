package info.openrocket.core.tuning;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TelemetrySeries {
	private static final int SUSTAINED_SAMPLES = 3;
	private static final double ALTITUDE_LAUNCH_DELTA_M = 1.0;
	private static final double VELOCITY_LAUNCH_THRESHOLD_MPS = 5.0;
	private static final double ACCEL_LAUNCH_THRESHOLD_MPS2 = 15.0;
	private static final double DEPLOYMENT_DESCENT_VELOCITY_MPS = 2.0;
	private static final double DEPLOYMENT_ACCEL_THRESHOLD_MPS2 = 5.0;
	private static final double DEPLOYMENT_VELOCITY_RECOVERY_RATIO = 0.85;

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

	public int estimateApogeeIndex() {
		int bestIndex = -1;
		double maxAltitude = Double.NEGATIVE_INFINITY;
		for (int i = 0; i < altitudeMetersAgl.size(); i++) {
			Double altitude = altitudeMetersAgl.get(i);
			if (altitude == null || !Double.isFinite(altitude)) {
				continue;
			}
			if (altitude > maxAltitude) {
				maxAltitude = altitude;
				bestIndex = i;
			}
		}
		return bestIndex;
	}

	public int estimateBurnoutIndex() {
		int launchIndex = estimateLaunchAnchorIndex();
		int apogeeIndex = estimateApogeeIndex();
		int start = Math.max(launchIndex, 0);
		int end = apogeeIndex >= start ? apogeeIndex : velocityZMetersPerSec.size() - 1;
		int bestIndex = -1;
		double maxVelocity = Double.NEGATIVE_INFINITY;
		for (int i = start; i <= end && i < velocityZMetersPerSec.size(); i++) {
			Double velocity = velocityZMetersPerSec.get(i);
			if (velocity == null || !Double.isFinite(velocity)) {
				continue;
			}
			if (velocity > maxVelocity) {
				maxVelocity = velocity;
				bestIndex = i;
			}
		}
		return bestIndex;
	}

	public int estimateDeploymentIndex() {
		int apogeeIndex = estimateApogeeIndex();
		if (apogeeIndex < 0 || apogeeIndex + 1 >= size()) {
			return -1;
		}

		int accelCandidate = firstSustainedDeploymentAccelerationIndex(apogeeIndex + 1);
		if (accelCandidate >= 0) {
			return accelCandidate;
		}

		int peakDescentIndex = -1;
		double peakDescentRate = 0.0;
		for (int i = apogeeIndex + 1; i < velocityZMetersPerSec.size(); i++) {
			Double velocity = velocityZMetersPerSec.get(i);
			if (velocity == null || !Double.isFinite(velocity) || velocity >= -DEPLOYMENT_DESCENT_VELOCITY_MPS) {
				continue;
			}
			double descentRate = Math.abs(velocity);
			if (descentRate > peakDescentRate) {
				peakDescentRate = descentRate;
				peakDescentIndex = i;
			}
		}
		if (peakDescentIndex < 0 || peakDescentRate <= 0.0) {
			return -1;
		}

		int streak = 0;
		for (int i = peakDescentIndex + 1; i < velocityZMetersPerSec.size(); i++) {
			Double velocity = velocityZMetersPerSec.get(i);
			if (velocity == null || !Double.isFinite(velocity)) {
				streak = 0;
				continue;
			}
			if (Math.abs(velocity) <= peakDescentRate * DEPLOYMENT_VELOCITY_RECOVERY_RATIO) {
				streak++;
				if (streak >= SUSTAINED_SAMPLES) {
					return i - SUSTAINED_SAMPLES + 1;
				}
			} else {
				streak = 0;
			}
		}
		return -1;
	}

	public EventMarkers estimateEventMarkers() {
		int launchIndex = estimateLaunchAnchorIndex();
		int burnoutIndex = estimateBurnoutIndex();
		int apogeeIndex = estimateApogeeIndex();
		int deploymentIndex = estimateDeploymentIndex();
		return new EventMarkers(
				launchIndex,
				timeAtIndex(launchIndex),
				burnoutIndex,
				timeAtIndex(burnoutIndex),
				apogeeIndex,
				timeAtIndex(apogeeIndex),
				deploymentIndex,
				timeAtIndex(deploymentIndex));
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

	private int firstSustainedDeploymentAccelerationIndex(int startIndex) {
		int streak = 0;
		for (int i = Math.max(0, startIndex); i < size(); i++) {
			Double velocity = valueAt(velocityZMetersPerSec, i);
			Double acceleration = valueAt(accelerationZMetersPerSec2, i);
			if (velocity == null || acceleration == null
					|| !Double.isFinite(velocity) || !Double.isFinite(acceleration)
					|| velocity > -DEPLOYMENT_DESCENT_VELOCITY_MPS
					|| acceleration < DEPLOYMENT_ACCEL_THRESHOLD_MPS2) {
				streak = 0;
				continue;
			}
			streak++;
			if (streak >= SUSTAINED_SAMPLES) {
				return i - SUSTAINED_SAMPLES + 1;
			}
		}
		return -1;
	}

	private Double timeAtIndex(int index) {
		return valueAt(timeSec, index);
	}

	private static Double valueAt(List<Double> values, int index) {
		if (values == null || index < 0 || index >= values.size()) {
			return null;
		}
		return values.get(index);
	}

	public record EventMarkers(int launchIndex,
							   Double launchTimeSec,
							   int burnoutIndex,
							   Double burnoutTimeSec,
							   int apogeeIndex,
							   Double apogeeTimeSec,
							   int deploymentIndex,
							   Double deploymentTimeSec) {
	}
}

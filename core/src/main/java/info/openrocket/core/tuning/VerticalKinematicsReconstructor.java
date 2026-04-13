package info.openrocket.core.tuning;

import java.util.ArrayList;
import java.util.List;

public final class VerticalKinematicsReconstructor {
	private static final double BAROMETRIC_EXPONENT = 0.190263;
	private static final double BAROMETRIC_SCALE_HEIGHT_M = 44330.0;
	private static final double BOOST_ALTITUDE_CORRECTION_GAIN = 0.12;
	private static final double BOOST_VELOCITY_CORRECTION_GAIN = 0.03;
	private static final double COAST_ALTITUDE_CORRECTION_GAIN = 0.18;
	private static final double COAST_VELOCITY_CORRECTION_GAIN = 0.04;
	private static final double DESCENT_ALTITUDE_CORRECTION_GAIN = 0.30;
	private static final double DESCENT_VELOCITY_CORRECTION_GAIN = 0.08;
	private static final int MIN_PRELAUNCH_BIAS_SAMPLES = 3;

	private VerticalKinematicsReconstructor() {
	}

	public static ReconstructionResult reconstruct(TelemetrySeries rawSeries) {
		List<Double> time = rawSeries.getTimeSec();
		List<Double> rawAltitude = rawSeries.getAltitudeMetersAgl();
		List<Double> rawVelocity = rawSeries.getVelocityZMetersPerSec();
		List<Double> rawAccel = rawSeries.getAccelerationZMetersPerSec2();
		List<Double> pressure = rawSeries.getPressurePa();
		TelemetrySeries.EventMarkers markers = rawSeries.estimateEventMarkers();
		int preLaunchEndExclusive = estimatePreLaunchEndExclusive(rawSeries, markers);
		BiasEstimate accelBias = estimateAccelerationBias(rawAccel, preLaunchEndExclusive);
		List<Double> biasCorrectedAccel = applyBiasCorrection(rawAccel, accelBias.value());
		List<Double> pressureAltitude = derivePressureAltitude(pressure, preLaunchEndExclusive);
		if (!hasFinite(pressureAltitude) || time.isEmpty()) {
			return new ReconstructionResult(rawSeries, rawSeries, VerticalIntegratorDiagnostics.EMPTY);
		}

		List<Double> correctedAltitude = new ArrayList<>(time.size());
		List<Double> correctedVelocity = new ArrayList<>(time.size());
		double driftSum = 0.0;
		double correctionSum = 0.0;
		int driftCount = 0;
		int correctionCount = 0;

		double previousAltitude = initialAltitude(rawAltitude, pressureAltitude, preLaunchEndExclusive);
		double previousVelocity = 0.0;
		correctedAltitude.add(previousAltitude);
		correctedVelocity.add(previousVelocity);

		for (int i = 1; i < time.size(); i++) {
			Double currentTime = time.get(i);
			Double previousTime = time.get(i - 1);
			double dt = validDelta(currentTime, previousTime);
			double accel = finiteOr(valueAt(biasCorrectedAccel, i), 0.0);
			if (i <= markers.launchIndex()) {
				Double anchorAltitude = preferredAltitudeAnchor(rawAltitude, pressureAltitude, i);
				double correctedAlt = anchorAltitude != null && Double.isFinite(anchorAltitude)
						? anchorAltitude
						: previousAltitude;
				correctedAltitude.add(correctedAlt);
				correctedVelocity.add(0.0);
				previousAltitude = correctedAlt;
				previousVelocity = 0.0;
				continue;
			}
			double predictedVelocity = previousVelocity + accel * dt;
			Double rawVelocityValue = valueAt(rawVelocity, i);
			if (rawVelocityValue != null && Double.isFinite(rawVelocityValue)) {
				predictedVelocity = 0.70 * predictedVelocity + 0.30 * rawVelocityValue;
			}
			double predictedAltitude = previousAltitude + previousVelocity * dt + 0.5 * accel * dt * dt;
			Double anchorAltitude = preferredAltitudeAnchor(rawAltitude, pressureAltitude, i);
			Double rawAltitudeValue = valueAt(rawAltitude, i);
			double correctedAlt = predictedAltitude;
			double correctedVel = predictedVelocity;
			if (anchorAltitude != null && Double.isFinite(anchorAltitude)) {
				double error = anchorAltitude - predictedAltitude;
				GainProfile gains = gainsForIndex(i, markers);
				correctedAlt = predictedAltitude + gains.altitudeGain() * error;
				correctedVel = predictedVelocity + gains.velocityGain() * error / Math.max(dt, 1e-6);
				correctionSum += Math.abs(correctedAlt - predictedAltitude);
				correctionCount++;
			}
			if (rawAltitudeValue != null && Double.isFinite(rawAltitudeValue) && anchorAltitude != null && Double.isFinite(anchorAltitude)) {
				driftSum += Math.abs(rawAltitudeValue - anchorAltitude);
				driftCount++;
			}
			correctedAltitude.add(correctedAlt);
			correctedVelocity.add(correctedVel);
			previousAltitude = correctedAlt;
			previousVelocity = correctedVel;
		}

		TelemetrySeries correctedSeries = new TelemetrySeries(rawSeries.getSchema());
		for (int i = 0; i < rawSeries.size(); i++) {
			correctedSeries.addPoint(
					valueAt(time, i),
					valueAt(correctedAltitude, i),
					valueAt(correctedVelocity, i),
					valueAt(rawSeries.getAccelerationXMetersPerSec2(), i),
					valueAt(rawSeries.getAccelerationYMetersPerSec2(), i),
					valueAt(biasCorrectedAccel, i),
					valueAt(pressure, i),
					valueAt(rawSeries.getTemperatureC(), i));
		}
		correctedSeries.setParserDiagnostics(rawSeries.getParserDiagnostics());

		double rawDriftMeanAbs = driftCount <= 0 ? Double.NaN : driftSum / driftCount;
		double correctionMeanAbs = correctionCount <= 0 ? Double.NaN : correctionSum / correctionCount;
		VerticalIntegratorDiagnostics diagnostics = new VerticalIntegratorDiagnostics(
				rawDriftMeanAbs,
				correctionMeanAbs,
				Double.NaN,
				Double.NaN,
				Double.NaN,
				Double.NaN,
				accelBias.value(),
				accelBias.sampleCount(),
				finiteOrNaN(markers.launchTimeSec()),
				finiteOrNaN(markers.burnoutTimeSec()),
				finiteOrNaN(markers.apogeeTimeSec()),
				finiteOrNaN(markers.deploymentTimeSec()));
		return new ReconstructionResult(rawSeries, correctedSeries, diagnostics);
	}

	private static List<Double> derivePressureAltitude(List<Double> pressure, int preLaunchEndExclusive) {
		ArrayList<Double> altitude = new ArrayList<>(pressure.size());
		double baselinePressure = averageFinitePressure(pressure, preLaunchEndExclusive);
		if (!Double.isFinite(baselinePressure)) {
			for (int i = 0; i < pressure.size(); i++) {
				altitude.add(null);
			}
			return altitude;
		}
		for (Double value : pressure) {
			if (value == null || !Double.isFinite(value) || value <= 0.0) {
				altitude.add(null);
				continue;
			}
			double ratio = value / baselinePressure;
			double altitudeMeters = BAROMETRIC_SCALE_HEIGHT_M * (1.0 - Math.pow(ratio, BAROMETRIC_EXPONENT));
			altitude.add(altitudeMeters);
		}
		return altitude;
	}

	private static boolean hasFinite(List<Double> values) {
		for (Double value : values) {
			if (value != null && Double.isFinite(value)) {
				return true;
			}
		}
		return false;
	}

	private static double initialAltitude(List<Double> rawAltitude, List<Double> pressureAltitude, int preLaunchEndExclusive) {
		int seedIndex = Math.max(0, Math.min(Math.max(preLaunchEndExclusive - 1, 0), Math.max(rawAltitude.size(), pressureAltitude.size()) - 1));
		Double raw = valueAt(rawAltitude, seedIndex);
		if (raw != null && Double.isFinite(raw)) {
			return raw;
		}
		Double pressure = valueAt(pressureAltitude, seedIndex);
		return pressure != null && Double.isFinite(pressure) ? pressure : 0.0;
	}

	private static double validDelta(Double currentTime, Double previousTime) {
		if (currentTime == null || previousTime == null
				|| !Double.isFinite(currentTime) || !Double.isFinite(previousTime)) {
			return 0.05;
		}
		double dt = currentTime - previousTime;
		if (!Double.isFinite(dt) || dt <= 0.0) {
			return 0.05;
		}
		return dt;
	}

	private static double firstFinite(List<Double> values, double fallback) {
		for (Double value : values) {
			if (value != null && Double.isFinite(value)) {
				return value;
			}
		}
		return fallback;
	}

	private static double finiteOr(Double value, double fallback) {
		return value != null && Double.isFinite(value) ? value : fallback;
	}

	private static Double valueAt(List<Double> values, int index) {
		if (values == null || index < 0 || index >= values.size()) {
			return null;
		}
		return values.get(index);
	}

	private static int estimatePreLaunchEndExclusive(TelemetrySeries rawSeries, TelemetrySeries.EventMarkers markers) {
		if (markers.launchIndex() > 0) {
			return markers.launchIndex();
		}
		int inferred = rawSeries.indexBeforeFirstAltitudeChange();
		return Math.max(1, Math.min(rawSeries.size(), inferred + 1));
	}

	private static BiasEstimate estimateAccelerationBias(List<Double> rawAccel, int preLaunchEndExclusive) {
		if (rawAccel == null || rawAccel.isEmpty() || preLaunchEndExclusive <= 0) {
			return new BiasEstimate(0.0, 0);
		}
		List<Double> finite = new ArrayList<>();
		for (int i = 0; i < Math.min(preLaunchEndExclusive, rawAccel.size()); i++) {
			Double value = rawAccel.get(i);
			if (value != null && Double.isFinite(value)) {
				finite.add(value);
			}
		}
		if (finite.size() < MIN_PRELAUNCH_BIAS_SAMPLES) {
			return new BiasEstimate(0.0, finite.size());
		}
		finite.sort(Double::compareTo);
		double median = finite.get(finite.size() / 2);
		if ((finite.size() & 1) == 0) {
			median = 0.5 * (finite.get(finite.size() / 2 - 1) + finite.get(finite.size() / 2));
		}
		return new BiasEstimate(median, finite.size());
	}

	private static List<Double> applyBiasCorrection(List<Double> rawAccel, double biasValue) {
		List<Double> corrected = new ArrayList<>(rawAccel.size());
		for (Double value : rawAccel) {
			if (value == null || !Double.isFinite(value)) {
				corrected.add(value);
			} else {
				corrected.add(value - biasValue);
			}
		}
		return corrected;
	}

	private static double averageFinitePressure(List<Double> pressure, int preLaunchEndExclusive) {
		double sum = 0.0;
		int n = 0;
		int limit = Math.min(preLaunchEndExclusive > 0 ? preLaunchEndExclusive : pressure.size(), pressure.size());
		for (int i = 0; i < limit; i++) {
			Double value = pressure.get(i);
			if (value == null || !Double.isFinite(value) || value <= 0.0) {
				continue;
			}
			sum += value;
			n++;
		}
		if (n > 0) {
			return sum / n;
		}
		for (Double value : pressure) {
			if (value != null && Double.isFinite(value) && value > 0.0) {
				return value;
			}
		}
		return Double.NaN;
	}

	private static Double preferredAltitudeAnchor(List<Double> rawAltitude, List<Double> pressureAltitude, int index) {
		Double anchorAltitude = valueAt(pressureAltitude, index);
		Double rawAltitudeValue = valueAt(rawAltitude, index);
		if ((anchorAltitude == null || !Double.isFinite(anchorAltitude))
				&& rawAltitudeValue != null && Double.isFinite(rawAltitudeValue)) {
			return rawAltitudeValue;
		}
		return anchorAltitude;
	}

	private static GainProfile gainsForIndex(int index, TelemetrySeries.EventMarkers markers) {
		int burnoutIndex = markers.burnoutIndex();
		int apogeeIndex = markers.apogeeIndex();
		int deploymentIndex = markers.deploymentIndex();
		if (deploymentIndex >= 0 && index >= deploymentIndex) {
			return new GainProfile(DESCENT_ALTITUDE_CORRECTION_GAIN, DESCENT_VELOCITY_CORRECTION_GAIN);
		}
		if (apogeeIndex >= 0 && index >= apogeeIndex) {
			return new GainProfile(DESCENT_ALTITUDE_CORRECTION_GAIN, DESCENT_VELOCITY_CORRECTION_GAIN);
		}
		if (burnoutIndex >= 0 && index <= burnoutIndex) {
			return new GainProfile(BOOST_ALTITUDE_CORRECTION_GAIN, BOOST_VELOCITY_CORRECTION_GAIN);
		}
		return new GainProfile(COAST_ALTITUDE_CORRECTION_GAIN, COAST_VELOCITY_CORRECTION_GAIN);
	}

	private static double finiteOrNaN(Double value) {
		return value != null && Double.isFinite(value) ? value : Double.NaN;
	}

	public static final class ReconstructionResult {
		private final TelemetrySeries rawSeries;
		private final TelemetrySeries correctedSeries;
		private final VerticalIntegratorDiagnostics diagnostics;

		public ReconstructionResult(TelemetrySeries rawSeries,
									TelemetrySeries correctedSeries,
									VerticalIntegratorDiagnostics diagnostics) {
			this.rawSeries = rawSeries;
			this.correctedSeries = correctedSeries;
			this.diagnostics = diagnostics == null ? VerticalIntegratorDiagnostics.EMPTY : diagnostics;
		}

		public TelemetrySeries getRawSeries() {
			return rawSeries;
		}

		public TelemetrySeries getCorrectedSeries() {
			return correctedSeries;
		}

		public VerticalIntegratorDiagnostics getDiagnostics() {
			return diagnostics;
		}
	}

	private record BiasEstimate(double value, int sampleCount) {
	}

	private record GainProfile(double altitudeGain, double velocityGain) {
	}
}

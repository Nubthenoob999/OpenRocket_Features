package info.openrocket.core.tuning;

import java.util.List;

public final class DerivedTelemetryQuantities {
	private static final double EPSILON = 1e-9;

	private DerivedTelemetryQuantities() {
	}

	public static Quantities summarize(TelemetrySeries series) {
		List<Double> velocity = series.getVelocityZMetersPerSec();
		List<Double> accel = series.getAccelerationZMetersPerSec2();
		List<Double> altitude = series.getAltitudeMetersAgl();
		List<Double> time = series.getTimeSec();
		TelemetrySeries.EventMarkers markers = series.estimateEventMarkers();

		double velocityMean = mean(velocity);
		double velocityAbsPeak = peakAbs(velocity);
		double accelMean = mean(accel);
		double accelAbsPeak = peakAbs(accel);
		double cdProxyMean = cdProxyMean(velocity, accel);
		double apogeeAltitudeMeters = apogeeAltitude(altitude);
		double apogeeTimeSec = apogeeTime(time, altitude);

		return new Quantities(
				velocityMean,
				velocityAbsPeak,
				accelMean,
				accelAbsPeak,
				cdProxyMean,
				apogeeAltitudeMeters,
				apogeeTimeSec,
				finiteOrNaN(markers.launchTimeSec()),
				finiteOrNaN(markers.burnoutTimeSec()),
				finiteOrNaN(markers.deploymentTimeSec()));
	}

	private static double mean(List<Double> values) {
		double sum = 0.0;
		int n = 0;
		for (Double v : values) {
			if (v == null || !Double.isFinite(v)) {
				continue;
			}
			sum += v;
			n++;
		}
		return n == 0 ? Double.NaN : sum / n;
	}

	private static double peakAbs(List<Double> values) {
		double peak = Double.NEGATIVE_INFINITY;
		for (Double v : values) {
			if (v == null || !Double.isFinite(v)) {
				continue;
			}
			peak = Math.max(peak, Math.abs(v));
		}
		return peak == Double.NEGATIVE_INFINITY ? Double.NaN : peak;
	}

	private static double cdProxyMean(List<Double> velocity, List<Double> accel) {
		List<Double> proxySeries = cdProxySeries(velocity, accel);
		double sum = 0.0;
		int n = 0;
		for (Double proxy : proxySeries) {
			if (proxy == null || !Double.isFinite(proxy)) {
				continue;
			}
			sum += proxy;
			n++;
		}
		return n == 0 ? Double.NaN : sum / n;
	}

	public static List<Double> cdProxySeries(List<Double> velocity, List<Double> accel) {
		java.util.ArrayList<Double> proxies = new java.util.ArrayList<>(Math.min(velocity.size(), accel.size()));
		for (int i = 0; i < velocity.size() && i < accel.size(); i++) {
			Double v = velocity.get(i);
			Double a = accel.get(i);
			if (v == null || a == null || !Double.isFinite(v) || !Double.isFinite(a)) {
				proxies.add(null);
				continue;
			}
			if (Math.abs(v) < 1.0) {
				proxies.add(null);
				continue;
			}

			// Cd proxy from drag-like acceleration scaling a ~ v^2 (unitless proxy for tuning guidance).
			double proxy = Math.abs(a) / Math.max(v * v, EPSILON);
			proxies.add(proxy);
		}
		return proxies;
	}

	private static double apogeeAltitude(List<Double> altitude) {
		double maxAltitude = Double.NEGATIVE_INFINITY;
		for (Double value : altitude) {
			if (value == null || !Double.isFinite(value)) {
				continue;
			}
			if (value > maxAltitude) {
				maxAltitude = value;
			}
		}
		return maxAltitude == Double.NEGATIVE_INFINITY ? Double.NaN : maxAltitude;
	}

	private static double apogeeTime(List<Double> time, List<Double> altitude) {
		double maxAltitude = Double.NEGATIVE_INFINITY;
		double maxAltitudeTime = Double.NaN;
		for (int i = 0; i < time.size() && i < altitude.size(); i++) {
			Double t = time.get(i);
			Double a = altitude.get(i);
			if (t == null || a == null || !Double.isFinite(t) || !Double.isFinite(a)) {
				continue;
			}
			if (a > maxAltitude) {
				maxAltitude = a;
				maxAltitudeTime = t;
			}
		}
		return maxAltitude == Double.NEGATIVE_INFINITY ? Double.NaN : maxAltitudeTime;
	}

	private static double finiteOrNaN(Double value) {
		return value != null && Double.isFinite(value) ? value : Double.NaN;
	}

	public static final class Quantities {
		private final double velocityMean;
		private final double velocityAbsPeak;
		private final double accelMean;
		private final double accelAbsPeak;
		private final double cdProxyMean;
		private final double apogeeAltitudeMeters;
		private final double apogeeTimeSec;
		private final double launchTimeSec;
		private final double burnoutTimeSec;
		private final double deploymentTimeSec;

		public Quantities(double velocityMean,
							 double velocityAbsPeak,
							 double accelMean,
							 double accelAbsPeak,
							 double cdProxyMean) {
			this(velocityMean, velocityAbsPeak, accelMean, accelAbsPeak, cdProxyMean,
					Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
		}

		public Quantities(double velocityMean,
							 double velocityAbsPeak,
							 double accelMean,
							 double accelAbsPeak,
							 double cdProxyMean,
							 double apogeeAltitudeMeters,
							 double apogeeTimeSec) {
			this(velocityMean, velocityAbsPeak, accelMean, accelAbsPeak, cdProxyMean,
					apogeeAltitudeMeters, apogeeTimeSec, Double.NaN, Double.NaN, Double.NaN);
		}

		public Quantities(double velocityMean,
							 double velocityAbsPeak,
							 double accelMean,
							 double accelAbsPeak,
							 double cdProxyMean,
							 double apogeeAltitudeMeters,
							 double apogeeTimeSec,
							 double launchTimeSec,
							 double burnoutTimeSec,
							 double deploymentTimeSec) {
			this.velocityMean = velocityMean;
			this.velocityAbsPeak = velocityAbsPeak;
			this.accelMean = accelMean;
			this.accelAbsPeak = accelAbsPeak;
			this.cdProxyMean = cdProxyMean;
			this.apogeeAltitudeMeters = apogeeAltitudeMeters;
			this.apogeeTimeSec = apogeeTimeSec;
			this.launchTimeSec = launchTimeSec;
			this.burnoutTimeSec = burnoutTimeSec;
			this.deploymentTimeSec = deploymentTimeSec;
		}

		public double getVelocityMean() {
			return velocityMean;
		}

		public double getVelocityAbsPeak() {
			return velocityAbsPeak;
		}

		public double getAccelMean() {
			return accelMean;
		}

		public double getAccelAbsPeak() {
			return accelAbsPeak;
		}

		public double getCdProxyMean() {
			return cdProxyMean;
		}

		public double getApogeeAltitudeMeters() {
			return apogeeAltitudeMeters;
		}

		public double getApogeeTimeSec() {
			return apogeeTimeSec;
		}

		public double getLaunchTimeSec() {
			return launchTimeSec;
		}

		public double getBurnoutTimeSec() {
			return burnoutTimeSec;
		}

		public double getDeploymentTimeSec() {
			return deploymentTimeSec;
		}
	}
}

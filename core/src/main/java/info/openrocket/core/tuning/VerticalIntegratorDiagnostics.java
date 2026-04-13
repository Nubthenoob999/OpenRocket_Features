package info.openrocket.core.tuning;

public final class VerticalIntegratorDiagnostics {
	public static final VerticalIntegratorDiagnostics EMPTY = new VerticalIntegratorDiagnostics(
			Double.NaN,
			Double.NaN,
			Double.NaN,
			Double.NaN,
			Double.NaN,
			Double.NaN,
			Double.NaN,
			0,
			Double.NaN,
			Double.NaN,
			Double.NaN,
			Double.NaN);

	private final double rawDriftMeanAbsMeters;
	private final double pressureAnchorCorrectionMeanAbsMeters;
	private final double apogeeTimeErrorBeforeSec;
	private final double apogeeTimeErrorAfterSec;
	private final double altitudeRmseBeforeMeters;
	private final double altitudeRmseAfterMeters;
	private final double accelBiasEstimateMetersPerSec2;
	private final int preLaunchSampleCount;
	private final double launchDetectedTimeSec;
	private final double burnoutDetectedTimeSec;
	private final double apogeeDetectedTimeSec;
	private final double deploymentDetectedTimeSec;

	public VerticalIntegratorDiagnostics(double rawDriftMeanAbsMeters,
										 double pressureAnchorCorrectionMeanAbsMeters,
										 double apogeeTimeErrorBeforeSec,
										 double apogeeTimeErrorAfterSec,
										 double altitudeRmseBeforeMeters,
										 double altitudeRmseAfterMeters) {
		this(rawDriftMeanAbsMeters,
				pressureAnchorCorrectionMeanAbsMeters,
				apogeeTimeErrorBeforeSec,
				apogeeTimeErrorAfterSec,
				altitudeRmseBeforeMeters,
				altitudeRmseAfterMeters,
				Double.NaN,
				0,
				Double.NaN,
				Double.NaN,
				Double.NaN,
				Double.NaN);
	}

	public VerticalIntegratorDiagnostics(double rawDriftMeanAbsMeters,
										 double pressureAnchorCorrectionMeanAbsMeters,
										 double apogeeTimeErrorBeforeSec,
										 double apogeeTimeErrorAfterSec,
										 double altitudeRmseBeforeMeters,
										 double altitudeRmseAfterMeters,
										 double accelBiasEstimateMetersPerSec2,
										 int preLaunchSampleCount,
										 double launchDetectedTimeSec,
										 double burnoutDetectedTimeSec,
										 double apogeeDetectedTimeSec,
										 double deploymentDetectedTimeSec) {
		this.rawDriftMeanAbsMeters = rawDriftMeanAbsMeters;
		this.pressureAnchorCorrectionMeanAbsMeters = pressureAnchorCorrectionMeanAbsMeters;
		this.apogeeTimeErrorBeforeSec = apogeeTimeErrorBeforeSec;
		this.apogeeTimeErrorAfterSec = apogeeTimeErrorAfterSec;
		this.altitudeRmseBeforeMeters = altitudeRmseBeforeMeters;
		this.altitudeRmseAfterMeters = altitudeRmseAfterMeters;
		this.accelBiasEstimateMetersPerSec2 = accelBiasEstimateMetersPerSec2;
		this.preLaunchSampleCount = Math.max(0, preLaunchSampleCount);
		this.launchDetectedTimeSec = launchDetectedTimeSec;
		this.burnoutDetectedTimeSec = burnoutDetectedTimeSec;
		this.apogeeDetectedTimeSec = apogeeDetectedTimeSec;
		this.deploymentDetectedTimeSec = deploymentDetectedTimeSec;
	}

	public double getRawDriftMeanAbsMeters() {
		return rawDriftMeanAbsMeters;
	}

	public double getPressureAnchorCorrectionMeanAbsMeters() {
		return pressureAnchorCorrectionMeanAbsMeters;
	}

	public double getApogeeTimeErrorBeforeSec() {
		return apogeeTimeErrorBeforeSec;
	}

	public double getApogeeTimeErrorAfterSec() {
		return apogeeTimeErrorAfterSec;
	}

	public double getAltitudeRmseBeforeMeters() {
		return altitudeRmseBeforeMeters;
	}

	public double getAltitudeRmseAfterMeters() {
		return altitudeRmseAfterMeters;
	}

	public double getAccelBiasEstimateMetersPerSec2() {
		return accelBiasEstimateMetersPerSec2;
	}

	public int getPreLaunchSampleCount() {
		return preLaunchSampleCount;
	}

	public double getLaunchDetectedTimeSec() {
		return launchDetectedTimeSec;
	}

	public double getBurnoutDetectedTimeSec() {
		return burnoutDetectedTimeSec;
	}

	public double getApogeeDetectedTimeSec() {
		return apogeeDetectedTimeSec;
	}

	public double getDeploymentDetectedTimeSec() {
		return deploymentDetectedTimeSec;
	}
}

package info.openrocket.core.tuning;

public final class ResidualMetrics {
	public static final ResidualMetrics EMPTY = new ResidualMetrics(0, Double.NaN, Double.NaN, Double.NaN, Double.NaN);

	private final int sampleCount;
	private final double signedMeanError;
	private final double mae;
	private final double rmse;
	private final double nrmsePercent;

	public ResidualMetrics(int sampleCount,
						   double signedMeanError,
						   double mae,
						   double rmse,
						   double nrmsePercent) {
		this.sampleCount = Math.max(0, sampleCount);
		this.signedMeanError = signedMeanError;
		this.mae = mae;
		this.rmse = rmse;
		this.nrmsePercent = nrmsePercent;
	}

	public int getSampleCount() {
		return sampleCount;
	}

	public double getSignedMeanError() {
		return signedMeanError;
	}

	public double getMae() {
		return mae;
	}

	public double getRmse() {
		return rmse;
	}

	public double getNrmsePercent() {
		return nrmsePercent;
	}
}

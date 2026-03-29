package info.openrocket.core.tuning;

public class ChannelMetrics {
	private final int sampleCount;
	private final double coverageRatio;
	private final double mapePercent;
	private final double smapePercent;
	private final double nrmsePercent;

	public ChannelMetrics(int sampleCount,
						 double coverageRatio,
						 double mapePercent,
						 double smapePercent,
						 double nrmsePercent) {
		this.sampleCount = sampleCount;
		this.coverageRatio = coverageRatio;
		this.mapePercent = mapePercent;
		this.smapePercent = smapePercent;
		this.nrmsePercent = nrmsePercent;
	}

	public int getSampleCount() {
		return sampleCount;
	}

	public double getCoverageRatio() {
		return coverageRatio;
	}

	public double getMapePercent() {
		return mapePercent;
	}

	public double getSmapePercent() {
		return smapePercent;
	}

	public double getNrmsePercent() {
		return nrmsePercent;
	}
}

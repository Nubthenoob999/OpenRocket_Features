package info.openrocket.core.tuning;

public final class DragResidualMetrics {
	public static final DragResidualMetrics EMPTY =
			new DragResidualMetrics(0, Double.NaN, Double.NaN, Double.NaN, Double.NaN);

	private final int sampleCount;
	private final double truthMean;
	private final double candidateMean;
	private final double delta;
	private final double deltaPercent;

	public DragResidualMetrics(int sampleCount,
							   double truthMean,
							   double candidateMean,
							   double delta,
							   double deltaPercent) {
		this.sampleCount = Math.max(0, sampleCount);
		this.truthMean = truthMean;
		this.candidateMean = candidateMean;
		this.delta = delta;
		this.deltaPercent = deltaPercent;
	}

	public int getSampleCount() {
		return sampleCount;
	}

	public double getTruthMean() {
		return truthMean;
	}

	public double getCandidateMean() {
		return candidateMean;
	}

	public double getDelta() {
		return delta;
	}

	public double getDeltaPercent() {
		return deltaPercent;
	}
}

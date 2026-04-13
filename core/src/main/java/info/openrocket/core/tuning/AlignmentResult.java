package info.openrocket.core.tuning;

public final class AlignmentResult {
	public static final AlignmentResult NONE = new AlignmentResult("", 0.0, Double.NaN, 0);

	private final String channel;
	private final double lagSec;
	private final double quality;
	private final int matchedSamples;

	public AlignmentResult(String channel, double lagSec, double quality, int matchedSamples) {
		this.channel = channel == null ? "" : channel;
		this.lagSec = lagSec;
		this.quality = quality;
		this.matchedSamples = Math.max(0, matchedSamples);
	}

	public String getChannel() {
		return channel;
	}

	public double getLagSec() {
		return lagSec;
	}

	public double getQuality() {
		return quality;
	}

	public int getMatchedSamples() {
		return matchedSamples;
	}
}

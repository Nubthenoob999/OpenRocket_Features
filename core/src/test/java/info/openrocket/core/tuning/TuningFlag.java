package info.openrocket.core.tuning;

public final class TuningFlag {
	private final String channel;
	private final String equationGroup;
	private final ScoreSeverity severity;
	private final double channelScore;

	public TuningFlag(String channel, String equationGroup, ScoreSeverity severity, double channelScore) {
		this.channel = channel;
		this.equationGroup = equationGroup;
		this.severity = severity;
		this.channelScore = channelScore;
	}

	public String getChannel() {
		return channel;
	}

	public String getEquationGroup() {
		return equationGroup;
	}

	public ScoreSeverity getSeverity() {
		return severity;
	}

	public double getChannelScore() {
		return channelScore;
	}
}
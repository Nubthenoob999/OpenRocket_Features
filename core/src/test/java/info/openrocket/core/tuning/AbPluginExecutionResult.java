package info.openrocket.core.tuning;

public final class AbPluginExecutionResult {
	public enum Status {
		SKIPPED,
		SUCCEEDED,
		FAILED,
		TIMED_OUT
	}

	private final Status status;
	private final int exitCode;
	private final String message;

	public AbPluginExecutionResult(Status status, int exitCode, String message) {
		this.status = status;
		this.exitCode = exitCode;
		this.message = message;
	}

	public Status getStatus() {
		return status;
	}

	public int getExitCode() {
		return exitCode;
	}

	public String getMessage() {
		return message;
	}
}
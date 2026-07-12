package info.openrocket.core.aerodynamics.physicsaero.validation.rasaero;

public final class RasaeroParseException extends IllegalArgumentException {
	private final Reason reason;
	public RasaeroParseException(Reason reason, String message) { super(reason + ":" + message); this.reason = reason; }
	public Reason reason() { return reason; }
	public enum Reason { MISSING_COLUMN, DUPLICATE_COLUMN, NONNUMERIC_CELL, MALFORMED_ROW, EMPTY_INPUT, INVALID_MANIFEST }
}

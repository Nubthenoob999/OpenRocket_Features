package info.openrocket.core.aerodynamics.physicsaero.api;

import java.util.List;

public record ValidityAssessment(Status status, List<String> reasonCodes) {
	public enum Status { VALID, WARNING, INVALID }
	public ValidityAssessment { reasonCodes = List.copyOf(reasonCodes); }
	public static ValidityAssessment valid() { return new ValidityAssessment(Status.VALID, List.of()); }
	public static ValidityAssessment invalid(String reason) { return new ValidityAssessment(Status.INVALID, List.of(reason)); }
	public boolean isValid() { return status != Status.INVALID; }
}

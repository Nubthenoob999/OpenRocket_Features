package info.openrocket.core.aerodynamics.physicsaero.api;

public interface AerodynamicCorrelation<I> {
	CorrelationMetadata metadata();
	ValidityAssessment assessValidity(I input);
	CorrelationResult evaluate(I input);
}

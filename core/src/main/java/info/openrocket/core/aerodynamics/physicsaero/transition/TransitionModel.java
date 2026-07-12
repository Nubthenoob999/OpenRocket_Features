package info.openrocket.core.aerodynamics.physicsaero.transition;
public interface TransitionModel {
	TransitionDecision evaluate(double reynoldsS, double reynoldsTheta, double shapeFactor, double lambdaTheta, double turbulencePercent);
	String methodId();
}

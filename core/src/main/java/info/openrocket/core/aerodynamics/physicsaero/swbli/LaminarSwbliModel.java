package info.openrocket.core.aerodynamics.physicsaero.swbli;
public final class LaminarSwbliModel {
	public static InteractionRiskMap.Calibration defaultCalibration(){return new InteractionRiskMap.Calibration(
			"Hakkinen-Greber-Trilling-Abarbanel-1959-envelope","v1",.8,.8,2,.8,500,
			.34,.18,.22,.16,.10,.22,.48,.70,40,4000,2.0);}
	public InteractionRiskMap.Evaluation evaluate(ShockInteractionInput input,double thermalFactor){return new InteractionRiskMap(defaultCalibration()).evaluate(input,.004,thermalFactor);}
}

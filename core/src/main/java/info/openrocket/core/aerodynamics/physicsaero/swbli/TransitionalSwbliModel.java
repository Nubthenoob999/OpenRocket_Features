package info.openrocket.core.aerodynamics.physicsaero.swbli;
/** Transitional calibration is separate; it is not an average of laminar and turbulent answers. */
public final class TransitionalSwbliModel {
	public static InteractionRiskMap.Calibration defaultCalibration(){return new InteractionRiskMap.Calibration(
			"Delery-1985-transitional-conservative-envelope","v1",.85,.85,1.6,.8,425,
			.34,.18,.24,.16,.08,.24,.52,.73,60,10000,2.0);}
	public InteractionRiskMap.Evaluation evaluate(ShockInteractionInput input,double thermalFactor){return new InteractionRiskMap(defaultCalibration()).evaluate(input,.0035,thermalFactor);}
}

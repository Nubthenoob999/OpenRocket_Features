package info.openrocket.core.aerodynamics.physicsaero.swbli;
public final class TurbulentSwbliModel {
	public static InteractionRiskMap.Calibration defaultCalibration(){return new InteractionRiskMap.Calibration(
			"Delery-1985-compression-interaction-envelope","v1",1.0,1.0,1.5,.9,350,
			.32,.18,.25,.17,.08,.28,.58,.78,100,20000,2.2);}
	public InteractionRiskMap.Evaluation evaluate(ShockInteractionInput input,double thermalFactor){return new InteractionRiskMap(defaultCalibration()).evaluate(input,.003,thermalFactor);}
}

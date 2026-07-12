package info.openrocket.core.aerodynamics.physicsaero.thermal;

import info.openrocket.core.aerodynamics.physicsaero.separation.SeparationState;

/** Engineering Reynolds/Chilton-Colburn diagnostic, never a structural thermal load. */
public final class ReynoldsAnalogyHeatFluxDiagnostic {
	public record Result(double heatFluxWM2,double stantonNumber,boolean valid,String reason,String methodId){ }
	public Result evaluate(double cf,double density,double velocity,double cp,double recoveryTemperature,double wallTemperature,double prandtl,SeparationState separation){
		if(separation==SeparationState.SEPARATED||separation==SeparationState.INVALID)return new Result(Double.NaN,Double.NaN,false,"SEPARATED_HEAT_TRANSFER_MODEL_UNAVAILABLE","reynolds-analogy-diagnostic-v1");
		if(cf<0||density<=0||velocity<0||cp<=0||prandtl<=0)throw new IllegalArgumentException("invalid heat diagnostic input");
		double st=.5*cf*Math.pow(prandtl,-2.0/3.0);return new Result(st*density*velocity*cp*(recoveryTemperature-wallTemperature),st,true,
				"ENGINEERING_DIAGNOSTIC_NOT_STRUCTURAL_THERMAL_ANALYSIS","reynolds-analogy-diagnostic-v1");
	}
}

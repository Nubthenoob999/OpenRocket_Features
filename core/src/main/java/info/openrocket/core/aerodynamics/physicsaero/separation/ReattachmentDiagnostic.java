package info.openrocket.core.aerodynamics.physicsaero.separation;
/** Reattachment remains diagnostic in Version 1. */
public final class ReattachmentDiagnostic {
	public record Result(SeparationState state,String reason,double confidence){ }
	public Result evaluate(boolean favorableGradient,double skinFrictionCoefficient,int persistentStations){
		if(favorableGradient&&skinFrictionCoefficient>0&&persistentStations>=3)return new Result(SeparationState.REATTACHMENT_CANDIDATE,"FAVORABLE_GRADIENT_AND_POSITIVE_SHEAR",.35);
		return new Result(SeparationState.SEPARATED,"REATTACHMENT_UNRESOLVED",.25);
	}
}

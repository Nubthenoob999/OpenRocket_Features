package info.openrocket.core.aerodynamics.physicsaero.transonic;
public final class FinDragDivergenceEstimator {
	public record Estimate(double dragDivergenceMach,double confidence,String methodId){ }
	public Estimate estimate(double thicknessRatio,double halfChordSweep,double liftCoefficient,double calibration){double c=Math.cos(halfChordSweep);double m=(calibration-liftCoefficient/(10*c*c)-thicknessRatio/c)/c+.04;return new Estimate(Math.max(.55,Math.min(1.05,m)),.6,"MISSILE_FIN_KORN_MASON_CALIBRATED_V1");}
}

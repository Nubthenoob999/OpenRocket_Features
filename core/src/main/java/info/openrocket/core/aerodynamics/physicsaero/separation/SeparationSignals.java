package info.openrocket.core.aerodynamics.physicsaero.separation;

import info.openrocket.core.aerodynamics.physicsaero.boundarylayer.BoundaryLayerState;
import info.openrocket.core.aerodynamics.physicsaero.boundarylayer.BoundaryLayerStation;

/** All raw signals used by separation decisions; none are replaced by the aggregate severity. */
public record SeparationSignals(double skinFrictionCoefficient, double wallShearPa, double shapeFactor,
		double thwaitesLambda, double clauserBeta, double velocityGradientPerS, double pressureGradientPaM,
		double displacementGrowthRate, double skinFrictionFallRate, double shockRisk, double pressureRatio,
		double normalMach, double totalPressureRatio, double wallToRecoveryTemperatureRatio,
		double reynoldsTheta, boolean closureBreakdown, boolean reversedFlow) {
	public SeparationSignals {
		if (!finite(skinFrictionCoefficient,wallShearPa,shapeFactor,thwaitesLambda,clauserBeta,velocityGradientPerS,
				pressureGradientPaM,displacementGrowthRate,skinFrictionFallRate,shockRisk,pressureRatio,normalMach,
				totalPressureRatio,wallToRecoveryTemperatureRatio,reynoldsTheta)) throw new IllegalArgumentException("non-finite separation signals");
	}
	public static SeparationSignals from(BoundaryLayerStation station, BoundaryLayerState state,
			BoundaryLayerState previous, double recoveryTemperatureK) {
		double beta=state.wallShearPa()>0?state.displacementThicknessM()/state.wallShearPa()*station.pressureGradientPaM():
				station.pressureGradientPaM()>0?Double.MAX_VALUE:0;
		double ds=Math.max(station.deltaSM(),1e-12);
		double growth=previous==null?0:(state.displacementThicknessM()-previous.displacementThicknessM())/ds;
		double fall=previous==null?0:(previous.skinFrictionCoefficient()-state.skinFrictionCoefficient())/ds;
		return new SeparationSignals(state.skinFrictionCoefficient(),state.wallShearPa(),state.shapeFactor(),state.thwaitesLambda(),
				beta,station.velocityGradientPerS(),station.pressureGradientPaM(),growth,fall,0,1,station.mach(),1,
				state.wallTemperatureK()/recoveryTemperatureK,state.reynoldsTheta(),false,state.skinFrictionCoefficient()<0);
	}
	private static boolean finite(double... values){for(double v:values)if(!Double.isFinite(v))return false;return true;}
}

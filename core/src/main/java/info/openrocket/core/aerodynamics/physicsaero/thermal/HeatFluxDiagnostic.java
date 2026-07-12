package info.openrocket.core.aerodynamics.physicsaero.thermal;
/** Optional Reynolds-analogy diagnostic; it is not a structural thermal load model. */
public final class HeatFluxDiagnostic {
	public double heatFluxWM2(double cf,double density,double velocity,double cp,double recoveryTemperature,double wallTemperature,double prandtl) {
		if (cf<0||density<=0||velocity<0||cp<=0||prandtl<=0) throw new IllegalArgumentException("invalid heat-flux state");
		double stanton=.5*cf*Math.pow(prandtl,-2.0/3.0);
		return stanton*density*velocity*cp*(recoveryTemperature-wallTemperature);
	}
}

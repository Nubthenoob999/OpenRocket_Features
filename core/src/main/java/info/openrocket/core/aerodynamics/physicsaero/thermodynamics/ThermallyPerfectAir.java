package info.openrocket.core.aerodynamics.physicsaero.thermodynamics;

import java.util.Map;
import info.openrocket.core.aerodynamics.physicsaero.gasdynamics.ThermodynamicModel;
import info.openrocket.core.aerodynamics.physicsaero.thermal.SutherlandViscosity;
public final class ThermallyPerfectAir implements ThermodynamicModel {
	public static final String SOURCE="NASA Glenn coefficients, McBride et al. TP-2002-211556";
	public static final NasaPolynomialSpecies N2=new NasaPolynomialSpecies("N2",.0280134,2,0,200,6000,new double[]{2.95257626,1.39690040e-3,-4.92631603e-7,7.86010367e-11,-4.60755321e-15,-923.948645,5.87188762},SOURCE);
	public static final NasaPolynomialSpecies O2=new NasaPolynomialSpecies("O2",.0319988,0,2,200,6000,new double[]{3.28253784,1.48308754e-3,-7.57966669e-7,2.09470555e-10,-2.16717794e-14,-1088.45772,5.45323129},SOURCE);
	private final AirMixture mixture=new AirMixture(Map.of(N2,.7905,O2,.2095));
	@Override public double gamma(double t){double cp=mixture.cpMass(t);return cp/(cp-mixture.gasConstant());}
	@Override public double gasConstant(){return mixture.gasConstant();}
	@Override public double viscosity(double t){return new SutherlandViscosity().viscosityPaS(t);}
	public double enthalpy(double t){return mixture.enthalpyMass(t);}
	public double entropy(double t,double p){return mixture.entropyMass(t,p);}
	public double totalTemperature(double staticT,double velocity){double target=enthalpy(staticT)+velocity*velocity/2,lo=staticT,hi=6000;if(target>enthalpy(hi))throw new IllegalArgumentException("total enthalpy outside frozen-air polynomial range");for(int i=0;i<80;i++){double mid=.5*(lo+hi);if(enthalpy(mid)<target)lo=mid;else hi=mid;}return .5*(lo+hi);}
	public AirMixture mixture(){return mixture;}
}

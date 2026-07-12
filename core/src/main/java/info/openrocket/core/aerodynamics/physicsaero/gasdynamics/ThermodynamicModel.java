package info.openrocket.core.aerodynamics.physicsaero.gasdynamics;

public interface ThermodynamicModel {
	double gamma(double temperatureK);
	double gasConstant();
	double viscosity(double temperatureK);
	default double speedOfSound(double temperatureK) {
		return Math.sqrt(gamma(temperatureK) * gasConstant() * temperatureK);
	}
}

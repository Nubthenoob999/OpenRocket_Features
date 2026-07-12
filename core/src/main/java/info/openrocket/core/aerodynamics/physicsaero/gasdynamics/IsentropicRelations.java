package info.openrocket.core.aerodynamics.physicsaero.gasdynamics;

import info.openrocket.core.aerodynamics.physicsaero.flow.GasState;
import info.openrocket.core.aerodynamics.physicsaero.flow.TotalState;

public final class IsentropicRelations {
	private IsentropicRelations() {}
	public static double temperatureRatio(double mach, double gamma) { validate(mach, gamma); return 1 + 0.5 * (gamma - 1) * mach * mach; }
	public static double pressureRatio(double mach, double gamma) { return Math.pow(temperatureRatio(mach, gamma), gamma / (gamma - 1)); }
	public static double densityRatio(double mach, double gamma) { return Math.pow(temperatureRatio(mach, gamma), 1 / (gamma - 1)); }
	public static TotalState totalState(GasState state, ThermodynamicModel model) {
		double g = model.gamma(state.temperatureK());
		return new TotalState(state.pressurePa() * pressureRatio(state.mach(), g),
				state.temperatureK() * temperatureRatio(state.mach(), g),
				state.densityKgM3() * densityRatio(state.mach(), g));
	}
	public static GasState staticState(TotalState total, double mach, ThermodynamicModel model) {
		double g = model.gamma(total.temperatureK());
		double tr = temperatureRatio(mach, g);
		double t = total.temperatureK() / tr;
		double p = total.pressurePa() / pressureRatio(mach, g);
		double rho = p / (model.gasConstant() * t);
		return new GasState(mach, p, t, rho, mach * model.speedOfSound(t));
	}
	public static double pressureCoefficient(double pressurePa, double freestreamPressurePa, double dynamicPressurePa) {
		if (dynamicPressurePa <= 0) throw new GasDynamicsException("dynamic pressure must be positive");
		return (pressurePa - freestreamPressurePa) / dynamicPressurePa;
	}
	private static void validate(double mach, double gamma) {
		if (mach < 0 || gamma <= 1 || !Double.isFinite(mach + gamma)) throw new GasDynamicsException("invalid isentropic input");
	}
}

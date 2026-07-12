package info.openrocket.core.aerodynamics.physicsaero.thermal;
public final class RecoveryTemperatureModel {
	public double recoveryTemperatureK(double edgeTemperatureK, double mach, double gamma, double prandtl, boolean turbulent) {
		double recovery = turbulent ? Math.cbrt(prandtl) : Math.sqrt(prandtl);
		return edgeTemperatureK * (1 + recovery * (gamma - 1) * 0.5 * mach * mach);
	}
}

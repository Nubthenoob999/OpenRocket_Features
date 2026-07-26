package info.openrocket.core.aerodynamics.physicsaero.thermal;
public final class RecoveryTemperatureModel {
	public double recoveryTemperatureK(double edgeTemperatureK, double mach, double gamma, double prandtl, boolean turbulent) {
		return recoveryTemperatureK(edgeTemperatureK, mach, gamma, prandtl, turbulent ? 1 : 0);
	}
	/** Interpolates the laminar and turbulent recovery factors through transition. */
	public double recoveryTemperatureK(double edgeTemperatureK, double mach, double gamma, double prandtl,
			double intermittency) {
		if (!(edgeTemperatureK > 0) || mach < 0 || gamma <= 1 || prandtl <= 0
				|| intermittency < 0 || intermittency > 1) {
			throw new IllegalArgumentException("invalid recovery-temperature state");
		}
		double recovery = (1 - intermittency) * Math.sqrt(prandtl)
				+ intermittency * Math.cbrt(prandtl);
		return edgeTemperatureK * (1 + recovery * (gamma - 1) * 0.5 * mach * mach);
	}
}

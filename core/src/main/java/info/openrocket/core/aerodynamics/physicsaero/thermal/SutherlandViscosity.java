package info.openrocket.core.aerodynamics.physicsaero.thermal;
/** Sutherland law for dry air; constants are centralized here. */
public final class SutherlandViscosity {
	public static final double T0_K = 273.15, MU0_PA_S = 1.716e-5, S_K = 110.4;
	public double viscosityPaS(double temperatureK) {
		if (temperatureK <= 0 || !Double.isFinite(temperatureK)) throw new IllegalArgumentException("invalid temperature");
		return MU0_PA_S * Math.pow(temperatureK / T0_K, 1.5) * (T0_K + S_K) / (temperatureK + S_K);
	}
}
